package ultra.downloads;
import android.content.Context;
import java.util.*;
/** Read-only live view of completed downloads outside all known playlists. */
public final class UnmatchedDownloads {
    static final String ID="auto:unmatched", NAME="Unmatched songs";
    static boolean is(String id){return ID.equals(id);}
    static Set<String> matched(Context c){
        Set<String> out=new HashSet<>();
        for(String id:PlaylistIndex.groups(c).keySet())if(!is(id))out.addAll(PlaylistIndex.members(c,id));
        return out;
    }
    static ArrayList<Library.Item> filter(List<Library.Item> items,Set<String> matched){
        ArrayList<Library.Item> out=new ArrayList<>();
        for(Library.Item i:items)if(!i.hidden&&"done".equals(i.state)&&!matched.contains(i.id))out.add(i);
        return out;
    }
    static ArrayList<Library.Item> items(Context c){try{return filter(VoiceRecordings.filter(c,Library.all(c)),matched(c));}catch(Exception e){return new ArrayList<>();}}
    static Set<String> ids(Context c){Set<String> out=new HashSet<>();for(Library.Item i:items(c))out.add(i.id);return out;}
}
