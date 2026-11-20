package ultra.downloads;
import android.content.Context;
import java.util.*;
import org.json.JSONArray;
/** Display-only tab choices. No songs, playlists or account data are deleted. */
public final class DownloadTabs {
 static final String PIN="download_tab_pins",HIDDEN="download_tab_hidden";
 static LinkedHashSet<String> read(Context c,String key){LinkedHashSet<String> out=new LinkedHashSet<>();try{JSONArray a=new JSONArray(Library.prefs(c).getString(key,"[]"));for(int i=0;i<a.length();i++){String s=a.optString(i);if(Arrays.asList(DownloadsScreen.TABS).contains(s))out.add(s);}}catch(Exception ignored){}return out;}
 static void save(Context c,String key,Collection<String> items){Library.prefs(c).edit().putString(key,new JSONArray(items).toString()).apply();}
 public static List<String> visible(Context c){LinkedHashSet<String> out=read(c,PIN);out.addAll(Arrays.asList(DownloadsScreen.TABS));out.removeAll(read(c,HIDDEN));return new ArrayList<>(out);}
 /** New Downloads entries follow the visible pinned order, not a hard-coded Songs tab. */
 public static String initial(Context c){List<String> tabs=visible(c);return tabs.isEmpty()?"Songs":tabs.get(0);}
 /** A recreation preserves an explicit in-screen choice, unless that tab was hidden. */
 public static String restored(Context c,String saved){return saved!=null&&visible(c).contains(saved)?saved:initial(c);}
 public static boolean pinned(Context c,String name){return read(c,PIN).contains(name);}
 public static void pin(Context c,String name,boolean value){LinkedHashSet<String> items=read(c,PIN);items.remove(name);if(value){LinkedHashSet<String> next=new LinkedHashSet<>();next.add(name);next.addAll(items);items=next;}save(c,PIN,items);}
 public static void hide(Context c,String name){LinkedHashSet<String> items=read(c,HIDDEN);items.add(name);save(c,HIDDEN,items);}
 public static void restore(Context c){Library.prefs(c).edit().remove(PIN).remove(HIDDEN).apply();}
}
