package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL52: quick-pick covers from embedded art (and their source in Diagnostics), no render churn from repeated fromTab. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL52Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();Art.network=false;
  synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();NativeIntegration.repeatCount=0;NativeIntegration.repeatCallers.clear();}
  File dir=new File(a.getFilesDir(),"ultra_audio_art");if(dir.isDirectory())for(File x:dir.listFiles())x.delete();
 }
 @After public void after(){HomeSource.embeddedReader=null;NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 static byte[] jpeg(int color){Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(color);ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,90,o);return o.toByteArray();}
 /** 14 songs: the 8 newest have a saved cover file (Recent), the 6 older ones only an embedded cover (Quick picks). */
 void library()throws Exception{
  for(int k=0;k<14;k++){String id=String.format("qp%09d",k);Library.Item i=Library.add(a,id,"Song "+k);i.artist="Artist "+(k%3);i.state="done";i.mime="audio/mpeg";i.created=1000+k;i.uri=new File(a.getCacheDir(),id+".mp3").getPath();
   if(k>=6){File f=Art.file(a,id);f.getParentFile().mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write(jpeg(0xff336699));}}}
  Library.save(a);
  HomeSource.embeddedReader=new HomeSource.EmbeddedReader(){public byte[] read(Context c,String uri){return jpeg(0xffaa3344);}};
 }
 void waitLoaded(HomeScreen h)throws Exception{for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){Thread.sleep(10);idle();}for(int n=0;n<150;n++){Thread.sleep(10);idle();}run(600);}

 @Test public void quickPicksWithoutASavedCoverUseTheEmbeddedCover()throws Exception{
  library();FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);waitLoaded(h);run(4500);
  String d=h.diagnostics();
  assertTrue(d,d.contains("recent: items 8, covers 8/8 (file=8)"));
  assertTrue(d,d.matches("(?s).*quick: items \\d, covers (\\d)/\\1 \\(embedded=\\d\\).*"));
  assertTrue("embedded cover saved as the cover file for every screen",Art.file(a,"qp000000000").isFile());
  h.destroy();
 }
 @Test public void songsWithNoCoverAnywhereAreReportedAsNone()throws Exception{
  library();HomeSource.embeddedReader=new HomeSource.EmbeddedReader(){public byte[] read(Context c,String uri){return null;}};
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);waitLoaded(h);run(4500);
  String d=h.diagnostics();assertTrue(d,d.matches("(?s).*quick: items \\d, covers 0/\\d \\(none=\\d\\).*"));h.destroy();
 }
 @Test public void repeatedFromTabOnALoadedHomeDoesNotReRender()throws Exception{
  library();FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();HomeScreen h=NativeIntegration.home;waitLoaded(h);
  int before=renders(h);
  for(int tick=0;tick<8;tick++){NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);run(700);}
  run(3000);
  assertSame(h,NativeIntegration.home);assertEquals("no render from repeated host opens",before,renders(h));
  assertTrue(NativeIntegration.homeReport(),NativeIntegration.homeReport().contains("repeat fromTab FEmusic_ultra_home while Home was showing (no-op): x8"));
  assertTrue("first load renders at most twice (songs, then playlists)",before<=2);
 }
 @Test public void rendersAreDebouncedAndUnchangedDataIsSkipped()throws Exception{
  library();FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);waitLoaded(h);
  int before=renders(h);
  Method rr=HomeScreen.class.getDeclaredMethod("requestRender");rr.setAccessible(true);for(int k=0;k<10;k++)rr.invoke(h);run(700);
  assertEquals("ten requests, unchanged data: nothing rebuilt",before,renders(h));assertTrue(h.diagnostics().contains("skipped as unchanged"));
  h.destroy();
 }
 /** DL53: the covers counter equals the covers actually on screen, including ones set late or from the cache. */
 @Test public void coverCounterMatchesCoversShownOnScreen()throws Exception{
  library();FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);waitLoaded(h);run(4500);
  int onScreen=0,views=0;ArrayDeque<View> q=new ArrayDeque<>();q.add(mount);
  while(!q.isEmpty()){View v=q.poll();if(v instanceof ImageView&&v.getTag() instanceof Object[]&&"quick".equals(((Object[])v.getTag())[2])){views++;if(((ImageView)v).getDrawable()!=null)onScreen++;}if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}
  assertTrue("quick covers visible",onScreen>0);assertTrue(h.diagnostics(),h.diagnostics().contains("quick: items "+views+", covers "+onScreen+"/"+views));
  h.refresh();waitLoaded(h);run(1000);// a re-render reuses cached covers: still counted
  assertTrue(h.diagnostics(),h.diagnostics().contains("covers "+onScreen+"/"+views));
  h.destroy();
 }
 /** DL53: NativeIntegration.homeShowing()Z is public static, side-effect free, true only for a built Home on top. */
 @Test public void homeShowingReportsABuiltHomeOnTop()throws Exception{
  Method m=NativeIntegration.class.getMethod("homeShowing");assertTrue(Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers()));assertEquals(boolean.class,m.getReturnType());assertEquals(0,m.getParameterTypes().length);
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  assertFalse("no Home",NativeIntegration.homeShowing());
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);assertFalse("queued, not built yet",NativeIntegration.homeShowing());idle();
  assertTrue("built and on top",NativeIntegration.homeShowing());
  int children=container.getChildCount();HomeScreen h=NativeIntegration.home;for(int k=0;k<3;k++)assertTrue(NativeIntegration.homeShowing());
  assertEquals("no side effects",children,container.getChildCount());assertSame(h,NativeIntegration.home);
  View cover=new View(a);container.addView(cover);assertFalse("another view on top",NativeIntegration.homeShowing());container.removeView(cover);
  NativeIntegration.homeMount.setVisibility(View.GONE);assertFalse("hidden",NativeIntegration.homeShowing());NativeIntegration.homeMount.setVisibility(View.VISIBLE);
  NativeIntegration.leave();idle();assertFalse("closed by a host tab switch",NativeIntegration.homeShowing());
 }
 static int renders(HomeScreen h)throws Exception{Field f=HomeScreen.class.getDeclaredField("renders");f.setAccessible(true);return f.getInt(h);}
}
