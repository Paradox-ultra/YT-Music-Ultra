package ultra.downloads;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import java.io.File;
import java.util.*;
import ultra.home.HomeTile;

/**
 * Read-only bridge from the Downloads data (Library, PlaylistIndex, PinManager, LikedPlaylists, Art) to the Home screen in
 * {@code ultra.home}. Home only sees {@link HomeTile} values. Nothing here touches the network: artwork comes from files
 * the module already saved (song covers, cached playlist art, member collages), and playlists come from the stored
 * Library roster. Every source fails soft to an empty list, so Home can fall back to the next source.
 */
public final class HomeSource {
    private HomeSource(){}

    /** Completed, visible downloads, newest first (same visibility rules as the Downloads Songs tab). */
    public static List<HomeTile> recentSongs(Context c){
        ArrayList<HomeTile> out=new ArrayList<>();
        try{
            ArrayList<Library.Item> all=VoiceRecordings.filter(c,Library.all(c));
            Collections.sort(all,new Comparator<Library.Item>(){public int compare(Library.Item a,Library.Item b){return Long.compare(b.created,a.created);}});
            for(Library.Item i:all)if(!i.hidden&&"done".equals(i.state))out.add(song(i));
        }catch(Throwable e){Diagnostics.error("home_recent",e);}
        return out;
    }
    /** DL56: YouTube Music Trending songs (TrendingFeed) as Home tiles; not downloaded, played by the host player. */
    public static List<HomeTile> trendingTiles(TrendingFeed.Feed f){
        ArrayList<HomeTile> out=new ArrayList<>();if(f==null)return out;
        for(TrendingFeed.Song s:f.songs)out.add(new HomeTile(s.videoId,s.title,s.artist,HomeTile.ONLINE,false,f.fetchedAt,s.thumb));
        return out;
    }
    public static TrendingFeed.Feed trendingCache(Context c){return TrendingFeed.cached(c);}
    /** Fetches Trending now (background thread only); null when offline or unparsable (the cache is kept). */
    public static TrendingFeed.Feed trendingRefresh(Context c){return TrendingFeed.refresh(c);}
    public static String trendingOutcome(){return TrendingFeed.lastOutcome;}

