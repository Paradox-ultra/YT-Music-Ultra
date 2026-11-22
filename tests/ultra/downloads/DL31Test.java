package ultra.downloads;
import app.revanced.extension.shared.utils.Utils;
import android.app.*;import android.view.*;import android.widget.*;import java.lang.ref.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class DL31Test {
 Activity host;int headerClicks,tabClicks;Button header,tab;
 @Before public void setup()throws Exception{
  host=Robolectric.buildActivity(Activity.class).setup().get();host.setTheme(android.R.style.Theme_Material_NoActionBar);Utils.testContext=host;
  FrameLayout root=new FrameLayout(host);
  header=new Button(host);header.setContentDescription("Library");header.setOnClickListener(v->headerClicks++);root.addView(header);
  tab=new Button(host);tab.setContentDescription("Library");tab.setOnClickListener(v->tabClicks++);root.addView(tab);
  host.setContentView(root);root.layout(0,0,400,800);header.layout(0,40,200,100);tab.layout(0,740,200,800);
 }
 @Test public void headerDropdownIsNeverTappedWhenReturningToLibrary(){
  // header added first and bottom tab last, then reverse the order: header must still be ignored
  ViewGroup root=(ViewGroup)header.getParent();root.removeView(tab);root.addView(tab,0);
  assertTrue(NativeIntegration.clickLibrary(root,800));
  assertEquals(0,headerClicks);assertEquals(1,tabClicks);
 }
 @Test public void withoutBottomTabNothingIsTapped(){
  ViewGroup root=(ViewGroup)header.getParent();root.removeView(tab);
  assertFalse(NativeIntegration.clickLibrary(root,800));
  assertEquals(0,headerClicks);
 }
 @Test public void alreadySelectedLibraryTabIsNotReselected(){
  tab.setSelected(true);
  assertTrue(NativeIntegration.clickLibrary((ViewGroup)header.getParent(),800));
  assertEquals(0,headerClicks);assertEquals(0,tabClicks);
 }
}
