import org.junit.*;import static org.junit.Assert.*;
import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class DebugRowKeyTest {
 static bqtu row(String id){bizw e=(bizw)bizw.a.createBuilder().instance;e.b=1;e.c=id;bhbf b=(bhbf)bjue.a.createBuilder();b.e(BrowseEndpointOuterClass.browseEndpoint,e);bqtu r=(bqtu)bqtu.a.createBuilder().instance;r.i=(bjue)b.build();r.e=UltraRadioBridge.text("T");r.g=UltraRadioBridge.text("S");r.h=UltraRadioBridge.text("x");r.c=btdg.a;r.b=1|4|16|64;return r;}
 @Test public void steps(){
  bqtu r=row("VLLM");byte[] bytes=r.toByteArray();
  Object ep0=r.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);assertTrue("direct read: "+(ep0==null?"null":ep0.getClass().getName()),ep0 instanceof bizw);
  Object parsed=bhbk.parseFrom(bqtu.a,bytes,ExtensionRegistryLite.getGeneratedRegistry());assertTrue("parsed type "+parsed.getClass().getName(),parsed instanceof bqtu);
  bqtu q=(bqtu)parsed;assertNotNull("q.i null",q.i);
  Object ep=q.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);assertNotNull("ep null",ep);assertTrue("ep type "+ep.getClass().getName(),ep instanceof bizw);
  assertEquals("VLLM",((bizw)ep).c);
  assertEquals("LM",ultra.downloads.LibraryPinOrder.key("VLLM"));
  assertEquals("LM",UltraRowKey.of(bytes));
 }
}
