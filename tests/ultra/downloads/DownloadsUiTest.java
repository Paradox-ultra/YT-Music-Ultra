package ultra.downloads;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=28,qualifiers="w480dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class DownloadsUiTest {
    public static class TestHost extends Activity{public void onCreate(android.os.Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
    /** Host shaped like Music: content container with the mini-player and bottom navigation drawn OVER its bottom. */
    public static class OverlayHost extends Activity{public FrameLayout content;public View player,nav;
        public void onCreate(android.os.Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);FrameLayout root=new FrameLayout(this);content=new FrameLayout(this);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
            player=new View(this);player.setBackgroundColor(0xff212121);root.addView(player,new FrameLayout.LayoutParams(-1,64,Gravity.BOTTOM));((FrameLayout.LayoutParams)player.getLayoutParams()).bottomMargin=56;
            nav=new View(this);nav.setBackgroundColor(0xff111111);root.addView(nav,new FrameLayout.LayoutParams(-1,56,Gravity.BOTTOM));setContentView(root);}}
    Context context;
    @Before public void before()throws Exception{
        Art.network=false;Art.memory.evictAll();synchronized(Art.failures){Art.failures.clear();}NativeUi.reset();NativeUi.forceFallback=false;context=RuntimeEnvironment.getApplication();context.setTheme(android.R.style.Theme_Material_NoActionBar);
        app.revanced.extension.shared.utils.Utils.testContext=context;
        org.robolectric.Shadows.shadowOf((Application)context).grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.READ_EXTERNAL_STORAGE);
        Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(context.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;
    }
    @After public void after(){NativeUi.forceFallback=false;}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    void image(Activity activity,String name)throws Exception{
        View v=activity.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,480,800);Bitmap bitmap=Bitmap.createBitmap(480,800,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));
        File directory=new File("/downloads");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,"DL10-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}assertEquals(480,bitmap.getWidth());
    }
    static boolean contains(View v,String value){if(v instanceof TextView && ((TextView)v).getText().toString().contains(value))return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)if(contains(g.getChildAt(n),value))return true;}return false;}
    static View tagged(View v,Object tag){if(tag.equals(v.getTag())&&v.getVisibility()==View.VISIBLE&&shown(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View f=tagged(g.getChildAt(n),tag);if(f!=null)return f;}}return null;}
    static boolean shown(View v){for(View p=v;p!=null;p=p.getParent() instanceof View?(View)p.getParent():null)if(p.getVisibility()!=View.VISIBLE)return false;return true;}
    static List<View> all(View v,List<View> out){out.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)all(g.getChildAt(i),out);}return out;}
    /** No version marker (DL7, DL8, DL9...) and no Unicode stand-in glyphs anywhere in normal UI. */
    static void assertNativeText(View root){for(View v:all(root,new ArrayList<View>())){CharSequence t=v instanceof TextView?((TextView)v).getText():null,d=v.getContentDescription();for(CharSequence s:new CharSequence[]{t,d}){if(s==null)continue;String x=s.toString();assertFalse(x,x.matches("(?s).*\\bDL\\d+\\b.*"));for(String glyph:new String[]{"⚙","⌕","▶","⤨","⇅","♫","▣","↓","Ⅱ","⋮"})assertFalse(x,x.contains(glyph));}}}
    DownloadsScreen open(Activity a){FrameLayout mount=new FrameLayout(a);a.setContentView(mount);DownloadsScreen s=new DownloadsScreen(a,mount,new Intent());idle();return s;}
    void call(DownloadsScreen s,String name)throws Exception{Method m=DownloadsScreen.class.getDeclaredMethod(name,boolean.class);m.setAccessible(true);m.invoke(s,true);}
    void set(DownloadsScreen s,String name,Object value)throws Exception{Field f=DownloadsScreen.class.getDeclaredField(name);f.setAccessible(true);f.set(s,value);}
    Library.Item done(String id,String title,String artist,String album)throws Exception{Library.Item i=Library.add(context,id,title);i.artist=artist;i.album=album;i.state="done";i.mime="audio/mp4";i.total=5242880;i.bytes=i.total;return i;}

    @Test public void emptyScreenRenders()throws Exception{
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            assertTrue(contains(decor,"Music for anywhere"));assertTrue(contains(decor,"Downloads"));assertFalse(contains(decor,"Back to music"));
            assertNotNull("native empty state",tagged(decor,"empty"));assertNull("no chips without music",tagged(decor,"chip:Songs"));assertNull("no Play without playable music",tagged(decor,"action:Play"));assertNull(tagged(decor,"action:Shuffle"));
            TextView title=(TextView)tagged(decor,"header_title");assertEquals("Downloads",title.getText().toString());
            assertNativeText(decor);image(a,"empty");screen.destroy();
        }
    }
    @Test public void populatedSongsHaveNativeRowsChipsAndPlayShuffle()throws Exception{
        done("abcdefghijk","A song with a long title that must ellipsize without hiding the actions","An Artist","An Album");done("bcdefghijkl","Second song","Other Artist","");Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            for(String chip:new String[]{"Songs","Artists","Albums"})assertNotNull(chip,tagged(decor,"chip:"+chip));assertTrue(tagged(decor,"chip:Songs").isSelected());assertFalse(tagged(decor,"chip:Artists").isSelected());
            assertNotNull(tagged(decor,"action:Play"));assertNotNull(tagged(decor,"action:Shuffle"));
            View row=tagged(decor,"song:abcdefghijk");assertNotNull(row);View thumb=tagged(row,"thumb");assertNotNull(thumb);
            decor.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));decor.layout(0,0,480,800);
            assertEquals("56dp thumbnail",56,thumb.getWidth());assertEquals(56,thumb.getHeight());
            assertNotNull("downloaded check icon",tagged(row,"downloaded"));View more=tagged(row,"row_action");assertEquals("More options",more.getContentDescription());
            assertTrue(contains(row,"An Artist • An Album"));assertTrue(contains(decor,"2 songs • 10.0 MB"));
            assertTrue("row action stays on screen",more.getRight()<=480&&more.getWidth()>0);
            assertNativeText(decor);image(a,"populated");
            more.performClick();idle();Dialog sheet=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(sheet);assertTrue(sheet.isShowing());View sd=sheet.getWindow().getDecorView();
            for(String item:new String[]{"Play","Go to artist","Go to album","Hide from Downloads","Delete download"})assertNotNull(item,tagged(sd,"menu:"+item));assertNativeText(sd);sheet.dismiss();screen.destroy();
        }
    }
    @Test public void unwrappedResolveErrorVisibleWithoutTruncation()throws Exception{
        Library.Item i=Library.add(context,"abcdefghijk","Andaaz e Karam");i.state="failed";i.error="[resolve] "+ResolveErrors.describe(new java.lang.reflect.InvocationTargetException(new NullPointerException("nonce argument is missing")));Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();assertTrue(contains(decor,"NullPointerException: nonce argument is missing"));assertFalse(contains(decor,"InvocationTargetException"));image(a,"dl12-resolve-error");screen.destroy();}
    }
    @Test public void transferPathAndInnerErrorRemainReadable()throws Exception{
        Library.Item i=Library.add(context,"abcdefghijk","Andaaz e Karam");i.state="failed";File temp=Library.temp(context,i);assertTrue(temp.getAbsolutePath().startsWith(context.getNoBackupFilesDir().getAbsolutePath()+File.separator));paradox.dl.Engine.prepare(temp);assertTrue(temp.getParentFile().isDirectory());
        i.error="[transfer] "+ResolveErrors.describe(new paradox.dl.Engine.FileFailure("Prepare transfer file",new File("/data/user/0/app.rvx.android.apps.youtube.musiy/no_backup/ultra_audio_parts/abcdefghijk.part.map"),new java.io.IOException("open failed: ENOENT (No such file or directory)")));Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();assertTrue(contains(decor,"abcdefghijk.part.map"));assertTrue(contains(decor,"ENOENT"));image(a,"dl13-transfer-path");screen.destroy();}
    }
    @Test public void activeDownloadsShowProgressAndPauseWithoutPlayActions()throws Exception{
        Library.Item two=Library.add(context,"lmnopqrstuv","Another song");two.artist="Another Artist";two.state="downloading";two.bytes=1700000;two.total=4000000;
        Library.Item three=Library.add(context,"mnopqrstuvw","Broken song");three.state="failed";three.error="Network unavailable";Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            View row=tagged(decor,"song:lmnopqrstuv");assertNotNull(row);assertEquals("Pause download",tagged(row,"row_action").getContentDescription());assertTrue(contains(row,"Downloading • 42%"));
            ProgressBar bar=null;for(View v:all(row,new ArrayList<View>()))if(v instanceof ProgressBar)bar=(ProgressBar)v;assertNotNull(bar);assertEquals(42,bar.getProgress());
            View failed=tagged(decor,"song:mnopqrstuvw");assertEquals("Retry download",tagged(failed,"row_action").getContentDescription());assertNotNull(tagged(failed,"failed"));assertTrue(contains(failed,"Network unavailable"));
            assertNull("no playable music, no Play",tagged(decor,"action:Play"));assertNull(tagged(decor,"action:Shuffle"));
            // progress updates in place without rebuilding rows
            two.bytes=3000000;Method render=DownloadsScreen.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(screen);assertSame(row,tagged(decor,"song:lmnopqrstuv"));assertTrue(contains(row,"Downloading • 75%"));assertEquals(75,bar.getProgress());
            assertNativeText(decor);image(a,"active");screen.destroy();
        }
    }
    /** DL8 name kept. Its old line 44 asserted the Unicode "Ⅱ" pause glyph, which this UI deliberately replaced with the
     *  native pause icon; the obsolete glyph expectation is now the native icon's content description. */
    @Test public void librarySearchAndGroupsRender()throws Exception{
        Library.Item one=done("abcdefghijk","A song with a long title that must wrap without hiding the actions","An Artist","An Album");one.bitrate=128000;
        Library.Item two=Library.add(context,"lmnopqrstuv","Another song");two.artist="Another Artist";two.state="downloading";two.bytes=1700000;two.total=4000000;Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            assertTrue(contains(decor,"A song with a long title"));
            View active=tagged(decor,"song:lmnopqrstuv");assertNotNull(active);assertEquals("native pause action replaces the old glyph","Pause download",tagged(active,"row_action").getContentDescription());
            assertNativeText(decor);image(a,"songs-and-active");
            tagged(decor,"chip:Artists").performClick();idle();assertTrue(contains(decor,"An Artist"));image(a,"artists");
            Field search=DownloadsScreen.class.getDeclaredField("search");search.setAccessible(true);((EditText)search.get(screen)).setText("missing song");idle();assertTrue(contains(decor,"No matching downloads"));
            screen.destroy();
        }
    }
    @Test public void groupChipsArtistsAlbumsAndSearch()throws Exception{
        done("abcdefghijk","One","An Artist","An Album");done("bcdefghijkl","Two","An Artist","An Album");done("cdefghijklm","Three","","");Library.save(context);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            tagged(decor,"chip:Artists").performClick();idle();assertTrue(tagged(decor,"chip:Artists").isSelected());assertNotNull(tagged(decor,"group:An Artist"));assertNotNull(tagged(decor,"group:Unknown Artist"));assertTrue(contains(decor,"Artist • 2 songs"));image(a,"artists");
            tagged(decor,"group:An Artist").performClick();idle();View groupChip=tagged(decor,"chip:An Artist");assertNotNull("group chip",groupChip);assertTrue(groupChip.isSelected());assertNotNull(tagged(decor,"song:abcdefghijk"));assertNull(tagged(decor,"song:cdefghijklm"));image(a,"group-chips");
            groupChip.performClick();idle();assertNull(tagged(decor,"chip:An Artist"));assertNotNull(tagged(decor,"group:An Artist"));
            tagged(decor,"chip:Albums").performClick();idle();assertNotNull(tagged(decor,"group:An Album"));assertNotNull(tagged(decor,"group:Unknown Album"));
            Field search=DownloadsScreen.class.getDeclaredField("search");search.setAccessible(true);((EditText)search.get(screen)).setText("missing song");idle();assertTrue(contains(decor,"No matching downloads"));
            assertNativeText(decor);screen.destroy();
        }
    }
    @Test public void contentInsetClearsHostMiniPlayerAndBottomNav()throws Exception{
        done("abcdefghijk","One","An Artist","An Album");Library.save(context);
        try(ActivityController<OverlayHost> c=Robolectric.buildActivity(OverlayHost.class).setup()){
            OverlayHost a=c.get();DownloadsScreen screen=new DownloadsScreen(a,a.content,new Intent());View decor=a.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));decor.layout(0,0,480,800);
            assertTrue("overlay is measured",DownloadsScreen.overlayInset(a.content.getChildAt(0))>=120);
            Method apply=DownloadsScreen.class.getDeclaredMethod("applyInsets");apply.setAccessible(true);apply.invoke(screen);
            Field f=DownloadsScreen.class.getDeclaredField("scroll");f.setAccessible(true);ScrollView scroll=(ScrollView)f.get(screen);assertTrue("list clears player + nav",scroll.getPaddingBottom()>=120);assertFalse(scroll.getClipToPadding());
            assertSame("host player kept",a.player.getParent(),a.nav.getParent());image(a,"overlay-inset");screen.destroy();
        }
    }
    @Test public void statusBarAndFullErrorDoNotOverlap()throws Exception{
        Library.Item item=Library.add(context,"abcdefghijk","Andaaz e Karam");item.state="failed";item.error="[resolve:refresh-empty] Native audio was unavailable. Replay the song after updating, then tap Retry.";Library.save(context);
        try(ActivityController<OverlayHost> c=Robolectric.buildActivity(OverlayHost.class).setup()){
            OverlayHost a=c.get();a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            DownloadsScreen page=new DownloadsScreen(a,a.content,new Intent());View decor=a.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));decor.layout(0,0,480,800);
            Method apply=DownloadsScreen.class.getDeclaredMethod("applyInsets");apply.setAccessible(true);apply.invoke(page);decor.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));decor.layout(0,0,480,800);
            View title=tagged(decor,"header_title");int[] at=new int[2];title.getLocationOnScreen(at);assertTrue("header below status bar",at[1]>=24);
            View row=tagged(decor,"song:abcdefghijk");TextView error=null;for(View v:all(row,new ArrayList<View>()))if(v instanceof TextView&&((TextView)v).getText().toString().startsWith("[resolve"))error=(TextView)v;
            assertNotNull(error);assertNull(error.getEllipsize());assertTrue(error.getMaxLines()>10);assertEquals(item.error,error.getText().toString());image(a,"statusbar-error");page.destroy();
        }
    }
    @Test public void thumbnailsLoadPersistedLocalArtworkAndTrimLetterbox()throws Exception{
        Library.Item i=done("abcdefghijk","With art","An Artist","An Album");Library.save(context);
        File art=Art.file(context,i.id);art.getParentFile().mkdirs();Bitmap fixture=Bitmap.createBitmap(480,360,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(fixture);cv.drawColor(Color.BLACK);Paint p=new Paint();p.setColor(0xffd03030);cv.drawRect(0,45,480,315,p);
        try(FileOutputStream out=new FileOutputStream(art)){fixture.compress(Bitmap.CompressFormat.JPEG,95,out);}
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();ImageView image=null;
            for(int n=0;n<100&&image==null;n++){Thread.sleep(20);idle();View row=tagged(decor,"song:abcdefghijk");for(View v:all(row,new ArrayList<View>()))if(v instanceof ImageView&&((ImageView)v).getDrawable() instanceof BitmapDrawable&&i.id.equals(v.getTag()))image=(ImageView)v;}
            assertNotNull("persisted artwork shown",image);Bitmap shown=((BitmapDrawable)image.getDrawable()).getBitmap();
            assertTrue("letterbox removed",Color.red(shown.getPixel(shown.getWidth()/2,1))>150);assertTrue("decoded near 56dp",shown.getWidth()<=240);
            image(a,"thumbnail");screen.destroy();
        }
    }
    @Test public void nativeResourcesFallBackSafelyAndAreReported()throws Exception{
        done("abcdefghijk","One","An Artist","An Album");Library.save(context);NativeUi.forceFallback=true;
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();assertNotNull(tagged(decor,"chip:Songs"));
            String report=NativeUi.report();assertTrue(report,report.contains("chip = fallback"));assertTrue(report,report.contains("header font = fallback: framework sans-serif bold (not YouTube Sans)"));assertTrue(report,report.contains("icon more = fallback"));assertTrue(report,report.contains("text view = fallback"));
            assertFalse("fallback is never reported as native",report.contains("= native"));assertNativeText(decor);image(a,"fallback");screen.destroy();
        }
    }
    @Test public void storageNamesAndPersistentLibrary()throws Exception{
        Library.Item item=Library.add(context,"0123456789_","Track");item.state="done";item.uri="content://media/external/audio/media/12";Library.save(context);
        Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);Library.Item loaded=Library.get(context,item.id);assertEquals("done",loaded.state);assertEquals(item.uri,loaded.uri);
        loaded.state="downloading";Library.save(context);f.set(null,null);assertEquals("paused",Library.get(context,item.id).state);
        assertFalse(Transfer.safeName("../../bad/name").contains("/"));
    }
    @Test public void duplicateAndWipeAdmission()throws Exception{
        Library.Item first=Library.add(context,"abcdefghijk","First");
        Library.Item same=Library.add(context,"abcdefghijk","Other");assertSame(first,same);assertEquals(1,Library.all(context).size());assertEquals("First",same.title);
        Library.wiping=true;try{Library.add(context,"lmnopqrstuv","Blocked");fail("wipe admitted a job");}catch(IOException expected){}finally{Library.wiping=false;}assertEquals(1,Library.all(context).size());
    }
    @Test public void publicationDeleteOnlyRecordedFile()throws Exception{
        File keep=new File(context.getFilesDir(),"unrelated.m4a"),owned=new File(context.getFilesDir(),"owned.m4a");try(FileOutputStream f=new FileOutputStream(keep)){f.write(1);}try(FileOutputStream f=new FileOutputStream(owned)){f.write(1);}
        Library.Item item=Library.add(context,"abcdefghijk","Track");item.uri=owned.getPath();Storage.delete(context,item);assertFalse(owned.exists());assertTrue(keep.exists());Storage.delete(context,item);keep.delete();
    }
    @Test public void endpointIdentityIsNotCurrentSong()throws Exception{
        Library.Item wanted=Library.add(context,"abcdefghijk","Wanted"),other=Library.add(context,"lmnopqrstuv","Current");wanted.state="downloading";other.state="downloading";
        app.revanced.extension.shared.utils.Utils.testContext=context;Endpoint ep=new Endpoint();ep.c=1;ep.d=wanted.id;ep.e=3;Hooks.videoEndpoint(ep);assertTrue(wanted.paused);assertFalse(other.paused);
    }
    public static class Endpoint{public int c,e;public Object d;}
    @Test public void catalogKeepsTrackIdsAndLinkedMetadata()throws Exception{
        org.json.JSONObject row=new org.json.JSONObject();row.put("playlistItemData",new org.json.JSONObject().put("videoId","abcdefghijk"));org.json.JSONArray cols=new org.json.JSONArray();cols.put(column("Title",null));cols.put(column("1,234 plays",null));cols.put(column("Artist","UCtest"));cols.put(column("Album","MPREtest"));row.put("flexColumns",cols);
        Catalog.Page page=new Catalog.Page();Catalog.row(new org.json.JSONObject().put("musicResponsiveListItemRenderer",row),page,0);assertEquals(1,page.tracks.size());Catalog.Track track=page.tracks.get("abcdefghijk");assertEquals("Artist",track.artist);assertEquals("Album",track.album);assertEquals("Title",track.title);
    }
    static org.json.JSONObject column(String text,String browse)throws Exception{org.json.JSONObject run=new org.json.JSONObject().put("text",text);if(browse!=null)run.put("navigationEndpoint",new org.json.JSONObject().put("browseEndpoint",new org.json.JSONObject().put("browseId",browse)));return new org.json.JSONObject().put("musicResponsiveListItemFlexColumnRenderer",new org.json.JSONObject().put("text",new org.json.JSONObject().put("runs",new org.json.JSONArray().put(run))));}

    @Test public void selectionWorksAcrossAllTabsAndKeepsActionsInBounds()throws Exception {
        Library.Item first=done("abcdefghijk","Selection One","Artist One","Album One");
        Library.Item second=done("bcdefghijkl","Selection Two","Artist Two","Album Two");
        File f=new File(context.getFilesDir(),"selection.mp3");try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}first.uri=f.getAbsolutePath();second.uri=f.getAbsolutePath();Library.save(context);
        PlaylistIndex.record("PLtestselection",new ArrayList<Catalog.Track>(Arrays.asList(new Catalog.Track(first.id,first.title,first.artist,first.album))),true);
        try(ActivityController<TestHost> c=Robolectric.buildActivity(TestHost.class).setup()){
            Activity a=c.get();DownloadsScreen screen=open(a);View decor=a.getWindow().getDecorView();
            for(String tabName:new String[]{"Songs","Artists","Albums","Playlists"}){
                set(screen,"tab",tabName);set(screen,"group","");call(screen,"render");
                String tag=tabName.equals("Songs")?"song:"+first.id:tabName.equals("Artists")?"group:Artist One":tabName.equals("Albums")?"group:Album One":"playlist:PLtestselection";
                View row=tagged(decor,tag);assertNotNull(tabName+" row",row);assertTrue(tabName+" longpress",row.performLongClick());
                assertTrue(tabName+" count",contains(decor,"1 selected"));
                View checkbox=tagged(tagged(decor,tag),"row_checkbox");assertNotNull(tabName+" checkbox",checkbox);
                assertTrue(((CheckBox)checkbox).isChecked());
                assertNotNull("play applicable",tagged(decor,"selection_action:Play"));
                assertNull("no download for saved",tagged(decor,"selection_action:Download"));
                if(tabName.equals("Songs"))image(a,"selection-songs");
                screen.onBackPressed();java.lang.reflect.Field selectionField=DownloadsScreen.class.getDeclaredField("selectionMode");selectionField.setAccessible(true);assertFalse(selectionField.getBoolean(screen));assertNull(tagged(decor,"row_checkbox"));
            }
            screen.destroy();
        }
    }
}
