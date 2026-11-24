package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;

/** DL49: PageSkin scan root + per-pass instrumentation, idempotent Home tab open, Back at a stock tab root. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class DL49Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;NativeIntegration.landedByHomeBack=false;NativeIntegration.homeBackPending=false;NativeIntegration.barController=new java.lang.ref.WeakReference<Object>(null);
  PageSkin.restoreAll();PageSkin.testIds.clear();PageSkin.ids=null;PageSkin.history.clear();PageSkin.scan=null;
 }
 @After public void after(){NativeIntegration.leave();NativeIntegration.destroyed(a);}
 int id(String name){Integer v=PageSkin.testIds.get(name);if(v==null){v=View.generateViewId();PageSkin.testIds.put(name,v);}return v;}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 void layout(View root){a.setContentView(root);View d=a.getWindow().getDecorView();d.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));d.layout(0,0,400,800);}

 // ------------------------------------------------------------------ FAIL 1
 @Test public void pageRowsAreFoundEvenWhenTheFirstFragmentContainerIsNotThePageAndHomeIsStale(){
  LinearLayout window=new LinearLayout(a);window.setOrientation(LinearLayout.VERTICAL);
  FrameLayout playerContainer=new FrameLayout(a);playerContainer.setId(id("fragment_container"));window.addView(playerContainer,new LinearLayout.LayoutParams(-1,10));
  FrameLayout pageContainer=new FrameLayout(a);pageContainer.setId(id("fragment_container"));window.addView(pageContainer,new LinearLayout.LayoutParams(-1,600));
  ultra.test.RecyclerView list=new ultra.test.RecyclerView(a);list.setId(id("section_list_content"));list.setOrientation(LinearLayout.VERTICAL);pageContainer.addView(list,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout swipe=new FrameLayout(a);LinearLayout parent=new LinearLayout(a);parent.setId(id("two_column_item_content_parent"));swipe.addView(parent);ImageView thumb=new ImageView(a);thumb.setId(id("thumbnail"));parent.addView(thumb,new LinearLayout.LayoutParams(56,56));TextView t=new TextView(a);t.setId(id("title"));t.setText("Sing with me");parent.addView(t);list.addView(swipe,new LinearLayout.LayoutParams(-1,72));
  layout(window);
  NativeIntegration.home=null;NativeIntegration.panel=null;
  PageSkin.apply(a.getWindow().getDecorView());
  assertEquals("row matched under the second container",1,(int)PageSkin.report.get("library.rows"));
  PageSkin.Scan s=PageSkin.scan;assertEquals(2,s.containers);assertEquals(1,s.n("section_list_content"));assertEquals(1,s.n("two_column_item_content_parent"));assertTrue(s.lists>=1);
  String line=s.line();assertTrue(line,line.matches("t=[a-z0-9_-]+,cov=[dh0]+,fc=\\d+/\\d+,slc=\\d+,bc=\\d+,sl=\\d+,tc=\\d+,tr=\\d+,ch=\\d+,rv=\\d+,n=\\d+\\+?"));
  assertTrue("passes the Diagnostics filter",line.matches("[a-z0-9_.:=,/-]{1,80}"));
  String report=PageSkin.report();assertTrue(report,report.contains("Last passes that saw page content"));assertTrue(report,report.contains("top classes: "));assertTrue(report,report.contains("slc=1"));
  Diagnostics.event("page_scan",line);assertTrue(Diagnostics.text(a).contains("page_scan#"));
 }
 @Test public void explicitPassesRunAfterAHostTabSwitch(){
  LinearLayout window=new LinearLayout(a);FrameLayout c=new FrameLayout(a);c.setId(id("fragment_container"));window.addView(c,new LinearLayout.LayoutParams(-1,600));layout(window);
  PageSkin.start(a);run(400);long before=PageSkin.passes;
  NativeIntegration.leave();run(3500);
  assertTrue("three explicit passes after the tab switch",PageSkin.passes-before>=3);
  PageSkin.stop();
 }

 // ------------------------------------------------------------------ FAIL 3
 FrameLayout attachWithContainer(){FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);return container;}
 @Test public void repeatedEarlyHomeOpensLeaveOneHomeAndItReusesInsteadOfRebuilding(){
  FrameLayout container=attachWithContainer();
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();
  assertEquals("one Home mount",1,mounts(container));ultra.home.HomeScreen first=NativeIntegration.home;assertNotNull(first);
  assertTrue("loading state or the page, never blank",container.findViewWithTag("home_loading")!=null||container.findViewWithTag("home_empty")!=null||container.findViewWithTag("home_hero")!=null);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();
  assertSame("reused, not rebuilt",first,NativeIntegration.home);assertEquals(1,mounts(container));
  for(int n=0;n<100&&!first.hasContent();n++){try{Thread.sleep(10);}catch(InterruptedException e){}idle();}
  assertTrue("Home has content (empty state with no downloads)",first.hasContent());assertNull(container.findViewWithTag("home_loading"));
 }
 @Test public void homeShowsALoadingStateBeforeItsDataArrives(){
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);ultra.home.HomeScreen h=new ultra.home.HomeScreen(a,mount);
  assertNotNull("loading view until the first data",mount.findViewWithTag("home_loading"));assertFalse(h.hasContent());
  for(int n=0;n<100&&!h.hasContent();n++){try{Thread.sleep(10);}catch(InterruptedException e){}idle();}
  assertTrue(h.hasContent());assertNull(mount.findViewWithTag("home_loading"));h.destroy();
 }
 static int mounts(ViewGroup g){int n=0;for(int k=0;k<g.getChildCount();k++)if("ultra_home_panel".equals(g.getChildAt(k).getTag()))n++;return n;}
 @Test public void healPutsHomeBackOnTopAndRebuildsADetachedMount(){
  FrameLayout container=attachWithContainer();
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();
  View hostPage=new View(a);container.addView(hostPage);// a host page view added later on top of Home
  run(900);assertEquals("Home back on top",NativeIntegration.homeMount,container.getChildAt(container.getChildCount()-1));
  container.removeView(NativeIntegration.homeMount);// something removed the mount
  run(2600);idle();assertNotNull(NativeIntegration.home);assertEquals("rebuilt",1,mounts(container));assertTrue(NativeIntegration.homeMount.isAttachedToWindow());
 }
 @Test public void homeShowsItsPageAfterFiveSecondsEvenIfDataNeverArrives(){
  FrameLayout container=attachWithContainer();NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();
  ultra.home.HomeScreen h=NativeIntegration.home;h.destroy();// executor stopped: data can never arrive
  try{Field d=ultra.home.HomeScreen.class.getDeclaredField("destroyed");d.setAccessible(true);d.setBoolean(h,false);}catch(Exception e){throw new AssertionError(e);}
  run(5200);assertTrue("fallback page instead of a blank shell",h.hasContent());
 }

 // ------------------------------------------------------------------ FAIL 4
 @Test public void backAtAStockTabRootGoesHomeButNeverLoops(){
  final List<Object> selected=new ArrayList<>();NativeIntegration.HomeSelector old=NativeIntegration.homeSelector;
  NativeIntegration.homeSelector=new NativeIntegration.HomeSelector(){public boolean select(Object c){selected.add(c);return true;}};
  try{
   assertFalse("no bar controller yet",NativeIntegration.backAtRoot());
   Object bar=new Object();NativeIntegration.fromTab(bar,NativeIntegration.DOWNLOADS_PIVOT);NativeIntegration.closePanel();NativeIntegration.panel=null;
   NativeIntegration.leave();// user switched to Explore
   assertTrue("Explore root: Back selects Home",NativeIntegration.backAtRoot());assertSame(bar,selected.get(0));
   NativeIntegration.homeBackPending=true;NativeIntegration.leave();NativeIntegration.homeBackPending=false;// Back from Home pivoted to Library
   assertFalse("Library reached by Back from Home: the next Back exits",NativeIntegration.backAtRoot());
   assertTrue("after that, Back at a root goes Home again",NativeIntegration.backAtRoot());
   NativeIntegration.panel=null;FrameLayout container=attachWithContainer();NativeIntegration.openHome(true);idle();
   assertFalse("module screens handle their own Back",NativeIntegration.backAtRoot());
  }finally{NativeIntegration.homeSelector=old;}
  try{Method m=Class.forName("UltraNavigationBridge").getMethod("home",Object.class);assertFalse("not a bar controller",(Boolean)m.invoke(null,new Object()));}catch(Exception e){throw new AssertionError(e);}
  try{Method b=NativeIntegration.class.getMethod("backAtRoot");assertEquals(boolean.class,b.getReturnType());assertTrue(Modifier.isStatic(b.getModifiers()));}catch(Exception e){throw new AssertionError(e);}
 }
}
