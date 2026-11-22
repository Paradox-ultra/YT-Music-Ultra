package ultra.downloads;
import android.app.*;import android.content.*;import android.widget.*;import org.json.*;import java.util.*;
/** A single local transaction. No network writes or audio-file operations. */
final class PlaylistTransfer {
 static boolean movable(Context c,String source){return source!=null&&!source.isEmpty()&&!UnmatchedDownloads.is(source)&&(source.startsWith("local:")||PlaylistIndex.complete(c,source));}
 static JSONArray rows(Context c,JSONObject locals,JSONObject copies,String id)throws JSONException{
  if(id.startsWith("local:")){JSONArray a=locals.optJSONArray(id.substring(6));if(a==null)throw new JSONException("Playlist no longer exists");return a;}
  JSONObject e=LocalPlaylistCopies.seed(c,copies,PlaylistIndex.key(id));if(e==null)throw new JSONException("Local copy is unavailable");JSONArray a=e.optJSONArray("tracks");return a==null?new JSONArray():a;
 }
 static void put(JSONObject locals,JSONObject copies,String id,JSONArray a)throws JSONException{
  if(id.startsWith("local:"))locals.put(id.substring(6),a);else{JSONObject e=copies.getJSONObject(PlaylistIndex.key(id));e.put("tracks",a).put("edited",true);}
 }
 static int transfer(Context c,List<Catalog.Track> tracks,String source,List<String> destinations,boolean move)throws JSONException{
  if(tracks==null||tracks.isEmpty()||destinations==null||destinations.isEmpty())throw new JSONException("Choose songs and a destination");
  synchronized(PlaylistIndex.LOCK){
   if(move&&!movable(c,source))throw new JSONException("Move requires a complete local source playlist");
   JSONObject locals=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}")),copies=LocalPlaylistCopies.read(c);
   LinkedHashSet<String> dest=new LinkedHashSet<>(destinations);if(dest.contains(source))throw new JSONException("Choose a different playlist");
   Set<String> chosen=new HashSet<>();for(Catalog.Track t:tracks)if(t!=null&&t.id!=null&&!t.id.isEmpty())chosen.add(t.id);
   if(chosen.isEmpty())throw new JSONException("No valid songs selected");
   JSONArray src=move?rows(c,locals,copies,source):null;
   if(move){Set<String> present=new HashSet<>();for(int n=0;n<src.length();n++)present.add(src.getJSONObject(n).optString("id"));if(!present.containsAll(chosen))throw new JSONException("Source changed. Select the songs again");}
   int added=0;
   for(String id:dest){JSONArray a=rows(c,locals,copies,id);Set<String> ids=new HashSet<>();for(int n=0;n<a.length();n++)ids.add(a.getJSONObject(n).optString("id"));for(Catalog.Track t:tracks)if(t!=null&&chosen.contains(t.id)&&ids.add(t.id)){a.put(new JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("album",t.album));added++;}put(locals,copies,id,a);}
   if(move){JSONArray kept=new JSONArray();for(int n=0;n<src.length();n++){JSONObject t=src.getJSONObject(n);if(!chosen.contains(t.optString("id")))kept.put(t);}put(locals,copies,source,kept);}
   if(!Library.prefs(c).edit().putString("ultra_local_playlists",locals.toString()).putString(LocalPlaylistCopies.PREF,copies.toString()).commit())throw new JSONException("Could not save playlist changes");
   PlaylistIndex.invalidateCache();return added;
  }
 }
 static void show(final Context c,List<Catalog.Track> input,final String source,final Runnable done){
  final ArrayList<Catalog.Track> tracks=new ArrayList<>();for(Catalog.Track t:input)tracks.add(new Catalog.Track(t.id,t.title,t.artist,t.album));
  final LinkedHashMap<String,String> options=new LinkedHashMap<>();
  for(Map.Entry<String,String> e:PlaylistIndex.groups(c).entrySet())if(!UnmatchedDownloads.is(e.getKey())&&!e.getKey().equals(source))options.put(e.getKey(),e.getValue());
  final ArrayList<String> ids=new ArrayList<>(options.keySet());final String[] labels=new String[ids.size()];for(int n=0;n<ids.size();n++)labels[n]=options.get(ids.get(n));final boolean[] checked=new boolean[ids.size()];
  AlertDialog.Builder b=NativeUi.dialog(c).setTitle("Copy or move "+tracks.size()+(tracks.size()==1?" song":" songs")).setMultiChoiceItems(labels,checked,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int which,boolean on){checked[which]=on;}}).setNegativeButton("Cancel",null).setPositiveButton("Copy",null).setNeutralButton(movable(c,source)?"Move":"New playlist",null);
  final AlertDialog d=b.create();d.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){d.getButton(-1).setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){confirm(c,tracks,source,ids,labels,checked,false,d,done);}});d.getButton(-3).setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){if(movable(c,source))confirm(c,tracks,source,ids,labels,checked,true,d,done);else PlaylistPicker.show(c,tracks,new Runnable(){public void run(){d.dismiss();done.run();}});}});}});d.show();NativeUi.polishDialog(c,d);
 }
 static void confirm(final Context c,final List<Catalog.Track> tracks,final String source,List<String> ids,String[] labels,boolean[] checked,final boolean move,final AlertDialog picker,final Runnable done){
  final ArrayList<String> dest=new ArrayList<>();StringBuilder names=new StringBuilder();for(int n=0;n<checked.length;n++)if(checked[n]){dest.add(ids.get(n));if(names.length()>0)names.append("\n");names.append(labels[n]);}if(dest.isEmpty()){Hooks.message("Choose at least one destination playlist");return;}
  NativeUi.dialog(c).setTitle(move?"Move songs?":"Copy songs?").setMessage(names+"\n\n"+(move?"Remove the selected songs only from the current local playlist after adding them to every chosen destination.":"Keep the songs in their current playlists and add them to every chosen destination.")+" Duplicates are skipped. Audio files and online playlists stay unchanged.").setPositiveButton(move?"Move locally":"Copy locally",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{transfer(c,tracks,source,dest,move);picker.dismiss();Hooks.message(move?"Songs moved locally":"Songs copied locally");done.run();}catch(Exception e){Hooks.message(e.getMessage());}}}).setNegativeButton("Cancel",null).show();
 }
}
