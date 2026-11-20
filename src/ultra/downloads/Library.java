package ultra.downloads;

import android.content.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

final class Library {
    static final Object LOCK=new Object();
    private static LinkedHashMap<String,Item> items;
    static volatile boolean wiping;
    static class Item {
        String localStamp="",artUrl="",id="", title="", artist="", album="", state="queued", error="", uri="", mime="", rendition="", folder="", tree="", quality="best";
        long duration;long bytes,total,created=System.currentTimeMillis();int bitrate,sourceBitrate;
        volatile boolean paused;boolean hidden,imported,recording;
        JSONObject json() throws JSONException {JSONObject j=new JSONObject();j.put("recording",recording);j.put("localStamp",localStamp);j.put("imported",imported);j.put("sourceBitrate",sourceBitrate);j.put("duration",duration);j.put("artUrl",artUrl);j.put("id",id).put("title",title).put("artist",artist).put("album",album).put("state",state).put("error",error).put("uri",uri).put("mime",mime).put("rendition",rendition).put("bytes",bytes).put("total",total).put("created",created).put("bitrate",bitrate).put("hidden",hidden).put("folder",folder).put("tree",tree).put("quality",quality);return j;}
        static Item parse(JSONObject j){Item i=new Item();i.recording=j.optBoolean("recording");i.localStamp=j.optString("localStamp");i.imported=j.optBoolean("imported");i.duration=j.optLong("duration");i.artUrl=j.optString("artUrl");i.id=j.optString("id");i.title=j.optString("title");i.artist=j.optString("artist");i.album=j.optString("album");i.state=j.optString("state");i.error=j.optString("error");i.uri=j.optString("uri");i.mime=j.optString("mime");i.rendition=j.optString("rendition");i.bytes=j.optLong("bytes");i.total=j.optLong("total");i.created=j.optLong("created");i.bitrate=j.optInt("bitrate");i.sourceBitrate=j.optInt("sourceBitrate");i.hidden=j.optBoolean("hidden");i.folder=j.optString("folder");i.tree=j.optString("tree");i.quality=j.optString("quality","best");return i;}
    }
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("ultra_downloads_settings",Context.MODE_PRIVATE);}
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"ultra_downloads_v1.json"));}
    private static void init(Context c) throws Exception {
        if(items!=null)return;LinkedHashMap<String,Item> loaded=new LinkedHashMap<>();AtomicFile f=file(c);
        if(f.getBaseFile().exists()){
            byte[] data=f.readFully();JSONArray a=new JSONArray(new String(data,"UTF-8"));
            for(int n=0;n<a.length();n++){Item i=Item.parse(a.getJSONObject(n));if(!i.id.matches("[A-Za-z0-9_-]{11}"))continue;if(Arrays.asList("queued","resolving","downloading","saving").contains(i.state)){i.state="paused";i.error="Interrupted. Tap Resume.";}loaded.put(i.id,i);}
        }
        items=loaded;
    }
    static ArrayList<Item> all(Context c) throws Exception {synchronized(LOCK){init(c);return new ArrayList<>(items.values());}}
    static Item get(Context c,String id) throws Exception {synchronized(LOCK){init(c);return items.get(id);}}
    static Item add(Context c,String id,String title) throws Exception {
        synchronized(LOCK){init(c);if(wiping)throw new IOException("Downloads are being cleared");Item i=items.get(id);if(i!=null){i.hidden=false;save(c);return i;}
            i=new Item();i.id=id;i.title=title.isEmpty()?id:title;i.folder=Transfer.safeName(prefs(c).getString("folder","YT Music Ultra"));i.tree=prefs(c).getString("tree","");i.quality=prefs(c).getString("quality","best");items.put(id,i);save(c);return i;}
    }
    static int addBatch(Context c,ArrayList<Catalog.Track> tracks)throws Exception{synchronized(LOCK){init(c);if(wiping)throw new IOException("Downloads are being cleared");for(Catalog.Track t:tracks)if(t.id==null||!t.id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Playlist contains an invalid song ID");int queued=0;for(Catalog.Track t:tracks){Item i=items.get(t.id);if(i!=null&&(DownloadsService.running(i.id)||i.state.equals("done")&&Storage.exists(c,i.uri))){i.hidden=false;continue;}if(i==null){i=new Item();i.id=t.id;i.title=t.title.isEmpty()?t.id:t.title;i.folder=Transfer.safeName(prefs(c).getString("folder","YT Music Ultra"));i.tree=prefs(c).getString("tree","");i.quality=prefs(c).getString("quality","best");items.put(i.id,i);}if(i.artist.isEmpty())i.artist=t.artist;if(i.album.isEmpty())i.album=t.album;i.state="queued";i.paused=false;i.hidden=false;i.error="";queued++;}save(c);return queued;}}
    static void save(Context c) throws Exception {synchronized(LOCK){init(c);JSONArray a=new JSONArray();for(Item i:items.values())a.put(i.json());AtomicFile f=file(c);FileOutputStream out=null;try{out=f.startWrite();out.write(a.toString().getBytes("UTF-8"));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}}
    static void forget(Context c,Item i)throws Exception{synchronized(LOCK){init(c);if(!i.paused && Arrays.asList("queued","resolving","downloading","saving").contains(i.state))throw new IOException("Pause this download first");i.hidden=true;save(c);}}
    static void remove(Context c,Item i)throws Exception{synchronized(LOCK){init(c);items.remove(i.id);save(c);}}
    static File temp(Context c,Item i){return new File(new File(c.getNoBackupFilesDir(),"ultra_audio_parts"),i.id+".part");}
    static void clearPartial(Context c,Item i)throws IOException{File f=temp(c,i);for(File x:new File[]{f,new File(f.getPath()+".ranges"),new File(f.getPath()+".ranges.new"),new File(f.getPath()+".map"),new File(f.getPath()+".mp3")})if(x.exists()&&!x.delete())throw new IOException("Could not remove partial audio");}
    /** Commit a device-audio scan once, without touching original audio or concurrent downloads. */
    static int applyImports(Context c, List<Item> incoming) throws Exception {
        synchronized(LOCK) {
            init(c); if(wiping)throw new IOException("Downloads are being cleared");
            LinkedHashMap<String,Item> before=new LinkedHashMap<>(items);int changed=0;
            try {
                Map<String,Item> byUri=new HashMap<>();for(Item old:items.values())if(!old.uri.isEmpty())byUri.put(old.uri,old);
                for(Item next:incoming) {
                    Item same=items.get(next.id);
                    if(same!=null&&!same.imported)continue;
                    if(same!=null&&!same.uri.equals(next.uri))continue;
                    Item source=byUri.get(next.uri);
                    if(source!=null&&!source.imported)continue;
                    if(source!=null){next.hidden=source.hidden;items.remove(source.id);}
                    items.put(next.id,next);byUri.put(next.uri,next);changed++;
                }
                if(changed>0)save(c);
                return changed;
            } catch(Exception e){items=before;throw e;}
        }
    }
}
