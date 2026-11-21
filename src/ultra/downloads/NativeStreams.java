package ultra.downloads;
import java.util.*;import java.lang.reflect.*;import android.os.SystemClock;
/** Memory-only playback data keyed by the response's own video id. Never writes URLs or headers to storage/logs. */
public final class NativeStreams {
 private NativeStreams(){}
 static final long TTL=10*60*1000;static final int LIMIT=24;
 static final class Entry{final Object data;final long at;Entry(Object d){data=d;at=SystemClock.elapsedRealtime();}}
 static final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>();
 static Map<String,String> headers=new HashMap<>();static long headersAt;
 public static void capture(String id,Object data){try{if(id==null||!id.matches("[A-Za-z0-9_-]{11}")||data==null)return;Object f=Hooks.field(data,"f");if(!(f instanceof List)||((List<?>)f).isEmpty())return;synchronized(cache){cache.remove(id);cache.put(id,new Entry(data));while(cache.size()>LIMIT)cache.remove(cache.keySet().iterator().next());}Diagnostics.event("stream_capture","native-response");}catch(Throwable ignored){}}
 static Object get(String id){synchronized(cache){Entry e=cache.get(id);if(e==null)return null;if(SystemClock.elapsedRealtime()-e.at>TTL){cache.remove(id);return null;}return e.data;}}
 static void invalidate(String id){synchronized(cache){cache.remove(id);}}
 /** Only the native request's two needed headers. They stay in memory and are never sent to another host. */
 public static void captureHeaders(Map<?,?> source){try{if(source==null)return;Map<String,String> copy=new HashMap<>();for(Map.Entry<?,?> e:source.entrySet()){String k=String.valueOf(e.getKey());if(k.equalsIgnoreCase("Authorization")||k.equalsIgnoreCase("X-Goog-Visitor-Id")||k.equalsIgnoreCase("X-Goog-AuthUser")||k.equalsIgnoreCase("X-Goog-PageId")){Object v=e.getValue();if(v instanceof String&&((String)v).length()<16384)copy.put(k.equalsIgnoreCase("Authorization")?"Authorization":k,(String)v);}}if(!copy.isEmpty())synchronized(cache){if(copy.containsKey("Authorization")&&!copy.get("Authorization").equals(headers.get("Authorization")))headers=new HashMap<>();headers.putAll(copy);headersAt=SystemClock.elapsedRealtime();}if(copy.containsKey("Authorization"))PlaylistIndex.accountReady();}catch(Throwable ignored){}}
 static Map<String,String> requestHeaders(){Map<String,String> result=new HashMap<>();synchronized(cache){if(SystemClock.elapsedRealtime()-headersAt<TTL)result.putAll(headers);}String auth=Hooks.authorization();if(!result.containsKey("Authorization")&&auth!=null&&!auth.isEmpty())result.put("Authorization",auth);return result;}
 public static String agent(String url){String fallback="Mozilla/5.0";try{String client=android.net.Uri.parse(url).getQueryParameter("c");Class<?> types=Class.forName("app.revanced.extension.shared.innertube.client.YouTubeClient$ClientType");Object[] values=(Object[])types.getMethod("values").invoke(null);for(Object type:values)if(String.valueOf(types.getMethod("getClientName").invoke(type)).equals(client))return String.valueOf(types.getMethod("getUserAgent").invoke(type));}catch(Throwable ignored){}return fallback;}
 static Hooks.Audio select(Object data,String required,String quality)throws Exception{
  if(data==null)return null;Object raw=Hooks.field(data,"f");if(!(raw instanceof List))return null;Hooks.Audio best=null;long rank=Long.MIN_VALUE;
  for(Object format:(List<?>)raw){String mime=String.valueOf(Hooks.field(format,"g")),url=String.valueOf(Hooks.field(format,"f"));if(!mime.startsWith("audio/")||!url.startsWith("https://"))continue;
   java.net.URI u;try{u=new java.net.URI(url);}catch(Exception e){continue;}String host=u.getHost();if(host==null||!(host.equals("googlevideo.com")||host.endsWith(".googlevideo.com")))continue;
   try{String expiry=android.net.Uri.parse(url).getQueryParameter("expire");if(expiry!=null&&Long.parseLong(expiry)*1000L<System.currentTimeMillis()+30000)continue;}catch(NumberFormatException ignored){}
   Object drm=Hooks.field(format,"s");if(drm instanceof Collection&&!((Collection<?>)drm).isEmpty())continue;
   String key=String.valueOf(Hooks.field(format,"e"))+":"+String.valueOf(Hooks.field(format,"p"));if(required!=null&&!required.equals(key))continue;
   int rate=((Number)Hooks.field(format,"h")).intValue();if(rate<=0)continue;long score=rate;
   if("saver".equals(quality))score=-rate;else if("aac".equals(quality)&&mime.contains("mp4"))score+=10000000;
   if(best==null||score>rank){Hooks.Audio a=new Hooks.Audio();a.url=url;a.mime=mime;a.key=key;a.bitrate=rate;Object size=Hooks.field(format,"q");if(size instanceof Number)a.size=((Number)size).longValue();else try{a.size=Long.parseLong(String.valueOf(size));}catch(NumberFormatException ignored){}a.agent=agent(url);best=a;rank=score;}
  }return best;
 }
}
