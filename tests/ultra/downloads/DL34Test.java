package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL34 "Soft Red x Warm White x Charcoal": theme tokens in both themes, the FEmusic_ultra_home host contract, Home content. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL34Test {
 public static class LightHost extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(b);}}
 public static class DarkHost extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity c;
 @Before public void setup()throws Exception{
  Palette.force=null;c=Robolectric.buildActivity(DarkHost.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=c;
  Library.prefs(c).edit().clear().commit();c.getSharedPreferences("ultra_library_pins",Context.MODE_PRIVATE).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(c.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;
  PlaylistIndex.invalidateCache();NativeUi.reset();NativeUi.forceFallback=false;Art.network=false;NativeIntegration.homePivotSeen=false;
 }
 @After public void after(){Palette.force=null;NativeIntegration.homePivotSeen=false;}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));}
 static View tagged(View v,Object tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View f=tagged(g.getChildAt(n),tag);if(f!=null)return f;}}return null;}
 static int fill(Drawable d){if(d instanceof RippleDrawable)d=((RippleDrawable)d).getDrawable(0);return ((GradientDrawable)d).getColor().getDefaultColor();}
 Library.Item done(String id,String title,String artist,long created)throws Exception{Library.Item i=Library.add(c,id,title);i.artist=artist;i.state="done";i.mime="audio/mp4";i.created=created;i.total=1000;i.bytes=1000;return i;}
 static HomeTile song(String id,String artist){return new HomeTile(id,"Song "+id,artist,"song",false,0);}

 // ------------------------------------------------------------------ tokens
 @Test public void tokensMatchTheSpecInBothThemes(){
  Palette d=Palette.DARK,l=Palette.LIGHT;
  assertEquals(0xff121010,d.page);assertEquals(0xff1c1818,d.card);assertEquals(0xff100d0d,d.elevated);assertEquals(0xffed7978,d.soft);assertEquals(0xfff6f6f6,d.text);assertEquals(0xffa9a4a4,d.muted);
  assertEquals(0xff191718,d.chipOff);assertEquals(0xffa9a4a4,d.chipOffInk);assertEquals(0xfff6f6f6,d.chipOnInk);assertEquals(0xff441f20,d.heroStart);assertEquals(0xff252020,d.heroEnd);assertEquals(0xff2a1f20,d.mini);assertEquals(0xffe5484d,d.fab);
  assertEquals(0xfffaf6f7,l.page);assertEquals(0xffffffff,l.card);assertEquals(0xffffffff,l.elevated);assertEquals(0xfff0575c,l.soft);assertEquals(0xff0a0808,l.text);assertEquals(0xff6b6262,l.muted);
  assertEquals(0xffffffff,l.chipOff);assertEquals(0xff6b6262,l.chipOffInk);assertEquals(0xff7a2426,l.chipOnInk);assertEquals(0xfff7c0c0,l.heroStart);assertEquals(0xfffbebeb,l.heroEnd);assertEquals(0xfffcf8f9,l.mini);assertEquals(0xffe34b51,l.fab);
  for(Palette p:new Palette[]{d,l}){assertEquals("one muted coral accent",0xffe5484d,p.accent);assertEquals(0xffe5484d,p.progress);}
  assertEquals("coral @30% over #121010",Color.rgb(Math.round(0xe5*.3f+0x12*.7f),Math.round(0x48*.3f+0x10*.7f),Math.round(0x4d*.3f+0x10*.7f)),d.chipOn);
  assertEquals("coral @25% over #FAF6F7",Color.rgb(Math.round(0xe5*.25f+0xfa*.75f),Math.round(0x48*.25f+0xf6*.75f),Math.round(0x4d*.25f+0xf7*.75f)),l.chipOn);
 }
 @Test public void themeFollowsTheHostActivityTheme(){
  assertTrue(Palette.dark(c));assertSame(Palette.DARK,Palette.of(c));
  Activity light=Robolectric.buildActivity(LightHost.class).setup().get();assertFalse(Palette.dark(light));assertSame(Palette.LIGHT,Palette.of(light));
  assertEquals(0xfffaf6f7,NativeUi.background(light));assertEquals(0xff0a0808,NativeUi.primary(light));
 }

 // ------------------------------------------------------------------ host contract
 @Test public void fromTabSignatureIsTheDocumentedSmaliContract()throws Exception{
  Method m=NativeIntegration.class.getMethod("fromTab",Object.class,String.class);
  assertTrue(Modifier.isStatic(m.getModifiers())&&Modifier.isPublic(m.getModifiers()));assertEquals(boolean.class,m.getReturnType());
  assertEquals("FEmusic_ultra_home",NativeIntegration.HOME_PIVOT);assertEquals("FEmusic_ultra_downloads",NativeIntegration.DOWNLOADS_PIVOT);
  assertNotNull("DL33 host call kept",NativeIntegration.class.getMethod("fromTab",Object.class));
  Method b=Class.forName("UltraNavigationBridge").getMethod("hasPivot",Object.class,String.class);assertFalse((Boolean)b.invoke(null,new Object(),"FEmusic_ultra_home"));
 }
 @Test public void homePivotOpensHomeAsATabAndOtherPivotsAreLeftToTheHost()throws Exception{
  FrameLayout container=new FrameLayout(c);container.setId(0x7f0b04e1);c.setContentView(container);NativeIntegration.attach(c);
  try{
   Object controller=new Object();
   assertFalse("host tabs stay host tabs",NativeIntegration.fromTab(controller,"FEmusic_explore"));idle();assertFalse(NativeIntegration.homeVisible());
   assertTrue(NativeIntegration.fromTab(controller,NativeIntegration.HOME_PIVOT));idle();
   assertTrue(NativeIntegration.homeIsTab());assertNull("no Downloads under the Home tab",NativeIntegration.panel);assertTrue(NativeIntegration.downloadsVisible());assertTrue(NativeIntegration.hostHasHomePivot());
   assertNull("tab Home has no back-to-Downloads button",tagged(container,"Back to Downloads"));
   Bundle saved=new Bundle();NativeIntegration.save(saved);assertTrue("Home tab survives recreation",saved.getBoolean("ultra_home_tab"));
   assertTrue(NativeIntegration.fromTab(controller,NativeIntegration.DOWNLOADS_PIVOT));idle();
   assertFalse("Downloads pivot replaces Home",NativeIntegration.homeVisible());assertNotNull(NativeIntegration.panel);
   assertEquals("Downloads hides its Home entry once the bar has Home",View.GONE,tagged(container,"action:Home").getVisibility());
   NativeIntegration.fromTab(controller,NativeIntegration.HOME_PIVOT);idle();assertTrue(NativeIntegration.homeIsTab());
   assertTrue("Back is consumed",NativeIntegration.back());idle();assertFalse("Back from the Home tab leaves it, like Downloads",NativeIntegration.homeVisible());
   NativeIntegration.fromTab(controller,NativeIntegration.HOME_PIVOT);idle();NativeIntegration.leave();idle();assertFalse("any other host tab closes Home",NativeIntegration.homeVisible());assertEquals(0,container.getChildCount());
  }finally{NativeIntegration.leave();NativeIntegration.destroyed(c);}
 }
 @Test public void restoredStateReopensTheHomeTab()throws Exception{
  Bundle state=new Bundle();state.putBoolean("ultra_home_tab",true);NativeIntegration.restore(state);
  FrameLayout container=new FrameLayout(c);container.setId(0x7f0b04e1);c.setContentView(container);
  try{NativeIntegration.attach(c);idle();assertTrue(NativeIntegration.homeIsTab());}finally{NativeIntegration.leave();NativeIntegration.destroyed(c);}
 }

 // ------------------------------------------------------------------ Home content
 @Test public void quickPicksSkipRecentsAndRotateArtists(){
  List<HomeTile> songs=Arrays.asList(song("a1","A"),song("b1","B"),song("a2","A"),song("a3","A"),song("c1","C"),song("b2","B"));
  List<String> ids=new ArrayList<>();for(HomeTile t:HomeLogic.quickPicks(songs,null,10))ids.add(t.id);
  assertEquals("most-downloaded artist first, then round-robin",Arrays.asList("a1","b1","c1","a2","b2","a3"),ids);
  ids.clear();for(HomeTile t:HomeLogic.quickPicks(songs,Arrays.asList("a1"),10))ids.add(t.id);
  assertEquals("recents excluded; A and B tie at 2, B's newest song is newer so B leads",Arrays.asList("b1","a2","c1","b2","a3"),ids);
  assertEquals(2,HomeLogic.quickPicks(songs,null,2).size());assertTrue(HomeLogic.quickPicks(null,null,5).isEmpty());assertTrue(HomeLogic.quickPicks(songs,null,0).isEmpty());
  List<HomeTile> mixed=Arrays.asList(song("s1","A"),new HomeTile("PL1","List","Playlist","playlist",false,0));assertEquals(1,HomeLogic.quickPicks(mixed,null,5).size());
 }
 @Test public void homeShowsRealContentInTheLightTheme()throws Exception{
  Activity light=Robolectric.buildActivity(LightHost.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=light;
  done("aaaaaaaaaaa","Traitor","Olivia",300);done("bbbbbbbbbbb","Love Me Not","Ravyn",200);done("ccccccccccc","Vampire","Olivia",100);Library.save(light);
  Library.prefs(light).edit().putString("radio_last_played","ccccccccccc").commit();
  PlaylistIndex.roster(light,Arrays.asList(new Catalog.Playlist("VLPLzz","Road",""),new Catalog.Playlist("VLPLaa","Calm","")));
  FrameLayout mount=new FrameLayout(light);light.setContentView(mount);HomeScreen home=new HomeScreen(light,mount);
  for(int n=0;n<100&&tagged(mount,"home_hero")==null;n++){Thread.sleep(10);idle();}
  Palette p=Palette.LIGHT;
  assertEquals(p.page,((ColorDrawable)tagged(mount,"home_root").getBackground()).getColor());
  TextView title=(TextView)tagged(mount,"home_title");assertEquals("Home",title.getText().toString());assertEquals(p.text,title.getCurrentTextColor());
  View all=tagged(mount,"chip:All");assertEquals(p.chipOn,fill(all.getBackground()));
  View hero=tagged(mount,"home_hero");assertTrue("last played song is the hero",((ViewGroup)hero).getChildAt(1) instanceof LinearLayout&&contains(hero,"Vampire"));
  assertNotNull(tagged(mount,"home_hero_play"));assertEquals(Palette.CORAL,fill(tagged(mount,"home_hero_play").getBackground()));
  for(String id:new String[]{"aaaaaaaaaaa","bbbbbbbbbbb","ccccccccccc"})assertNotNull(id,tagged(mount,"home_tile:song:"+id));
  View road=tagged(mount,"home_card:playlist:PLzz");assertNotNull(road);assertEquals(p.card,fill(road.getBackground()));assertTrue("light cards are lifted",road.getElevation()>0);
  assertTrue(contains(mount,"Playlist • "));assertFalse("no placeholder text",contains(mount,"Lorem"));
  tagged(mount,"chip:Artists").performClick();idle();assertNotNull(tagged(mount,"home_card:artist:Olivia"));assertNull(tagged(mount,"home_hero"));
  Bitmap b=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888);mount.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));mount.layout(0,0,400,800);mount.draw(new Canvas(b));
  home.destroy();
 }
 @Test public void downloadsUsesTheSpecSurfacesInBothThemes()throws Exception{
  for(Class<? extends Activity> k:Arrays.<Class<? extends Activity>>asList(DarkHost.class,LightHost.class)){
   Activity a=Robolectric.buildActivity(k).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;
   Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();
   done("aaaaaaaaaaa","Traitor","Olivia",100);Library.save(a);Palette p=Palette.of(a);
   FrameLayout mount=new FrameLayout(a);a.setContentView(mount);DownloadsScreen s=new DownloadsScreen(a,mount,new Intent());settle();
   LinearLayout root=(LinearLayout)mount.getChildAt(0);assertEquals(p.page,((ColorDrawable)root.getBackground()).getColor());
   assertNotNull("brand line",tagged(mount,"brand"));TextView title=(TextView)tagged(mount,"header_title");assertEquals("Downloads",title.getText().toString());assertEquals(p.text,title.getCurrentTextColor());
   View songs=tagged(mount,"chip:Songs");assertEquals(p.chipOn,fill(songs.getBackground()));
   View row=tagged(mount,"song:aaaaaaaaaaa");assertEquals(p.card,fill(row.getBackground()));assertEquals(!p.dark,row.getElevation()>0);
   assertTrue("hero gradient",tagged(mount,"hero").getBackground() instanceof NativeUi.Hero);assertEquals(Palette.CORAL,fill(tagged(mount,"hero_play").getBackground()));
   tagged(mount,"chip:Playlists").performClick();settle();View fab=tagged(mount,"action:New local playlist");assertNotNull("+ New FAB",fab);assertEquals(View.VISIBLE,fab.getVisibility());assertEquals(p.fab,fill(fab.getBackground()));
   tagged(mount,"chip:Songs").performClick();settle();assertEquals("FAB only on Playlists",View.GONE,tagged(mount,"action:New local playlist").getVisibility());
   s.destroy();
  }
 }
 @Test public void heroDrawsTheSubtleLeftToRightGradient(){
  for(Palette p:new Palette[]{Palette.DARK,Palette.LIGHT}){
   Palette.force=p.dark;Drawable hero=NativeUi.hero(c);Bitmap b=Bitmap.createBitmap(300,100,Bitmap.Config.ARGB_8888);hero.setBounds(0,0,300,100);hero.draw(new Canvas(b));
   int left=b.getPixel(20,50),right=b.getPixel(280,20);
   assertTrue("starts near heroStart",near(left,p.heroStart,24));assertTrue("ends near heroEnd",near(right,p.heroEnd,24));
  }
 }
 static boolean near(int a,int b,int tol){return Math.abs(Color.red(a)-Color.red(b))<=tol&&Math.abs(Color.green(a)-Color.green(b))<=tol&&Math.abs(Color.blue(a)-Color.blue(b))<=tol;}
 static boolean contains(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().contains(s))return true;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(contains(((ViewGroup)v).getChildAt(n),s))return true;return false;}
}
