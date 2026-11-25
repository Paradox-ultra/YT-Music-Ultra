package ultra.downloads;
import android.app.*;import android.content.*;import android.content.pm.*;import android.graphics.*;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import ultra.home.*;

/** DL56: Home "Trending songs" from YouTube Music Explore's Trending shelf (cached 6 h), host-player taps, no header icon. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28,qualifiers="w400dp-h800dp-mdpi") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL56Test {
 public static class Host extends Activity{public void onCreate(Bundle b){setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(b);}}
 Activity a;Locale locale;TrendingFeed.Fetcher realFetcher;
 @Before public void setup()throws Exception{
  locale=Locale.getDefault();realFetcher=TrendingFeed.fetcher;
  a=Robolectric.buildActivity(Host.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;Library.prefs(a).edit().clear().commit();
  Field f=Library.class.getDeclaredField("items");f.setAccessible(true);f.set(null,null);new File(a.getFilesDir(),"ultra_downloads_v1.json").delete();Library.wiping=false;PlaylistIndex.invalidateCache();TrendingFeed.cacheFile(a).delete();TrendingFeed.resetSession();TrendingFeed.DEADLINE_MS=15000;
  NativeIntegration.panel=null;NativeIntegration.home=null;NativeIntegration.homeMount=null;NativeIntegration.homeOpenPending=false;HomeScreen.COVERS.evictAll();HomeScreen.lastReport="";Art.network=true;
  synchronized(Art.failures){Art.failures.clear();}synchronized(NativeIntegration.TIMELINE){NativeIntegration.TIMELINE.clear();NativeIntegration.repeatCount=0;NativeIntegration.repeatCallers.clear();}
 }
 @After public void after(){TrendingFeed.fetcher=realFetcher;Locale.setDefault(locale);Art.network=false;NativeIntegration.leave();NativeIntegration.destroyed(a);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void run(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms));}
 static String read(File f)throws IOException{return new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8");}
 static void write(File f,String s)throws IOException{java.nio.file.Files.write(f.toPath(),s.getBytes("UTF-8"));}
 static String vid(int n){return String.format("vid%08d",n);}

 // ------------------------------------------------------------------ fixture shaped like the browse response
 static JSONObject runs(Object... parts)throws JSONException{JSONArray r=new JSONArray();for(int n=0;n<parts.length;n+=2){JSONObject run=new JSONObject().put("text",parts[n]);if(parts[n+1]!=null)run.put("navigationEndpoint",new JSONObject().put("browseEndpoint",new JSONObject().put("browseId",parts[n+1])));r.put(run);}return new JSONObject().put("runs",r);}
 static JSONObject thumb(String url)throws JSONException{return new JSONObject().put("musicThumbnailRenderer",new JSONObject().put("thumbnail",new JSONObject().put("thumbnails",new JSONArray().put(new JSONObject().put("url","https://i.ytimg.com/small.jpg")).put(new JSONObject().put("url",url)))));}
 static JSONObject row(String id,String title,String artist)throws JSONException{
  JSONObject col0=new JSONObject().put("musicResponsiveListItemFlexColumnRenderer",new JSONObject().put("text",runs(title,null)));
  JSONObject col1=new JSONObject().put("musicResponsiveListItemFlexColumnRenderer",new JSONObject().put("text",runs(artist,"UCartist"+artist.length()," • ",null,"1.2M plays",null)));
  return new JSONObject().put("musicResponsiveListItemRenderer",new JSONObject().put("playlistItemData",new JSONObject().put("videoId",id)).put("flexColumns",new JSONArray().put(col0).put(col1)).put("thumbnail",thumb("https://lh3.googleusercontent.com/"+id)));
 }
 static JSONObject tile(String id,String title,String artist)throws JSONException{
  return new JSONObject().put("musicTwoRowItemRenderer",new JSONObject().put("title",runs(title,null)).put("subtitle",runs("Song",null," • ",null,artist,"UCx")).put("navigationEndpoint",new JSONObject().put("watchEndpoint",new JSONObject().put("videoId",id))).put("thumbnailRenderer",thumb("https://i.ytimg.com/vi/"+id+"/hq.jpg")));
 }
 static JSONObject shelf(String title,JSONArray items)throws JSONException{return new JSONObject().put("musicCarouselShelfRenderer",new JSONObject().put("header",new JSONObject().put("musicCarouselShelfBasicHeaderRenderer",new JSONObject().put("title",runs(title,null)))).put("contents",items));}
 static String response(JSONObject... shelves)throws JSONException{JSONArray contents=new JSONArray();for(JSONObject s:shelves)contents.put(s);
  return new JSONObject().put("contents",new JSONObject().put("singleColumnBrowseResultsRenderer",new JSONObject().put("tabs",new JSONArray().put(new JSONObject().put("tabRenderer",new JSONObject().put("content",new JSONObject().put("sectionListRenderer",new JSONObject().put("contents",contents)))))))).toString();}
 static String trending(int from,int count){try{JSONArray items=new JSONArray();for(int n=from;n<from+count;n++)items.put(row(vid(n),"Song "+n,"Artist "+n));
  return response(shelf("New releases",new JSONArray().put(tile("zzzzzzzzzzz","Album",""))),shelf("Trending",items),shelf("Moods",new JSONArray()));}catch(JSONException e){throw new RuntimeException(e);}}

 // ------------------------------------------------------------------ parsing and request
 @Test public void parsesTheTrendingShelfTopTenWithTitleArtistAndCover()throws Exception{
  JSONArray items=new JSONArray();for(int n=0;n<14;n++){items.put(row(vid(n),"Song "+n,"Artist "+n));if(n==0){items.put(row(vid(0),"dup","dup"));items.put(row("bad id","x","y"));}}
  List<TrendingFeed.Song> s=TrendingFeed.parse(response(shelf("Trending in India",new JSONArray().put(row(vid(90),"other","o"))),shelf("Trending",items)));
  assertEquals("top 10",10,s.size());assertEquals("exact 'Trending' shelf wins over a title that only contains it",vid(0),s.get(0).videoId);assertEquals("duplicates and invalid ids dropped",vid(1),s.get(1).videoId);
  assertEquals("Song 0",s.get(0).title);assertEquals("Artist 0",s.get(0).artist);assertEquals("largest thumbnail","https://lh3.googleusercontent.com/"+vid(0),s.get(0).thumb);
  List<TrendingFeed.Song> f=TrendingFeed.parse(response(shelf("Charts",new JSONArray()),shelf("Trending in India",new JSONArray().put(tile(vid(5),"Tile song","Tile artist")))));
  assertEquals("fallback: first shelf containing 'Trending', two-row tiles too",vid(5),f.get(0).videoId);assertEquals("Tile artist",f.get(0).artist);assertEquals("Tile song",f.get(0).title);
  assertTrue("no Trending shelf",TrendingFeed.parse(response(shelf("New releases",items))).isEmpty());
 }
 @Test public void requestIsAnonymousExploreWithTheDeviceCountry()throws Exception{
  final List<String> bodies=new ArrayList<>();TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String body){bodies.add(body);return "{}";}};
  Locale.setDefault(new Locale("de","DE"));TrendingFeed.refresh(a);Locale.setDefault(new Locale("en"));TrendingFeed.refresh(a);
  JSONObject de=new JSONObject(bodies.get(0)),none=new JSONObject(bodies.get(1));
  assertEquals("FEmusic_explore",de.getString("browseId"));JSONObject client=de.getJSONObject("context").getJSONObject("client");
  assertEquals("WEB_REMIX",client.getString("clientName"));assertEquals("1.20241127.01.00",client.getString("clientVersion"));assertEquals("en",client.getString("hl"));assertEquals("DE",client.getString("gl"));
  assertEquals("no country in the locale: IN","IN",none.getJSONObject("context").getJSONObject("client").getString("gl"));
  assertEquals("only the client context, no account data",1,de.getJSONObject("context").length());
  assertEquals("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",TrendingFeed.URL);
 }
 @Test public void cacheHasASixHourTtlAndSurvivesFailures()throws Exception{
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{return trending(0,12);}};
  TrendingFeed.Feed f=TrendingFeed.refresh(a);assertNotNull(f);assertEquals(10,f.songs.size());assertEquals("online",TrendingFeed.lastOutcome);
  TrendingFeed.Feed c=TrendingFeed.cached(a);assertEquals(vid(0),c.songs.get(0).videoId);assertEquals("Artist 0",c.songs.get(0).artist);
  assertTrue(c.fresh(c.fetchedAt+TrendingFeed.TTL_MS-1));assertFalse(c.fresh(c.fetchedAt+TrendingFeed.TTL_MS+1));
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{throw new IOException("no route");}};
  assertNull(TrendingFeed.refresh(a));assertTrue(TrendingFeed.lastOutcome.startsWith("offline"));assertEquals("cache kept",10,TrendingFeed.cached(a).songs.size());
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b){return "{\"contents\":{}}";}};
  assertNull(TrendingFeed.refresh(a));assertTrue(TrendingFeed.lastOutcome.startsWith("parse"));assertEquals(10,TrendingFeed.cached(a).songs.size());
 }

 // ------------------------------------------------------------------ Home
 void downloads()throws Exception{for(int k=0;k<10;k++){Library.Item i=Library.add(a,String.format("dl%09d",k),"Download "+k);i.artist="A";i.state="done";i.mime="audio/mpeg";i.created=1000+k;}Library.save(a);}
 /** Covers for trending ids already on disk (as the cover pipeline saves them), so no test touches the network. */
 void covers(int from,int to)throws Exception{for(int n=from;n<to;n++){File f=Art.file(a,"trend_"+vid(n));f.getParentFile().mkdirs();Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xffcc5544);try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,90,o);}}}
 HomeScreen home(FrameLayout mount)throws Exception{HomeScreen h=new HomeScreen(a,mount);for(int n=0;n<300&&(h.isLoading()||!h.hasContent());n++){Thread.sleep(10);run(50);}for(int n=0;n<150;n++){Thread.sleep(10);run(20);}run(4500);return h;}
 static List<String> row(View root){List<String> out=new ArrayList<>();View r=root.findViewWithTag("home_recent");if(r==null)return out;ArrayDeque<View> q=new ArrayDeque<>();q.add(r);
  while(!q.isEmpty()){View v=q.poll();if(v.getTag() instanceof String&&((String)v.getTag()).startsWith("home_tile:"))out.add(((String)v.getTag()).substring(10));if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}return out;}
 static int renders(HomeScreen h)throws Exception{Field f=HomeScreen.class.getDeclaredField("renders");f.setAccessible(true);return f.getInt(h);}

 @Test public void homeShowsTrendingSongsWithCoversFromTheFeed()throws Exception{
  downloads();covers(0,10);for(int k=0;k<10;k++){File f=Art.file(a,String.format("dl%09d",k));f.getParentFile().mkdirs();java.nio.file.Files.copy(Art.file(a,"trend_"+vid(0)).toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}final int[] calls={0};TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{calls[0]++;return trending(0,12);}};
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=home(mount);
  assertEquals(1,calls[0]);assertNotNull(mount.findViewWithTag("home_section:Trending songs"));assertNull(mount.findViewWithTag("home_section:Recent downloads"));
  List<String> ids=row(mount);assertEquals(10,ids.size());assertEquals("online:"+vid(0),ids.get(0));assertEquals("online:"+vid(9),ids.get(9));
  String d=h.diagnostics();assertTrue(d,d.contains("trending: 10 songs from online"));assertTrue(d+"\n"+NativeIntegration.TIMELINE,d.contains("trending: items 10, covers 10/10 (online=10)"));
  assertNotNull("Quick picks kept",mount.findViewWithTag("home_section:Quick picks"));assertNotNull("hero kept",mount.findViewWithTag("home_hero"));
  assertTrue("first load renders at most 3 times (songs, playlists, trending)",renders(h)<=3);assertTrue(d,d.contains("quick: items 6, covers 6/6"));h.destroy();
 }
 @Test public void cacheShowsAtOnceAndAFreshCacheIsNotRefetched()throws Exception{
  downloads();covers(0,30);
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{return trending(20,10);}};TrendingFeed.refresh(a);// fresh cache: songs 20..29
  final int[] calls={0};TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{calls[0]++;return trending(0,10);}};
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=home(mount);
  assertEquals("fresh cache: no request",0,calls[0]);assertEquals("online:"+vid(20),row(mount).get(0));assertTrue(h.diagnostics(),h.diagnostics().contains("from cache"));h.destroy();
  // stale cache: shown first, then replaced by the refresh
  JSONObject j=new JSONObject(read(TrendingFeed.cacheFile(a)));j.put("at",System.currentTimeMillis()-TrendingFeed.TTL_MS-1000);write(TrendingFeed.cacheFile(a),j.toString());
  final java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);
  TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{try{gate.await(60,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}return trending(0,10);}};
  FrameLayout m2=new FrameLayout(a);a.setContentView(m2);HomeScreen h2=new HomeScreen(a,m2);try{for(int n=0;n<200&&(row(m2).isEmpty()||!row(m2).get(0).startsWith("online:"));n++){Thread.sleep(10);run(50);}
  assertEquals("stale cache shown while refreshing","online:"+vid(20),row(m2).get(0));
  gate.countDown();for(int n=0;n<200&&!row(m2).get(0).equals("online:"+vid(0));n++){Thread.sleep(10);run(50);}
  assertEquals("refreshed list replaces it","online:"+vid(0),row(m2).get(0));assertTrue(renders(h2)<=4);}finally{gate.countDown();h2.destroy();}
 }
 @Test public void offlineShowsTheCacheOrElseRecentDownloads()throws Exception{
  downloads();TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{throw new IOException("offline");}};
  FrameLayout mount=new FrameLayout(a);a.setContentView(mount);HomeScreen h=home(mount);
  assertNotNull("no cache, offline: Recent downloads as before",mount.findViewWithTag("home_section:Recent downloads"));assertNull(mount.findViewWithTag("home_section:Trending songs"));
  assertEquals("song:"+String.format("dl%09d",9),row(mount).get(0));assertTrue(h.diagnostics(),h.diagnostics().contains("trending: none, showing Recent downloads; last refresh: offline"));h.destroy();
  JSONArray songs=new JSONArray();for(int n=0;n<10;n++)songs.put(new JSONObject().put("id",vid(n)).put("title","S").put("artist","A").put("thumb",""));
  write(TrendingFeed.cacheFile(a),new JSONObject().put("at",System.currentTimeMillis()-3*TrendingFeed.TTL_MS).put("gl","IN").put("songs",songs).toString());covers(0,10);
  FrameLayout m2=new FrameLayout(a);a.setContentView(m2);HomeScreen h2=home(m2);
  assertNotNull("offline with a (stale) cache: the cache",m2.findViewWithTag("home_section:Trending songs"));assertEquals("online:"+vid(0),row(m2).get(0));h2.destroy();
 }
 @Test public void tapsUseTheHostWatchRouteOrAnIntentForThisPackageOrNothing()throws Exception{
  Method play=Class.forName("UltraWatchBridge").getMethod("play",String.class);assertTrue(Modifier.isStatic(play.getModifiers()));
  assertFalse("invalid ids are rejected",(Boolean)play.invoke(null,"not an id"));assertEquals("none",HomeSource.playOnline(a,"not an id"));
  String route=HomeSource.playOnline(a,vid(1));assertTrue(route,route.startsWith("watch")||route.startsWith("intent")||route.equals("none"));NativePlayback.MAIN.removeCallbacksAndMessages(null);
  Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.youtube.com/watch?v="+vid(1)));view.setPackage(a.getPackageName());
  ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName=a.getPackageName();info.activityInfo.name="Watch";
  Shadows.shadowOf(a.getPackageManager()).addResolveInfoForIntent(view,info);
  String again=HomeSource.playOnline(a,vid(1));
  if(again.startsWith("intent")){Intent started=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("https://music.youtube.com/watch?v="+vid(1),started.getDataString());assertEquals("restricted to this app",a.getPackageName(),started.getPackage());}
  else fail("an activity resolves, so the deep link must be used first: "+again);
 }
 @Test public void homeHeaderHasNoIconAndKeepsItsHeight()throws Exception{
  downloads();TrendingFeed.fetcher=new TrendingFeed.Fetcher(){public String post(String b)throws IOException{throw new IOException("offline");}};
  FrameLayout container=new FrameLayout(a);container.setId(0x7f0b04e1);a.setContentView(container);NativeIntegration.attach(a);
  NativeIntegration.openHome(false);idle();// layer mode is where DL33-DL55 showed the download icon
  View brand=container.findViewWithTag("brand");ViewGroup r=(ViewGroup)brand.getParent();
  assertEquals("brand line only",1,r.getChildCount());assertEquals(1f,((LinearLayout.LayoutParams)brand.getLayoutParams()).weight,0f);assertEquals("row keeps its 52dp height",NativeUi.dp(a,52),r.getMinimumHeight());
 }
}
