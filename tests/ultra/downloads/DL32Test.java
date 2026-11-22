package ultra.downloads;
import app.revanced.extension.shared.utils.Utils;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowLooper;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class DL32Test {
 @After public void tearDown(){Diagnostics.selectorTitle=null;}
 Activity host;
 static void set(String name,Object value)throws Exception{Field f=NativeIntegration.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 Dialog sheet(String title){Dialog d=new Dialog(host);TextView t=new TextView(host);t.setText(title);d.setContentView(t);return d;}
 @Before public void setup()throws Exception{
  host=Robolectric.buildActivity(Activity.class).setup().get();host.setTheme(android.R.style.Theme_Material_NoActionBar);Utils.testContext=host;
  set("sheetGuardUntil",0L);set("sheetGuardUsed",false);Diagnostics.selectorTitle="View my";
 }
 @Test public void selectorSheetRightAfterLeavingDownloadsIsDismissed(){
  NativeIntegration.armSheetGuard();
  Dialog d=sheet("View my");Diagnostics.watch(d);d.show();ShadowLooper.idleMainLooper();
  assertFalse(d.isShowing());
 }
 @Test public void manualSelectorSheetAfterGuardSpentStaysOpen(){
  NativeIntegration.armSheetGuard();
  Dialog first=sheet("View my");Diagnostics.watch(first);first.show();ShadowLooper.idleMainLooper();assertFalse(first.isShowing());
  Dialog second=sheet("View my");Diagnostics.watch(second);second.show();ShadowLooper.idleMainLooper();
  assertTrue(second.isShowing());
 }
 @Test public void selectorSheetWithoutRecentDownloadsExitStaysOpen(){
  Dialog d=sheet("View my");Diagnostics.watch(d);d.show();ShadowLooper.idleMainLooper();
  assertTrue(d.isShowing());
 }
 @Test public void otherDialogInWindowStaysOpenAndDoesNotSpendGuard(){
  NativeIntegration.armSheetGuard();
  Dialog other=sheet("Try Premium");Diagnostics.watch(other);other.show();ShadowLooper.idleMainLooper();
  assertTrue(other.isShowing());
  Dialog d=sheet("View my");Diagnostics.watch(d);d.show();ShadowLooper.idleMainLooper();
  assertFalse(d.isShowing());
 }
 @Test public void expiredGuardLeavesSheetOpen()throws Exception{
  NativeIntegration.armSheetGuard();set("sheetGuardUntil",1L); // far in the past on the uptime clock
  Dialog d=sheet("View my");Diagnostics.watch(d);d.show();ShadowLooper.idleMainLooper();
  assertTrue(d.isShowing());
 }
 @Test public void closingThePanelArmsTheGuard()throws Exception{
  set("sheetGuardUntil",0L);NativeIntegration.closePanel();
  assertTrue(NativeIntegration.sheetGuardActive());
 }
}
