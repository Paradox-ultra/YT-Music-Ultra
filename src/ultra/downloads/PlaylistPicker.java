package ultra.downloads;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit on-device destinations. Never pretends to edit an account playlist. */
final class PlaylistPicker {
 static ArrayList<String> names(Context c)throws JSONException{
  JSONObject map=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}"));ArrayList<String> names=new ArrayList<>();Iterator<String> it=map.keys();while(it.hasNext())names.add(it.next());Collections.sort(names,String.CASE_INSENSITIVE_ORDER);return names;
 }
 static int add(Context c,String name,List<Catalog.Track> tracks)throws JSONException{
  if(name==null||name.trim().isEmpty())throw new JSONException("Playlist name is empty");
  synchronized(PlaylistIndex.LOCK){
   JSONObject map=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}"));JSONArray a=map.optJSONArray(name);if(a==null)a=new JSONArray();Set<String> ids=new HashSet<>();for(int n=0;n<a.length();n++){JSONObject old=a.optJSONObject(n);if(old!=null)ids.add(old.optString("id"));}int added=0;
   for(Catalog.Track t:tracks)if(t!=null&&t.id!=null&&!t.id.isEmpty()&&ids.add(t.id)){a.put(new JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("album",t.album));added++;}
   map.put(name,a);Library.prefs(c).edit().putString("ultra_local_playlists",map.toString()).apply();PlaylistIndex.invalidateCache();return added;
  }
 }
 static int listHeightDp(int screenHeightDp){return screenHeightDp>0&&screenHeightDp<480?Math.max(96,screenHeightDp-232):Math.max(56,Math.min(280,screenHeightDp-320));}
 static boolean accountEligible(Context c,List<Catalog.Track> tracks){return false;}
 static TextView label(Context c,String text,int size,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;}
 static GradientDrawable rounded(Context c,int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(NativeUi.dp(c,radius));if(color==0xff000000)g.setStroke(NativeUi.dp(c,1),0xff262626);return g;}
 static void section(Context c,LinearLayout list,String title){TextView v=label(c,title,11,0xffaaaaaa);v.setTypeface(Typeface.create("sans-serif-medium",0));v.setLetterSpacing(.02f);v.setPadding(NativeUi.dp(c,4),NativeUi.dp(c,18),0,NativeUi.dp(c,8));list.addView(v);}
 static View destination(Context c,String name,String subtitle,boolean online){
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(NativeUi.dp(c,12),NativeUi.dp(c,10),NativeUi.dp(c,12),NativeUi.dp(c,10));row.setMinimumHeight(NativeUi.dp(c,76));
  row.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22ffffff),rounded(c,0xff000000,16),null));
  FrameLayout tile=new FrameLayout(c);GradientDrawable cover=new GradientDrawable(GradientDrawable.Orientation.TL_BR,online?new int[]{0xff000000,0xff000000}:new int[]{0xff000000,0xff000000});cover.setCornerRadius(NativeUi.dp(c,12));tile.setBackground(cover);
  ImageView icon=new ImageView(c);icon.setImageDrawable(NativeUi.appIcon(c));tile.addView(icon,new FrameLayout.LayoutParams(NativeUi.dp(c,25),NativeUi.dp(c,25),Gravity.CENTER));row.addView(tile,new LinearLayout.LayoutParams(NativeUi.dp(c,48),NativeUi.dp(c,48)));
  LinearLayout text=new LinearLayout(c);text.setOrientation(1);TextView title=label(c,name,16,0xfff5f5f5);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);TextView meta=label(c,subtitle,12,0xffaaaaaa);meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.setPadding(0,NativeUi.dp(c,4),0,0);text.addView(meta);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(NativeUi.dp(c,12));row.addView(text,tp);TextView arrow=label(c,"›",26,0xffaaaaaa);arrow.setPadding(NativeUi.dp(c,8),0,0,0);row.addView(arrow);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=NativeUi.dp(c,8);row.setLayoutParams(lp);row.setTag(online?"account_destination":"local_destination");return row;
 }
 static void show(final Context c,final List<Catalog.Track> tracks,final Runnable done){
  final boolean allowAccount=false;final boolean compact=c.getResources().getConfiguration().screenHeightDp<480;
  LinearLayout body=new LinearLayout(c);body.setOrientation(1);body.setPadding(NativeUi.dp(c,20),NativeUi.dp(c,compact?12:20),NativeUi.dp(c,20),NativeUi.dp(c,compact?0:8));
  LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=label(c,"Add to playlist",compact?20:22,0xfff5f5f5);title.setTypeface(Typeface.create("sans-serif-medium",0));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView count=label(c,String.valueOf(tracks.size())+(!allowAccount&&compact?" • Offline":""),13,0xfff5f5f5);count.setGravity(Gravity.CENTER);count.setBackground(rounded(c,0xff000000,16));count.setPadding(NativeUi.dp(c,12),NativeUi.dp(c,compact?4:6),NativeUi.dp(c,12),NativeUi.dp(c,compact?4:6));count.setContentDescription(tracks.size()+" songs selected");header.addView(count);body.addView(header);
  TextView info=new TextView(c);info.setText("Local copies only • Online playlists stay unchanged");if(compact)info.setVisibility(View.GONE);info.setTextColor(0xffaaaaaa);info.setTextSize(13);info.setPadding(0,NativeUi.dp(c,10),0,0);body.addView(info);
  LinearLayout list=new LinearLayout(c);list.setOrientation(1);ScrollView scroll=new ScrollView(c);scroll.addView(list);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,NativeUi.dp(c,listHeightDp(c.getResources().getConfiguration().screenHeightDp)),1f);sp.topMargin=NativeUi.dp(c,compact?4:12);body.addView(scroll,sp);
  final AlertDialog dialog=NativeUi.dialog(c).setView(body).setNegativeButton("Cancel",null).create();
  section(c,list,"On this device");
  final Map<String,String> copies=LocalPlaylistCopies.groups(c,LikedPlaylists.groups(PlaylistIndex.read(c),PlaylistIndex.rawGroups(c)));
  for(final Map.Entry<String,String> e:copies.entrySet()){
   JSONObject saved=LocalPlaylistCopies.entry(c,e.getKey());JSONArray a=saved==null?null:saved.optJSONArray("tracks");int songs=a==null?0:a.length();
   View row=destination(c,e.getValue(),songs+(songs==1?" song • Local copy":" songs • Local copy"),false);row.setContentDescription("Add to local copy "+e.getValue());
   row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){confirmCopy(c,e.getKey(),e.getValue(),tracks,done,dialog);}});list.addView(row);
  }
  try{ArrayList<String> names=names(c);if(names.isEmpty()&&copies.isEmpty()){LinearLayout empty=new LinearLayout(c);empty.setOrientation(1);empty.setGravity(Gravity.CENTER);empty.setPadding(NativeUi.dp(c,16),NativeUi.dp(c,compact?10:20),NativeUi.dp(c,16),NativeUi.dp(c,compact?10:20));empty.setBackground(rounded(c,0xff000000,16));empty.setTag("local_empty_state");if(!compact){empty.addView(SelectionStyle.unmatched(c),new LinearLayout.LayoutParams(NativeUi.dp(c,48),NativeUi.dp(c,48)));}TextView heading=label(c,"Your first offline playlist",16,0xfff5f5f5);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setGravity(Gravity.CENTER);heading.setPadding(0,NativeUi.dp(c,compact?0:12),0,NativeUi.dp(c,6));empty.addView(heading);TextView hint=label(c,"Create a playlist for these songs.\nIt stays on this device.",13,0xffaaaaaa);hint.setGravity(Gravity.CENTER);empty.addView(hint);list.addView(empty);}for(final String name:names){JSONArray saved=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}")).optJSONArray(name);int songs=saved==null?0:saved.length();View row=destination(c,name,songs+(songs==1?" song • Offline playlist":" songs • Offline playlist"),false);row.setContentDescription("Add to existing playlist "+name);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){confirm(c,name,tracks,done,dialog);}});list.addView(row);}}catch(Exception e){Hooks.message("Could not read local playlists");return;}
  Button create=new Button(c);create.setText("+  Create new playlist");create.setContentDescription("Create new local playlist");create.setAllCaps(false);create.setTextSize(15);create.setTypeface(Typeface.create("sans-serif-medium",0));create.setTextColor(0xff000000);create.setBackground(rounded(c,0xfff5f5f5,24));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,NativeUi.dp(c,compact?44:50));cp.topMargin=NativeUi.dp(c,compact?8:12);create.setLayoutParams(cp);create.setOnClickListener(new View.OnClickListener(){public void onClick(View v){final EditText input=new EditText(c);input.setHint("Playlist name");final AlertDialog next=NativeUi.dialog(c).setTitle("New local playlist").setView(input).setPositiveButton("Create and add",null).setNegativeButton("Cancel",null).create();next.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){next.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Enter a name");return;}try{int n=add(c,name,tracks);next.dismiss();dialog.dismiss();Hooks.message("Added "+n+" songs to "+name);done.run();}catch(Exception e){input.setError("Could not save playlist");}}});}});next.show();NativeUi.polishDialog(c,next);}});body.addView(create);dialog.show();
  if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(rounded(c,0xff000000,24));dialog.getWindow().setLayout(-1,-2);dialog.getWindow().getDecorView().setPadding(NativeUi.dp(c,12),0,NativeUi.dp(c,12),0);}dialog.getButton(-2).setTextColor(0xfff5f5f5);
 }
 static void confirmCopy(final Context c,final String id,final String name,final List<Catalog.Track> tracks,final Runnable done,final AlertDialog picker){
  NativeUi.dialog(c).setTitle("Add to "+name+"?").setMessage(tracks.size()+(tracks.size()==1?" selected song.":" selected songs.")+" Changes stay in this local copy. Your online playlist is never changed. Existing songs are kept and duplicates are skipped.").setPositiveButton("Add locally",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{int n=LocalPlaylistCopies.add(c,id,tracks);picker.dismiss();Hooks.message("Added "+n+(n==1?" song locally to ":" songs locally to ")+name);done.run();}catch(Exception e){Hooks.message("Could not save local playlist");}}}).setNegativeButton("Cancel",null).show();
 }
 static void confirm(final Context c,final String name,final List<Catalog.Track> tracks,final Runnable done,final AlertDialog picker){
  NativeUi.dialog(c).setTitle("Add to "+name+"?").setMessage(tracks.size()+(tracks.size()==1?" selected song.":" selected songs.")+" Existing songs are kept; duplicates are skipped. This changes only the local playlist.").setPositiveButton("Add songs",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{int n=add(c,name,tracks);picker.dismiss();Hooks.message("Added "+n+" songs to "+name);done.run();}catch(Exception e){Hooks.message("Could not save playlist");}}}).setNegativeButton("Cancel",null).show();
 }
}
