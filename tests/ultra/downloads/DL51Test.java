package ultra.downloads;
import android.app.*;import android.content.*;import android.content.res.ColorStateList;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL51: Home sections never blank, covers applied across re-renders, per-section diagnostics, Home timeline, page styling. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL51Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();
  synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();}
  PageSkin.restoreAll();PageSkin.testIds.clear();PageSkin.ids=null;PageSkin.history.clear();PageSkin.scan=null;
 }
 @After public void after(){NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 void songs(int n)throws Exception{
  for(int k=0;k<n;k++){String id=String.format("cov%08d",k);Library.Item i=Library.add(a,id,"Song "+k);i.artist="Artist "+(k%3);i.state="done";i.mime="audio/mp4";i.created=1000+k;
   File f=Art.file(a,id);f.getParentFile().mkdirs();Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff336699);try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,90,o);}}
  Library.save(a);
 }
 static List<View> tagged(View v,String prefix,List<View> out){if(v.getTag() instanceof String&&((String)v.getTag()).startsWith(prefix))out.add(v);if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)tagged(((ViewGroup)v).getChildAt(k),prefix,out);return out;}
 static int withCover(View v){int n=0;if(v instanceof ImageView&&((ImageView)v).getDrawable() instanceof BitmapDrawable)n++;if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)n+=withCover(((ViewGroup)v).getChildAt(k));return n;}

 @Test public void recentAndQuickPicksAreVisibleWithCoversAfterBackToBackRenders()throws Exception{
  songs(14);FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);
  for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){Thread.sleep(10);idle();}
  h.refresh();h.refresh();// re-renders while covers decode
  for(int n=0;n<300&&(h.isLoading());n++){Thread.sleep(10);idle();}
  for(int n=0;n<100;n++){Thread.sleep(10);idle();}run(4500);
  List<View> tiles=tagged(mount,"home_tile:",new ArrayList<View>()),cards=tagged(mount,"home_card:",new ArrayList<View>());
  assertEquals("8 recent tiles",8,tiles.size());assertFalse("quick picks",cards.isEmpty());
  for(View v:tiles)assertEquals("tile visible",1f,v.getAlpha(),0f);for(View v:cards)assertEquals("card visible",1f,v.getAlpha(),0f);
  int covered=0;for(View v:tiles)covered+=withCover(v);assertEquals("every recent tile has its cover",8,covered);
  String d=h.diagnostics();assertTrue(d,d.contains("recent: items 8, covers 8/8"));assertTrue(d,d.contains("quick: items"));assertTrue(d,d.contains("render #"));
  h.destroy();
 }
 @Test public void timelineRecordsCallersCancelledOpensAndTheTabSwitch()throws Exception{
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);NativeIntegration.leave();idle();
  NativeIntegration.fromTab(new Object(),NativeIntegration.HOME_PIVOT);idle();assertNotNull(NativeIntegration.home);
  NativeIntegration.leave();run(6000);
  assertNull("nothing re-opens Home after the tab switch (self-checks, loading retries)",NativeIntegration.home);assertEquals(0,DL49Test.mounts(container));
  String r=NativeIntegration.homeReport();
  assertTrue(r,r.contains("fromTab FEmusic_ultra_home from "));assertTrue(r,r.contains("openHome cancelled: a host tab switch came first"));assertTrue(r,r.contains("openHome built a new Home (tab)"));
  assertTrue(r,r.contains("leave (host tab switch) from "));assertTrue(r,r.contains("Home closed"));assertTrue(r,r.matches("(?s).*\\n\\s+\\d+ ms  .*"));
  assertTrue("shown in Download settings > Diagnostics",Diagnostics.text(a).contains("Home timeline"));
 }

 // ------------------------------------------------------------------ page styling
 int id(String name){Integer v=PageSkin.testIds.get(name);if(v==null){v=View.generateViewId();PageSkin.testIds.put(name,v);}return v;}
 <T extends View> T tag(T v,String name){v.setId(id(name));return v;}
 @Test public void artistRowsRoundSuggestionsCardToolbarTabsAndMenus(){
  LinearLayout window=new LinearLayout(a);window.setOrientation(LinearLayout.VERTICAL);FrameLayout c=tag(new FrameLayout(a),"fragment_container");window.addView(c,new LinearLayout.LayoutParams(-1,700));
  LinearLayout page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);c.addView(page);
  LinearLayout toolbar=tag(new LinearLayout(a),"toolbar");TextView tt=new TextView(a);tt.setText("Explore");tt.setTextColor(0xffdddddd);toolbar.addView(tt);page.addView(toolbar,new LinearLayout.LayoutParams(-1,56));
  View tabs=tag(new LinearLayout(a),"tabbed_view");page.addView(tabs,new LinearLayout.LayoutParams(-1,40));
  View header=tag(new LinearLayout(a),"header_container");page.addView(header,new LinearLayout.LayoutParams(-1,40));
  ultra.test.RecyclerView list=tag(new ultra.test.RecyclerView(a),"section_list_content");list.setOrientation(LinearLayout.VERTICAL);page.addView(list,new LinearLayout.LayoutParams(-1,-2));
  FrameLayout swipe=new FrameLayout(a);LinearLayout parent=tag(new LinearLayout(a),"two_column_item_content_parent");swipe.addView(parent);ImageView thumb=tag(new ImageView(a),"thumbnail");parent.addView(thumb,new LinearLayout.LayoutParams(56,56));
  TextView sub=tag(new TextView(a),"subtitle");sub.setText("Artist • 40.2 lakh subscribers");parent.addView(sub);ImageView dots=tag(new ImageView(a),"contextual_menu_anchor");parent.addView(dots,new LinearLayout.LayoutParams(24,24));list.addView(swipe,new LinearLayout.LayoutParams(-1,72));
  a.setContentView(window);View d=a.getWindow().getDecorView();d.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));d.layout(0,0,400,800);
  Palette p=Palette.of(a);PageSkin.apply(d);
  assertEquals("artist art round","ultra-round",thumb.getTag(PageSkin.TAG_KEY));assertTrue(thumb.getClipToOutline());assertEquals(1,(int)PageSkin.report.get("library.artists"));
  assertEquals(ColorStateList.valueOf(p.text),dots.getImageTintList());assertEquals(1,(int)PageSkin.report.get("library.menus"));
  assertEquals(p.page,((ColorDrawable)toolbar.getBackground()).getColor());assertEquals(p.text,tt.getCurrentTextColor());assertEquals(p.page,((ColorDrawable)tabs.getBackground()).getColor());
  assertEquals(1,(int)PageSkin.report.get("page.toolbar"));assertEquals(1,(int)PageSkin.report.get("page.tabs"));
  sub.setText("Playlist • 72 tracks");swipe.setTag(PageSkin.TAG_KEY,null);PageSkin.apply(d);assertEquals("recycled into a playlist row: rounded square again","ultra",thumb.getTag(PageSkin.TAG_KEY));
  PageSkin.set(a,false);assertFalse("switch-off unclips",thumb.getClipToOutline());
 }
 @Test public void searchSuggestionRowsBecomeSoftCards(){
  LinearLayout window=new LinearLayout(a);FrameLayout c=tag(new FrameLayout(a),"fragment_container");window.addView(c,new LinearLayout.LayoutParams(-1,600));
  ultra.test.RecyclerView sl=tag(new ultra.test.RecyclerView(a),"suggestion_list");sl.setOrientation(LinearLayout.VERTICAL);c.addView(sl);
  LinearLayout row=tag(new LinearLayout(a),"edit_suggestion");TextView t=tag(new TextView(a),"text");t.setText("traitor");row.addView(t);sl.addView(row,new LinearLayout.LayoutParams(-1,48));
  a.setContentView(window);View d=a.getWindow().getDecorView();d.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));d.layout(0,0,400,800);
  PageSkin.apply(d);assertTrue(row.getBackground() instanceof InsetDrawable);assertTrue(PageSkin.summary(PageSkin.ids),PageSkin.summary(PageSkin.ids).contains("page=search"));
 }
}
