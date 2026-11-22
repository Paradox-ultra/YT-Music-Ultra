package ultra.downloads;
import android.content.Context;
import java.util.*;
import java.util.regex.Pattern;
/** Reversible display preference. Never changes files or manual Hide flags. */
final class VoiceRecordings {
 static final String PREF="show_voice_recordings";
 static final Pattern NAME=Pattern.compile("(?i)^(?:voice(?:[ _-]*(?:note|memo|recording))?|recording|record|recorder|memo|call[ _-]*recording)(?:[ _.-]|[0-9]|$).*|^PTT[_-][0-9]{8}[-_].*");
 static boolean named(String name,String path){
  String p=(path==null?"":path).toLowerCase(Locale.ROOT).replace('\\','/');
  if(p.matches(".*(?:^|/)(?:recordings?|voice recorder|voice recordings?|voice notes?|call recordings?|whatsapp voice notes|sounds/voice)(?:/|$).*"))return true;
  return name!=null&&NAME.matcher(name).matches();
 }
 static boolean isRecording(Library.Item i){return i!=null&&i.imported&&(i.recording||named(i.title,i.uri));}
 static boolean showing(Context c){return Library.prefs(c).getBoolean(PREF,false);}
 static boolean visible(Context c,Library.Item i){return i!=null&&!i.hidden&&(showing(c)||!isRecording(i));}
 static ArrayList<Library.Item> filter(Context c,List<Library.Item> items){ArrayList<Library.Item> out=new ArrayList<>();for(Library.Item i:items)if(visible(c,i))out.add(i);return out;}
 static void set(Context c,boolean show){Library.prefs(c).edit().putBoolean(PREF,show).apply();}
}
