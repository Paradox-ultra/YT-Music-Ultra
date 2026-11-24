package ultra.downloads;
import android.app.*;import android.content.*;import android.content.res.ColorStateList;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;

/** DL38: PageSkin matching on the device's resource ids (Library, Search, shell) and Explore's Litho structure. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class DL37Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 public static class FakeFloatingActionButton extends TextView{public FakeFloatingActionButton(Context c){super(c);}}
 public static class FakePivotBar extends LinearLayout{public FakePivotBar(Context c){super(c);}}
 public static class ComponentHost extends FrameLayout{public ComponentHost(Context c){super(c);}}
 /** Horizontal list, like a carousel RecyclerView. */
 public static class RecyclerView extends LinearLayout{public RecyclerView(Context c){super(c);}@Override public boolean canScrollHorizontally(int d){return getOrientation()==HORIZONTAL;}}
 Activity a;FrameLayout content;LinearLayout page,window;Palette p;
 @Before public void setup(){
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  NativeIntegration.panel=null;NativeIntegration.home=null;PageSkin.restoreAll();PageSkin.testIds.clear();PageSkin.ids=null;p=Palette.of(a);
  window=new LinearLayout(a);window.setOrientation(LinearLayout.VERTICAL);
  content=new FrameLayout(a);content.setId(id("fragment_container"));window.addView(content,new LinearLayout.LayoutParams(400,620));
  page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);content.addView(page,new FrameLayout.LayoutParams(-1,-1));
 }
 int id(String name){Integer v=PageSkin.testIds.get(name);if(v==null){v=View.generateViewId();PageSkin.testIds.put(name,v);}return v;}
 <T extends View> T tag(T v,String name){v.setId(id(name));return v;}
 TextView text(String s,int color){TextView t=new TextView(a);t.setText(s);t.setTextColor(color);return t;}
 void layout(){a.setContentView(window);View d=a.getWindow().getDecorView();d.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));d.layout(0,0,400,800);}
 void pass(){PageSkin.apply(a.getWindow().getDecorView());}
 static Drawable inner(View v){Drawable d=v.getBackground();return d instanceof InsetDrawable?((InsetDrawable)d).getDrawable():null;}
 static int fill(Drawable d){if(d instanceof RippleDrawable)d=((RippleDrawable)d).getDrawable(0);return ((GradientDrawable)d).getColor().getDefaultColor();}
 static int count(String key){Integer n=PageSkin.report.get(key);return n==null?0:n;}

 // ------------------------------------------------------------------ Library
 View libRow,radioRow,chipOn,chipOff,fab,header;ImageView libThumb,radioThumb;TextView libTitle,libSub,chipOnText,chipOffText,headerTitle;
 View libraryRow(LinearLayout list,String title,String sub){
  FrameLayout swipe=tag(new FrameLayout(a),"swipe_layout");LinearLayout parent=tag(new LinearLayout(a),"two_column_item_content_parent");swipe.addView(parent,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout thumbParent=tag(new FrameLayout(a),"thumbnail_parent");ImageView thumb=tag(new ImageView(a),"thumbnail");thumbParent.addView(thumb,new FrameLayout.LayoutParams(56,56));parent.addView(thumbParent,new LinearLayout.LayoutParams(56,56));
  LinearLayout details=tag(new LinearLayout(a),"details_container");details.setOrientation(LinearLayout.VERTICAL);TextView t=tag(text(title,0xffffffff),"title");TextView s=tag(text(sub,0xffaaaaaa),"subtitle");details.addView(t);details.addView(s);parent.addView(details);
  list.addView(swipe,new LinearLayout.LayoutParams(-1,72));libThumb=thumb;libTitle=t;libSub=s;return swipe;
 }
 void library(){
  header=tag(new LinearLayout(a),"header_container");headerTitle=text("Library",0xffffffff);((LinearLayout)header).addView(headerTitle);page.addView(header,new LinearLayout.LayoutParams(-1,56));
  LinearLayout cloud=tag(new LinearLayout(a),"chip_cloud");page.addView(cloud,new LinearLayout.LayoutParams(-1,44));
  chipOn=chip(cloud,"Playlists",0xff030303);chipOnText=(TextView)((ViewGroup)chipOn).getChildAt(0);chipOff=chip(cloud,"Podcasts",0xffffffff);chipOffText=(TextView)((ViewGroup)chipOff).getChildAt(0);
  LinearLayout sort=tag(new LinearLayout(a),"music_sort_filter_button_container");sort.addView(text("Recently saved",0xffdddddd));page.addView(sort,new LinearLayout.LayoutParams(-1,40));
  ultra.test.RecyclerView list=tag(new ultra.test.RecyclerView(a),"section_list_content");list.setOrientation(LinearLayout.VERTICAL);page.addView(list,new LinearLayout.LayoutParams(-1,-2));
  radioRow=libraryRow(list,"Radio","Based on your last played song");radioThumb=libThumb;
  libRow=libraryRow(list,"Sing with me","Playlist • Paradox ultra • 72 tracks");
  fab=tag(new FakeFloatingActionButton(a),"floating_action_button");((TextView)fab).setText("New");page.addView(fab,new LinearLayout.LayoutParams(120,48));
 }
 View chip(LinearLayout parent,String label,int ink){FrameLayout c=tag(new FrameLayout(a),"chip_contents");c.setClickable(true);TextView t=tag(text(label,ink),"chip_cloud_chip_text");c.addView(t);parent.addView(c,new LinearLayout.LayoutParams(-2,36));return c;}

 @Test public void libraryRowsChipsHeaderSortAndFabMatchByDeviceIds(){
  library();shell();layout();pass();
  assertTrue("row card on the list child",inner(libRow) instanceof RippleDrawable);assertEquals(p.card,fill(inner(libRow)));assertTrue("thumbnail rounded",libThumb.getClipToOutline());
  assertEquals(p.text,libTitle.getCurrentTextColor());assertEquals(p.muted,libSub.getCurrentTextColor());
  assertTrue("Radio row featured",inner(radioRow) instanceof NativeUi.Hero);assertNotNull("coral Radio tile",radioThumb.getColorFilter());
  assertEquals(p.chipOn,fill(chipOn.getBackground()));assertEquals(p.chipOnInk,chipOnText.getCurrentTextColor());assertEquals(p.chipOff,fill(chipOff.getBackground()));assertEquals(p.chipOffInk,chipOffText.getCurrentTextColor());
  assertEquals(p.page,((ColorDrawable)header.getBackground()).getColor());assertEquals(p.text,headerTitle.getCurrentTextColor());
  assertEquals(ColorStateList.valueOf(p.fab),fab.getBackgroundTintList());
  assertEquals(2,count("library.rows"));assertEquals(2,count("library.chips"));assertEquals(1,count("library.radio"));assertEquals(1,count("library.fab"));assertEquals(1,count("library.header"));assertEquals(1,count("library.sort"));
  assertEquals("library = sum of library elements",count("library.rows")+count("library.chips")+count("library.radio")+count("library.fab")+count("library.header")+count("library.sort"),count("library"));
  String line=PageSkin.summary(PageSkin.ids);assertTrue(line,line.startsWith("library=8,search=0,explore=0,shell="));assertTrue(line,line.matches("library=\\d+,search=\\d+,explore=\\d+,shell=\\d+,ids=\\d+/\\d+,page=library"));
  assertTrue("summary passes the Diagnostics filter",line.matches("[a-z0-9_.:=,/-]{1,80}"));
  assertEquals("tab bar untouched",0xff101010,((ColorDrawable)pivot.getBackground()).getColor());assertEquals(0xffffffff,pivotText.getCurrentTextColor());
  pass();assertEquals("idempotent: same counts on a second pass",2,count("library.rows"));assertTrue(inner(libRow) instanceof RippleDrawable);
 }
 @Test public void recycledRadioRowAndSwitchOffRestoreTheStockLook(){
  library();layout();pass();
  libSub.setText("x");((TextView)((ViewGroup)((ViewGroup)((ViewGroup)radioRow).getChildAt(0)).getChildAt(1)).getChildAt(1)).setText("Playlist • 12 tracks");radioRow.setTag(PageSkin.TAG_KEY,null);pass();
  assertFalse(inner(radioRow) instanceof NativeUi.Hero);assertNull("art untinted after recycling",radioThumb.getColorFilter());
  PageSkin.set(a,false);
  assertNull("row background back to stock",libRow.getBackground());assertEquals(0xff030303,chipOnText.getCurrentTextColor());assertEquals(0xffaaaaaa,((TextView)((ViewGroup)((ViewGroup)((ViewGroup)libRow).getChildAt(0)).getChildAt(1)).getChildAt(1)).getCurrentTextColor());
  pass();assertNull("off stays off",libRow.getBackground());
 }

 // ------------------------------------------------------------------ Search
 @Test public void searchSuggestionsResultsDividerAndFieldMatch(){
  EditText query=new EditText(a);page.addView(query,new LinearLayout.LayoutParams(-1,48));
  ultra.test.RecyclerView suggestions=tag(new ultra.test.RecyclerView(a),"suggestion_list");suggestions.setOrientation(LinearLayout.VERTICAL);page.addView(suggestions,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout sug=tag(new LinearLayout(a),"edit_suggestion");ImageView icon=tag(new ImageView(a),"search_type_icon");sug.addView(icon,new LinearLayout.LayoutParams(24,24));TextView sugText=tag(text("traitor",0xffcccccc),"text");sug.addView(sugText);suggestions.addView(sug,new LinearLayout.LayoutParams(-1,48));
  View divider=tag(new View(a),"shelf_divider");page.addView(divider,new LinearLayout.LayoutParams(-1,1));
  LinearLayout result=tag(new LinearLayout(a),"two_row_item");result.setOrientation(LinearLayout.VERTICAL);FrameLayout tc=tag(new FrameLayout(a),"thumbnail_container");ImageView thumb=tag(new ImageView(a),"thumbnail");tc.addView(thumb,new FrameLayout.LayoutParams(120,120));result.addView(tc);
  LinearLayout tl=tag(new LinearLayout(a),"title_layout");TextView title=tag(text("Love Me Not",0xffffffff),"title");tl.addView(title);result.addView(tl);TextView sub=tag(text("Ravyn Lenae",0xffaaaaaa),"text1");result.addView(sub);page.addView(result,new LinearLayout.LayoutParams(160,190));
  layout();pass();
  assertEquals(1,count("search.suggestions"));assertEquals(p.text,sugText.getCurrentTextColor());assertEquals(ColorStateList.valueOf(p.muted),icon.getImageTintList());
  assertTrue("result card",inner(result) instanceof RippleDrawable);assertTrue(thumb.getClipToOutline());assertEquals(p.muted,sub.getCurrentTextColor());assertEquals(p.text,title.getCurrentTextColor());
  assertEquals(p.hairline,((ColorDrawable)divider.getBackground()).getColor());assertTrue("query pill",query.getBackground() instanceof GradientDrawable);
  assertEquals(1,count("search.results"));assertEquals(1,count("search.dividers"));assertEquals(1,count("search.field"));assertEquals(4,count("search"));
 }

 // ------------------------------------------------------------------ Explore (Litho structure)
 @Test public void exploreGridCarouselAndChartRowsMatchByStructure(){
  LinearLayout browse=tag(new LinearLayout(a),"browse_content");page.addView(browse,new LinearLayout.LayoutParams(-1,-1));
  ultra.test.RecyclerView feed=new ultra.test.RecyclerView(a);feed.setOrientation(LinearLayout.VERTICAL);browse.addView(feed,new LinearLayout.LayoutParams(-1,-2));
  // (1) 2x2 category grid: one full-width host holding two half-width cards per line
  ComponentHost gridRow=new ComponentHost(a);LinearLayout line=new LinearLayout(a);gridRow.addView(line,new FrameLayout.LayoutParams(-1,-1));
  ComponentHost cat1=new ComponentHost(a),cat2=new ComponentHost(a);for(ComponentHost c:new ComponentHost[]{cat1,cat2}){ImageView ic=new ImageView(a);c.addView(ic,new FrameLayout.LayoutParams(24,24));c.addView(text("New releases",0xffffffff),new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM));line.addView(c,new LinearLayout.LayoutParams(190,80));}
  feed.addView(gridRow,new LinearLayout.LayoutParams(-1,90));
  // (2) section header + carousel of album tiles
  ComponentHost headerRow=new ComponentHost(a);TextView sectionTitle=text("New albums and singles",0xffdddddd);headerRow.addView(sectionTitle);feed.addView(headerRow,new LinearLayout.LayoutParams(-1,48));
  RecyclerView carousel=new RecyclerView(a);carousel.setOrientation(LinearLayout.HORIZONTAL);ComponentHost tile=null;
  for(int n=0;n<3;n++){tile=new ComponentHost(a);LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.addView(new ImageView(a),new LinearLayout.LayoutParams(140,140));col.addView(text("Album",0xffffffff));col.addView(text("Artist",0xffaaaaaa));tile.addView(col);carousel.addView(tile,new LinearLayout.LayoutParams(150,190));}
  feed.addView(carousel,new LinearLayout.LayoutParams(-1,200));
  // (3) chart row: rank, thumbnail, two lines, action menu
  ComponentHost chart=new ComponentHost(a);LinearLayout cl=new LinearLayout(a);cl.addView(text("1",0xffffffff));ImageView art=new ImageView(a);cl.addView(art,new LinearLayout.LayoutParams(56,56));LinearLayout lines=new LinearLayout(a);lines.setOrientation(LinearLayout.VERTICAL);lines.addView(text("Song",0xffffffff));lines.addView(text("Artist",0xffaaaaaa));cl.addView(lines);chart.addView(cl);
  feed.addView(chart,new LinearLayout.LayoutParams(-1,72));
  ComponentHost opaque=new ComponentHost(a);feed.addView(opaque,new LinearLayout.LayoutParams(-1,120));
  layout();pass();
  assertTrue("category card",inner(cat1) instanceof RippleDrawable);assertTrue(inner(cat2) instanceof RippleDrawable);assertEquals(2,count("explore.grid"));
  assertTrue("carousel tile card",inner(tile) instanceof RippleDrawable);assertEquals(3,count("explore.tiles"));
  assertTrue("chart row card",inner(chart) instanceof RippleDrawable);assertTrue(art.getClipToOutline());assertEquals(1,count("explore.rows"));
  assertNull("header is not a card",headerRow.getBackground());assertEquals(p.text,sectionTitle.getCurrentTextColor());assertEquals(1,count("explore.headers"));
  assertEquals("Litho host with nothing inside is reported, not styled",1,count("explore.opaque"));assertNull(opaque.getBackground());
  assertEquals(2+3+1+1+1,count("explore"));
 }

 // ------------------------------------------------------------------ shell
 View mini,play,pivot;TextView pivotText,miniTitle;ProgressBar progress;ImageView next;
 void shell(){
  mini=tag(new LinearLayout(a),"mini_player");LinearLayout meta=tag(new LinearLayout(a),"mini_player_metadata");miniTitle=tag(text("Alone, Pt. II",0xffffffff),"mini_player_title");meta.addView(miniTitle);meta.addView(tag(text("Alan Walker",0xffaaaaaa),"mini_player_subtitle"));((LinearLayout)mini).addView(meta);
  play=tag(new ImageView(a),"mini_player_play_pause_replay_button");((LinearLayout)mini).addView(play,new LinearLayout.LayoutParams(48,48));next=tag(new ImageView(a),"mini_player_next_button");((LinearLayout)mini).addView(next,new LinearLayout.LayoutParams(40,40));
  progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);((LinearLayout)mini).addView(progress,new LinearLayout.LayoutParams(200,4));window.addView(mini,new LinearLayout.LayoutParams(-1,64));
  pivot=tag(new FakePivotBar(a),"pivot_bar");pivotText=text("Library",0xffffffff);((ViewGroup)pivot).addView(pivotText);pivot.setBackgroundColor(0xff101010);window.addView(pivot,new LinearLayout.LayoutParams(-1,56));
 }
 @Test public void miniPlayerMatchesByIdsAndTheTabBarIsNeverTouched(){
  shell();layout();pass();
  assertTrue(mini.getBackground() instanceof InsetDrawable);assertEquals(p.mini,fill(inner(mini)));assertEquals(Palette.CORAL,((GradientDrawable)play.getBackground()).getColor().getDefaultColor());assertEquals(ColorStateList.valueOf(Color.WHITE),((ImageView)play).getImageTintList());
  assertEquals(ColorStateList.valueOf(p.text),next.getImageTintList());assertEquals(ColorStateList.valueOf(p.progress),progress.getProgressTintList());assertEquals(p.text,miniTitle.getCurrentTextColor());
  assertEquals(5,count("shell"));assertEquals(0xff101010,((ColorDrawable)pivot.getBackground()).getColor());assertEquals(0xffffffff,pivotText.getCurrentTextColor());
 }
 @Test public void everyPassReportsAllPagesEvenWhenNothingMatched(){
  layout();pass();
  String line=PageSkin.summary(PageSkin.ids);assertTrue(line,line.matches("library=0,search=0,explore=0,shell=0,ids=\\d+/\\d+,page=other"));
  assertTrue(PageSkin.report(),PageSkin.report().contains("ids not in this host"));
  Diagnostics.event("page_skin",line);assertTrue(Diagnostics.text(a).contains("page_skin#"));assertTrue(Diagnostics.text(a).contains(line));
 }
 @Test public void moduleScreensOnTopAreSkipped(){
  library();layout();NativeIntegration.home=null;FrameLayout mount=new FrameLayout(a);mount.setTag("ultra_home_panel");content.addView(mount);pass();assertNull("module mount never restyled",mount.getBackground());
 }
 @Test public void idsResolveByNameInTheHostPackage(){
  assertEquals("app.rvx.android.apps.youtube.musiy",PageSkin.HOST_PACKAGE);
  PageSkin.testIds.clear();PageSkin.ids=null;assertEquals("unknown in this test host: resolves to 0, never a guessed number",0,PageSkin.resolve(a,"two_column_item_content_parent"));
 }
}
