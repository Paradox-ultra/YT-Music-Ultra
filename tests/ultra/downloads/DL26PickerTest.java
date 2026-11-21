package ultra.downloads;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowAlertDialog;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class DL26PickerTest {
 Context c;
 @Before public void setup()throws Exception{c=Robolectric.buildActivity(Activity.class).setup().get();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;Art.network=false;Library.prefs(c).edit().clear().commit();PlaylistIndex.invalidateCache();Library.prefs(c).edit().putString("local_playlist_index",new JSONObject().put("PLtest",new JSONObject().put("name","Cloud mix")).toString()).putString("ultra_local_playlists",new JSONObject().put("Offline mix",new JSONArray()).toString()).commit();PlaylistIndex.invalidateCache();}
 Catalog.Track track(String id){return new Catalog.Track(id,"Song","Artist","Album");}
 Catalog.Track local()throws Exception{Library.Item i=new Library.Item();i.uri="content://media/external/audio/media/93826";i.id=LocalAudio.localId(i.uri);i.imported=true;i.state="done";Library.applyImports(c,Arrays.asList(i));return track(i.id);}
 int tagged(View v,String tag){int n=tag.equals(v.getTag())?1:0;if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)n+=tagged(((ViewGroup)v).getChildAt(k),tag);return n;}
 void check(List<Catalog.Track> tracks,int online)throws Exception{PlaylistPicker.show(c,tracks,()->{});AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());View root=d.getWindow().getDecorView();assertEquals(online,tagged(root,"account_destination"));assertEquals(2,tagged(root,"local_destination"));assertNotNull(d.getButton(-2));d.dismiss();}
 @Test public void deviceOnlyShowsOnlyOfflineDestinations()throws Exception{Catalog.Track t=local();assertFalse(PlaylistPicker.accountEligible(c,Arrays.asList(t)));check(Arrays.asList(t),0);}
 @Test public void mixedSelectionAlsoHidesAccountDestinations()throws Exception{check(Arrays.asList(track("abcdefghijk"),local()),0);}
 @Test public void verifiedVideoAlsoUsesLocalCopies()throws Exception{assertFalse(PlaylistPicker.accountEligible(c,Arrays.asList(track("abcdefghijk"))));check(Arrays.asList(track("abcdefghijk")),0);}
 @Test public void missingIdentityAndEmptySelectionsNeverExposeOnline(){assertFalse(PlaylistPicker.accountEligible(c,Collections.emptyList()));assertFalse(PlaylistPicker.accountEligible(c,Arrays.asList(track("local_unknown"))));}
 @Test public void localRowCarriesCountAndLargeTouchTarget(){View row=PlaylistPicker.destination(c,"Offline mix","12 songs • Offline playlist",false);assertEquals("local_destination",row.getTag());assertTrue(row.getMinimumHeight()>=NativeUi.dp(c,72));}
}
