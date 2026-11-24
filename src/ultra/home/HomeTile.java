package ultra.home;

/**
 * One cover tile on Home. Plain data only, so the section logic in {@link HomeLogic} stays testable without Android.
 * Ids are the module's own: a downloaded song id, a Library playlist key (no VL prefix), or an artist name.
 */
public final class HomeTile {
    public static final String SONG="song",PLAYLIST="playlist",ARTIST="artist";
    public final String id,title,subtitle,kind;
    /** Liked music (LM) or a playlist merged into it by LikedPlaylists. */
    public final boolean liked;
    /** Newest first; download time for songs, 0 when unknown. */
    public final long time;
    public HomeTile(String id,String title,String subtitle,String kind,boolean liked,long time){
        this.id=id==null?"":id;this.title=title==null?"":title;this.subtitle=subtitle==null?"":subtitle;this.kind=kind==null?SONG:kind;this.liked=liked;this.time=time;
    }
    public boolean song(){return SONG.equals(kind);}
    public boolean playlist(){return PLAYLIST.equals(kind);}
    public boolean artist(){return ARTIST.equals(kind);}
    /** Dedupe key: kinds never collide (a song id cannot hide a playlist with the same text). */
    public String key(){return kind+":"+id;}
    @Override public String toString(){return key();}
}
