package ultra.downloads;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL50: Home populates on a cold start under repeated host opens; a tab switch cancels queued opens; page attribution. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class DL50Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;
  PageSkin.restoreAll();PageSkin.testIds.clear();PageSkin.ids=null;PageSkin.history.clear();PageSkin.scan=null;
 }
 @After public void after(){NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 /** Advances the clock too: renders are debounced (DL52), a second render can be up to 500 ms away. */
 void waitFor(HomeScreen h){for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){try{Thread.sleep(10);}catch(InterruptedException e){}run(50);}}
 void library(int songs,int playlists)throws Exception{
  for(int n=0;n<songs;n++){Library.Item i=Library.add(a,String.format("song%07d",n).substring(0,11),"Song "+n);i.artist="Artist "+(n%5);i.state="done";i.mime="audio/mp4";i.created=1000+n;}Library.save(a);
  List<Catalog.Playlist> rows=new ArrayList<>();for(int n=0;n<playlists;n++)rows.add(new Catalog.Playlist("VLPL"+n,"Playlist "+n,""));PlaylistIndex.roster(a,rows);
  JSONObject copies=new JSONObject();for(int n=0;n<playlists;n++){JSONArray t=new JSONArray();for(int k=0;k<40;k++)t.put(new JSONObject().put("id","x"+k).put("title","T"+k).put("artist","A"));copies.put("PL"+n,new JSONObject().put("name","Playlist "+n).put("tracks",t).put("complete",true));}
  Library.prefs(a).edit().putString(LocalPlaylistCopies.PREF,copies.toString()).commit();PlaylistIndex.invalidateCache();
 }

 @Test public void coldStartWithRepeatedHostOpensEndsPopulatedNotEmpty()throws Exception{
  library(30,250);
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  // host enforcer: fromTab(FEmusic_ultra_home) every ~700 ms for 5.5 s, plus the 0.8 s / 2.5 s self-checks
  for(int tick=0;tick<8;tick++){NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);run(700);}
  HomeScreen h=NativeIntegration.home;assertNotNull(h);waitFor(h);run(6000);
  assertNull("never the empty-state page with 30 songs and 250 playlists",container.findViewWithTag("home_empty"));
  assertNotNull("Recent downloads shown",container.findViewWithTag("home_section:Recent downloads"));
  assertNotNull("Library playlists shown",container.findViewWithTag("home_section:Your playlists"));
  assertNotNull(container.findViewWithTag("home_card:playlist:PL0"));assertTrue(container.findViewWithTag("home_card:playlist:PL0").toString()!=null);
  assertEquals("one Home mount",1,DL49Test.mounts(container));
 }
 @Test public void playlistCountsComeFromOneReadAndMatchTheStoredTracks()throws Exception{
  library(0,120);long t0=System.nanoTime();List<HomeTile> lists=HomeSource.playlists(a);long ms=(System.nanoTime()-t0)/1_000_000;
  assertEquals(120,lists.size());assertEquals("40 songs",lists.get(0).subtitle);assertTrue("one JSON read per load, not one per playlist ("+ms+" ms)",ms<3000);
 }
 @Test public void aTabSwitchCancelsAHomeOpenQueuedBeforeIt()throws Exception{
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);// queued, not yet run
  NativeIntegration.leave();// the user tapped Library before the queued open ran
  idle();run(3000);
  assertNull("no Home after the user left",NativeIntegration.home);assertEquals(0,DL49Test.mounts(container));
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();assertNotNull("a later Home select still opens it",NativeIntegration.home);
 }
 @Test public void emptyStateOnlyWhenAFinishedLoadHasNothing()throws Exception{
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);
  assertNotNull(mount.findViewWithTag("home_loading"));h.refresh();h.refresh();assertTrue("refresh during a load is coalesced",h.isLoading());
  waitFor(h);assertNotNull("finished, nothing downloaded: empty state",mount.findViewWithTag("home_empty"));assertFalse(h.isLoading());h.destroy();
 }

 // ------------------------------------------------------------------ page attribution
 int id(String name){Integer v=PageSkin.testIds.get(name);if(v==null){v=View.generateViewId();PageSkin.testIds.put(name,v);}return v;}
 @Test public void exploreCarouselTilesCountForExploreNotSearch(){
  LinearLayout window=new LinearLayout(a);FrameLayout c=new FrameLayout(a);c.setId(id("fragment_container"));window.addView(c,new LinearLayout.LayoutParams(-1,600));
  LinearLayout browse=new LinearLayout(a);browse.setId(id("browse_content"));browse.setOrientation(LinearLayout.VERTICAL);c.addView(browse);
  ultra.test.RecyclerView feed=new ultra.test.RecyclerView(a);feed.setId(id("section_list_content"));browse.addView(feed,new LinearLayout.LayoutParams(-1,-2));
  for(int n=0;n<4;n++){LinearLayout tile=new LinearLayout(a);tile.setId(id("two_row_item"));ImageView th=new ImageView(a);th.setId(id("thumbnail"));tile.addView(th,new LinearLayout.LayoutParams(120,120));feed.addView(tile,new LinearLayout.LayoutParams(150,190));}
  a.setContentView(window);View d=a.getWindow().getDecorView();d.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));d.layout(0,0,400,800);
  PageSkin.apply(d);
  String line=PageSkin.summary(PageSkin.ids);assertTrue(line,line.startsWith("library=0,search=0,explore=4,shell=0,"));assertTrue(line,line.endsWith(",page=explore"));
  assertTrue(PageSkin.report(),PageSkin.report().contains("page explore"));assertTrue(PageSkin.report(),PageSkin.report().contains("search.results=4"));
 }
}
