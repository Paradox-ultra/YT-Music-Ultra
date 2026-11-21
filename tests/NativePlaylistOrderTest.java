import app.revanced.extension.shared.utils.Utils;import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import java.util.*;import android.app.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class NativePlaylistOrderTest {
 Activity c;
 @Before public void setup(){c=Robolectric.buildActivity(Activity.class).setup().get();Utils.testContext=c;c.getSharedPreferences("ultra_downloads_settings",0).edit().clear().commit();}
 bqtu row(String id,String title){bqtu row=new RadioRowTest().row(id);row.e=UltraRadioBridge.text(title);return row;}
 @Test public void nativeOrderIgnoresArtistsAndIsNotAlphabetical()throws Exception{List<Object> rows=Arrays.<Object>asList(row("VLPLz","Z list"),row("UCsinger","Artist"),row("VLLM","Liked music"),row("VLPLb","B list"),row("FEmusic_ultra_radio","Radio"));UltraPlaylistIndex.accept(rows);String order=c.getSharedPreferences("ultra_downloads_settings",0).getString("native_playlist_order","");assertEquals("[\"PLz\",\"LM\",\"PLb\"]",order);}
 @Test public void nestedMenuPlaylistDoesNotLeakIntoOrder(){bqtu row=row("VLPLz","Z list");row.k=row("VLPLmenu","Liked Songs").i;UltraPlaylistIndex.accept(Arrays.asList(row));assertEquals("[\"PLz\"]",c.getSharedPreferences("ultra_downloads_settings",0).getString("native_playlist_order",""));}
}
