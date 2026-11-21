package ultra.downloads;
import android.app.Activity;import android.content.*;import android.os.*;import android.widget.*;import java.lang.reflect.*;import java.util.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class DL26DefaultTabTest {
 Context c;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;Art.network=false;Library.prefs(c).edit().clear().commit();PlaylistIndex.invalidateCache();}
 String tab(DownloadsScreen s)throws Exception{Field f=DownloadsScreen.class.getDeclaredField("tab");f.setAccessible(true);return (String)f.get(s);}
 DownloadsScreen open(Bundle saved){Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.setTheme(android.R.style.Theme_Material_NoActionBar);FrameLayout mount=new FrameLayout(a);a.setContentView(mount);return new DownloadsScreen(a,mount,new Intent(),saved);}
 @Test public void unpinnedDefaultRemainsSongs(){assertEquals("Songs",DownloadTabs.initial(c));}
 @Test public void pinnedPlaylistsIsDefault(){DownloadTabs.pin(c,"Playlists",true);assertEquals("Playlists",DownloadTabs.initial(c));assertEquals("Playlists",DownloadTabs.visible(c).get(0));}
 @Test public void mostRecentlyPinnedVisibleTabWins(){DownloadTabs.pin(c,"Playlists",true);DownloadTabs.pin(c,"Albums",true);assertEquals("Albums",DownloadTabs.initial(c));DownloadTabs.pin(c,"Playlists",true);assertEquals("Playlists",DownloadTabs.initial(c));}
 @Test public void hiddenPinsNeverBecomeDefault(){DownloadTabs.pin(c,"Playlists",true);DownloadTabs.hide(c,"Playlists");assertEquals("Songs",DownloadTabs.initial(c));}
 @Test public void hidingSongsUsesFirstVisibleTab(){DownloadTabs.hide(c,"Songs");assertEquals("Artists",DownloadTabs.initial(c));}
 @Test public void unpinAndRestoreDefaultsAreRespected(){DownloadTabs.pin(c,"Playlists",true);DownloadTabs.pin(c,"Playlists",false);assertEquals("Songs",DownloadTabs.initial(c));DownloadTabs.pin(c,"Albums",true);DownloadTabs.restore(c);assertEquals("Songs",DownloadTabs.initial(c));}
 @Test public void explicitChoiceSurvivesRecreation(){DownloadTabs.pin(c,"Playlists",true);assertEquals("Songs",DownloadTabs.restored(c,"Songs"));assertEquals("Artists",DownloadTabs.restored(c,"Artists"));}
 @Test public void missingOrHiddenSavedTabFallsBack(){DownloadTabs.pin(c,"Playlists",true);DownloadTabs.hide(c,"Albums");assertEquals("Playlists",DownloadTabs.restored(c,null));assertEquals("Playlists",DownloadTabs.restored(c,"Albums"));assertEquals("Playlists",DownloadTabs.restored(c,"invalid"));}
 @Test public void freshScreenSelectsPinnedPlaylistsAndReentryResetsManualChoice()throws Exception{DownloadTabs.pin(c,"Playlists",true);DownloadsScreen first=open(null);assertEquals("Playlists",tab(first));Field f=DownloadsScreen.class.getDeclaredField("tab");f.setAccessible(true);f.set(first,"Songs");assertEquals("Songs",tab(first));first.destroy();DownloadsScreen next=open(null);assertEquals("Playlists",tab(next));next.destroy();}
 @Test public void recreatedScreenKeepsExplicitSongsChoice()throws Exception{DownloadTabs.pin(c,"Playlists",true);Bundle state=new Bundle();state.putString("tab","Songs");DownloadsScreen s=open(state);assertEquals("Songs",tab(s));s.destroy();}
}
