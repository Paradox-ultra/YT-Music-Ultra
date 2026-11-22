package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.widget.*;import java.io.*;import java.util.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28) @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DL29CoversTest {
 Context c;
 @Before public void setup()throws Exception{c=Robolectric.buildActivity(Activity.class).setup().get();c.setTheme(android.R.style.Theme_Material_NoActionBar);app.revanced.extension.shared.utils.Utils.testContext=c;Art.network=false;Art.memory.evictAll();synchronized(Art.failures){Art.failures.clear();}synchronized(Art.waiting){Art.waiting.clear();}}
 Library.Item item(String id){Library.Item i=new Library.Item();i.id=id;i.title=id;return i;}
 Bitmap render(Drawable d,int size){Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);d.setBounds(0,0,size,size);d.draw(cv);return b;}
 long bright(Bitmap b){long n=0;for(int x=0;x<b.getWidth();x+=3)for(int y=0;y<b.getHeight();y+=3){int px=b.getPixel(x,y);if(Color.red(px)+Color.green(px)+Color.blue(px)>240)n++;}return n;}
 boolean same(Bitmap a,Bitmap b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;for(int x=0;x<a.getWidth();x+=2)for(int y=0;y<a.getHeight();y+=2)if(a.getPixel(x,y)!=b.getPixel(x,y))return false;return true;}

 @Test public void recordingsGetTheirOwnCoverKind(){
  Library.Item voice=item("x");voice.imported=true;voice.title="Voice 023.m4a";voice.uri="content://media/external/audio/media/11";
  Library.Item song=item("y");song.imported=true;song.title="Kesariya";song.uri="content://media/external/audio/media/12";
  Library.Item online=item("abcdefghijk");
  assertTrue(Covers.isRecording(voice));assertFalse(Covers.isRecording(song));assertFalse(Covers.isRecording(online));
  assertEquals("recording",Covers.kindOf(voice));assertEquals("song",Covers.kindOf(song));
  Library.Item rec=item("z");rec.imported=true;rec.title="Recording 12 Oct";assertTrue(Covers.isRecording(rec));
 }
 @Test public void placeholderCoversAreDrawnAndDistinct(){
  Bitmap song=render(new Covers.Cover("song"),120),rec=render(new Covers.Cover("recording"),120),top=render(new Covers.Cover("topsongs"),120),pl=render(new Covers.Cover("playlist"),120),artist=render(new Covers.Cover("artist"),120);
  assertTrue("song cover paints a visible glyph",bright(song)>10);
  assertTrue(bright(rec)>10);assertTrue(bright(top)>10);assertTrue(bright(pl)>10);assertTrue(bright(artist)>10);
  assertFalse(same(song,rec));assertFalse(same(song,top));assertFalse(same(song,artist));assertFalse(same(rec,top));
 }
 @Test public void everySongIdHasADirectThumbnailUrl(){
  Library.Item i=item("abcdefghijk");
  List<String> urls=Art.artUrls(i);
  assertEquals(1,urls.size());assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",urls.get(0));
  i.artUrl="https://i.ytimg.com/vi/abcdefghijk/mqdefault.jpg";urls=Art.artUrls(i);
  assertEquals(2,urls.size());assertEquals(i.artUrl,urls.get(0));
  Library.Item local=item("playlist_ABC");local.artUrl="";assertTrue(Art.artUrls(local).isEmpty());
  assertTrue(Art.allowed("https://i.ytimg.com/vi/x/hqdefault.jpg"));assertFalse(Art.allowed("https://evil.example.com/x.jpg"));assertFalse(Art.allowed("http://i.ytimg.com/x.jpg"));
 }
 @Test public void missingArtPlaceholderIsDesignedCoverNotAppIcon(){
  Library.Item i=item("zzzzzzzzzzz");ImageView image=new ImageView(c);int jobs=Art.started.get();
  Art.load(c,i,image);
  assertTrue(image.getDrawable() instanceof Covers.Cover);
  assertEquals(jobs,Art.started.get());
  Library.Item voice=item("qqqqqqqqqqq");voice.imported=true;voice.title="Voice memo 4";voice.uri="content://media/external/audio/media/31";
  Art.load(c,voice,new ImageView(c));
 }
 @Test public void topSongsNamesMatch(){assertTrue(Covers.isTopSongs("Top Songs"));assertTrue(Covers.isTopSongs("My top songs 2026"));assertFalse(Covers.isTopSongs("Road trip"));assertFalse(Covers.isTopSongs(null));assertFalse(Covers.isTopSongs("Toppers"));}
 @Test public void collageNeedsRealMemberArtwork()throws Exception{
  Library.Item a=item("aaaaaaaaaaa"),b=item("bbbbbbbbbbb");
  assertNull(Covers.collage(Arrays.asList(a,b),120));
  for(Library.Item i:new Library.Item[]{a,b}){Bitmap art=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new Canvas(art).drawColor(i==a?0xff2040c0:0xffc02040);File f=Art.file(c,i.id);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){art.compress(Bitmap.CompressFormat.PNG,100,out);}art.recycle();}
  Bitmap collage=Covers.collage(Arrays.asList(a,b),120);
  assertNotNull(collage);assertEquals(120,collage.getWidth());assertEquals(120,collage.getHeight());
  assertTrue("member art visible in collage",Color.blue(collage.getPixel(30,30))>150||Color.red(collage.getPixel(30,30))>150);
  Art.file(c,a.id).delete();Art.file(c,b.id).delete();
 }
 @Test public void playlistCoverFallsBackToEmblemByName(){
  Library.Item none=item("qqqqqqqqqq1");
  Drawable top=Covers.playlist(c,"Top Songs",Arrays.asList(none),120);
  assertTrue(top instanceof Covers.Cover);
  Drawable generic=Covers.playlist(c,"Road trip",Arrays.asList(none),120);
  assertTrue(generic instanceof Covers.Cover);
  assertFalse(same(render(top,120),render(generic,120)));
 }
}
