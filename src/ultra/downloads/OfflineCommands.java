package ultra.downloads;
import java.io.*;
import java.lang.reflect.*;
import java.net.URLDecoder;
import java.util.*;
/** Handles only the offline upsell endpoint. No player-state fallback. */
public final class OfflineCommands {
    static final int ENDPOINT=0x7f877ca;
    static final class Part {int field,start,end; Part(int f,int s,int e){field=f;start=s;end=e;}}
    static long var(byte[] b,int[] pos,int end)throws IOException{long v=0;for(int n=0;n<10;n++){if(pos[0]>=end)throw new IOException("Truncated wire value");int x=b[pos[0]++]&255;if(n==9&&(x&254)!=0)throw new IOException("Wire overflow");v|=(long)(x&127)<<(7*n);if(x<128)return v;}throw new IOException("Bad wire value");}
    static List<Part> fields(byte[] b,int start,int end)throws IOException{List<Part> out=new ArrayList<>();int[] p={start};while(p[0]<end){long tag=var(b,p,end);int f=(int)(tag>>>3),w=(int)(tag&7);if(f<1||f>536870911)throw new IOException("Bad field");if(w==0)var(b,p,end);else if(w==1)p[0]+=8;else if(w==5)p[0]+=4;else if(w==2){long n=var(b,p,end);if(n<0||n>end-p[0])throw new IOException("Bad length");out.add(new Part(f,p[0],p[0]+(int)n));p[0]+=(int)n;}else throw new IOException("Unsupported wire type");if(p[0]>end)throw new IOException("Overrun");}return out;}
    static byte[] one(byte[] b,int f)throws IOException{byte[] out=null;for(Part p:fields(b,0,b.length))if(p.field==f){if(out!=null)throw new IOException("Ambiguous endpoint");out=Arrays.copyOfRange(b,p.start,p.end);}return out;}
    static byte[] serial(Object proto)throws Exception{return (byte[])proto.getClass().getMethod("toByteArray").invoke(proto);}
    static final class Target {String video,playlist; Target(String v,String p){video=v;playlist=p;}}
    static Target params(String encoded)throws Exception{
        if(encoded==null||encoded.length()>4096)throw new IOException("Invalid download parameters");
        String safe=URLDecoder.decode(encoded.replace("+","%2B"),"UTF-8");
        byte[] b=java.util.Base64.getUrlDecoder().decode(safe);
        byte[] v=one(b,4),context=one(b,5),selection=context==null?null:one(context,10);
        byte[] song=selection==null?null:one(selection,1),playlist=selection==null?null:one(selection,2);
        String direct=v==null?null:new String(v,"UTF-8"),nested=song==null?null:new String(song,"UTF-8"),list=playlist==null?null:new String(playlist,"UTF-8");
        if(direct!=null&&nested!=null&&!direct.equals(nested))throw new IOException("Conflicting song IDs");
        String video=direct!=null?direct:nested;
        if(video!=null&&list!=null)throw new IOException("Ambiguous selection");
        if(video!=null&&video.matches("[A-Za-z0-9_-]{11}"))return new Target(video,null);
        if(video==null&&list!=null&&list.matches("[A-Za-z0-9_-]{2,200}"))return new Target(null,list);
        throw new IOException("No exact download identity");
    }
    static String endpointParams(byte[] bytes)throws Exception{byte[] endpoint=one(bytes,ENDPOINT);if(endpoint==null)return null;byte[] p=one(endpoint,1);return p==null?null:new String(p,"UTF-8");}
    // Elements embeds the command in an extension. Walk bounded protobuf fields, not text matches.
    static String find(byte[] b,int start,int end,int depth,int[] budget)throws Exception{
        if(depth>32||--budget[0]<0)return null;
        List<Part> fs;try{fs=fields(b,start,end);}catch(IOException e){return null;}
        String result=null;
        for(Part p:fs){String value=null;if(p.field==ENDPOINT){byte[] raw=one(Arrays.copyOfRange(b,p.start,p.end),1);if(raw!=null)value=new String(raw,"UTF-8");}else value=find(b,p.start,p.end,depth+1,budget);
            if(value!=null){if(result!=null&&!result.equals(value))throw new IOException("Multiple download selections");result=value;}}
        return result;
    }
    static boolean hasEndpoint(byte[] b,int start,int end,int depth,int[] budget){if(depth>32||--budget[0]<0)return false;try{for(Part p:fields(b,start,end)){if(p.field==ENDPOINT||hasEndpoint(b,p.start,p.end,depth+1,budget))return true;}}catch(IOException ignored){}return false;}
    public static boolean hasOfflineRequest(byte[] b){return b!=null&&b.length<=262144&&hasEndpoint(b,0,b.length,0,new int[]{8192});}
    public static boolean isOfflineSheet(byte[] b){if(b==null||b.length>1048576)return false;byte[] needle="offline_upsell".getBytes(java.nio.charset.StandardCharsets.US_ASCII);outer:for(int n=0;n<=b.length-needle.length;n++){for(int k=0;k<needle.length;k++)if(b[n+k]!=needle[k])continue outer;return true;}return false;}
    public static void sheetFallback(){NativeIntegration.open();Hooks.message("Blocked the subscription offer. No exact song came with it; choose the song's Download action.");}
    public static String[] selection(byte[] b)throws Exception{if(b==null||b.length>262144)return null;String raw=find(b,0,b.length,0,new int[]{8192});if(raw==null)return null;Target t=params(raw);return new String[]{t.video,t.playlist};}
    /** Every length-delimited value (bounded depth and count) that is an id-shaped ASCII string. Used only to rebuild
     *  candidate commands for an exact byte comparison; never treated as an identity on its own. */
    public static List<String> idStrings(byte[] b){List<String> out=new ArrayList<>();if(b!=null&&b.length<=4096)idStrings(b,0,b.length,0,out);return out;}
    static void idStrings(byte[] b,int start,int end,int depth,List<String> out){
        if(depth>4||out.size()>16)return;
        List<Part> parts;try{parts=fields(b,start,end);}catch(IOException e){return;}
        for(Part p:parts){String s=new String(b,p.start,p.end-p.start,java.nio.charset.StandardCharsets.ISO_8859_1);
            if(s.matches("[A-Za-z0-9_-]{2,200}"))out.add(s);else idStrings(b,p.start,p.end,depth+1,out);}
    }
    static boolean warned;
    public static void failure(Throwable e){Diagnostics.error("hook",e);android.util.Log.e("UltraDownloads","Download hook failure",e);if(!warned){warned=true;Hooks.message("Download could not start: "+e.getClass().getSimpleName());}}
    public static void dispatchWire(byte[] bytes){try{Target t=params(endpointParams(bytes));if(t.video!=null)Hooks.download(t.video);else NativeIntegration.open(t.playlist);}catch(Throwable e){failure(e);Hooks.message("This item has no exact downloadable song or playlist ID");}}
    public static Object menu(Object item){
        if(item==null)return null;
        try{byte[] b=serial(item);if(b.length>262144)return item;String p=find(b,0,b.length,0,new int[]{4096});if(p==null)return item;Target t=params(p);return NativeModels.service(t.video,t.playlist);}catch(Throwable e){return item;}
    }
    public static boolean independent(Object item){try{if(item==null||item.getClass().getField("b").getInt(item)!=2)return false;Object row=item.getClass().getField("d").get(item);if(row==null||row.getClass().getField("b").getInt(row)!=73)return false;Object title=row.getClass().getField("c").get(row);return title!=null&&"Download".equals(title.getClass().getField("d").get(title));}catch(Throwable e){return false;}}
    public static void dispatch(Object command){
        try{String raw=endpointParams(serial(command));Target t=params(raw);if(t.video!=null)Hooks.download(t.video);else NativeIntegration.open(t.playlist);}
        catch(Throwable e){Hooks.message("This Download item has no exact song or playlist ID. No Premium subscription is needed.");}
    }
}
