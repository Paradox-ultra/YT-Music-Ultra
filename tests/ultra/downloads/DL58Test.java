package ultra.downloads;
import android.app.*;import android.content.*;import android.content.pm.*;import android.content.res.AssetFileDescriptor;import android.database.*;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import java.util.concurrent.*;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL58: Home taps never touch files, content providers, prefs commits or the network on the main thread; Trending taps use the deep link first. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL58Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 /** A SAF-like provider whose file open blocks for 30 s once armed (a slow or hung document provider). */
 public static class SlowProvider extends ContentProvider{
  static volatile boolean armed;static volatile CountDownLatch release=new CountDownLatch(1);static final List<String> opened=Collections.synchronizedList(new ArrayList<String>());
  public boolean onCreate(){return true;}
  public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{opened.add(Thread.currentThread().getName());if(armed)try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException ignored){}throw new FileNotFoundException("slow provider");}
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{openAssetFile(uri,mode);return null;}
  public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}public String getType(Uri u){return "audio/mpeg";}public Uri insert(Uri u,ContentValues v){return null;}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
 }
 Activity a;TrendingFeed.Fetcher realFetcher;
 @Before public void setup()throws Exception{
  realFetcher=TrendingFeed.fetcher;SlowProvider.armed=false;SlowProvider.opened.clear();SlowProvider.release=new CountDownLatch(1);
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  java.lang.reflect.Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  TrendingFeed.cacheFile(a).delete();TrendingFeed.resetSession();
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{throw new IOException("offline");}};
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();Art.network=false;
  synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();}
  Robolectric.setupContentProvider(SlowProvider.class,"ultra.slow");
 }
 @After public void after(){SlowProvider.armed=false;SlowProvider.release.countDown();TrendingFeed.fetcher=realFetcher;NativePlayback.MAIN.removeCallbacksAndMessages(null);NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 static String vid(int n){return String.format("vid%08d",n);}
 static List<String> timeline(){synchronized(NativeIntegration.TIMELINE){return new ArrayList<>(NativeIntegration.TIMELINE);}}
 static List<View> all(View root,String prefix){List<View> out=new ArrayList<>();ArrayDeque<View> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){View v=q.poll();if(v.getTag() instanceof String&&((String)v.getTag()).startsWith(prefix))out.add(v);if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}return out;}
 /** Downloads whose files live behind the slow content provider (as SAF downloads do). */
 void slowDownloads()throws Exception{for(int k=0;k<10;k++){Library.Item i=Library.add(a,String.format("dl%09d",k),"Download "+k);i.artist="Artist "+(k%3);i.state="done";i.mime="audio/mpeg";i.created=1000+k;i.uri="content://ultra.slow/tree/doc"+k;}Library.save(a);}
 HomeScreen home(FrameLayout mount)throws Exception{HomeScreen h=new HomeScreen(a,mount);for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){Thread.sleep(10);run(50);}run(600);return h;}
 long clickMs(View v){long t=System.nanoTime();v.performClick();return (System.nanoTime()-t)/1000000;}

 @Test public void songTapReturnsAtOnceWhileTheContentProviderHangs()throws Exception{
  slowDownloads();FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=home(mount);
  List<View> tiles=all(mount,"home_tile:song:");assertFalse("song tiles on Home",tiles.isEmpty());
  SlowProvider.armed=true;SlowProvider.opened.clear();
  long ms=clickMs(tiles.get(0));assertTrue("tile tap returned in "+ms+" ms",ms<200);
  for(int n=0;n<200&&SlowProvider.opened.isEmpty();n++)Thread.sleep(5);
  assertFalse("the existence check ran",SlowProvider.opened.isEmpty());assertNotEquals("not on the main thread",Looper.getMainLooper().getThread().getName(),SlowProvider.opened.get(0));
  assertTrue(SlowProvider.opened.get(0),SlowProvider.opened.get(0).startsWith("ultra-"));
  // Every other Home tap while the provider still hangs: hero card, hero play, quick-pick cards, chips.
  View hero=mount.findViewWithTag("home_hero");if(hero!=null)assertTrue(clickMs(hero)<200);
  View heroPlay=mount.findViewWithTag("home_hero_play");if(heroPlay!=null)assertTrue(clickMs(heroPlay)<200);
  for(View card:all(mount,"home_card:"))assertTrue(clickMs(card)<200);
  long t=System.nanoTime();for(int n=0;n<20;n++)run(50);assertTrue("main looper keeps running",(System.nanoTime()-t)/1000000<5000);
  SlowProvider.release.countDown();h.destroy();
 }
 @Test public void chipsArtistsAndPlaylistTapsStayOnTheMainThreadWithoutIo()throws Exception{
  slowDownloads();JSONArray order=new JSONArray().put(new JSONObject().put("id","PLabcdef").put("title","Mix"));
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=home(mount);SlowProvider.armed=true;
  for(View chip:all(mount,"chip:"))assertTrue(clickMs(chip)<200);
  for(String name:HomeLogic.CHIPS){View c=mount.findViewWithTag("chip:"+name);if(c!=null){assertTrue(clickMs(c)<200);run(50);for(View card:all(mount,"home_card:"))assertTrue(clickMs(card)<200);}}
  assertTrue("the providers were never opened on the main thread",!SlowProvider.opened.contains(Looper.getMainLooper().getThread().getName()));
  h.destroy();
 }
 @Test public void trendingTapUsesTheHostDeepLinkFirst()throws Exception{
  Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/watch?v="+vid(3)));view.setPackage(a.getPackageName());
  ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName=a.getPackageName();info.activityInfo.name=a.getPackageName()+".MusicActivity";
  Shadows.shadowOf(a.getPackageManager()).addResolveInfoForIntent(view,info);
  String route=HomeSource.playOnline(a,vid(3));assertTrue(route,route.startsWith("intent -> "));assertTrue("host component named",route.contains("MusicActivity"));
  Intent started=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("https://music.youtube.com/watch?v="+vid(3),started.getDataString());assertEquals(a.getPackageName(),started.getPackage());
  assertTrue((started.getFlags()&Intent.FLAG_ACTIVITY_SINGLE_TOP)!=0);assertEquals("an Activity context: no NEW_TASK",0,started.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK);
  assertTrue("leave() ran first",timeline().toString().contains("leave (host tab switch)"));
  Intent fromApp=HomeSource.watchIntent(a.getApplication(),vid(3));assertTrue((fromApp.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
  // No activity for the link: the watch bridge is the fallback (it builds the command here), never before the link.
  String other=HomeSource.playOnline(a,vid(4));assertTrue(other,other.startsWith("watch -> ")||other.equals("none"));assertNull("no activity started",Shadows.shadowOf(a).getNextStartedActivity());
 }
 @Test public void trendingTileTapLogsTheRouteAndReturnsFast()throws Exception{
  slowDownloads();JSONArray songs=new JSONArray();for(int n=0;n<10;n++)songs.put(new JSONObject().put("id",vid(n)).put("title","S"+n).put("artist","A").put("thumb",""));
  java.nio.file.Files.write(TrendingFeed.cacheFile(a).toPath(),new JSONObject().put("at",System.currentTimeMillis()).put("gl","IN").put("songs",songs).toString().getBytes("UTF-8"));
  Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/watch?v="+vid(0)));view.setPackage(a.getPackageName());
  ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName=a.getPackageName();info.activityInfo.name=a.getPackageName()+".MusicActivity";
  Shadows.shadowOf(a.getPackageManager()).addResolveInfoForIntent(view,info);
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);
  for(int n=0;n<300&&all(mount,"home_tile:online:").isEmpty();n++){Thread.sleep(10);run(50);}
  SlowProvider.armed=true;long ms=clickMs(all(mount,"home_tile:online:").get(0));assertTrue(ms+" ms",ms<200);
  String t=timeline().toString();assertTrue(t,t.contains("trending tap "+vid(0)+": intent -> .MusicActivity"));h.destroy();
 }
 @Test public void diagnosticsEventsNeverWriteOnTheCallersThread()throws Exception{
  File log=new File(a.getFilesDir(),Diagnostics.LOG);log.delete();final CountDownLatch hold=new CountDownLatch(1);
  Diagnostics.WRITER.execute(new Runnable(){public void run(){try{hold.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}});
  long t=System.nanoTime();Diagnostics.event("home_open","native-playlist");long ms=(System.nanoTime()-t)/1000000;
  assertTrue(ms<100);assertFalse("not written on the caller's thread",log.exists()&&new String(java.nio.file.Files.readAllBytes(log.toPath()),"UTF-8").contains("native-playlist"));
  assertTrue("pending lines are shown",Diagnostics.text(a).contains("home_open native-playlist"));
  hold.countDown();for(int n=0;n<200&&!(log.exists()&&new String(java.nio.file.Files.readAllBytes(log.toPath()),"UTF-8").contains("native-playlist"));n++)Thread.sleep(10);
  assertTrue("written by the writer thread",new String(java.nio.file.Files.readAllBytes(log.toPath()),"UTF-8").contains("home_open native-playlist"));
 }
}