    /**
     * Plays a Trending song (not downloaded) in the host. DL58 order: route 1 = the host's own deep-link handling (as
     * openPlaylist): NativeIntegration.leave(), then ACTION_VIEW https://music.youtube.com/watch?v=<id> restricted to
     * this package (SINGLE_TOP, NEW_TASK when not an Activity), only when an activity resolves. Route 2, only when none
     * resolves: UltraWatchBridge (WatchEndpoint -> kud.f -> MusicBrowserService playFromMediaId). Neither: a toast.
     * Returns "intent -> <component>", "watch -> <media id kind>" or "none"; no Diagnostics calls (DL57).
     */
    public static String playOnline(Context c,String videoId){
        if(!TrendingFeed.valid(videoId)){Hooks.message("This song could not be opened");return "none";}
        try{
            Intent i=watchIntent(c,videoId);
            android.content.ComponentName target=i.resolveActivity(c.getPackageManager());
            if(target!=null){
                NativeIntegration.leave();String threw="none";
                try{c.startActivity(i);}catch(Throwable e){threw=e.getClass().getSimpleName();}
                log("playOnline intent: resolveActivity=non-null, startActivity threw="+threw);
                if(threw.equals("none")){log("playOnline route=intent");return "intent -> "+target.getShortClassName();}
            }else log("playOnline intent: resolveActivity=null");
        }catch(Throwable e){log("playOnline intent: error "+e.getClass().getSimpleName());}
        boolean ok=false;
        try{ok=Boolean.TRUE.equals(Class.forName("UltraWatchBridge").getMethod("play",String.class).invoke(null,videoId));}catch(Throwable ignored){}
        if(ok){log("playOnline route=watch");return "watch -> MusicBrowserService playFromMediaId";}
        log("playOnline route=none");Hooks.message("This song could not be opened");return "none";
    }
    /** DL61: logcat only (tag ULTRAHOME); route and exception class names, never ids, urls or paths. */
    static void log(String what){try{android.util.Log.d("ULTRAHOME",what);}catch(Throwable ignored){}}
    static Intent watchIntent(Context c,String videoId){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/watch?v="+videoId));i.setPackage(c.getPackageName());i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(!(c instanceof android.app.Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** The last song the player actually played (Radio's stored seed), only when it is downloaded; otherwise null. */
    public static HomeTile lastPlayed(Context c){
        try{
            String id=Library.prefs(c).getString("radio_last_played","");if(id.isEmpty())return null;
            Library.Item i=Library.get(c,id);
            return i!=null&&!i.hidden&&"done".equals(i.state)&&VoiceRecordings.visible(c,i)?song(i):null;
        }catch(Throwable e){return null;}
    }
    static HomeTile song(Library.Item i){String artist=i.artist.isEmpty()?"Unknown artist":i.artist;return new HomeTile(i.id,i.title.isEmpty()?"Untitled":i.title,artist,HomeTile.SONG,false,i.created);}

    /**
     * The user's Library playlists with the same names and order as Downloads > Playlists (native Library order, pins
     * applied, Liked merged). Device-only local playlists and the Unmatched auto list are left out: they are not Library
     * playlists and stay in Downloads.
     */
    public static List<HomeTile> playlists(Context c){
        ArrayList<HomeTile> out=new ArrayList<>();
        try{
            JSONIndex index=new JSONIndex(c);
            for(Map.Entry<String,String> e:PlaylistIndex.groups(c).entrySet()){
                String id=e.getKey();if(id==null||id.startsWith("local:")||UnmatchedDownloads.is(id))continue;
                out.add(new HomeTile(id,e.getValue(),index.subtitle(id),HomeTile.PLAYLIST,index.liked(id),0));
            }
        }catch(Throwable e){Diagnostics.error("home_playlists",e);}
        return out;
    }
    /** Pinned playlists in pin order, resolved against the Library roster (Radio and unknown ids are skipped). */
    public static List<HomeTile> pinned(Context c,List<HomeTile> playlists){
        ArrayList<HomeTile> out=new ArrayList<>();
        try{
            HashMap<String,HomeTile> byId=new HashMap<>();for(HomeTile t:playlists)byId.put(PlaylistIndex.key(t.id),t);
            for(String id:PinManager.getPinnedList(c)){HomeTile t=byId.get(PlaylistIndex.key(id));if(t!=null&&!out.contains(t))out.add(t);}
        }catch(Throwable e){Diagnostics.error("home_pins",e);}
        return out;
    }
    /** Same group key as the Downloads Artists tab, so an artist tile opens exactly that group. */
    static final String UNKNOWN_ARTIST="Unknown Artist";
    /** Artists of downloaded songs, most recently downloaded first. */
    public static List<HomeTile> artists(List<HomeTile> songs){
        LinkedHashMap<String,Integer> counts=new LinkedHashMap<>();
        for(HomeTile s:songs){String a=s.subtitle.isEmpty()||s.subtitle.equals("Unknown artist")?UNKNOWN_ARTIST:s.subtitle;Integer n=counts.get(a);counts.put(a,n==null?1:n+1);}
        ArrayList<HomeTile> out=new ArrayList<>();
        for(Map.Entry<String,Integer> e:counts.entrySet())out.add(new HomeTile(e.getKey(),e.getKey(),e.getValue()+(e.getValue()==1?" song":" songs"),HomeTile.ARTIST,false,0));
        return out;
    }

    /**
     * Subtitles and Liked flags, read ONCE per load (DL50). DL34-DL49 called LocalPlaylistCopies.entry per playlist, which
     * re-parses and re-serialises the whole copies JSON (all tracks) every time: hundreds of full parses for a large
     * Library, seconds on a v7a phone. Home only displays, so it reads the copies and the cached index directly and never
     * seeds or writes.
     */
    static final class JSONIndex{
        final org.json.JSONObject copies,index;
        JSONIndex(Context c){org.json.JSONObject read;try{read=LocalPlaylistCopies.read(c);}catch(Throwable e){read=new org.json.JSONObject();}copies=read;index=PlaylistIndex.read(c);}
        org.json.JSONObject entry(String id){String key=PlaylistIndex.key(id);org.json.JSONObject e=copies.optJSONObject(key);return e!=null?e:index.optJSONObject(key);}
        String subtitle(String id){org.json.JSONObject e=entry(id);org.json.JSONArray t=e==null?null:e.optJSONArray("tracks");int count=t==null?0:t.length();return count>0?count+(count==1?" song":" songs"):"In your Library";}
        boolean liked(String id){return LikedPlaylists.liked(id,entry(id));}
    }

    // ------------------------------------------------------------------ artwork (disk only, call off the main thread)
    /** Cover for a tile, or null for Home's flat placeholder. Songs: see {@link #songArt}. */
    public static Bitmap art(Context c,HomeTile t,int sizePx){return art(c,t,sizePx,null);}
    /** source[0] receives where the cover came from: file, embedded, fetched or none (Diagnostics). */
    public static Bitmap art(Context c,HomeTile t,int sizePx,String[] source){
        try{
            if(t.song())return songArt(c,t.id,sizePx,source);
            if(t.online())return onlineArt(c,t,sizePx,source);
            if(t.artist()){for(Library.Item i:Library.all(c))if("done".equals(i.state)&&!i.hidden&&t.id.equals(i.artist.isEmpty()?UNKNOWN_ARTIST:i.artist)){Bitmap b=decode(Art.file(c,i.id),sizePx);if(b!=null)return b;}return null;}
            Bitmap cached=decode(Art.file(c,"playlist_"+PlaylistIndex.key(t.id)),sizePx);if(cached!=null)return cached;
            Set<String> ids=PlaylistIndex.members(c,t.id);ArrayList<Library.Item> members=new ArrayList<>();
            for(Library.Item i:Library.all(c))if(ids.contains(i.id)&&"done".equals(i.state))members.add(i);
            return Covers.collage(members,sizePx);
        }catch(Throwable e){return null;}
    }
    static Bitmap decode(File f,int sizePx){return f!=null&&f.isFile()?Art.decode(f,sizePx):null;}

    /**
     * Song cover, DL52. DL33-DL51 read only the saved cover file (Art.file), which exists only once the download or the
     * Downloads list saved it; older and imported songs (the Quick picks) often have none, so they showed the note icon.
     * Order: (1) the saved cover file; (2) the cover embedded in the audio file (the module's MP3s carry an APIC frame,
     * imported files often have one), saved as the cover file so every screen reuses it; (3) the same YouTube thumbnail
     * request the Downloads list already makes for a song without a saved cover (Art.fetch, i.ytimg.com, only while
     * Art.network is on and its retry backoff allows), saved as the cover file.
     */
    static Bitmap songArt(Context c,String id,int sizePx,String[] source){
        File f=Art.file(c,id);
        Bitmap b=decode(f,sizePx);if(b!=null){note(source,"file");return b;}
        Library.Item i;try{i=Library.get(c,id);}catch(Throwable e){i=null;}
        if(i==null){note(source,"none");return null;}
        byte[] embedded=embedded(c,i.uri);
        if(embedded!=null&&save(f,embedded)){b=decode(f,sizePx);if(b!=null){Art.memory.remove(id);note(source,"embedded");return b;}}
        if(Art.network&&Art.retryAllowed(id,android.os.SystemClock.uptimeMillis())){
            try{if(i.artUrl==null||i.artUrl.isEmpty())Catalog.metadata(i);}catch(Throwable ignored){}
            if(Art.fetch(c,i)){Art.succeeded(id);b=decode(f,sizePx);if(b!=null){note(source,"fetched");return b;}}
            else Art.failed(id,android.os.SystemClock.uptimeMillis());
        }
        note(source,"none");return null;
    }
    /**
     * Trending cover from disk only (DL57): the file is saved by TrendingFeed.cover on Home's Trending thread; the cover
     * threads never wait on the network for Trending.
     */
    static Bitmap onlineArt(Context c,HomeTile t,int sizePx,String[] source){
        Bitmap b=decode(TrendingFeed.coverFile(c,t.id),sizePx);if(b!=null){note(source,"online");return b;}
        note(source,"none");return null;
    }
    static void note(String[] source,String what){if(source!=null&&source.length>0)source[0]=what;}
    /** Tests only: replaces the MediaMetadataRetriever read. */
    interface EmbeddedReader{byte[] read(Context c,String uri);}
    static EmbeddedReader embeddedReader=null;
    /** Embedded picture from the audio file itself (local, no network). */
    static byte[] embedded(Context c,String uri){
        if(uri==null||uri.isEmpty())return null;
        if(embeddedReader!=null)return embeddedReader.read(c,uri);
        android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();
        try{if(uri.startsWith("content:"))r.setDataSource(c,android.net.Uri.parse(uri));else r.setDataSource(uri);byte[] pic=r.getEmbeddedPicture();return pic!=null&&pic.length>64&&pic.length<8_000_000?pic:null;}
        catch(Throwable e){return null;}
        finally{try{r.release();}catch(Throwable ignored){}}
    }
    /** Writes a cover file atomically (temp + rename), like Art.fetch. */
    static boolean save(File target,byte[] data){
        if(target.isFile())return true;
        try{target.getParentFile().mkdirs();File tmp=new File(target.getPath()+"."+Thread.currentThread().getId()+".tmp");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){out.write(data);}
            if(!target.isFile()&&!tmp.renameTo(target)){tmp.delete();return target.isFile();}tmp.delete();return true;
        }catch(Throwable e){return target.isFile();}
    }

