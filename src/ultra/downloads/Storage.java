package ultra.downloads;

import android.content.*;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

final class Storage {
    static String verify(File f) throws Exception {
        String ext=Transfer.container(f);MediaExtractor extractor=new MediaExtractor();
        try {
            extractor.setDataSource(f.getAbsolutePath());boolean found=false;
            for(int n=0;n<extractor.getTrackCount();n++){
                MediaFormat format=extractor.getTrackFormat(n);String mime=format.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){extractor.selectTrack(n);found=true;break;}
            }
            if(!found)throw new IOException("Downloaded file does not contain a playable audio track");
            ByteBuffer sample=ByteBuffer.allocate(256*1024);if(extractor.readSampleData(sample,0)<=0)throw new IOException("Downloaded file contains no audio samples");
        } finally {extractor.release();}
        return ext;
    }
    static boolean exists(Context c,String value){if(value==null||value.isEmpty())return false;try{Uri u=Uri.parse(value);if("content".equals(u.getScheme())){try(android.content.res.AssetFileDescriptor f=c.getContentResolver().openAssetFileDescriptor(u,"r")){return f!=null;}}return new File(value).isFile();}catch(Exception e){return false;}}
    static String mime(String ext){return ext.equals("mp3")?"audio/mpeg":ext.equals("m4a")?"audio/mp4":ext.equals("webm")?"audio/webm":ext.equals("ogg")?"audio/ogg":"audio/flac";}
    static String subfolder(Context c,Library.Item i){String mode=Library.prefs(c).getString("organization","flat");if(mode.equals("artist"))return Transfer.safeName(i.artist.isEmpty()?"Unknown Artist":i.artist);if(mode.equals("album"))return Transfer.safeName(i.artist.isEmpty()?"Unknown Artist":i.artist)+"/"+Transfer.safeName(i.album.isEmpty()?"Unknown Album":i.album);return "";}
    static Uri directory(Context c,Uri parent,String name) throws Exception {
        String parentId=DocumentsContract.getDocumentId(parent);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(parent,parentId);
        try(Cursor rows=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(rows!=null)while(rows.moveToNext())if(name.equals(rows.getString(1))&&DocumentsContract.Document.MIME_TYPE_DIR.equals(rows.getString(2)))return DocumentsContract.buildDocumentUriUsingTree(parent,rows.getString(0));
        }
        Uri u=DocumentsContract.createDocument(c.getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,name);
        if(u==null)throw new IOException("Cannot create the selected download subfolder");return u;
    }
    static String publish(Context c,Library.Item i,File source,String ext,Transfer.Observer observer)throws Exception {
        String title=Transfer.safeName(i.title),name=title+" ["+i.id+"]."+ext;
        String type=mime(ext),sub=subfolder(c,i);Uri destination=null;File legacy=null;
        boolean mediaStore=false;
        try {
            if(!i.tree.isEmpty()){
                boolean granted=false;for(UriPermission p:c.getContentResolver().getPersistedUriPermissions())if(p.getUri().toString().equals(i.tree)&&p.isWritePermission())granted=true;
                if(!granted)throw new IOException("Selected download folder access was revoked. Choose it again in Downloads settings.");
                Uri tree=Uri.parse(i.tree);Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                if(!sub.isEmpty())for(String part:sub.split("/"))parent=directory(c,parent,part);
                destination=DocumentsContract.createDocument(c.getContentResolver(),parent,type,name);
                if(destination==null)throw new IOException("Selected download folder is unavailable");
            }else if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,type);values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Music/"+i.folder+(sub.isEmpty()?"":"/"+sub));values.put(MediaStore.MediaColumns.IS_PENDING,1);values.put(MediaStore.Audio.Media.TITLE,i.title);values.put(MediaStore.Audio.Media.ARTIST,i.artist);values.put(MediaStore.Audio.Media.ALBUM,i.album);
                destination=c.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);mediaStore=true;
                if(destination==null)throw new IOException("Android could not create an audio-library entry");
            }else{
                if(c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new IOException("Storage permission is required. Open Downloads and grant it.");
                File directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),i.folder+(sub.isEmpty()?"":"/"+sub));if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create the Music folder");legacy=new File(directory,name);for(int n=2;legacy.exists();n++)legacy=new File(directory,title+" ["+i.id+"] ("+n+")."+ext);
            }
            i.uri=legacy!=null?legacy.getAbsolutePath():destination.toString();Library.save(c);
            try(InputStream in=new FileInputStream(source);OutputStream out=legacy!=null?new FileOutputStream(legacy):c.getContentResolver().openOutputStream(destination,"w")){
                if(out==null)throw new IOException("Could not open the download destination");byte[] buffer=new byte[65536];int got;while((got=in.read(buffer))!=-1){Transfer.check(observer);out.write(buffer,0,got);}out.flush();
            }
            Transfer.check(observer);
            if(mediaStore){ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);if(c.getContentResolver().update(destination,done,null,null)!=1)throw new IOException("Android could not finalize the audio file");}
            if(legacy!=null){MediaScannerConnection.scanFile(c,new String[]{legacy.getAbsolutePath()},new String[]{type},null);return legacy.getAbsolutePath();}
            return destination.toString();
        }catch(Exception e){if(destination!=null)try{if(DocumentsContract.isDocumentUri(c,destination))DocumentsContract.deleteDocument(c.getContentResolver(),destination);else c.getContentResolver().delete(destination,null,null);}catch(Exception cleanup){e.addSuppressed(cleanup);}if(legacy!=null&&legacy.exists()&&!legacy.delete())e.addSuppressed(new IOException("Could not remove incomplete output"));if(!exists(c,i.uri)){i.uri="";try{Library.save(c);}catch(Exception save){e.addSuppressed(save);}}throw e;}
    }
    static void delete(Context c,Library.Item i)throws Exception {
        if(i.imported)throw new IOException("This is an existing device file. Remove it from Downloads without deleting the original.");if(i.uri.isEmpty())return;Uri u=Uri.parse(i.uri);
        if("content".equals(u.getScheme())){
            boolean removed=DocumentsContract.isDocumentUri(c,u)?DocumentsContract.deleteDocument(c.getContentResolver(),u):c.getContentResolver().delete(u,null,null)>0;
            if(!removed&&exists(c,i.uri))throw new IOException("Android did not delete the audio file");
        }else{File f=new File(i.uri);if(f.exists()&&!f.delete())throw new IOException("Could not delete the audio file");}
    }
}
