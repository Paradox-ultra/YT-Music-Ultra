package ultra.downloads;
import android.content.*;import android.graphics.*;import android.widget.*;import android.os.*;import java.io.*;import java.lang.ref.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/**
 * Artwork for saved songs. Uses only the existing sources: the persisted file under files/ultra_audio_art and the
 * item's existing artUrl (filled by Catalog.metadata). One job per id at a time; views asking for an id that is already
 * being loaded wait for that job. A failed fetch is retried after a backoff, at most MAX_ATTEMPTS times per process.
 */
final class Art{
 static boolean network=true;
 static final long RETRY_MS=30000;static final int MAX_ATTEMPTS=3;
 /** id -> {failed attempts, uptime of last failure}. */
 static final Map<String,long[]> failures=new HashMap<>();
 static final android.util.LruCache<String,Bitmap> memory=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount());}};
 static final ExecutorService jobs=Executors.newSingleThreadExecutor();
 /** id -> views waiting for the job already queued for that id. */
 static final Map<String,List<WeakReference<ImageView>>> waiting=new HashMap<>();
 static final Handler main=new Handler(Looper.getMainLooper());
 /** Number of jobs started; tests use it to prove de-duplication. */
 static final AtomicInteger started=new AtomicInteger();
 static void invalidate(Context c,String id){memory.remove(id);synchronized(failures){failures.remove(id);}File f=file(c,id);if(f.exists())f.delete();}
 static File file(Context c,String id){return new File(new File(c.getFilesDir(),"ultra_audio_art"),id+".jpg");}

 static boolean retryAllowed(String id,long now){synchronized(failures){long[] f=failures.get(id);if(f==null)return true;if(f[0]>=MAX_ATTEMPTS)return false;return now-f[1]>=RETRY_MS*(1L<<Math.max(0,f[0]-1));}}
 static void failed(String id,long now){synchronized(failures){long[] f=failures.get(id);if(f==null)f=new long[2];f[0]++;f[1]=now;failures.put(id,f);}}
 static void succeeded(String id){synchronized(failures){failures.remove(id);}}

 /** Artwork URL candidates, YouTube image hosts only. The item's stored artUrl comes first; every YouTube song id also has a direct thumbnail, so a missing or failed artUrl never leaves a song without cover art. */
 static List<String> artUrls(Library.Item i){
  List<String> urls=new ArrayList<>();
  if(i.artUrl!=null&&i.artUrl.startsWith("https://"))urls.add(i.artUrl);
  if(Catalog.metadataAllowed(i)){String direct="https://i.ytimg.com/vi/"+i.id+"/hqdefault.jpg";if(!urls.contains(direct))urls.add(direct);}
  return urls;
 }
 static boolean allowed(String url){try{if(!url.startsWith("https://"))return false;String h=new URL(url).getHost();return h.endsWith(".ytimg.com")||h.equals("i.ytimg.com")||h.endsWith(".googleusercontent.com")||h.equals("www.gstatic.com")||h.endsWith(".ggpht.com");}catch(Exception e){return false;}}

 /** Downloads the item's artwork URL (YouTube image hosts only) to its persisted file. Returns true when the file exists afterwards. */
 static boolean fetch(Context c,Library.Item i){
  File target=file(c,i.id);if(target.isFile())return true;
  for(String url:artUrls(i))if(fetch(c,i,url))return true;
  return target.isFile();
 }
 static boolean fetch(Context c,Library.Item i,String artUrl){
  File target=file(c,i.id);if(target.isFile())return true;
  if(artUrl==null||!artUrl.startsWith("https://")||!allowed(artUrl))return false;
  try{URL u=new URL(artUrl);
   HttpURLConnection con=(HttpURLConnection)u.openConnection();con.setConnectTimeout(8000);con.setReadTimeout(8000);
   try{if(con.getResponseCode()!=200)return false;ByteArrayOutputStream out=new ByteArrayOutputStream();
    try(InputStream in=con.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)return false;out.write(b,0,n);}}
    byte[] data=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
    // Tiny images are YouTube's grey missing-thumbnail placeholder, not artwork.
    if(options.outWidth<200||options.outWidth>3000||options.outHeight>3000)return false;
    target.getParentFile().mkdirs();
    // Written to a private temp file and renamed, so the service and the screen never see a half-written image.
    File tmp=new File(target.getPath()+"."+Thread.currentThread().getId()+".tmp");
    try(FileOutputStream f=new FileOutputStream(tmp)){f.write(data);}
    if(!target.isFile()&&!tmp.renameTo(target)){tmp.delete();return target.isFile();}
    tmp.delete();return true;
   }finally{con.disconnect();}
  }catch(Exception ignored){return target.isFile();}
 }

 static void load(Context c,Library.Item i,ImageView view){load(c,i,view,Math.round(56*c.getResources().getDisplayMetrics().density));}
 static void load(final Context c,final Library.Item i,ImageView view,final int size){
  final String id=i.id;view.setTag(id);
  Bitmap saved=memory.get(id);if(saved!=null){view.setImageBitmap(saved);return;}
  view.setImageDrawable(Covers.placeholder(c,Covers.kindOf(i)));
  final Context app=c.getApplicationContext()!=null?c.getApplicationContext():c;
  if(!retryAllowed(id,SystemClock.uptimeMillis())||!file(app,id).isFile()&&(!network||artUrls(i).isEmpty()&&!Catalog.metadataAllowed(i)))return;
  synchronized(waiting){List<WeakReference<ImageView>> list=waiting.get(id);if(list!=null){list.add(new WeakReference<>(view));return;}list=new ArrayList<>();list.add(new WeakReference<>(view));waiting.put(id,list);}
  started.incrementAndGet();
  try{jobs.execute(new Runnable(){public void run(){
   Bitmap bitmap=null;
   try{File f=file(app,id);
    if(!f.isFile()&&network){String before=i.title+"\u0000"+i.artist+"\u0000"+i.artUrl;if(i.artUrl==null||i.artUrl.isEmpty())Catalog.metadata(i);fetch(app,i);if(!before.equals(i.title+"\u0000"+i.artist+"\u0000"+i.artUrl))try{Library.save(app);}catch(Exception ignored){}}
    if(f.isFile())bitmap=decode(f,size);
   }catch(Throwable ignored){}
   if(bitmap!=null){memory.put(id,bitmap);succeeded(id);}else failed(id,SystemClock.uptimeMillis());
   deliver(id,bitmap);
  }});}catch(RejectedExecutionException e){synchronized(waiting){waiting.remove(id);}}
 }
 static void deliver(final String id,final Bitmap bitmap){
  final List<WeakReference<ImageView>> views;synchronized(waiting){views=waiting.remove(id);}
  if(views==null||bitmap==null)return;
  main.post(new Runnable(){public void run(){for(WeakReference<ImageView> r:views){ImageView v=r.get();if(v!=null&&id.equals(v.getTag()))v.setImageBitmap(bitmap);}}});
 }
 /** Decodes near the displayed size and removes letterbox bars, which YouTube video thumbnails carry above and below the picture. */
 static Bitmap decode(File f,int size){
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),bounds);
  if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
  int sample=1,want=Math.max(1,size);while(bounds.outWidth/(sample*2)>=want&&bounds.outHeight/(sample*2)>=want)sample*=2;
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;Bitmap b=BitmapFactory.decodeFile(f.getPath(),options);
  return b==null?null:trim(b);
 }
 static Bitmap trim(Bitmap b){
  int w=b.getWidth(),h=b.getHeight();if(w<8||h<8)return b;
  int top=0,bottom=0,limit=h/4;
  while(top<limit&&dark(b,top))top++;
  while(bottom<limit&&dark(b,h-1-bottom))bottom++;
  // Only symmetric bars count as letterboxing; a dark edge on one side is part of the artwork.
  if(top<h/20||bottom<h/20||Math.abs(top-bottom)>2+h/50)return b;
  try{return Bitmap.createBitmap(b,0,top,w,h-top-bottom);}catch(Throwable e){return b;}
 }
 static boolean dark(Bitmap b,int y){int w=b.getWidth();for(int k=0;k<8;k++){int px=b.getPixel(Math.min(w-1,w*k/8+w/16),y);if(Color.red(px)>20||Color.green(px)>20||Color.blue(px)>20)return false;}return true;}
}
