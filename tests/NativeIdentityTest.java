import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import com.google.protos.youtube.api.innertube.WatchEndpointOuterClass;
import org.junit.Test;
import com.google.protos.youtube.api.innertube.OfflineWatchEndpointOuterClass;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=28)
public class NativeIdentityTest {
 @Test public void nativeSideloadedUsesExactMediaUri(){bjue e=otv.a("content://media/external/audio/media/123","ytm://music.youtube.com/sideloaded/play?list=PLSL",0,false);String id=ultra.downloads.LocalAudio.nativeUriId("content://media/external/audio/media/123");assertNotNull(id);assertEquals(id,UltraNativeIdentity.id(row(e)));assertEquals(id,UltraNativeIdentity.id(new bads(null,row(e),null,null)));assertNull(UltraNativeIdentity.id(row(otv.a("content://other/audio/media/123","",0,false))));}
 bjue browse(String id){bizw e=(bizw)bizw.a.createBuilder().instance;e.c=id;bhbf b=(bhbf)bjue.a.createBuilder();b.e(BrowseEndpointOuterClass.browseEndpoint,e);return (bjue)b.build();}
 bjue watch(String id){bwmb e=(bwmb)bwmb.a.createBuilder().instance;e.d=id;bhbf b=(bhbf)bjue.a.createBuilder();b.e(WatchEndpointOuterClass.watchEndpoint,e);return (bjue)b.build();}
 bjue offline(String id){brka e=(brka)brka.a.createBuilder().instance;e.c=id;bhbf b=(bhbf)bjue.a.createBuilder();b.e(OfflineWatchEndpointOuterClass.offlineWatchEndpoint,e);return (bjue)b.build();}
 @Test public void offlineWatchUsesExactSongIdNotPlaylistToken(){assertEquals("trackAAAAA1",UltraNativeIdentity.id(row(offline("trackAAAAA1"))));assertNull(UltraNativeIdentity.id(row(offline("PPAD"))));}
 bqtu row(bjue e){bqtu r=(bqtu)bqtu.a.createBuilder().instance;r.i=e;return r;}
 @Test public void libraryArtistEndpointSharesCanonicalArtistIdentity(){assertEquals("UCartist",UltraNativeIdentity.id(row(browse("MPLAUCartist"))));assertEquals("UCartist",ultra.downloads.LibraryPinOrder.key("MPLAUCartist"));assertNull(UltraNativeIdentity.id(row(browse("MPLAinvalid"))));}
 @Test public void exactPlaylistAlbumArtist(){for(String id:new String[]{"VLPLnative","MPREalbum","UCartist","VLLM"}){assertEquals(id,UltraNativeIdentity.id(row(browse(id))));}}
 @Test public void exactWatchIdAndWrappedModel(){bqtu r=row(watch("trackAAAAA1"));assertEquals("trackAAAAA1",UltraNativeIdentity.id(r));assertEquals("trackAAAAA1",UltraNativeIdentity.id(new oez(r,null)));}
 @Test public void fallbackEndpointAndUnknown(){bqtu r=row(browse("FEnotselectable"));r.k=watch("trackBBBBB2");assertEquals("trackBBBBB2",UltraNativeIdentity.id(r));assertNull(UltraNativeIdentity.id(row(watch("bad"))));assertNull(UltraNativeIdentity.id(null));assertNull(UltraNativeIdentity.id(new Object()));}
}
