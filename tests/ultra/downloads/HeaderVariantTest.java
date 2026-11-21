package ultra.downloads;
import org.junit.*;import static org.junit.Assert.*;import java.util.*;import java.nio.file.*;
public class HeaderVariantTest {
 byte[] field(int n,byte[] b){return HeaderCommands.field(n,b);}byte[] join(byte[]... a){java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();for(byte[] b:a)o.write(b,0,b.length);return o.toByteArray();}
 @Test public void ownerNativeHeaderKeepsEditPlayDownloadAndUsesExactShuffle()throws Exception{for(String id:new String[]{"PLownPlaylist","LM","OLAK5uy_album"}){
  byte[] ep=field(0x3239f4a,join(field(1,HeaderCommands.text(id)),field(4,HeaderCommands.text("observed-shuffle-params"))));
  byte[] candidate=join(field(2,HeaderCommands.iconNumber(194)),field(3,ep));byte[] share=join(field(2,HeaderCommands.iconNumber(65)),field(3,field(999,HeaderCommands.text("share"))),field(5,HeaderCommands.text("Share")));
  byte[] h=join(field(1,HeaderCommands.text(id)),field(2,HeaderCommands.text("Download")),field(3,HeaderCommands.text("Edit")),field(4,HeaderCommands.text("Play")),field(5,share));byte[] response=field(777,field(1,candidate));byte[] out=HeaderCommands.shuffle(h,response);assertFalse(Arrays.equals(h,out));assertTrue(new String(out,"UTF-8").contains("Edit"));assertTrue(new String(out,"UTF-8").contains("Download"));assertTrue(new String(out,"UTF-8").contains("Shuffle"));assertArrayEquals(ep,HeaderCommands.path(out,5,3));}}
 @Test public void unrelatedNativeHeaderIsUnchanged(){byte[] b=field(1,HeaderCommands.text("Artist"));assertSame(b,HeaderCommands.shuffle(b,b));}
}
