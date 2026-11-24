package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.drawable.*;import android.os.Looper;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL33: Home screen pure logic (greeting, chips, section fallback order), Home rendering, and the Downloads restyle. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class DL33Test {
 Activity c;
 @Before public void setup()throws Exception{
  c=Robolectric.buildActivity(Activity.class).setup().get();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;
  Library.prefs(c).edit().clear().commit();c.getSharedPreferences("ultra_library_pins",Context.MODE_PRIVATE).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(c.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;
  PlaylistIndex.invalidateCache();NativeUi.reset();NativeUi.forceFallback=false;Art.network=false;
 }
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 static HomeTile song(String id,long t){return new HomeTile(id,"Song "+id,"Artist","song",false,t);}
 static HomeTile list(String id,boolean liked){return new HomeTile(id,"List "+id,"Playlist","playlist",liked,0);}
 static HomeTile artist(String id){return new HomeTile(id,id,"1 song","artist",false,0);}
 static List<String> keys(List<HomeTile> l){List<String> o=new ArrayList<>();for(HomeTile t:l)o.add(t.key());return o;}
 static View tagged(View v,Object tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View f=tagged(g.getChildAt(n),tag);if(f!=null)return f;}}return null;}
 static boolean contains(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().contains(s))return true;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(contains(((ViewGroup)v).getChildAt(n),s))return true;return false;}
 static int fill(Drawable d){return ((GradientDrawable)d).getColor().getDefaultColor();}
 Library.Item done(String id,String title,String artist,long created)throws Exception{Library.Item i=Library.add(c,id,title);i.artist=artist;i.state="done";i.mime="audio/mp4";i.created=created;i.total=1000;i.bytes=1000;return i;}

 // ------------------------------------------------------------------ pure logic
 @Test public void greetingFollowsDeviceHour(){
  assertEquals("Good evening",HomeLogic.greeting(0));assertEquals("Good evening",HomeLogic.greeting(4));
  assertEquals("Good morning",HomeLogic.greeting(5));assertEquals("Good morning",HomeLogic.greeting(11));
  assertEquals("Good afternoon",HomeLogic.greeting(12));assertEquals("Good afternoon",HomeLogic.greeting(16));
  assertEquals("Good evening",HomeLogic.greeting(17));assertEquals("Good evening",HomeLogic.greeting(23));
  assertEquals("out of range hours wrap",HomeLogic.greeting(9),HomeLogic.greeting(33));assertEquals("Good evening",HomeLogic.greeting(-1));
 }
 @Test public void chipsAreTheFiveFiltersInOrderAndFilterByKind(){
  assertArrayEquals(new String[]{"All","Playlists","Downloads","Artists","Liked"},HomeLogic.CHIPS);
  List<HomeTile> mixed=Arrays.asList(song("s1",2),list("PL1",false),artist("Ana"),list("LM",true),song("s2",1));
  assertEquals(5,HomeLogic.filter(mixed,"All").size());
  assertEquals(Arrays.asList("playlist:PL1","playlist:LM"),keys(HomeLogic.filter(mixed,"Playlists")));
  assertEquals(Arrays.asList("song:s1","song:s2"),keys(HomeLogic.filter(mixed,"Downloads")));
  assertEquals(Arrays.asList("artist:Ana"),keys(HomeLogic.filter(mixed,"Artists")));
  assertEquals(Arrays.asList("playlist:LM"),keys(HomeLogic.filter(mixed,"Liked")));
  assertEquals("unknown chip means All",5,HomeLogic.filter(mixed,"Podcasts").size());assertEquals("All",HomeLogic.chip(null));
  assertTrue(HomeLogic.filter(null,"All").isEmpty());
 }
 @Test public void pickUpUsesLastPlayedThenDownloadsThenPinsThenLibraryWithoutDuplicates(){
  HomeTile last=song("s3",1);
  List<HomeTile> recent=Arrays.asList(song("s1",3),song("s2",2),song("s3",1));
  List<HomeTile> pins=Arrays.asList(list("LM",true),list("PL2",false));
  List<HomeTile> lib=Arrays.asList(list("PL1",false),list("LM",true),list("PL2",false),list("PL3",false));
  assertEquals(Arrays.asList("song:s3","song:s1","song:s2","playlist:LM","playlist:PL2","playlist:PL1","playlist:PL3"),keys(HomeLogic.pickUp(last,recent,pins,lib,10)));
  assertEquals("limit holds",3,HomeLogic.pickUp(last,recent,pins,lib,3).size());
 }
 @Test public void pickUpFallsBackWhenSourcesAreEmptyAndSongsLeaveRoomForPlaylists(){
  List<HomeTile> lib=Arrays.asList(list("PL1",false),list("PL2",false));
  assertEquals("no songs, no pins: Library playlists",Arrays.asList("playlist:PL1","playlist:PL2"),keys(HomeLogic.pickUp(null,Collections.<HomeTile>emptyList(),null,lib,10)));
  assertEquals("pins only",Arrays.asList("playlist:PL2"),keys(HomeLogic.pickUp(null,null,Arrays.asList(list("PL2",false)),null,10)));
  assertTrue("everything empty",HomeLogic.pickUp(null,null,null,null,10).isEmpty());
  List<HomeTile> many=new ArrayList<>();for(int n=0;n<20;n++)many.add(song("s"+n,n));
  List<HomeTile> out=HomeLogic.pickUp(null,many,null,lib,10);int songs=0;for(HomeTile t:out)if(t.song())songs++;
  assertEquals(HomeLogic.SONG_SHARE,songs);assertEquals(Arrays.asList("playlist:PL1","playlist:PL2"),keys(HomeLogic.filter(out,"Playlists")));
 }
 @Test public void gridKeepsLibraryOrderAndFollowsTheChip(){
  List<HomeTile> lib=Arrays.asList(list("PLz",false),list("LM",true),list("PLa",false));
  List<HomeTile> songs=Arrays.asList(song("s1",2));List<HomeTile> artists=Arrays.asList(artist("Ana"));
  assertEquals("Your playlists",HomeLogic.gridTitle("All"));assertEquals(Arrays.asList("playlist:PLz","playlist:LM","playlist:PLa"),keys(HomeLogic.grid("All",lib,songs,artists)));
  assertEquals(keys(HomeLogic.grid("All",lib,songs,artists)),keys(HomeLogic.grid("Playlists",lib,songs,artists)));
  assertEquals(Arrays.asList("song:s1"),keys(HomeLogic.grid("Downloads",lib,songs,artists)));
  assertEquals(Arrays.asList("artist:Ana"),keys(HomeLogic.grid("Artists",lib,songs,artists)));
  assertEquals(Arrays.asList("playlist:LM"),keys(HomeLogic.grid("Liked",lib,songs,artists)));
  assertTrue(HomeLogic.empty(null,Collections.<HomeTile>emptyList()));assertFalse(HomeLogic.empty(songs,null));
 }

 // ------------------------------------------------------------------ data bridge
 @Test public void sourcesReadOnlyHeldDataInLibraryOrder()throws Exception{
  done("aaaaaaaaaaa","Old","Ana",100);done("bbbbbbbbbbb","New","",300);Library.Item hidden=done("ccccccccccc","Hidden","Ana",200);hidden.hidden=true;Library.save(c);
  Library.prefs(c).edit().putString("radio_last_played","aaaaaaaaaaa").commit();
  PlaylistIndex.roster(c,Arrays.asList(new Catalog.Playlist("VLPLzz","Road",""),new Catalog.Playlist("VLLM","Liked music",""),new Catalog.Playlist("VLPLaa","Calm","")));
  List<HomeTile> recent=HomeSource.recentSongs(c);assertEquals(Arrays.asList("song:bbbbbbbbbbb","song:aaaaaaaaaaa"),keys(recent));assertEquals("Unknown artist",recent.get(0).subtitle);
  assertEquals("song:aaaaaaaaaaa",HomeSource.lastPlayed(c).key());
  List<HomeTile> lists=HomeSource.playlists(c);List<String> names=new ArrayList<>();for(HomeTile t:lists)names.add(t.title);
  List<String> expected=new ArrayList<>();for(Map.Entry<String,String> e:PlaylistIndex.groups(c).entrySet())if(!e.getKey().startsWith("local:")&&!UnmatchedDownloads.is(e.getKey()))expected.add(e.getValue());
  assertEquals("same names and order as Library playlists in Downloads",expected,names);assertTrue(names.contains("Road")&&names.contains("Calm"));
  boolean liked=false;for(HomeTile t:lists)if(t.liked)liked=true;assertTrue("Liked music flagged",liked);
  List<HomeTile> pins=HomeSource.pinned(c,lists);assertFalse("LM is a default pin",pins.isEmpty());assertTrue(pins.get(0).liked);
  assertEquals(Arrays.asList("artist:Unknown Artist","artist:Ana"),keys(HomeSource.artists(recent)));
 }
 @Test public void playlistLinkStaysInsideThisAppAndRejectsBadIds(){
  Intent i=HomeSource.playlistIntent(c,"PLabc_-1");assertEquals(c.getPackageName(),i.getPackage());assertEquals(Intent.ACTION_VIEW,i.getAction());assertEquals("https://music.youtube.com/playlist?list=PLabc_-1",i.getDataString());
  assertNull(HomeSource.playlistIntent(c,"local:Gym"));assertNull(HomeSource.playlistIntent(c,"bad id"));assertNull(HomeSource.playlistIntent(c,null));
 }

 // ------------------------------------------------------------------ rendering
 // DL34 replaced the DL33 Spotify-style look: these UI checks now assert the DL34 tokens (Palette) and structure.
 static int cardFill(View v){Drawable d=v.getBackground();if(d instanceof RippleDrawable)d=((RippleDrawable)d).getDrawable(0);return fill(d);}
 @Test public void homeRendersGreetingChipsPickUpAndGridOnBlack()throws Exception{
  done("aaaaaaaaaaa","Traitor","Olivia",100);Library.save(c);
  PlaylistIndex.roster(c,Arrays.asList(new Catalog.Playlist("VLPLzz","Road",""),new Catalog.Playlist("VLPLaa","Calm",""),new Catalog.Playlist("VLPLbb","Gym","")));
  FrameLayout mount=new FrameLayout(c);c.setContentView(mount);HomeScreen home=new HomeScreen(c,mount);
  for(int n=0;n<100&&tagged(mount,"home_hero")==null;n++){Thread.sleep(10);idle();}
  Palette p=Palette.of(c);assertTrue("dark host theme",p.dark);
  TextView greet=(TextView)tagged(mount,"home_greeting");assertTrue(greet.getText().toString().startsWith("Good "));assertNotNull(tagged(mount,"brand"));
  for(String chip:HomeLogic.CHIPS)assertNotNull(chip,tagged(mount,"chip:"+chip));
  View all=tagged(mount,"chip:All");assertTrue(all.isSelected());assertEquals(p.chipOn,fill(all.getBackground()));assertEquals(p.chipOff,fill(tagged(mount,"chip:Liked").getBackground()));
  assertNotNull(tagged(mount,"home_section:Recent downloads"));assertNotNull(tagged(mount,"home_section:Your playlists"));
  assertNotNull(tagged(mount,"home_tile:song:aaaaaaaaaaa"));for(String id:new String[]{"PLzz","PLaa","PLbb"})assertNotNull(id,tagged(mount,"home_card:playlist:"+id));
  View card=tagged(mount,"home_card:playlist:PLaa");assertEquals(p.card,cardFill(card));assertEquals(NativeUi.dp(c,20),((GradientDrawable)((RippleDrawable)card.getBackground()).getDrawable(0)).getCornerRadius(),0.5f);
  assertEquals(p.page,((android.graphics.drawable.ColorDrawable)tagged(mount,"home_root").getBackground()).getColor());
  tagged(mount,"chip:Downloads").performClick();idle();assertNotNull(tagged(mount,"home_hero"));assertNull(tagged(mount,"home_card:playlist:PLzz"));assertNotNull(tagged(mount,"home_tile:song:aaaaaaaaaaa"));
  home.destroy();
 }
 @Test public void emptyHomeShowsOneLineAndAnOutlinedLibraryButton()throws Exception{
  FrameLayout mount=new FrameLayout(c);c.setContentView(mount);HomeScreen home=new HomeScreen(c,mount);
  for(int n=0;n<100&&tagged(mount,"home_empty")==null;n++){Thread.sleep(10);idle();}
  View empty=tagged(mount,"home_empty");assertNotNull(empty);assertTrue(contains(empty,"will show up here"));
  TextView button=(TextView)tagged(mount,"home_open_library");assertEquals("Open Library",button.getText().toString());assertEquals(0,fill(button.getBackground()));assertEquals(Palette.CORAL,button.getCurrentTextColor());
  home.destroy();
 }
 @Test public void downloadsKeepsBehaviourWithNewSurfacesAndHomeEntry()throws Exception{
  done("aaaaaaaaaaa","Traitor","Olivia",100);Library.save(c);
  FrameLayout mount=new FrameLayout(c);c.setContentView(mount);DownloadsScreen s=new DownloadsScreen(c,mount,new Intent());Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
  Palette p=Palette.of(c);
  View home=tagged(mount,"action:Home");assertNotNull("Home entry while the host bar has no Home pivot",home);assertEquals("Open Home",home.getContentDescription().toString());
  View songs=tagged(mount,"chip:Songs");assertTrue(songs.isSelected());assertEquals(p.chipOn,fill(songs.getBackground()));assertEquals(p.chipOff,fill(tagged(mount,"chip:Artists").getBackground()));
  assertEquals(p.chipOn,fill(tagged(mount,"action:Play").getBackground()));assertEquals(p.chipOff,fill(tagged(mount,"action:Shuffle").getBackground()));
  assertEquals(p.card,cardFill(tagged(mount,"song:aaaaaaaaaaa")));assertNotNull(tagged(mount,"hero"));assertEquals("Recently downloaded",((TextView)tagged(mount,"sort_label")).getText().toString());
  tagged(mount,"chip:Artists").performClick();idle();assertTrue("chip behaviour unchanged",tagged(mount,"chip:Artists").isSelected());
  s.show("Artists","Olivia");idle();assertTrue(tagged(mount,"chip:Artists").isSelected());assertNotNull("Home hand-off opens the artist group",tagged(mount,"chip:Olivia"));
  s.show("Podcasts","x");idle();assertTrue("unknown tab ignored",tagged(mount,"chip:Artists").isSelected());
  s.destroy();
 }
 @Test public void animationsFollowTheAnimatorDurationSetting()throws Exception{
  Method scale=android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);scale.setAccessible(true);
  try{scale.invoke(null,0f);assertFalse(NativeUi.animate());View v=new View(c);NativeUi.enter(v);assertEquals("no fade when animations are off",1f,v.getAlpha(),0f);
   scale.invoke(null,1f);assertTrue(NativeUi.animate());}
  finally{scale.invoke(null,1f);}
 }

 @Test public void homeLayerMountsAboveDownloadsAndBackReturnsToDownloads()throws Exception{
  FrameLayout container=new FrameLayout(c);container.setId(0x7f0b04e1);c.setContentView(container);
  NativeIntegration.attach(c);
  try{
   NativeIntegration.open();idle();assertNotNull(NativeIntegration.panel);assertFalse(NativeIntegration.homeVisible());
   NativeIntegration.openHome();idle();assertTrue(NativeIntegration.homeVisible());assertTrue(NativeIntegration.downloadsVisible());
   assertEquals("Home sits above Downloads in fragment_container","ultra_home_panel",container.getChildAt(container.getChildCount()-1).getTag());
   assertTrue("back is consumed by Home",NativeIntegration.back());idle();assertFalse(NativeIntegration.homeVisible());assertNotNull("Downloads still open",NativeIntegration.panel);
   assertNull(tagged(container,"ultra_home_panel"));
   NativeIntegration.openHome();idle();NativeIntegration.leave();idle();
   assertFalse("a host tab change closes Home",NativeIntegration.homeVisible());assertNull(NativeIntegration.panel);assertEquals(0,container.getChildCount());
  }finally{NativeIntegration.leave();NativeIntegration.destroyed(c);}
 }
}
