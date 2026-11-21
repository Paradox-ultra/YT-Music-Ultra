package ultra.downloads;
import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import android.app.*;import android.view.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class RadioGateTest {
 @Test public void obfuscatedRecyclerAdapterBindingsExist()throws Exception{Class<?> rv=Class.forName("android.support.v7.widget.RecyclerView"),adapter=Class.forName("th");assertEquals(adapter,rv.getField("n").getType());assertEquals(void.class,adapter.getMethod("eb").getReturnType());}
 @Test public void gateIsClearedOnPause(){RadioContext.libraryVisible=true;NativePages.stop();assertFalse(RadioContext.libraryVisible);}
 @Test public void noSeedDoesNotInventOne(){Activity a=Robolectric.buildActivity(Activity.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().remove("radio_last_played").commit();RadioContext.seed="";try{String id=Hooks.currentId();if(id!=null&&!id.isEmpty())return;}catch(Throwable ignored){}assertEquals("",RadioContext.seed());}
}
