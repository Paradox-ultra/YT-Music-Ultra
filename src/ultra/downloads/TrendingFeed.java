package ultra.downloads;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/**
 * Home "Trending songs": the "Trending" shelf of YouTube Music Explore, requested anonymously (DL56, hardened DL57).
 *
 * <p>POST https://music.youtube.com/youtubei/v1/browse?prettyPrint=false with browseId FEmusic_explore and a WEB_REMIX
 * client context (hl en, gl = the device country, IN when unknown). No cookies, no Authorization, no account headers.
 * The shelf is the musicCarouselShelfRenderer titled exactly "Trending", else the first whose title contains
 * "Trending"; the top 10 items with a videoId are kept (videoId, title, artist, thumbnail URL).
 *
 * <p>DL57 rules: nothing here may run on the main thread or take a lock the main thread uses.
 * <ul>
 * <li>Cache: its own small file (files/ultra_trending_v1.json, written to a temp file and renamed), not
 *     SharedPreferences, so no apply() is ever queued for the main thread to wait on.</li>
 * <li>{@link #refresh}: one request at a time, on a daemon worker; the caller waits at most {@link #DEADLINE_MS}
 *     (15 s) for request plus parse, then aborts the connection and gives up. Body capped at 2 MB, no redirects.</li>
 * <li>Parse: streaming (JsonReader); only musicCarouselShelfRenderer objects are materialised, the rest is skipped;
 *     the deadline is checked while parsing.</li>
 * <li>After 2 failures in a row no more requests are made until the process restarts.</li>
 * <li>No Diagnostics calls (Diagnostics.event holds a lock the main thread uses while it writes a file).</li>
 * </ul>
 */
public final class TrendingFeed {
    private TrendingFeed(){}
    static final String URL="https://music.youtube.com/youtubei/v1/browse?prettyPrint=false";
    static final String CLIENT_VERSION="1.20241127.01.00",FILE="ultra_trending_v1.json";
    public static final long TTL_MS=6L*60*60*1000;
    public static final int LIMIT=10,MAX_FAILURES=2;
    static final int MAX_BYTES=2*1024*1024,MAX_COVER_BYTES=1024*1024;
    /** Request + parse must finish within this (tests shorten it). */
    static volatile long DEADLINE_MS=15000;

    /** One trending song. */
    public static final class Song{
        public final String videoId,title,artist,thumb;
        public Song(String videoId,String title,String artist,String thumb){this.videoId=videoId;this.title=title==null?"":title;this.artist=artist==null?"":artist;this.thumb=thumb==null?"":thumb;}
    }
    /** A cached or fetched list, with when it was fetched (ms) and for which country. */
    public static final class Feed{
        public final List<Song> songs;public final long fetchedAt;public final String gl;
        Feed(List<Song> songs,long fetchedAt,String gl){this.songs=songs;this.fetchedAt=fetchedAt;this.gl=gl;}
        public boolean fresh(long now){return now-fetchedAt<TTL_MS&&now>=fetchedAt;}
    }

    /** Tests only: replaces the HTTP call (request body in, response body out; throw for offline). */
    interface Fetcher{String post(String body)throws IOException;}
    static Fetcher fetcher=new Fetcher(){public String post(String body)throws IOException{return http(body);}};
    /** Last outcome, for Diagnostics: "online", "offline: ...", "parse: ...", "timeout: ...", "stopped: ...". */
    public static volatile String lastOutcome="not fetched yet";
    /** Failures in a row this session; at MAX_FAILURES no more requests are made. */
    static volatile int failures;
    private static final Object BUSY=new Object();private static Object busy;
    private static final AtomicReference<HttpURLConnection> open=new AtomicReference<>();
    /** Tests only. */
    static void resetSession(){failures=0;lastOutcome="not fetched yet";synchronized(BUSY){busy=null;}}
    private static void release(Object owner){synchronized(BUSY){if(busy==owner)busy=null;}}
    public static boolean stopped(){return failures>=MAX_FAILURES;}

