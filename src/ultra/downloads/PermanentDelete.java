package ultra.downloads;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;import android.os.Bundle;
import android.provider.*;
import java.io.*;
import java.util.*;

/** A confirmed, frozen list of exact device files. Never expands folders or deletes online data. */
final class PermanentDelete {
 static final int REQUEST=928;
 static final class Target {
  final String id,uri,title; final boolean imported;
  Target(Library.Item i){id=i.id;uri=i.uri;title=i.title;imported=i.imported;}
  Target(Bundle b){id=b.getString("id","");uri=b.getString("uri","");title=b.getString("title","");imported=b.getBoolean("imported");}
  Bundle bundle(){Bundle b=new Bundle();b.putString("id",id);b.putString("uri",uri);b.putString("title",title);b.putBoolean("imported",imported);return b;}
 }
 final Activity host;final Context context;final Runnable refresh;
 final ArrayList<Target> targets=new ArrayList<>();
 int index,deleted,failed,retried=-1; volatile boolean waiting,busy,systemDeleted;
 PermanentDelete(Activity host,Context context,List<Library.Item> items,Runnable refresh){this.host=host;this.context=context;this.refresh=refresh;HashSet<String> seen=new HashSet<>();for(Library.Item i:items)if(seen.add(i.id))targets.add(new Target(i));}
 Bundle save(){if(!waiting)return null;Bundle b=new Bundle();ArrayList<Bundle> list=new ArrayList<>();for(Target t:targets)list.add(t.bundle());b.putParcelableArrayList("targets",list);b.putInt("index",index);b.putInt("deleted",deleted);b.putInt("failed",failed);b.putInt("retried",retried);b.putBoolean("systemDeleted",systemDeleted);return b;}
 static PermanentDelete restore(Activity host,Context c,Bundle b,Runnable refresh){if(b==null)return null;PermanentDelete d=new PermanentDelete(host,c,Collections.<Library.Item>emptyList(),refresh);ArrayList<Bundle> list=b.getParcelableArrayList("targets");if(list==null)return null;for(Bundle t:list)d.targets.add(new Target(t));d.index=b.getInt("index");d.deleted=b.getInt("deleted");d.failed=b.getInt("failed");d.retried=b.getInt("retried",-1);d.systemDeleted=b.getBoolean("systemDeleted");d.waiting=d.busy=true;return d;}
 static boolean media(String value){Uri u=Uri.parse(value);return "content".equals(u.getScheme())&&"media".equals(u.getAuthority())&&u.getPath()!=null&&u.getPath().matches("/[^/]+/audio/media/[0-9]+");}
 static boolean absent(Context c,String value)throws Exception{android.database.Cursor cursor=c.getContentResolver().query(Uri.parse(value),new String[]{"_id"},null,null,null);if(cursor==null)throw new IOException("Android could not verify deletion");try{return cursor.getCount()==0;}finally{cursor.close();}}
 boolean unchanged(Target t)throws Exception{Library.Item now=Library.get(context,t.id);return now!=null&&now.uri.equals(t.uri)&&now.imported==t.imported;}
 void start(){
  if(busy)return;busy=true;
  try{for(Target t:targets)if(!unchanged(t)||DownloadsService.running(t.id))throw new IOException();}catch(Exception e){busy=false;Hooks.message("The selected files changed or a download is active. Select them again. Nothing was deleted.");refresh.run();return;}
  if(Build.VERSION.SDK_INT>=30){ArrayList<Uri> media=new ArrayList<>();for(Target t:targets)if(media(t.uri))media.add(Uri.parse(t.uri));
   if(!media.isEmpty())try{PendingIntent request=MediaStore.createDeleteRequest(context.getContentResolver(),media);waiting=true;systemDeleted=true;host.startIntentSenderForResult(request.getIntentSender(),REQUEST,null,0,0,0);return;}catch(Exception e){waiting=false;busy=false;Hooks.message("Android could not open file-deletion permission. Nothing was deleted.");refresh.run();return;}}
  work();
 }
 void result(int result){if(!waiting)return;waiting=false;if(result!=Activity.RESULT_OK){busy=false;Hooks.message(deleted==0?"Deletion cancelled. Files kept.":"Deletion stopped. "+deleted+" files deleted.");refresh.run();return;}work();}
 void work(){new Thread(new Runnable(){public void run(){
  for(;index<targets.size();index++){
   final Target t=targets.get(index);
   try{
    if(!unchanged(t)||DownloadsService.running(t.id))throw new IOException("File changed or download is active");
    if(systemDeleted&&media(t.uri)){if(!absent(context,t.uri))throw new IOException("Android kept the file");}
    else if(!t.uri.isEmpty()){
     Uri u=Uri.parse(t.uri);
     if("content".equals(u.getScheme())){
      if(DocumentsContract.isDocumentUri(context,u)){if(!DocumentsContract.deleteDocument(context.getContentResolver(),u))throw new IOException("File was not deleted");}
      else {if(!media(t.uri))throw new IOException("Not a selected audio file");int rows=context.getContentResolver().delete(u,null,null);if(rows==0&&!absent(context,t.uri))throw new IOException("File was not deleted");}
     }else{
      if(t.imported)throw new IOException("Imported files require a MediaStore audio URI");
      if(u.getScheme()!=null&&!"file".equals(u.getScheme()))throw new IOException("Unsupported file URI");
      File f=new File("file".equals(u.getScheme())?u.getPath():t.uri);if(!f.isFile()&&f.exists())throw new IOException("Not an audio file");if(f.exists()&&!f.delete())throw new IOException("File was not deleted");
     }
    }
    if(!unchanged(t))throw new IOException("Download record changed");
    Library.Item current=Library.get(context,t.id);Library.clearPartial(context,current);Library.remove(context,current);Art.invalidate(context,t.id);deleted++;
   }catch(final Exception e){
    if(Build.VERSION.SDK_INT==29&&e instanceof RecoverableSecurityException&&retried!=index){retried=index;final PendingIntent action=((RecoverableSecurityException)e).getUserAction().getActionIntent();waiting=true;host.runOnUiThread(new Runnable(){public void run(){try{host.startIntentSenderForResult(action.getIntentSender(),REQUEST,null,0,0,0);}catch(Exception ex){waiting=false;failed++;index++;work();}}});return;}
    failed++;
   }
  }
  busy=false;host.runOnUiThread(new Runnable(){public void run(){Hooks.message(deleted+" song"+(deleted==1?"":"s")+" permanently deleted"+(failed>0?". "+failed+" kept because Android denied deletion or the file changed.":". Online playlists unchanged.") );refresh.run();}});
 }},"selected-audio-delete").start();}
}
