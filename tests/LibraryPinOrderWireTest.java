import app.revanced.extension.shared.utils.Utils;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import org.junit.*;import static org.junit.Assert.*;
import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import android.app.*;import java.util.*;
import ultra.downloads.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class LibraryPinOrderWireTest {
 Activity c;
 @Before public void setup(){c=Robolectric.buildActivity(Activity.class).setup().get();Utils.testContext=c;c.getSharedPreferences("ultra_library_pins",0).edit().clear().commit();}
 static bqtu row(String id){bizw e=(bizw)bizw.a.createBuilder().instance;e.b=1;e.c=id;bhbf b=(bhbf)bjue.a.createBuilder();b.e(BrowseEndpointOuterClass.browseEndpoint,e);bqtu r=(bqtu)bqtu.a.createBuilder().instance;r.i=(bjue)b.build();r.e=UltraRadioBridge.text("T");r.g=UltraRadioBridge.text("S");r.h=UltraRadioBridge.text("x");r.c=btdg.a;r.b=1|4|16|64;return r;}
 static byte[] concat(byte[] a,byte[] b){byte[] out=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;}
 static byte[] withBadge(byte[] rowBytes){byte[] textRun=HeaderCommands.field(1,HeaderCommands.text("Auto playlist"));byte[] pinRun=HeaderCommands.field(9,HeaderCommands.field(1,LibraryPinOrder.PIN_ICON));byte[] subtitle=concat(HeaderCommands.field(3,textRun),HeaderCommands.field(3,pinRun));return concat(rowBytes,HeaderCommands.field(60,subtitle));}
 static byte[] section(byte[]... rows){byte[] out=new byte[0];for(byte[] r:rows)out=concat(out,HeaderCommands.field(7,r));return out;}
 static int idx(byte[] hay,String s){byte[] n=HeaderCommands.text(s);outer:for(int i=0;i<=hay.length-n.length;i++){for(int j=0;j<n.length;j++)if(hay[i+j]!=n[j])continue outer;return i;}return -1;}
 @Test public void keyMapping(){assertEquals("LM",LibraryPinOrder.key("VLLM"));assertEquals("PLzebra",LibraryPinOrder.key("VLPLzebra"));assertEquals("ULTRA_RADIO",LibraryPinOrder.key("FEmusic_ultra_radio"));assertEquals("UCsinger",LibraryPinOrder.key("UCsinger"));assertNull(LibraryPinOrder.key("garbage"));assertNull(LibraryPinOrder.key(null));}
 @Test public void defaultPinsKeepNativeOrderAndBadge(){byte[] lm=withBadge(row("VLLM").toByteArray()),radio=row("FEmusic_ultra_radio").toByteArray(),zebra=row("VLPLzebra").toByteArray(),apple=row("VLPLapple").toByteArray();byte[] in=section(lm,radio,zebra,apple);byte[] out=LibraryPinOrder.reorder(in,c);assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"));assertTrue(idx(out,"VLLM")<idx(out,"VLPLzebra"));assertTrue(idx(out,"yt_outline_pin_vd_theme_24")>=0);}
 @Test public void unpinnedLikedDropsBelowRadioAndLosesBadge(){byte[] lm=withBadge(row("VLLM").toByteArray()),radio=row("FEmusic_ultra_radio").toByteArray(),zebra=row("VLPLzebra").toByteArray();PinManager.setPinned(c,"LM",false);byte[] out=LibraryPinOrder.reorder(section(lm,radio,zebra),c);assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"));assertTrue(idx(out,"VLPLzebra")<idx(out,"VLLM"));assertEquals(-1,idx(out,"yt_outline_pin_vd_theme_24"));assertTrue(idx(out,"Auto playlist")>=0);}
 @Test public void userPinnedPlaylistMovesUp(){byte[] lm=row("VLLM").toByteArray(),radio=row("FEmusic_ultra_radio").toByteArray(),zebra=row("VLPLzebra").toByteArray(),apple=row("VLPLapple").toByteArray();PinManager.setPinned(c,"PLapple",true);byte[] out=LibraryPinOrder.reorder(section(lm,radio,zebra,apple),c);assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"));assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLPLapple"));assertTrue(idx(out,"VLPLapple")<idx(out,"VLPLzebra"));}
 @Test public void orderFollowsLatestPinState(){PinManager.setPinned(c,"LM",false);PinManager.setPinned(c,"LM",true);byte[] lm=row("VLLM").toByteArray(),radio=row("FEmusic_ultra_radio").toByteArray(),zebra=row("VLPLzebra").toByteArray();byte[] out=LibraryPinOrder.reorder(section(zebra,lm,radio),c);assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"));assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLPLzebra"));}
 @Test public void elementsWrappedRowsReorderAndStripBadge(){
  byte[] lm=withBadge(row("VLLM").toByteArray()), radio=row("FEmusic_ultra_radio").toByteArray();
  for(int i=0;i<12;i++){lm=HeaderCommands.field(123456+i,lm);radio=HeaderCommands.field(123456+i,radio);}
  PinManager.setPinned(c,"LM",false);
  byte[] out=LibraryPinOrder.reorder(section(lm,radio),c);
  assertTrue(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"));
  assertEquals(-1,idx(out,"yt_outline_pin_vd_theme_24"));
 }
}
