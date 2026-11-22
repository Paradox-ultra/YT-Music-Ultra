package ultra.downloads;
import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import android.app.Activity;import java.nio.file.*;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=28)
public class LibraryEvidenceTest {
 Activity a;@Before public void setup(){a=Robolectric.buildActivity(Activity.class).setup().get();app.revanced.extension.shared.utils.Utils.testContext=a;a.getFileStreamPath(LibraryEvidence.FILE).delete();LibraryEvidence.latest="none";}
 @Test public void privateFileContainsOnlySchema()throws Exception{String schema="library-schema-only-v1\nbqtu.i:bjue\nstatus:captured\n";LibraryEvidence.store(schema);assertTrue(a.getFileStreamPath(LibraryEvidence.FILE).exists());assertEquals(schema,new String(Files.readAllBytes(a.getFileStreamPath(LibraryEvidence.FILE).toPath()),"UTF-8"));assertTrue(Diagnostics.text(a).contains(schema));}
 @Test public void urlAndSpacesRejected(){LibraryEvidence.store("https://private.invalid/token");assertFalse(a.getFileStreamPath(LibraryEvidence.FILE).exists());LibraryEvidence.store("Private user title");assertFalse(a.getFileStreamPath(LibraryEvidence.FILE).exists());}
}
