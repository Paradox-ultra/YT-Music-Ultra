package ultra.downloads;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.util.*;
/** DL21: match device songs to playlist tracks by song name; artist only breaks ties. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class LocalAudioNameMatchTest {
 static Catalog.Track t(String id,String title,String artist){return new Catalog.Track(id,title,artist,"");}
 @Test public void catalogSuffixesAndLocalTagVariationsStillMatchByName(){
  List<Catalog.Track> tracks=Arrays.asList(t("aaaaaaaaaa1","Kesariya (From \"Brahmastra\")","Arijit Singh, Pritam"));
  assertEquals("aaaaaaaaaa1",LocalAudio.match("Kesariya.mp3","Kesariya","Arijit Singh",tracks));
  assertEquals("aaaaaaaaaa1",LocalAudio.match("Kesariya.mp3","Kesariya","",tracks));
  assertEquals("aaaaaaaaaa1",LocalAudio.match("02 - Kesariya - Arijit Singh.mp3","","",tracks));
  assertEquals("aaaaaaaaaa1",LocalAudio.match("Kesariya Official Video.mp3","","",tracks));
  assertEquals("aaaaaaaaaa1",LocalAudio.match("Arijit Singh - Kesariya (Official Audio).mp3","","",tracks));}
 @Test public void uniqueSongNameMatchesEvenWhenArtistTagIsMessy(){
  List<Catalog.Track> tracks=Arrays.asList(t("bbbbbbbbb22","Apna Bana Le","Arijit Singh, Sachin-Jigar"));
  assertEquals("bbbbbbbbb22",LocalAudio.match("Apna Bana Le.mp3","Apna Bana Le","Various Artists",tracks));}
 @Test public void sameTitleDifferentSongsNeedTheArtistTiebreak(){
  List<Catalog.Track> tracks=Arrays.asList(t("ccccccccc33","Pal","Arijit Singh"),t("ddddddddd44","Pal","KK"));
  assertEquals("ddddddddd44",LocalAudio.match("Pal.mp3","Pal","KK",tracks));
  assertNull(LocalAudio.match("Pal.mp3","Pal","",tracks));
  assertNull(LocalAudio.match("Pal.mp3","","",tracks));}
 @Test public void realVersionsNeverMatchTheOriginalSong(){
  List<Catalog.Track> tracks=Arrays.asList(t("eeeeeeeee55","Pal","Arijit Singh"));
  assertNull(LocalAudio.match("Pal.mp3","Pal (Remix)","Arijit Singh",tracks));
  assertNull(LocalAudio.match("Pal Remix DJ.mp3","","",tracks));
  assertNull(LocalAudio.match("Pal (Live).mp3","","",tracks));}
 @Test public void exactIdentityStillWinsOverEverything(){
  List<Catalog.Track> tracks=Arrays.asList(t("fffffffff66","Pal","Arijit Singh"));
  assertEquals("fffffffff66",LocalAudio.match("Anything [fffffffff66].mp3","Other","Else",tracks));}
 @Test public void unrelatedSongNeverMatches(){
  List<Catalog.Track> tracks=Arrays.asList(t("ggggggggg77","Kesariya","Arijit Singh"));
  assertNull(LocalAudio.match("Tum Hi Ho.mp3","Tum Hi Ho","Arijit Singh",tracks));
  assertNull(LocalAudio.match("random file.mp3","","",tracks));}
}
