package ultra.downloads;
import android.content.*;import android.media.browse.MediaBrowser;import android.media.session.MediaController;import android.net.Uri;import android.os.*;import java.util.*;import java.lang.reflect.*;import org.json.*;
/** Uses Music's existing sideloaded endpoint, session and queue. No second player. */
public final class NativePlayback {
 static MediaBrowser browser;static int generation;static final Handler MAIN=new Handler(Looper.getMainLooper());
 /** DL58: the saved-file check before a queue starts never takes longer than this; it then goes on with what it has. */
 static volatile long CHECK_LIMIT_MS=20000;
 /** DL58: background thread for Play / Shuffle (file and content-provider checks, queue build). */
 static final java.util.concurrent.ExecutorService WORK=java.util.concurrent.Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"ultra-playback-prepare");t.setDaemon(true);return t;}});
 /** Result of an asynchronous start, delivered on the main thread (not called after cancel). */
 public interface Done{void done(int queued,Exception error,boolean limited);}
 /** A running asynchronous start; cancel() stops it (nothing is queued afterwards). */
 public static final class Handle{final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();public void cancel(){cancelled.set(true);}public boolean cancelled(){return cancelled.get();}}

 /**
  * Starts the queue off the main thread (DL58): checks, queue build and the host command run on WORK; done() is posted to
  * the main thread unless the handle was cancelled. Callers on the main thread return at once.
  */
 public static Handle startAsync(Context input,final ArrayList<String> ids,final int index,final Done done){
  final Context c=input.getApplicationContext()!=null?input.getApplicationContext():input;final Handle h=new Handle();
  try{WORK.execute(new Runnable(){public void run(){
   int queued=0;Exception error=null;boolean[] limited={false};
   try{queued=start(c,ids,index,h,limited);}catch(Exception e){error=e;}
   final int q=queued;final Exception err=error;final boolean lim=limited[0];
   if(!h.cancelled()&&done!=null)MAIN.post(new Runnable(){public void run(){if(!h.cancelled())done.done(q,err,lim);}});
  }});}catch(java.util.concurrent.RejectedExecutionException e){if(done!=null)done.done(0,e,false);}
  return h;
 }
 /** Background threads only. */
 public static void start(final Context input,final ArrayList<String> ids,final int index)throws Exception{start(input,ids,index,null,new boolean[1]);}
 /**
  * Background threads only. Checks the saved files (at most CHECK_LIMIT_MS; a check that does not return in time ends
  * the check and the queue starts with the files confirmed so far, limited[0]=true), then builds the queue and hands
  * it to the host. A missing file still stops the start, as before. Returns the number of songs queued (0 = cancelled).
  */
 static int start(final Context input,final ArrayList<String> ids,final int index,final Handle handle,final boolean[] limited)throws Exception{
  final Context c=input.getApplicationContext()!=null?input.getApplicationContext():input;if(ids.isEmpty())return 0;
  final List<String> uris=java.util.Collections.synchronizedList(new ArrayList<String>());final List<String> confirmedIds=java.util.Collections.synchronizedList(new ArrayList<String>());
  final java.util.concurrent.atomic.AtomicReference<Exception> missing=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();final java.util.concurrent.CountDownLatch checked=new java.util.concurrent.CountDownLatch(1);
  // The checks run on their own thread so a provider call that never returns cannot hold the start past the limit.
  WORK.execute(new Runnable(){public void run(){try{for(String id:ids){if(stop.get()||handle!=null&&handle.cancelled())return;Library.Item i=Library.get(c,id);
    if(i==null||!Storage.exists(c,i.uri)){missing.set(new java.io.IOException("A saved audio file is missing"));return;}
    if(stop.get())return;synchronized(uris){uris.add(i.uri);confirmedIds.add(id);}}}catch(Exception e){missing.set(e);}finally{checked.countDown();}}});
  boolean complete=checked.await(CHECK_LIMIT_MS,java.util.concurrent.TimeUnit.MILLISECONDS);stop.set(true);
  if(handle!=null&&handle.cancelled())return 0;
  if(missing.get()!=null)throw missing.get();
  final ArrayList<String> queue;final int selected;
  synchronized(uris){queue=new ArrayList<>(uris);int at=confirmedIds.indexOf(ids.get(Math.max(0,Math.min(index,ids.size()-1))));selected=at<0?0:at;}
  if(!complete){limited[0]=true;NativeIntegration.timelineAsync("play: file check stopped at "+(CHECK_LIMIT_MS/1000)+" s, "+queue.size()+"/"+ids.size()+" confirmed");}
  if(queue.isEmpty())throw new java.io.IOException("Saved files could not be checked in time. Try again.");
  final String token=UUID.randomUUID().toString();Library.prefs(c).edit().putString("native_queue_"+token,new JSONArray(queue).toString()).apply();
  final String group=new Uri.Builder().scheme("ytm").authority("music.youtube.com").appendPath("sideloaded").appendPath("play").appendQueryParameter("list","PLSL").appendQueryParameter("ultra_queue",token).build().toString();
  Object command=Class.forName("otv").getMethod("a",String.class,String.class,int.class,boolean.class).invoke(null,queue.get(selected),group,selected,false);final String mediaId=(String)Class.forName("kud").getMethod("f",Class.forName("bjue")).invoke(null,command);
  if(handle!=null&&handle.cancelled())return 0;
  command(c,mediaId);return queue.size();
 }
 public static void command(final Context c,final String mediaId){
  MAIN.post(new Runnable(){public void run(){final int request=++generation;if(browser!=null){browser.disconnect();browser=null;}browser=new MediaBrowser(c,new ComponentName(c.getPackageName(),"com.google.android.apps.youtube.music.mediabrowser.MusicBrowserService"),new MediaBrowser.ConnectionCallback(){@Override public void onConnected(){if(request!=generation)return;try{MediaController controller=new MediaController(c,browser.getSessionToken());controller.getTransportControls().playFromMediaId(mediaId,new Bundle());NativeIntegration.timeline("player: connected, playFromMediaId sent ("+kind(mediaId)+")");}catch(Exception e){NativeIntegration.timeline("player: playFromMediaId failed "+e.getClass().getSimpleName());Hooks.message("Music could not start saved playback: "+e.getClass().getSimpleName());}}@Override public void onConnectionFailed(){if(request!=generation)return;NativeIntegration.timeline("player: MusicBrowserService connection failed");Hooks.message("Music's player connection is unavailable. Reopen Music and retry.");}@Override public void onConnectionSuspended(){if(request==generation)Hooks.message("Music player disconnected. Tap the saved song to reconnect.");}},null);browser.connect();}});
 }
 /** Media id shape for the Home timeline, never the id itself. */
 static String kind(String mediaId){if(mediaId==null)return "null";int colon=mediaId.indexOf(':');return (colon>0&&colon<24?mediaId.substring(0,colon):"opaque")+", "+mediaId.length()+" chars";}
 /** Supplies native local-file models only for our saved queue; all other endpoints use the original loader. */
 public static Object nativeQueue(Object endpoint){
  try{String group=(String)endpoint.getClass().getField("e").get(endpoint);if(group==null||group.isEmpty())return null;String token=Uri.parse(group).getQueryParameter("ultra_queue");if(token==null)return null;
   Context c=Hooks.context();JSONArray requested=new JSONArray(Library.prefs(c).getString("native_queue_"+token,"[]"));HashMap<String,Library.Item> items=new HashMap<>();for(Library.Item item:Library.all(c))items.put(item.uri,item);ArrayList<Object> tracks=new ArrayList<>();HashSet<String> seen=new HashSet<>();boolean selectedPresent=false;String selected=(String)endpoint.getClass().getField("d").get(endpoint);for(int n=0;n<requested.length();n++){Library.Item item=items.get(requested.getString(n));if(item!=null&&Storage.exists(c,item.uri)&&seen.add(item.uri)){tracks.add(localModel(item));if(item.uri.equals(selected))selectedPresent=true;}}if(tracks.isEmpty()||!selectedPresent)throw new java.io.IOException("The selected saved file is missing");
   Object list=Class.forName("beeh").getMethod("n",Collection.class).invoke(null,tracks);Object queue=Class.forName("kdz").getMethod("l",Class.forName("beeh"),String.class,String.class).invoke(null,list,"Downloads",group);return Class.forName("bfdt").getMethod("i",Object.class).invoke(null,queue);
  }catch(Exception e){try{return Class.forName("bfdt").getMethod("h",Throwable.class).invoke(null,new java.io.IOException("Saved queue is unavailable",e));}catch(Exception ignored){return null;}}
 }
 static Object localModel(Library.Item item)throws Exception{Object model=NativeModels.fresh("bqtc");NativeModels.set(model,"b",1|4|32|0x4000|0x8000|0x20000);NativeModels.set(model,"c","ultra_"+item.id);NativeModels.set(model,"e",item.title);NativeModels.set(model,"i",item.artist);NativeModels.set(model,"r",item.album);NativeModels.set(model,"s",item.uri);NativeModels.set(model,"u",item.duration);try{Context context=Hooks.context();java.io.File artwork=Art.file(context,item.id);if(artwork.isFile()){Object thumb=Class.forName("qml").getMethod("c",String.class,Context.class,int.class).invoke(null,artwork.getAbsolutePath(),context,0x7f0801c3);NativeModels.set(model,"g",thumb);NativeModels.set(model,"b",((Number)model.getClass().getField("b").get(model)).intValue()|16);}}catch(Exception ignored){}return Class.forName("bqst").getConstructor(Class.forName("bqtc")).newInstance(model);}
}
