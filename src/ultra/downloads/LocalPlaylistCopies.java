package ultra.downloads;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Detached device copies. Source cache is read-only; edited copies never refresh or write back. */
final class LocalPlaylistCopies {
 static final String PREF="ultra_playlist_copies_v1";
 static JSONObject read(Context c)throws JSONException{return new JSONObject(Library.prefs(c).getString(PREF,"{}"));}
 static void save(Context c,JSONObject all){Library.prefs(c).edit().putString(PREF,all.toString()).apply();PlaylistIndex.invalidateCache();}
 static JSONObject seed(Context c,JSONObject all,String id)throws JSONException{
  JSONObject old=all.optJSONObject(id),source=LikedPlaylists.entry(c,id);
  if(old!=null&&(old.optBoolean("edited")||old.optBoolean("complete")))return old;
  if(source==null)return old;
  JSONObject next=new JSONObject(source.toString());
  if(!next.has("tracks"))next.put("tracks",new JSONArray());
  next.put("sourceId",id).put("edited",false);
  if(old==null||!old.toString().equals(next.toString()))all.put(id,next);
  return next;
 }
 static Map<String,String> groups(Context c,Map<String,String> roster){
  synchronized(PlaylistIndex.LOCK){
   LinkedHashMap<String,String> out=new LinkedHashMap<>(roster);
   try{JSONObject all=read(c);String before=all.toString();
    for(String id:roster.keySet())seed(c,all,id);
    Iterator<String> it=all.keys();while(it.hasNext()){String id=it.next();JSONObject e=all.optJSONObject(id);if(e!=null)out.put(id,e.optString("name",out.containsKey(id)?out.get(id):"Playlist"));}
    if(!before.equals(all.toString()))save(c,all);
   }catch(JSONException e){Diagnostics.error("local_copies",e);}
   return out;
  }
 }
 static JSONObject entry(Context c,String id){
  if(id==null||id.startsWith("local:")||UnmatchedDownloads.is(id))return null;
  synchronized(PlaylistIndex.LOCK){try{JSONObject all=read(c);String before=all.toString();JSONObject e=seed(c,all,PlaylistIndex.key(id));if(!before.equals(all.toString()))save(c,all);return e;}catch(JSONException e){return null;}}
 }
 static void refresh(Context c,String id){
  synchronized(PlaylistIndex.LOCK){try{JSONObject all=read(c);id=PlaylistIndex.key(id);if(!all.has(id))return;String before=all.toString();seed(c,all,id);if(!before.equals(all.toString()))save(c,all);}catch(JSONException e){Diagnostics.error("local_copies",e);}}
 }
 static int add(Context c,String id,List<Catalog.Track> tracks)throws JSONException{
  synchronized(PlaylistIndex.LOCK){JSONObject all=read(c);id=PlaylistIndex.key(id);JSONObject e=seed(c,all,id);if(e==null)throw new JSONException("Playlist copy is unavailable");
   JSONArray rows=e.optJSONArray("tracks");if(rows==null)rows=new JSONArray();Set<String> ids=new HashSet<>();for(int n=0;n<rows.length();n++){JSONObject t=rows.optJSONObject(n);if(t!=null)ids.add(t.optString("id"));}
   int added=0;for(Catalog.Track t:tracks)if(t!=null&&t.id!=null&&!t.id.isEmpty()&&ids.add(t.id)){rows.put(new JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("album",t.album));added++;}
   e.put("tracks",rows).put("edited",true);all.put(id,e);save(c,all);return added;
  }
 }
 static int remove(Context c,String id,Set<String> ids)throws JSONException{
  synchronized(PlaylistIndex.LOCK){JSONObject all=read(c);id=PlaylistIndex.key(id);JSONObject e=seed(c,all,id);if(e==null)throw new JSONException("Playlist copy is unavailable");JSONArray old=e.optJSONArray("tracks"),rows=new JSONArray();int removed=0;
   if(old!=null)for(int n=0;n<old.length();n++){JSONObject t=old.optJSONObject(n);if(t!=null&&ids.contains(t.optString("id")))removed++;else rows.put(old.get(n));}
   e.put("tracks",rows).put("edited",true);all.put(id,e);save(c,all);return removed;
  }
 }
}
