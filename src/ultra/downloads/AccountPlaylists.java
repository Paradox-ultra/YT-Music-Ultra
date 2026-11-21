package ultra.downloads;
import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;
/** Legacy account helpers. Downloads never submits account playlist changes. */
final class AccountPlaylists {
 static List<String> videoIds(Context c,List<Catalog.Track> tracks)throws Exception{
  LinkedHashSet<String> ids=new LinkedHashSet<>();
  for(Catalog.Track t:tracks){
   if(t==null||t.id==null||!t.id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Some songs have no YouTube video identity. Use a local playlist for these files.");
   Library.Item item=Library.get(c,t.id);
   if(item!=null&&item.imported&&item.id.equals(LocalAudio.localId(item.uri)))throw new IOException("Some selected songs are device-only audio. Use a local playlist; no online songs were added.");
   ids.add(t.id);
  }
  if(ids.isEmpty())throw new IOException("Select songs first");return new ArrayList<>(ids);
 }
 static JSONObject payload(String playlist,List<String> ids)throws JSONException{
  JSONArray actions=new JSONArray();for(String id:ids)actions.put(new JSONObject().put("action","ACTION_ADD_VIDEO").put("addedVideoId",id).put("dedupeOption","DEDUPE_OPTION_SKIP"));
  JSONObject client=new JSONObject().put("clientName","ANDROID_MUSIC").put("clientVersion","8.30.54").put("hl","en").put("gl","IN");
  return new JSONObject().put("context",new JSONObject().put("client",client)).put("playlistId",PlaylistIndex.key(playlist)).put("actions",actions);
 }
 static void add(Context c,String playlist,List<Catalog.Track> tracks)throws Exception{
  throw new IOException("Online playlist writes are disabled in Downloads. Choose a local copy.");
 }
}
