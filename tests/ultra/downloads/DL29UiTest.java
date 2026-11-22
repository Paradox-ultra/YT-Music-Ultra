package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL29UiTest {
 Activity c;
 @Before public void setup()throws Exception{c=Robolectric.buildActivity(Activity.class).setup().get();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;Art.network=false;VoiceRecordings.set(c,true);Art.memory.evictAll();Library.wiping=false;java.lang.reflect.Field field=Library.class.getDeclaredField("items");field.setAccessible(true);field.set(null,null);new File(c.getFilesDir(),"ultra_downloads_v1.json").delete();Library.prefs(c).edit().clear().commit();VoiceRecordings.set(c,true);PlaylistIndex.invalidateCache();}
 Library.Item file(String id,String name)throws Exception{Library.Item i=Library.add(c,id,name);File f=new File(c.getCacheDir(),name+".mp3");try(FileOutputStream o=new FileOutputStream(f)){o.write(new byte[]{1,2,3});}i.uri=f.getPath();i.state="done";Library.save(c);return i;}
 DownloadsScreen screen(FrameLayout mount)throws Exception{DownloadsScreen s=new DownloadsScreen(c,mount,new Intent());java.lang.reflect.Field fg=DownloadsScreen.class.getDeclaredField("foreground");fg.setAccessible(true);fg.setBoolean(s,false);java.lang.reflect.Method render=DownloadsScreen.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);render.invoke(s,true);return s;}
 void clearIndicators(View v){v.setScrollIndicators(0);if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)clearIndicators(((ViewGroup)v).getChildAt(n));}
 void snapshot(View view,String name)throws Exception{clearIndicators(view);view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));view.layout(0,0,400,800);Bitmap b=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);cv.drawColor(Color.BLACK);view.draw(cv);new File("qa").mkdirs();try(FileOutputStream out=new FileOutputStream("qa/"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
 static List<View> tagged(View v,String tag){ArrayList<View> out=new ArrayList<>();if(tag.equals(v.getTag()))out.add(v);if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)out.addAll(tagged(((ViewGroup)v).getChildAt(n),tag));return out;}

 @Test public void songRowsUseDesignedCovers()throws Exception{
  file("abcdefghijk","Kesariya");Library.Item rec=file("lmnopqrstuv","Voice 023");rec.imported=true;Library.save(c);
  FrameLayout mount=new FrameLayout(c);c.setContentView(mount);DownloadsScreen s=screen(mount);
  ListView list=findList(mount);assertNotNull("songs tab uses the virtual list",list);assertTrue(list.getCount()>=2);
  for(int n=0;n<list.getCount();n++){
   View row=list.getAdapter().getView(n,null,list);List<View> thumbs=tagged(row,"thumb");assertEquals(1,thumbs.size());
   ViewGroup g=(ViewGroup)thumbs.get(0);assertTrue("row art fallback is a designed cover",((ImageView)g.getChildAt(0)).getDrawable() instanceof Covers.Cover);
  }
  mount.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));mount.layout(0,0,400,800);
  assertFalse("laid-out rows show covers",tagged(mount,"thumb").isEmpty());
  snapshot(mount,"DL29-songs");s.destroy();
 }
 static ListView findList(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){ListView l=findList(((ViewGroup)v).getChildAt(n));if(l!=null)return l;}return null;}
 @Test public void playlistRowsUseDesignedCovers()throws Exception{
  file("abcdefghijk","Kesariya");
  JSONObject m=new JSONObject().put("Top Songs",new JSONArray().put(new JSONObject().put("id","abcdefghijk"))).put("Road trip",new JSONArray());
  Library.prefs(c).edit().putString("ultra_local_playlists",m.toString()).commit();PlaylistIndex.invalidateCache();
  FrameLayout mount=new FrameLayout(c);c.setContentView(mount);DownloadsScreen s=screen(mount);
  java.lang.reflect.Field tab=DownloadsScreen.class.getDeclaredField("tab");tab.setAccessible(true);tab.set(s,"Playlists");
  java.lang.reflect.Method render=DownloadsScreen.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);render.invoke(s,true);
  List<View> thumbs=tagged(mount,"thumb");assertFalse("playlist rows show covers",thumbs.isEmpty());
  snapshot(mount,"DL29-playlists");s.destroy();
 }
}