    static final class Deadline extends IOException{Deadline(String m){super(m);}}
    static final class ParseFailure extends IOException{ParseFailure(String m,Throwable cause){super(m,cause);}}
    static void check(long deadlineNanos)throws Deadline{if(System.nanoTime()-deadlineNanos>0)throw new Deadline("deadline");}

    /** Device country as gl: two letters from the default locale, IN when unknown. */
    public static String country(){String c=Locale.getDefault().getCountry();return c!=null&&c.length()==2&&Character.isLetter(c.charAt(0))&&Character.isLetter(c.charAt(1))?c.toUpperCase(Locale.ROOT):"IN";}
    static String body(String gl)throws JSONException{
        JSONObject client=new JSONObject().put("clientName","WEB_REMIX").put("clientVersion",CLIENT_VERSION).put("hl","en").put("gl",gl);
        return new JSONObject().put("context",new JSONObject().put("client",client)).put("browseId","FEmusic_explore").toString();
    }
    static String http(String body)throws IOException{
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEADLINE_MS);
        int budget=(int)Math.max(1000,Math.min(10000,DEADLINE_MS));
        HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection();open.set(c);
        c.setConnectTimeout(budget);c.setReadTimeout(budget);c.setRequestMethod("POST");c.setDoOutput(true);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Origin","https://music.youtube.com");
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        try{
            byte[] b=body.getBytes("UTF-8");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}
            int status=c.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);
            if(c.getContentLength()>MAX_BYTES)throw new IOException("response too large");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream(256*1024)){byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("response over 2 MB");out.write(buf,0,n);check(deadline);}
                return new String(out.toByteArray(),"UTF-8");}
        }finally{open.compareAndSet(c,null);c.disconnect();}
    }

    // ------------------------------------------------------------------ cache (own file, never SharedPreferences)
    static File cacheFile(Context c){return new File(c.getFilesDir(),FILE);}
    /** Reads the cached list (background thread). Null when there is none or it is unreadable. */
    public static Feed cached(Context c){
        try{File f=cacheFile(c);if(!f.isFile()||f.length()>256*1024)return null;
            byte[] data=new byte[(int)f.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(f))){in.readFully(data);}
            JSONObject j=new JSONObject(new String(data,"UTF-8"));JSONArray a=j.optJSONArray("songs");if(a==null||a.length()==0)return null;
            ArrayList<Song> out=new ArrayList<>();for(int n=0;n<a.length()&&out.size()<LIMIT;n++){JSONObject s=a.optJSONObject(n);if(s!=null&&valid(s.optString("id")))out.add(new Song(s.optString("id"),s.optString("title"),s.optString("artist"),s.optString("thumb")));}
            return out.isEmpty()?null:new Feed(out,j.optLong("at"),j.optString("gl"));
        }catch(Throwable e){return null;}
    }
    static void save(Context c,Feed f){
        try{JSONArray a=new JSONArray();for(Song s:f.songs)a.put(new JSONObject().put("id",s.videoId).put("title",s.title).put("artist",s.artist).put("thumb",s.thumb));
            byte[] data=new JSONObject().put("at",f.fetchedAt).put("gl",f.gl).put("songs",a).toString().getBytes("UTF-8");
            File target=cacheFile(c),tmp=new File(target.getPath()+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);}
            if(!tmp.renameTo(target)){target.delete();if(!tmp.renameTo(target))tmp.delete();}
        }catch(Throwable ignored){}
    }

    /**
     * Fetches and parses now (background thread only; the caller waits at most DEADLINE_MS). Saves and returns the new
     * list, or returns null and keeps the cache when offline, on an HTTP error, on timeout, when no Trending shelf could
     * be parsed, when a request is still running, or after MAX_FAILURES failures in a row this session.
     */
    public static Feed refresh(Context c){
        if(stopped()){lastOutcome="stopped: "+failures+" failures in a row, no more requests this session";return null;}
        if(!Art.network){lastOutcome="offline: network off";return null;}
        final Object owner=new Object();
        synchronized(BUSY){if(busy!=null){lastOutcome="busy: the previous request is still running";return null;}busy=owner;}
        final String gl=country();final long limit=DEADLINE_MS,deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(limit);
        final FutureTask<List<Song>> task=new FutureTask<>(new Callable<List<Song>>(){public List<Song> call()throws Exception{
            try{String json=fetcher.post(body(gl));check(deadline);
                try{return parse(json,deadline);}catch(Deadline d){throw d;}catch(Throwable e){throw new ParseFailure(e.getClass().getSimpleName(),e);}
            }finally{release(owner);}// released before the result is published, so the next refresh never sees a stale "busy"
        }});
        Thread worker=new Thread(new Runnable(){public void run(){try{task.run();}finally{release(owner);}}},"ultra-trending");
        worker.setDaemon(true);worker.setPriority(Thread.MIN_PRIORITY);
        try{worker.start();}catch(Throwable e){release(owner);return fail("offline: "+e.getClass().getSimpleName());}
        List<Song> songs;
        try{songs=task.get(limit,TimeUnit.MILLISECONDS);}
        catch(TimeoutException e){abort(task);return fail("timeout: no complete answer within "+limit/1000+" s");}
        catch(InterruptedException e){abort(task);Thread.currentThread().interrupt();lastOutcome="cancelled";return null;}
        catch(ExecutionException e){Throwable t=e.getCause();
            if(t instanceof Deadline)return fail("timeout: no complete answer within "+limit/1000+" s");
            if(t instanceof ParseFailure)return fail("parse: "+t.getMessage());
            return fail("offline: "+(t==null?"error":t.getClass().getSimpleName()+(t.getMessage()==null?"":" "+t.getMessage())));}
        catch(Throwable e){abort(task);return fail("offline: "+e.getClass().getSimpleName());}
        if(songs==null||songs.isEmpty())return fail("parse: no Trending shelf with songs");
        failures=0;Feed f=new Feed(songs,System.currentTimeMillis(),gl);save(c,f);lastOutcome="online";return f;
    }
    private static void abort(FutureTask<?> task){task.cancel(true);HttpURLConnection c=open.getAndSet(null);if(c!=null)try{c.disconnect();}catch(Throwable ignored){}}
    private static Feed fail(String why){int n=++failures;lastOutcome=why+(n>=MAX_FAILURES?" (stopped for this session after "+n+" failures)":"");return null;}

    // ------------------------------------------------------------------ covers (background thread, no redirects)
    static File coverFile(Context c,String videoId){return Art.file(c,"trend_"+videoId);}
    public static boolean hasCover(Context c,String videoId){return coverFile(c,videoId).isFile();}
    /** Cover candidates: the feed thumbnail at a usable size, then the video's own hqdefault (YouTube image hosts only). */
    static List<String> coverUrls(Song s){
        ArrayList<String> out=new ArrayList<>();String t=s.thumb;
        if(t.startsWith("https://")){int eq=t.lastIndexOf('=');if(t.contains("googleusercontent.com/")&&eq>0&&t.indexOf("=w",eq)==eq)t=t.substring(0,eq)+"=w544-h544-l90-rj";out.add(t);}
        String direct="https://i.ytimg.com/vi/"+s.videoId+"/hqdefault.jpg";if(!out.contains(direct))out.add(direct);
        return out;
    }
    /** Saves the song's cover as trend_<id> (background thread only). True when the file exists afterwards. */
    public static boolean cover(Context c,Song s){
        File target=coverFile(c,s.videoId);if(target.isFile())return true;if(!Art.network)return false;
        for(String url:coverUrls(s)){if(Thread.currentThread().isInterrupted())return false;if(coverFrom(url,target))return true;}
        return target.isFile();
    }
    static boolean coverFrom(String url,File target){
        if(!Art.allowed(url))return false;
        try{HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();con.setConnectTimeout(6000);con.setReadTimeout(6000);con.setInstanceFollowRedirects(false);
            try{if(con.getResponseCode()!=200)return false;ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=con.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_COVER_BYTES)return false;out.write(b,0,n);}}
                byte[] data=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(o.outWidth<150||o.outHeight<80||o.outWidth>3000||o.outHeight>3000)return false;// 120x90 is YouTube's grey placeholder
                target.getParentFile().mkdirs();File tmp=new File(target.getPath()+".t.tmp");
                try(FileOutputStream f=new FileOutputStream(tmp)){f.write(data);}
                if(!target.isFile()&&!tmp.renameTo(target)){tmp.delete();return target.isFile();}
                tmp.delete();return true;
            }finally{con.disconnect();}
        }catch(Throwable e){return target.isFile();}
    }

    // ------------------------------------------------------------------ parsing (streaming)
    static boolean valid(String id){
        if(id==null||id.length()!=11)return false;
        for(int n=0;n<11;n++){char ch=id.charAt(n);if(!(ch>='A'&&ch<='Z'||ch>='a'&&ch<='z'||ch>='0'&&ch<='9'||ch=='_'||ch=='-'))return false;}
        return true;
    }
    static List<Song> parse(String json)throws IOException{return parse(json,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEADLINE_MS));}
    /** The Trending shelf's top songs: exact title "Trending" first, else the first shelf whose title contains it. */
    static List<Song> parse(String json,long deadline)throws IOException{
        ArrayList<JSONObject> shelves=new ArrayList<>();
        JsonReader r=new JsonReader(new StringReader(json));
        try{walk(r,shelves,0,deadline);}finally{try{r.close();}catch(IOException ignored){}}
        JSONObject pick=null;
        for(JSONObject s:shelves)if("Trending".equals(shelfTitle(s).trim())){pick=s;break;}
        if(pick==null)for(JSONObject s:shelves)if(shelfTitle(s).toLowerCase(Locale.ROOT).contains("trending")){pick=s;break;}
        ArrayList<Song> out=new ArrayList<>();if(pick==null)return out;
        JSONArray items=pick.optJSONArray("contents");if(items==null)return out;
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        for(int n=0;n<items.length()&&out.size()<LIMIT;n++){Song s=song(items.optJSONObject(n));if(s!=null&&seen.add(s.videoId))out.add(s);}
        return out;
    }
    /** Walks the document; materialises musicCarouselShelfRenderer objects only. True = stop (exact "Trending" found). */
    static boolean walk(JsonReader r,List<JSONObject> out,int depth,long deadline)throws IOException{
        check(deadline);
        JsonToken t=r.peek();
        if(depth>100){r.skipValue();return false;}
        if(t==JsonToken.BEGIN_OBJECT){r.beginObject();
            while(r.hasNext()){String name=r.nextName();
                if("musicCarouselShelfRenderer".equals(name)&&r.peek()==JsonToken.BEGIN_OBJECT){Object shelf=read(r,0,deadline);
                    if(shelf instanceof JSONObject&&out.size()<200){out.add((JSONObject)shelf);if("Trending".equals(shelfTitle((JSONObject)shelf).trim()))return true;}}
                else if(walk(r,out,depth+1,deadline))return true;}
            r.endObject();return false;}
        if(t==JsonToken.BEGIN_ARRAY){r.beginArray();while(r.hasNext())if(walk(r,out,depth+1,deadline))return true;r.endArray();return false;}
        r.skipValue();return false;
    }
    static Object read(JsonReader r,int depth,long deadline)throws IOException{
        JsonToken t=r.peek();
        if(depth>40){r.skipValue();return JSONObject.NULL;}
        switch(t){
            case BEGIN_OBJECT:{check(deadline);JSONObject o=new JSONObject();r.beginObject();while(r.hasNext()){String k=r.nextName();Object v=read(r,depth+1,deadline);try{o.put(k,v);}catch(JSONException ignored){}}r.endObject();return o;}
            case BEGIN_ARRAY:{JSONArray a=new JSONArray();r.beginArray();while(r.hasNext())a.put(read(r,depth+1,deadline));r.endArray();return a;}
            case STRING:return r.nextString();
            case NUMBER:return r.nextString();
            case BOOLEAN:return r.nextBoolean();
            case NULL:r.nextNull();return JSONObject.NULL;
            default:r.skipValue();return JSONObject.NULL;
        }
    }
    static String shelfTitle(JSONObject shelf){
        JSONObject header=shelf.optJSONObject("header");if(header==null)return "";
        for(Iterator<String> it=header.keys();it.hasNext();){JSONObject h=header.optJSONObject(it.next());if(h!=null){String t=text(h.optJSONObject("title"));if(!t.isEmpty())return t;}}
        return "";
    }
    /** musicResponsiveListItemRenderer (list rows) or musicTwoRowItemRenderer (tiles). */
    static Song song(JSONObject item){
        if(item==null)return null;
        JSONObject r=item.optJSONObject("musicResponsiveListItemRenderer");
        if(r!=null){
            String id=r.optJSONObject("playlistItemData")!=null?r.optJSONObject("playlistItemData").optString("videoId"):"";
            if(!valid(id))id=videoId(r,0);
            JSONArray cols=r.optJSONArray("flexColumns");String title="",artist="";
            if(cols!=null){title=text(column(cols,0));artist=artist(column(cols,1));}
            return valid(id)?new Song(id,title,artist,thumb(r)):null;
        }
        r=item.optJSONObject("musicTwoRowItemRenderer");
        if(r!=null){String id=videoId(r,0);return valid(id)?new Song(id,text(r.optJSONObject("title")),artist(r.optJSONObject("subtitle")),thumb(r)):null;}
        return null;
    }
    static JSONObject column(JSONArray cols,int n){JSONObject c=cols.optJSONObject(n);if(c==null)return null;JSONObject r=c.optJSONObject("musicResponsiveListItemFlexColumnRenderer");return r==null?null:r.optJSONObject("text");}
    /** Joined runs of a text object. */
    static String text(JSONObject t){
        if(t==null)return "";String simple=t.optString("simpleText","");if(!simple.isEmpty())return simple;
        JSONArray runs=t.optJSONArray("runs");if(runs==null)return "";StringBuilder b=new StringBuilder();for(int n=0;n<runs.length();n++){JSONObject r=runs.optJSONObject(n);if(r!=null)b.append(r.optString("text"));}return b.toString();
    }
    /** Artist names from runs: runs that link to an artist page, else the first text part before " • ". */
    static String artist(JSONObject t){
        if(t==null)return "";JSONArray runs=t.optJSONArray("runs");if(runs==null)return text(t);
        StringBuilder b=new StringBuilder();
        for(int n=0;n<runs.length();n++){JSONObject r=runs.optJSONObject(n);if(r==null)continue;JSONObject nav=r.optJSONObject("navigationEndpoint");JSONObject browse=nav==null?null:nav.optJSONObject("browseEndpoint");
            String bid=browse==null?"":browse.optString("browseId");if(bid.startsWith("UC")){if(b.length()>0)b.append(", ");b.append(r.optString("text"));}}
        if(b.length()>0)return b.toString();
        String all=text(t);int dot=all.indexOf(" • ");String first=dot>0?all.substring(0,dot):all;
        if(!first.equals("Song")&&!first.equals("Video"))return first;
        if(dot<0)return "";String rest=all.substring(dot+3);int next=rest.indexOf(" • ");return next<0?rest:rest.substring(0,next);
    }
    static String videoId(Object o,int depth){
        if(depth>12)return "";
        if(o instanceof JSONObject){JSONObject j=(JSONObject)o;JSONObject w=j.optJSONObject("watchEndpoint");if(w!=null&&valid(w.optString("videoId")))return w.optString("videoId");
            for(Iterator<String> it=j.keys();it.hasNext();){String v=videoId(j.opt(it.next()),depth+1);if(!v.isEmpty())return v;}}
        else if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int n=0;n<a.length();n++){String v=videoId(a.opt(n),depth+1);if(!v.isEmpty())return v;}}
        return "";
    }
    /** Largest thumbnail URL of the item. */
    static String thumb(JSONObject r){
        JSONObject t=r.optJSONObject("thumbnail");if(t==null)t=r.optJSONObject("thumbnailRenderer");if(t==null)return "";
        JSONObject m=t.optJSONObject("musicThumbnailRenderer");JSONObject inner=m==null?null:m.optJSONObject("thumbnail");JSONArray list=inner==null?null:inner.optJSONArray("thumbnails");
        if(list==null||list.length()==0)return "";JSONObject last=list.optJSONObject(list.length()-1);return last==null?"":last.optString("url");
    }
}
