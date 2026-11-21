package ultra.downloads;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class PerformanceOptimizationTest {

    @Test
    public void libraryRowsPruningSkipsBranchesWithoutLikedSongs() {
        // Construct protobuf wire bytes where branch A contains "Unrelated Playlist",
        // and branch B contains "Liked Songs".
        byte[] fieldUnrelated = HeaderCommands.field(2, HeaderCommands.text("Kishore Kumar Classics"));
        byte[] fieldLikedSongs = HeaderCommands.field(2, HeaderCommands.text("Liked Songs, Auto playlist • 0 tracks"));

        byte[] combined = new byte[fieldUnrelated.length + fieldLikedSongs.length];
        System.arraycopy(fieldUnrelated, 0, combined, 0, fieldUnrelated.length);
        System.arraycopy(fieldLikedSongs, 0, combined, fieldUnrelated.length, fieldLikedSongs.length);

        final AtomicInteger predicateEvaluations = new AtomicInteger(0);
        Predicate<byte[]> countingPredicate = new Predicate<byte[]>() {
            @Override
            public boolean test(byte[] bytes) {
                predicateEvaluations.incrementAndGet();
                return true; // remove candidate
            }
        };

        byte[] cleaned = LibraryRows.clean(combined, countingPredicate);
        assertNotNull(cleaned);
        // Only the candidate containing "Liked Songs" should trigger predicate test!
        // Unrelated branch must be pruned before recursing!
        assertEquals("Predicate must only be invoked for branches containing candidate bytes", 1, predicateEvaluations.get());
        assertTrue("Unrelated playlist bytes must be preserved intact", RadioWire.contains(cleaned, HeaderCommands.text("Kishore Kumar Classics")));
    }

    @Test
    public void regexPrecompilationMatchesAccurately() {
        // Test exact Liked Songs title
        assertTrue(LibraryViewClean.empty(Arrays.asList("Liked Songs", "5 tracks")));
        assertTrue(LibraryViewClean.empty(Arrays.asList("Liked Songs, Auto playlist • 12 tracks")));
        assertTrue(LibraryViewClean.empty(Arrays.asList("Liked Songs\nAuto playlist • 100 tracks")));

        // Test non-matching official Liked Music or user playlist
        assertFalse(LibraryViewClean.empty(Arrays.asList("Liked Music", "100 tracks")));
        assertFalse(LibraryViewClean.empty(Arrays.asList("Liked Songs Forever", "5 tracks")));
        assertFalse(LibraryViewClean.empty(Arrays.asList("My Liked Songs", "10 tracks")));
        assertFalse(LibraryViewClean.empty(Arrays.asList("Liked Songs"))); // Header without metadata
    }
}
