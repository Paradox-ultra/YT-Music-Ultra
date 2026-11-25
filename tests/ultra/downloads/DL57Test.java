package ultra.downloads;
import android.app.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import java.util.concurrent.*;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL57: Trending can never hold up Home or a tap: late start, 15 s deadline, 2 MB cap, streaming parse, stop after 2 failures. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL57Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;TrendingFeed.Fetcher realFetcher;final CountDownLatch hang=new CountDownLatch(1);
 @Before public void setup()throws Exception{
  realFetcher=TrendingFeed.fetcher;
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  java.lang.reflect.Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();
  TrendingFeed.cacheFile(a).delete();TrendingFeed.resetSession();TrendingFeed.DEADLINE_MS=15000;
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();Art.network=true;
  synchronized(Art.failures){Art.failures.clear();}synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();}
 }
 @After public void after(){hang.countDown();TrendingFeed.fetcher=realFetcher;TrendingFeed.DEADLINE_MS=15000;Art.network=false;NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 static String vid(int n){return String.format("vid%08d",n);}
 static List<String> timeline(){synchronized(NativeIntegration.TIMELINE){return new ArrayList<>(NativeIntegration.TIMELINE);}}
 static int index(List<String> t,String part){for(int n=0;n<t.size();n++)if(t.get(n).contains(part))return n;return -1;}
 void downloads()throws Exception{for(int k=0;k<10;k++){Library.Item i=Library.add(a,String.format("dl%09d",k),"Download "+k);i.artist="A";i.state="done";i.mime="audio/mpeg";i.created=1000+k;}Library.save(a);}
 void staleCache()throws Exception{JSONArray songs=new JSONArray();for(int n=0;n<10;n++)songs.put(new JSONObject().put("id",vid(n)).put("title","S"+n).put("artist","A").put("thumb",""));
  java.nio.file.Files.write(TrendingFeed.cacheFile(a).toPath(),new JSONObject().put("at",System.currentTimeMillis()-2*TrendingFeed.TTL_MS).put("gl","IN").put("songs",songs).toString().getBytes("UTF-8"));
  for(int n=0;n<10;n++){File f=Art.file(a,"trend_"+vid(n));f.getParentFile().mkdirs();Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,90,o);}}}
 /** Fetcher that blocks for up to 60 s (a hung connection); counts calls and notes whether Home had rendered first. */
 final List<Boolean> renderedBefore=Collections.synchronizedList(new ArrayList<Boolean>());
 TrendingFeed.Fetcher hung(){return new TrendingFeed.Fetcher(){public String post(String b)throws IOException{renderedBefore.add(index(timeline(),"render #1")>=0);try{hang.await(60,TimeUnit.SECONDS);}catch(InterruptedException e){throw new InterruptedIOException();}throw new IOException("hung");}};}
 static View find(View root,String tagPrefix){ArrayDeque<View> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){View v=q.poll();if(v.getTag() instanceof String&&((String)v.getTag()).startsWith(tagPrefix))return v;if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}return null;}

 @Test public void hungFetchNeverHoldsUpHomeRenderOrTaps()throws Exception{
  downloads();staleCache();TrendingFeed.fetcher=hung();
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);
  long t0=System.nanoTime();HomeScreen h=new HomeScreen(a,mount);
  for(int n=0;n<300&&(h.isLoading()||!h.hasContent()||mount.findViewWithTag("home_section:Trending songs")==null);n++){Thread.sleep(10);run(50);}
  long renderMs=(System.nanoTime()-t0)/1000000;
  assertTrue("Home rendered its content",h.hasContent());assertNotNull("Recent downloads row present",find(mount,"home_tile:"));
  assertTrue("render did not wait for the hung request: "+renderMs+" ms",renderMs<6000);
  assertEquals("the stale cache is shown while the request hangs","Trending songs",((TextView)mount.findViewWithTag("home_section:Trending songs")).getText().toString());
  for(int n=0;n<100&&renderedBefore.isEmpty();n++){Thread.sleep(10);run(10);}
  assertEquals("one request, and it is still hanging",1,renderedBefore.size());assertTrue("the request started only after the first render",renderedBefore.get(0));
  assertEquals("still hanging",1,hang.getCount());
  // Tap an online tile and a quick pick while the request hangs: both return at once.
  View tile=find(mount,"home_tile:online:");assertNotNull(tile);long t1=System.nanoTime();tile.performClick();long tapMs=(System.nanoTime()-t1)/1000000;NativePlayback.MAIN.removeCallbacksAndMessages(null);run(50);
  assertTrue("tap did not wait: "+tapMs+" ms",tapMs<1000);assertTrue(timeline().toString(),index(timeline(),"trending tap "+vid(0))>=0);
  View card=find(mount,"home_card:");if(card!=null){long t2=System.nanoTime();card.performClick();assertTrue((System.nanoTime()-t2)/1000000<1000);NativePlayback.MAIN.removeCallbacksAndMessages(null);}
  // Chip switch + re-render still run while hanging.
  h.refresh();for(int n=0;n<50;n++){Thread.sleep(5);run(50);}assertTrue(h.hasContent());assertEquals(1,hang.getCount());
  h.destroy();
 }
 @Test public void trendingDoesNotStartBeforeTheFirstRender()throws Exception{
  downloads();final List<String> seen=Collections.synchronizedList(new ArrayList<String>());
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{seen.add(String.valueOf(index(timeline(),"render #1")));throw new IOException("offline");}};
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=new HomeScreen(a,mount);
  assertTrue("no Trending work in the constructor",index(timeline(),"trending: started")<0);
  for(int n=0;n<300&&seen.isEmpty();n++){Thread.sleep(10);run(50);}
  List<String> t=timeline();assertTrue(t.toString(),index(t,"render #1")>=0&&index(t,"render #1")<index(t,"trending: started"));
  assertFalse("render #1 logged before the request",seen.get(0).equals("-1"));h.destroy();
 }
 @Test public void deadlineAbortsAHungRequestAndTwoFailuresStopTheSession()throws Exception{
  TrendingFeed.DEADLINE_MS=400;final int[] calls={0};
  final TrendingFeed.Fetcher h=hung();TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{calls[0]++;return h.post(b);}};
  long t0=System.nanoTime();assertNull(TrendingFeed.refresh(a));long ms=(System.nanoTime()-t0)/1000000;
  assertTrue("gave up at the deadline, not after 60 s: "+ms,ms<2500);assertTrue(TrendingFeed.lastOutcome,TrendingFeed.lastOutcome.startsWith("timeout"));
  assertNull("a request still hanging blocks a second one",TrendingFeed.refresh(a));assertTrue(TrendingFeed.lastOutcome.startsWith("busy"));assertEquals(1,calls[0]);
  hang.countDown();for(int n=0;n<100&&TrendingFeed.lastOutcome.startsWith("busy")&&n<100;n++){Thread.sleep(20);TrendingFeed.refresh(a);}
  assertEquals("two failures in a row",2,calls[0]);assertTrue(TrendingFeed.lastOutcome,TrendingFeed.lastOutcome.contains("stopped for this session"));assertTrue(TrendingFeed.stopped());
  assertNull(TrendingFeed.refresh(a));assertEquals("no third request this session",2,calls[0]);assertTrue(TrendingFeed.lastOutcome.startsWith("stopped"));
 }
 @Test public void slowParseIsCutByTheDeadlineAndSuccessResetsTheFailureCount()throws Exception{
  try{TrendingFeed.parse("{\"a\":[1,2,3]}",System.nanoTime()-1);fail("deadline ignored");}catch(TrendingFeed.Deadline expected){}
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{throw new IOException("x");}};TrendingFeed.refresh(a);assertEquals(1,TrendingFeed.failures);
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b){return DL56Test.trending(0,10);}};assertNotNull(TrendingFeed.refresh(a));assertEquals(0,TrendingFeed.failures);
 }
 @Test public void largeResponseParsesByStreamingAndCacheIsAFileNotPrefs()throws Exception{
  StringBuilder filler=new StringBuilder("{\"contents\":{\"filler\":[");for(int n=0;n<12000;n++){if(n>0)filler.append(',');filler.append("{\"x\":{\"y\":[\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",1,true,null]}}");}
  String t=DL56Test.trending(0,12);String big=filler.append("],\"real\":").append(t).append("}}").toString();assertTrue(big.length()>1000000);
  long t0=System.nanoTime();List<TrendingFeed.Song> s=TrendingFeed.parse(big);long ms=(System.nanoTime()-t0)/1000000;
  assertEquals(10,s.size());assertEquals(vid(0),s.get(0).videoId);assertTrue("parse took "+ms+" ms",ms<5000);
  assertEquals(2*1024*1024,TrendingFeed.MAX_BYTES);
  final String body=t;TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b){return body;}};assertNotNull(TrendingFeed.refresh(a));
  assertTrue(TrendingFeed.cacheFile(a).isFile());assertFalse("no SharedPreferences write (no queued apply for the main thread)",Library.prefs(a).contains("ultra_trending_v1"));
  assertEquals(10,TrendingFeed.cached(a).songs.size());
 }
 @Test public void coversOnlyFromYouTubeImageHostsAtAUsableSize(){
  TrendingFeed.Song s=new TrendingFeed.Song(vid(1),"t","a","https://lh3.googleusercontent.com/abc=w120-h120-l90-rj");
  List<String> u=TrendingFeed.coverUrls(s);assertEquals("https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",u.get(0));assertEquals("https://i.ytimg.com/vi/"+vid(1)+"/hqdefault.jpg",u.get(1));
  assertEquals("other hosts are never fetched",false,TrendingFeed.coverFrom("https://example.com/a.jpg",new File(a.getFilesDir(),"x.jpg")));
  assertFalse(TrendingFeed.coverFrom("http://i.ytimg.com/vi/x/hq.jpg",new File(a.getFilesDir(),"x.jpg")));
 }
}