    // ------------------------------------------------------------------ actions
    /** Plays a downloaded song through the native player, queueing the other recent downloads after it. */
    /**
     * DL58: everything a Home song tap does (Library read, file / content-URI checks, queue prefs write, host command
     * build) runs on this background thread; only NativePlayback.command's MediaBrowser connect is posted to main.
     */
    static final java.util.concurrent.ExecutorService ACTIONS=java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"ultra-home-actions");t.setDaemon(true);return t;}});
    public static void play(Context c,final String songId){
        final Context app=c.getApplicationContext()!=null?c.getApplicationContext():c;
        try{ACTIONS.execute(new Runnable(){public void run(){
            try{
                ArrayList<String> ids=new ArrayList<>();for(HomeTile t:recentSongs(app))ids.add(t.id);
                int index=ids.indexOf(songId);if(index<0){ids.add(0,songId);index=0;}
                NativePlayback.start(app,ids,index);
            }catch(Throwable e){Hooks.message(e.getMessage()==null?"This song could not be played":e.getMessage());}
        }});}catch(java.util.concurrent.RejectedExecutionException e){Hooks.message("This song could not be played");}
    }

    /**
     * Opens the host's own native playlist page. The module has no in-app browse command of its own for playlists, so the
     * same music.youtube.com playlist link the Library select mode already shares is handed to the app's own link
     * handling (setPackage keeps it inside this app; no browser, no extra request from the module). When no component
     * accepts the link, the playlist's downloaded songs open in Downloads instead.
     */
    public static void openPlaylist(Context c,String id){
        String key=PlaylistIndex.key(id);
        Intent i=playlistIntent(c,key);
        try{
            if(i!=null&&i.resolveActivity(c.getPackageManager())!=null){NativeIntegration.leave();c.startActivity(i);Diagnostics.event("home_open","native-playlist");return;}
        }catch(Throwable e){Diagnostics.error("home_open",e);}
        Diagnostics.event("home_open","downloads-fallback");NativeIntegration.showDownloads("Playlists",id);
    }
    static Intent playlistIntent(Context c,String key){
        if(key==null||!key.matches("[A-Za-z0-9_-]{2,200}"))return null;
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/playlist?list="+key));
        i.setPackage(c.getPackageName());i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);return i;
    }
    /** Artists open their downloaded songs in Downloads (Artists tab), the module's own artist view. */
    public static void openArtist(String name){NativeIntegration.showDownloads("Artists",name);}
}
