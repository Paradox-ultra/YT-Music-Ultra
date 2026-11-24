package ultra.home;

import java.util.*;

/** Pure Home rules: greeting by hour, chip filtering, section fallback order. No Android types, unit tested in DL33Test. */
public final class HomeLogic {
    private HomeLogic(){}
    public static final String ALL="All",PLAYLISTS="Playlists",DOWNLOADS="Downloads",ARTISTS="Artists",LIKED="Liked";
    public static final String[] CHIPS={ALL,PLAYLISTS,DOWNLOADS,ARTISTS,LIKED};
    /** "Pick up where you left off" holds at most this many tiles; songs take at most SONG_SHARE of them. */
    public static final int PICK_UP_LIMIT=10,SONG_SHARE=6;

    /** Device-time greeting: 05:00-11:59 morning, 12:00-16:59 afternoon, everything else evening. */
    public static String greeting(int hour){
        int h=((hour%24)+24)%24;
        if(h>=5&&h<12)return "Good morning";
        if(h>=12&&h<17)return "Good afternoon";
        return "Good evening";
    }

    /** An unknown or empty chip name means All. */
    public static String chip(String name){for(String c:CHIPS)if(c.equals(name))return c;return ALL;}

    /** Whether a tile belongs under a chip. All keeps everything; Downloads means downloaded songs. */
    public static boolean matches(HomeTile t,String chip){
        if(t==null)return false;
        switch(chip(chip)){
            case PLAYLISTS:return t.playlist();
            case DOWNLOADS:return t.song();
            case ARTISTS:return t.artist();
            case LIKED:return t.liked;
            default:return true;
        }
    }
    public static List<HomeTile> filter(List<HomeTile> tiles,String chip){
        ArrayList<HomeTile> out=new ArrayList<>();if(tiles==null)return out;
        for(HomeTile t:tiles)if(matches(t,chip))out.add(t);
        return out;
    }

    /**
     * "Pick up where you left off", in fallback order: the last song played (when it is downloaded), then recent downloads,
     * then pinned playlists, then the rest of the account's Library playlists. Each source only fills what earlier sources
     * left empty; duplicates are dropped and songs never take more than {@link #SONG_SHARE} places, so playlists still show.
     */
    public static List<HomeTile> pickUp(HomeTile lastPlayed,List<HomeTile> recentSongs,List<HomeTile> pinned,List<HomeTile> playlists,int limit){
        LinkedHashMap<String,HomeTile> out=new LinkedHashMap<>();
        int songs=0;
        List<HomeTile> first=new ArrayList<>();if(lastPlayed!=null)first.add(lastPlayed);if(recentSongs!=null)first.addAll(recentSongs);
        for(HomeTile t:first){if(out.size()>=limit||songs>=Math.min(SONG_SHARE,limit))break;if(t!=null&&!out.containsKey(t.key())){out.put(t.key(),t);songs++;}}
        for(List<HomeTile> source:Arrays.asList(pinned,playlists)){
            if(source==null)continue;
            for(HomeTile t:source){if(out.size()>=limit)break;if(t!=null&&!out.containsKey(t.key()))out.put(t.key(),t);}
        }
        return new ArrayList<>(out.values());
    }

    /** Title of the grid section under a chip; All shows the user's Library playlists. */
    public static String gridTitle(String chip){
        switch(chip(chip)){
            case DOWNLOADS:return "Recently downloaded";
            case ARTISTS:return "Artists in your downloads";
            case LIKED:return "Liked";
            default:return "Your playlists";
        }
    }
    /** Grid contents under a chip. Playlist order is kept exactly as given (the Library order). */
    public static List<HomeTile> grid(String chip,List<HomeTile> playlists,List<HomeTile> songs,List<HomeTile> artists){
        switch(chip(chip)){
            case DOWNLOADS:return filter(songs,DOWNLOADS);
            case ARTISTS:return filter(artists,ARTISTS);
            case LIKED:return filter(playlists,LIKED);
            default:return filter(playlists,PLAYLISTS);
        }
    }
    /**
     * "Quick picks" (DL34 Home): downloaded songs that are not already in the recent row, taken round-robin across artists
     * in order of how many of their songs you downloaded (ties: most recent first), so one artist never fills the list.
     * Deterministic for the same library, so the list does not jump around between visits.
     */
    public static List<HomeTile> quickPicks(List<HomeTile> songs,Collection<String> exclude,int limit){
        ArrayList<HomeTile> out=new ArrayList<>();if(songs==null||limit<=0)return out;
        LinkedHashMap<String,ArrayDeque<HomeTile>> byArtist=new LinkedHashMap<>();
        for(HomeTile s:songs){if(s==null||!s.song()||(exclude!=null&&exclude.contains(s.id)))continue;String a=s.subtitle;ArrayDeque<HomeTile> q=byArtist.get(a);if(q==null)byArtist.put(a,q=new ArrayDeque<>());q.add(s);}
        ArrayList<ArrayDeque<HomeTile>> queues=new ArrayList<>(byArtist.values());
        Collections.sort(queues,new Comparator<ArrayDeque<HomeTile>>(){public int compare(ArrayDeque<HomeTile> a,ArrayDeque<HomeTile> b){return b.size()-a.size();}});
        boolean any=true;
        while(out.size()<limit&&any){any=false;for(ArrayDeque<HomeTile> q:queues){if(out.size()>=limit)break;HomeTile t=q.poll();if(t!=null){out.add(t);any=true;}}}
        return out;
    }

    /** Nothing to show anywhere: Home falls back to its single quiet line and the Library button. */
    public static boolean empty(List<HomeTile> pickUp,List<HomeTile> grid){return (pickUp==null||pickUp.isEmpty())&&(grid==null||grid.isEmpty());}
}
