package ultra.downloads;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;import static org.junit.Assert.*;

/** DL58 addendum: Downloads Play / Shuffle and "Reading playlist" never block the main thread, Back always dismisses, 20 s limits. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL58DownloadsTest {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;DownloadsScreen.PlaylistReader realReader;final CountDownLatch release=new CountDownLatch(1);
 @Before public void setup()throws Exception{
  realReader=DownloadsScreen.reader;DL58Test.SlowProvider.armed=false;DL58Test.SlowProvider.opened.clear();DL58Test.SlowProvider.release=new CountDownLatch(1);
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();Art.network=false;
  synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();}
  Robolectric.setupContentProvider(DL58Test.SlowProvider.class,"ultra.slow");
 }
 @After public void after(){release.countDown();DL58Test.SlowProvider.armed=false;DL58Test.SlowProvider.release.countDown();DownloadsScreen.reader=realReader;DownloadsScreen.READ_LIMIT_MS=20000;NativePlayback.CHECK_LIMIT_MS=20000;NativePlayback.MAIN.removeCallbacksAndMessages(null);}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 /** 281 saved songs (the tester's library size); the first `real` are ordinary files, the rest sit behind the slow provider. */
 void library(int real)throws Exception{for(int k=0;k<281;k++){Library.Item i=Library.add(a,String.format("s%010d",k),"Song "+k);i.artist="A"+(k%9);i.state="done";i.mime="audio/mpeg";i.created=1000+k;
   if(k<real){File file=new File(a.getFilesDir(),"song"+k+".mp3");try(FileOutputStream o=new FileOutputStream(file)){o.write(1);}i.uri=file.getAbsolutePath();}else i.uri="content://ultra.slow/tree/doc"+k;}Library.save(a);}
 DownloadsScreen screen(Intent in){FrameLayout mount=new FrameLayout(a);a.setContentView(mount);DownloadsScreen s=new DownloadsScreen(a,mount,in);run(2000);return s;}
 static boolean queued(Context c){for(String k:Library.prefs(c).getAll().keySet())if(k.startsWith("native_queue_"))return true;return false;}
 static String timeline(){synchronized(NativeIntegration.TIMELINE){return NativeIntegration.TIMELINE.toString();}}

 @Test public void playAllOver281SlowFilesKeepsTheMainThreadFreeAndBackCancels()throws Exception{
  library(0);DownloadsScreen s=screen(new Intent());DL58Test.SlowProvider.armed=true;DL58Test.SlowProvider.opened.clear();
  Method playAll=DownloadsScreen.class.getDeclaredMethod("playAll",boolean.class);playAll.setAccessible(true);
  long t=System.nanoTime();playAll.invoke(s,true);long ms=(System.nanoTime()-t)/1000000;assertTrue("Shuffle returned in "+ms+" ms",ms<200);
  for(int n=0;n<200&&DL58Test.SlowProvider.opened.isEmpty();n++)Thread.sleep(5);
  assertFalse("checks started",DL58Test.SlowProvider.opened.isEmpty());assertFalse("never on the main thread",DL58Test.SlowProvider.opened.contains(Looper.getMainLooper().getThread().getName()));
  long t2=System.nanoTime();run(600);assertTrue("main looper keeps running",(System.nanoTime()-t2)/1000000<2000);
  Dialog d=ShadowDialog.getLatestDialog();assertNotNull("Preparing playback shown after 400 ms",d);assertTrue(d.isShowing());
  d.onBackPressed();run(50);assertFalse("Back dismisses",d.isShowing());
  DL58Test.SlowProvider.armed=false;DL58Test.SlowProvider.release.countDown();Thread.sleep(300);run(200);
  assertFalse("cancelled: nothing queued",queued(a));assertFalse(timeline().contains("player:"));
 }
 @Test public void fileCheckStopsAt20sEquivalentAndGoesOnWithTheConfirmedSongs()throws Exception{
  library(10);NativePlayback.CHECK_LIMIT_MS=300;DL58Test.SlowProvider.armed=true;
  final ArrayList<String> ids=new ArrayList<>();for(int k=0;k<281;k++)ids.add(String.format("s%010d",k));
  final Object[] result=new Object[3];long t=System.nanoTime();
  NativePlayback.startAsync(a,ids,0,new NativePlayback.Done(){public void done(int q,Exception e,boolean limited){result[0]=q;result[1]=e;result[2]=limited;}});
  assertTrue((System.nanoTime()-t)/1000000<100);
  for(int n=0;n<300&&!queued(a);n++)Thread.sleep(10);
  assertTrue("queue built long before the 30 s provider calls end",queued(a));
  String q=null;for(Map.Entry<String,?> e:Library.prefs(a).getAll().entrySet())if(e.getKey().startsWith("native_queue_"))q=(String)e.getValue();
  assertEquals("only the 10 confirmed songs are queued",10,new org.json.JSONArray(q).length());
  Thread.sleep(100);NativePlayback.MAIN.removeCallbacksAndMessages(null);// the host MediaBrowser connect (not available under Robolectric)
  run(50);assertTrue(timeline(),timeline().contains("play: file check stopped at 0 s, 10/281 confirmed"));
 }
 @Test public void readingPlaylistIsDismissedByBackAndTheReadIsCancelled()throws Exception{
  final List<String> events=Collections.synchronizedList(new ArrayList<String>());
  DownloadsScreen.reader=new DownloadsScreen.PlaylistReader(){public ArrayList<Catalog.Track> read(String id)throws Exception{events.add("start");try{release.await(60,TimeUnit.SECONDS);}catch(InterruptedException e){events.add("interrupted");throw e;}return new ArrayList<>();}};
  long t=System.nanoTime();DownloadsScreen s=screen(new Intent().putExtra("playlist","PLslowlist"));
  Dialog busy=null;for(Dialog d:ShadowDialog.getShownDialogs())if(d instanceof ProgressDialog)busy=d;assertNotNull("Reading playlist shown",busy);assertTrue(busy.isShowing());
  for(int n=0;n<200&&events.isEmpty();n++)Thread.sleep(5);assertEquals("start",events.get(0));
  busy.onBackPressed();run(50);assertFalse("Back dismisses Reading playlist",busy.isShowing());
  for(int n=0;n<200&&events.size()<2;n++)Thread.sleep(5);assertEquals("the read is cancelled","interrupted",events.get(1));
  run(500);assertTrue("no confirm dialog afterwards",ShadowDialog.getLatestDialog()==busy);
 }
 @Test public void readingPlaylistGivesUpAtTheLimit()throws Exception{
  DownloadsScreen.READ_LIMIT_MS=300;DownloadsScreen.reader=new DownloadsScreen.PlaylistReader(){public ArrayList<Catalog.Track> read(String id)throws Exception{release.await(60,TimeUnit.SECONDS);return new ArrayList<>();}};
  DownloadsScreen s=screen(new Intent().putExtra("playlist","PLslowlist"));
  Dialog busy=null;for(Dialog d:ShadowDialog.getShownDialogs())if(d instanceof ProgressDialog)busy=d;assertNotNull(busy);
  for(int n=0;n<200&&busy.isShowing();n++){Thread.sleep(10);run(20);}
  assertFalse("Reading playlist closed after the limit, not after 60 s",busy.isShowing());
 }
}
