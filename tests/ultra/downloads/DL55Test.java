package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL55: cover counters counted where the bitmap is set (real paths), quiet timeline for repeated host opens. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL55Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();HomeScreen.lastReport="";Art.network=false;
  synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();NativeIntegration.repeatCount=0;NativeIntegration.repeatCallers.clear();}
  File dir=new File(a.getFilesDir(),"ultra_audio_art");if(dir.isDirectory())for(File x:dir.listFiles())x.delete();
 }
 @After public void after(){HomeSource.embeddedReader=null;NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 static byte[] jpeg(int color){Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(color);ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,o);return o.toByteArray();}
 void library()throws Exception{
  for(int k=0;k<14;k++){String id=String.format("dd%09d",k);Library.Item i=Library.add(a,id,"Song "+k);i.artist="Artist "+(k%3);i.state="done";i.mime="audio/mpeg";i.created=1000+k;i.uri=new File(a.getCacheDir(),id+".mp3").getPath();
   if(k>=6){File f=Art.file(a,id);f.getParentFile().mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write(jpeg(0xff336699));}}}
  Library.save(a);HomeSource.embeddedReader=new HomeSource.EmbeddedReader(){public byte[] read(Context c,String uri){return jpeg(0xffaa3344);}};
 }
 void settle(HomeScreen h)throws Exception{for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){Thread.sleep(10);run(50);}for(int n=0;n<200;n++){Thread.sleep(10);idle();}run(4500);}
 /** Covers actually on screen per section, read from the ImageViews themselves. */
 static Map<String,int[]> onScreen(View root){Map<String,int[]> m=new HashMap<>();ArrayDeque<View> q=new ArrayDeque<>();q.add(root);
  while(!q.isEmpty()){View v=q.poll();if(v instanceof ImageView&&v.getTag() instanceof Object[]){String sec=(String)((Object[])v.getTag())[2];int[] c=m.get(sec);if(c==null){c=new int[2];m.put(sec,c);}c[0]++;if(((ImageView)v).getDrawable() instanceof BitmapDrawable)c[1]++;}if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}return m;}

 @Test public void coverCountersMatchBitmapsSetThroughEveryRealPath()throws Exception{
  library();
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();HomeScreen h=NativeIntegration.home;settle(h);
  Map<String,int[]> seen=onScreen(container);int[] recent=seen.get("recent"),quick=seen.get("quick");
  assertEquals("all 8 recent covers on screen (saved files)",8,recent[1]);assertTrue("quick covers on screen (embedded)",quick[1]>0);
  String d=h.diagnostics();
  assertTrue(d,d.contains("recent: items 8, covers 8/8 (file=8)"));assertTrue(d,d.contains("quick: items "+quick[0]+", covers "+quick[1]+"/"+quick[0]+" (embedded="+quick[1]+")"));
  String r=NativeIntegration.homeReport();
  assertTrue(r,r.contains("covers on screen: recent 8/8, quick "+quick[1]+"/"+quick[0]));assertFalse("render lines no longer print at-render-time art counts",r.contains(" art 0/"));
  // closing Home (e.g. opening Download settings) keeps the last report for Diagnostics
  NativeIntegration.leave();idle();String after=NativeIntegration.homeReport();
  assertTrue(after,after.contains("Ultra Home: not open now; last shown:"));assertTrue(after,after.contains("recent: items 8, covers 8/8"));
  // a second Home gets every cover from the cache: counted on the cache-hit path too
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();HomeScreen h2=NativeIntegration.home;assertNotSame(h,h2);settle(h2);
  assertTrue(h2.diagnostics(),h2.diagnostics().contains("recent: items 8, covers 8/8"));assertTrue(h2.diagnostics(),h2.diagnostics().contains("quick: items "+quick[0]+", covers "+quick[1]+"/"+quick[0]));
 }
 @Test public void repeatedHostOpensWhileHomeShowsAreOneSummaryLineWithTheirCaller()throws Exception{
  library();FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();settle(NativeIntegration.home);
  int lines;synchronized(NativeIntegration.TIMELINE){lines=NativeIntegration.TIMELINE.size();}
  for(int k=0;k<30;k++){NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);run(1000);}
  synchronized(NativeIntegration.TIMELINE){assertEquals("no timeline entry per repeated call",lines,NativeIntegration.TIMELINE.size());}
  String r=NativeIntegration.homeReport();
  assertTrue(r,r.contains("repeat fromTab FEmusic_ultra_home while Home was showing (no-op): x30"));assertTrue("caller frames recorded",r.contains("callers {")&&r.contains("=30}"));
  assertFalse(r,r.contains("openHome on the open Home tab: no-op"));assertEquals(1,r.split("repeat fromTab").length-1);
 }
}
