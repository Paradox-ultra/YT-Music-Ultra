package ultra.downloads;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;import java.lang.reflect.*;import java.util.*;import android.net.Uri;import android.content.Context;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class NativePlaybackTest {
 @Test public void nativeSideloadedEndpointHasSelectedUriAndQueue()throws Exception{
  String selected="content://media/external/audio/media/42",group="ytm://music.youtube.com/sideloaded/play?list=PLSL&ultra_queue=test";
  Object endpoint=Class.forName("otv").getMethod("a",String.class,String.class,int.class,boolean.class).invoke(null,selected,group,2,false);
  String id=(String)Class.forName("kud").getMethod("f",Class.forName("bjue")).invoke(null,endpoint);assertNotNull(id);assertFalse(id.isEmpty());
  Object decoded=Class.forName("kud").getMethod("d",String.class).invoke(null,id);Object recovered=decoded.getClass().getField("e").get(decoded);assertEquals(endpoint,recovered);
  Object ext=Class.forName("buea").getField("b").get(null);Object map=Class.forName("bhbg").getDeclaredField("j").get(recovered);Object descriptor=ext.getClass().getField("d").get(ext);Object local=map.getClass().getMethod("l",Class.forName("bhbh")).invoke(map,descriptor);
  assertEquals(selected,local.getClass().getField("d").get(local));assertEquals(group,local.getClass().getField("e").get(local));assertEquals(2,local.getClass().getField("f").getInt(local));
 }
 @Test public void unrelatedDeviceFileEndpointIsUntouched()throws Exception{Object endpoint=NativeModels.fresh("buea");NativeModels.set(endpoint,"e","ytm://music.youtube.com/sideloaded/play?list=PLSL");assertNull(NativePlayback.nativeQueue(endpoint));}
 @Test public void notificationUsesHostOnly(){Context c=RuntimeEnvironment.getApplication();android.content.Intent i=NativeIntegration.notificationIntent(c);assertEquals("com.google.android.apps.youtube.music.activities.MusicActivity",i.getComponent().getClassName());assertEquals(0,i.getFlags()&android.content.Intent.FLAG_ACTIVITY_NEW_TASK);}
}
