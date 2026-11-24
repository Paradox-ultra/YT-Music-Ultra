import app.revanced.extension.shared.utils.Utils;
import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import java.util.*;

/** DL34 bottom-bar order built by UltraPivots.order from host brso rows: Home, Explore, Search, Library, Downloads. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class DL34PivotOrderTest {
 @Before public void setup(){Utils.testContext=Robolectric.buildActivity(android.app.Activity.class).setup().get();}
 static brso row(String id){brso r=(brso)brso.a.createBuilder().instance;r.b|=1;r.e=id;return r;}
 static List<String> ids(List<?> rows){List<String> out=new ArrayList<>();for(Object o:rows)out.add(((brso)o).e);return out;}
 @Test public void newOrderIsHomeExploreSearchLibraryDownloads(){
  List<Object> host=new ArrayList<>(Arrays.<Object>asList(row("FEmusic_library_landing"),row("FEmusic_home"),row("FEmusic_explore"),row("FEmusic_ultra_downloads")));
  List<?> out=UltraPivots.order(host,row("FEmusic_ultra_home"),row("FEmusic_ultra_downloads"));
  assertEquals(Arrays.asList("FEmusic_ultra_home","FEmusic_explore","FEmusic_home","FEmusic_library_landing","FEmusic_ultra_downloads"),ids(out));
 }
 @Test public void idempotentKeepsUnknownHostTabsAndToleratesMissingOnes(){
  List<Object> host=new ArrayList<>(Arrays.<Object>asList(row("FEmusic_library_landing"),row("FEmusic_podcasts"),row("FEmusic_explore")));
  List<?> once=UltraPivots.order(host,row("FEmusic_ultra_home"),row("FEmusic_ultra_downloads"));
  List<?> twice=UltraPivots.order(new ArrayList<Object>(once),row("FEmusic_ultra_home"),row("FEmusic_ultra_downloads"));
  assertEquals(Arrays.asList("FEmusic_ultra_home","FEmusic_explore","FEmusic_library_landing","FEmusic_ultra_downloads","FEmusic_podcasts"),ids(twice));
  assertNull(UltraPivots.order(null,row("FEmusic_ultra_home"),null));
  assertEquals("no Home row supplied: order without Home",Arrays.asList("FEmusic_explore","FEmusic_library_landing","FEmusic_podcasts"),ids(UltraPivots.order(host,null,null)));
 }
}
