package ultra.downloads;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;

public final class Hooks {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String PREFIX="app.revanced.extension.";
    public static Context context() throws Exception {
        Class<?> u=Class.forName(PREFIX+"shared.utils.Utils");
        return (Context)u.getMethod("getContext").invoke(null);
    }
    public static void message(final String s) {MAIN.post(new Runnable(){public void run(){try{Toast.makeText(context(),s,Toast.LENGTH_LONG).show();}catch(Exception ignored){}}});}
    public static void open(){NativeIntegration.open();}
    public static String currentId() throws Exception {return (String)Class.forName(PREFIX+"music.shared.VideoInformation").getMethod("getVideoId").invoke(null);}
    public static void current(){try{download(currentId());}catch(Exception e){message("Play a song before downloading it");}}
    public static void download(String id) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{11}")){message("This item does not contain a downloadable song ID");return;}
        try { Context c=context();DownloadsService.enqueue(c,id,"");message("Added to Downloads"); }
        catch(Exception e){message("Download could not start: "+e.getClass().getSimpleName());}
    }
    /** Receives the exact native command's decoded endpoint, never the current player item. */
    public static void videoEndpoint(Object endpoint) {
        try {
            int kind=((Number)field(endpoint,"c")).intValue();Object id=field(endpoint,"d");
            if(kind!=1 || !(id instanceof String))throw new IOException("Unsupported song endpoint");
            int action=((Number)field(endpoint,"e")).intValue();
            if(action==2||action==9){open();return;}
            if(action==3){Library.Item item=Library.get(context(),(String)id);if(item!=null)DownloadsService.pause(context(),item);return;}
            if(action==0){message("This download action is not supported");return;}
            download((String)id);
        } catch(Exception e){message("This download command did not contain a song ID");}
    }
    public static void playlistEndpoint(Object endpoint) {
        try {
            final String id=(String)field(endpoint,"c");
            if(id==null || !id.matches("[A-Za-z0-9_-]{2,200}"))throw new IOException("Invalid playlist");
            NativeIntegration.open(id);
        }catch(Exception e){message("This command did not contain a playlist ID");}
    }
    static Object field(Object o,String name)throws Exception {Field f=o.getClass().getField(name);return f.get(o);}
    public static final class Audio {
        public String url,mime,key,agent="Mozilla/5.0";public long size;public int bitrate;
    }
    interface Route {Audio get()throws Exception;}
    interface Probe{void check(Audio audio)throws Exception;}
    static Probe probe=new Probe(){public void check(Audio a)throws Exception{long n=Transfer.probe(a.url,new Transfer.Observer(){public boolean stopped(){return Thread.currentThread().isInterrupted();}public void progress(long x,long y){}},a.agent);if(a.size>0&&a.size!=n)throw new IOException("Audio response size differs from player metadata");a.size=n;}};
    static Audio usable(Audio a)throws Exception{if(a!=null)probe.check(a);return a;}
    static Audio attempt(String label,Route route,List<String> errors){try{Audio a=usable(route.get());if(a!=null){Diagnostics.event("stream_resolve",label);return a;}errors.add(label+": no usable direct audio");}catch(Exception e){errors.add(label+": "+ResolveErrors.describe(e));Diagnostics.error("resolve_"+label,ResolveErrors.root(e));}return null;}
    /** Source failure never skips the later independent routes. */
    public static Audio resolve(final String id,final boolean renew,final String requiredKey,final String quality)throws Exception{
        if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Invalid song identity");
        if(renew)NativeStreams.invalidate(id);final List<String> errors=new ArrayList<>();Audio a;
        if(!renew){a=attempt("native-player",new Route(){public Audio get()throws Exception{return NativeStreams.select(NativeStreams.get(id),requiredKey,quality);}},errors);if(a!=null)return a;}
        final Class<?> request=Class.forName(PREFIX+"shared.patches.spoof.requests.StreamingDataRequest");
        if(!renew){a=attempt("player-cache",new Route(){public Audio get()throws Exception{return cachedAudio(request,id,requiredKey,quality);}},errors);if(a!=null)return a;}
        a=attempt("native-refresh",new Route(){public Audio get()throws Exception{
            try{Class.forName(PREFIX+"shared.patches.auth.AuthPatch").getMethod("checkAccessToken").invoke(null);}catch(Throwable ignored){}
            String[] nonce=nonces();request.getMethod("fetchRequest",String.class,String.class,String.class,Map.class,String.class).invoke(null,id,nonce[0],nonce[1],NativeStreams.requestHeaders(),"");return cachedAudio(request,id,requiredKey,quality);
        }},errors);if(a!=null)return a;
        a=attempt("legacy-spoof",new Route(){public Audio get()throws Exception{
            Class<?> patch=Class.forName(PREFIX+"shared.patches.spoof.SpoofStreamingDataPatch");patch.getMethod("fetchStreams",String.class,Map.class).invoke(null,"https://www.youtube.com/youtubei/v1/player?id="+id,NativeStreams.requestHeaders());return cachedAudio(request,id,requiredKey,quality);
        }},errors);if(a!=null)return a;
        a=PlayerFallback.resolve(id,requiredKey,quality,errors);if(a!=null)return a;
        StringBuilder report=new StringBuilder("No usable audio link. Attempts:");for(String error:errors)report.append("\n").append(error);throw new IOException(report.toString());
    }
    static String[] nonces()throws Exception{Class<?> helper=Class.forName("com.liskovsoft.googlecommon.common.helpers.YouTubeHelper");return new String[]{(String)helper.getMethod("generateTParameter").invoke(null),(String)helper.getMethod("generateContentPlaybackNonce").invoke(null)};}
    static Audio cachedAudio(Class<?> request,String id,String key,String quality)throws Exception{Object cached=request.getMethod("getRequestForVideoId",String.class).invoke(null,id);if(cached==null)return null;Object data=request.getMethod("getStream").invoke(cached);return NativeStreams.select(data,key,quality);}
    static String authorization() {try{return (String)Class.forName(PREFIX+"shared.patches.auth.AuthPatch").getMethod("getAuthorization").invoke(null);}catch(Exception e){return "";}}
}
