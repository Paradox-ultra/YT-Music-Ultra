import app.revanced.extension.shared.utils.Utils;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.ref.*;import java.lang.reflect.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowLooper;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class DL27BackTest {
 public static class Host extends Activity implements yo,bps {
  int exits,libraryClicks;final yn dispatcher=new yn(()->exits++);
  final bpp lifecycle=new bpp(){public bpo a(){return bpo.e;}public void b(bpr observer){((bpq)observer).a(Host.this,bpn.ON_START);}public void c(bpr observer){}};
  public yn getOnBackPressedDispatcher(){return dispatcher;}public bpp getLifecycle(){return lifecycle;}
 }
 Host host;
 static Object get(String name)throws Exception{Field f=ultra.downloads.NativeIntegration.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static void set(String name,Object value)throws Exception{Field f=ultra.downloads.NativeIntegration.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 @Before public void setup()throws Exception{
  host=Robolectric.buildActivity(Host.class).setup().get();host.setTheme(android.R.style.Theme_Material_NoActionBar);Utils.testContext=host;
  FrameLayout root=new FrameLayout(host),content=new FrameLayout(host);content.setId(0x7f0b04e1);root.addView(content);
  Button library=new Button(host);library.setContentDescription("Library");library.setOnClickListener(v->host.libraryClicks++);root.addView(library,new FrameLayout.LayoutParams(80,50,Gravity.BOTTOM));host.setContentView(root);root.layout(0,0,400,800);library.layout(0,750,80,800);
  set("activity",new WeakReference<Activity>(host));set("navigation",new WeakReference<Object>(null));ultra.downloads.NativeIntegration.open();ShadowLooper.idleMainLooper();assertTrue(ultra.downloads.NativeIntegration.downloadsVisible());
 }
 @After public void cleanup(){ultra.downloads.NativeIntegration.closePanel();UltraBackBridge.remove(host);}
 @Test public void dispatcherBackReturnsToLibraryWithoutExitingThenHostOwnsLibraryBack()throws Exception{
  host.dispatcher.c();assertFalse(ultra.downloads.NativeIntegration.downloadsVisible());assertEquals(0,host.exits);assertEquals(1,host.libraryClicks);
  host.dispatcher.c();assertEquals(1,host.exits);
 }
 @Test public void playlistDetailBackStaysInDownloadsBeforeRootBack()throws Exception{
  Object panel=get("panel");Field group=panel.getClass().getDeclaredField("group");group.setAccessible(true);group.set(panel,"PLfixture");
  host.dispatcher.c();assertEquals("",group.get(panel));assertTrue(ultra.downloads.NativeIntegration.downloadsVisible());assertEquals(0,host.libraryClicks);assertEquals(0,host.exits);
  host.dispatcher.c();assertFalse(ultra.downloads.NativeIntegration.downloadsVisible());assertEquals(1,host.libraryClicks);
 }
 @Test public void selectionBackIsConsumedBeforeClosingDownloads()throws Exception{
  Object panel=get("panel");Field selection=panel.getClass().getDeclaredField("selectionMode");selection.setAccessible(true);selection.setBoolean(panel,true);
  host.dispatcher.c();assertFalse(selection.getBoolean(panel));assertTrue(ultra.downloads.NativeIntegration.downloadsVisible());assertEquals(0,host.exits);
 }
 @Test public void registrationIsReplacedNotStackedAndRemovedWhenPanelCloses(){UltraBackBridge.install(host);UltraBackBridge.install(host);host.dispatcher.c();assertEquals(1,host.libraryClicks);host.dispatcher.c();assertEquals(1,host.exits);}
}
