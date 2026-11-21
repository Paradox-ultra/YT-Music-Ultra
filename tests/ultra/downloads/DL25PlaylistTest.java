package ultra.downloads;
import android.app.Activity;
import android.content.Context;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class DL25PlaylistTest {
 Context c;
 @Before public void setup(){c=Robolectric.buildActivity(Activity.class).setup().get();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;Library.prefs(c).edit().clear().commit();PlaylistIndex.invalidateCache();}
 Catalog.Track track(String id){return new Catalog.Track(id,"Title "+id,"Artist","Album");}
 @Test public void pickerListLeavesSpaceForControlsInLandscape(){assertEquals(128,PlaylistPicker.listHeightDp(360));assertEquals(280,PlaylistPicker.listHeightDp(800));assertTrue(PlaylistPicker.listHeightDp(480)<=160);}
 @Test public void existingPlaylistGetsSongsWithoutReplacingOrDuplicating()throws Exception{
  PlaylistPicker.add(c,"Existing",Arrays.asList(track("one")));assertEquals(1,PlaylistPicker.add(c,"Existing",Arrays.asList(track("one"),track("two"))));
  JSONArray a=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}")).getJSONArray("Existing");assertEquals(2,a.length());assertEquals("one",a.getJSONObject(0).getString("id"));assertEquals("Title two",a.getJSONObject(1).getString("title"));assertEquals(Arrays.asList("Existing"),PlaylistPicker.names(c));
 }
 @Test public void savesReadLatestDestinationAndDoNotLoseConcurrentEdits()throws Exception{PlaylistPicker.add(c,"A",Arrays.asList(track("one")));PlaylistPicker.add(c,"B",Arrays.asList(track("two")));PlaylistPicker.add(c,"A",Arrays.asList(track("three")));JSONObject map=new JSONObject(Library.prefs(c).getString("ultra_local_playlists","{}"));assertEquals(2,map.getJSONArray("A").length());assertEquals(1,map.getJSONArray("B").length());}
 @Test public void assignmentRemovesSongFromUnmatchedButKeepsOtherSong()throws Exception{Library.Item one=new Library.Item();one.id="one";one.state="done";Library.Item two=new Library.Item();two.id="two";two.state="done";PlaylistPicker.add(c,"Existing",Arrays.asList(track("one")));Set<String> matched=UnmatchedDownloads.matched(c);assertTrue(matched.contains("one"));assertEquals("two",UnmatchedDownloads.filter(Arrays.asList(one,two),matched).get(0).id);}
 @Test public void dedicatedArtworkAndPrimaryActionHaveAccessibleIdentity(){View art=SelectionStyle.unmatched(c);assertEquals("unmatched_artwork",art.getTag());assertTrue(art.getContentDescription().toString().contains("Unmatched"));final int[] hits={0};View action=SelectionStyle.action(c,"not_a_resource","playlist","Add to playlist",v->hits[0]++);assertEquals("Add to playlist",action.getContentDescription());action.performClick();assertEquals(1,hits[0]);assertTrue(action.getMinimumHeight()>=NativeUi.dp(c,48));}
 @Test public void localElevenCharacterHashIsNeverSentAsYouTubeVideoId()throws Exception{Library.Item i=new Library.Item();i.uri="content://media/external/audio/media/42";i.id=LocalAudio.localId(i.uri);i.imported=true;i.state="done";assertEquals(11,i.id.length());Library.applyImports(c,Arrays.asList(i));assertNotNull(Library.get(c,i.id));try{AccountPlaylists.videoIds(c,Arrays.asList(track(i.id)));fail("device hash accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("device-only"));}}
 @Test public void accountPayloadUsesVerifiedIdsAndSkipsDuplicates()throws Exception{JSONObject p=AccountPlaylists.payload("VLPLverified",Arrays.asList("abcdefghijk"));assertEquals("PLverified",p.getString("playlistId"));JSONObject a=p.getJSONArray("actions").getJSONObject(0);assertEquals("ACTION_ADD_VIDEO",a.getString("action"));assertEquals("abcdefghijk",a.getString("addedVideoId"));assertEquals("DEDUPE_OPTION_SKIP",a.getString("dedupeOption"));}
}
