package ultra.downloads;

import android.app.Activity;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SelectionModeTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(android.R.style.Theme_Material_NoActionBar);
        app.revanced.extension.shared.utils.Utils.testContext = context;
    }

    @Test
    public void selectionToggleAndCount() {
        SelectionState state = new SelectionState();
        assertFalse(state.isActive());
        assertEquals(0, state.count());

        // Enter selection mode on long press
        state.enter("Songs", "", "video1111111");
        assertTrue(state.isActive());
        assertEquals(1, state.count());
        assertTrue(state.isSelected("video1111111"));

        // Toggle another item on
        state.toggle("video2222222");
        assertEquals(2, state.count());
        assertTrue(state.isSelected("video2222222"));

        // Toggle first item off
        state.toggle("video1111111");
        assertEquals(1, state.count());
        assertFalse(state.isSelected("video1111111"));

        // Toggle last item off -> auto-exits mode
        state.toggle("video2222222");
        assertEquals(0, state.count());
        assertFalse(state.isActive());
    }

    @Test
    public void selectAllAndClear() {
        SelectionState state = new SelectionState();
        state.enter("Songs", "", "songA");
        List<String> allKeys = Arrays.asList("songA", "songB", "songC");
        state.selectAll(allKeys);

        assertEquals(3, state.count());
        assertTrue(state.isSelected("songA"));
        assertTrue(state.isSelected("songB"));
        assertTrue(state.isSelected("songC"));

        state.clear();
        assertEquals(0, state.count());
        state.exit();
        assertFalse(state.isActive());
    }

    @Test
    public void navigationResetsSelection() {
        SelectionState state = new SelectionState();
        state.enter("Songs", "", "song1");
        assertTrue(state.isActive());

        // Navigating to Artists tab must reset selection
        boolean reset = state.checkNavigationReset("Artists", "");
        assertTrue(reset);
        assertFalse(state.isActive());
        assertEquals(0, state.count());

        // Entering in a group detail, then navigating back
        state.enter("Playlists", "PL123", "songX");
        assertTrue(state.isActive());
        reset = state.checkNavigationReset("Playlists", "");
        assertTrue(reset);
        assertFalse(state.isActive());
    }

    @Test
    public void groupExpansionWithDeduplicationAndOrder() {
        SelectionState state = new SelectionState();

        Library.Item i1 = new Library.Item();
        i1.id = "trackAAAAA1"; i1.title = "Song A"; i1.artist = "Artist One"; i1.album = "Album Alpha"; i1.state = "done";
        Library.Item i2 = new Library.Item();
        i2.id = "trackBBBBB2"; i2.title = "Song B"; i2.artist = "Artist One"; i2.album = "Album Beta"; i2.state = "done";
        Library.Item i3 = new Library.Item();
        i3.id = "trackCCCCC3"; i3.title = "Song C"; i3.artist = "Artist Two"; i3.album = "Album Alpha"; i3.state = "done";

        List<Library.Item> all = Arrays.asList(i1, i2, i3);

        // Expand Artist selection
        state.enter("Artists", "", "Artist One");
        ArrayList<Catalog.Track> expanded = state.expandToTracks(context, all);
        assertEquals(2, expanded.size());
        assertEquals("trackAAAAA1", expanded.get(0).id);
        assertEquals("trackBBBBB2", expanded.get(1).id);

        // Expand Album selection with duplicate tracks (deduplication check)
        state.enter("Albums", "", "Album Alpha");
        ArrayList<Catalog.Track> albumExpanded = state.expandToTracks(context, all);
        assertEquals(2, albumExpanded.size());
        assertEquals("trackAAAAA1", albumExpanded.get(0).id);
        assertEquals("trackCCCCC3", albumExpanded.get(1).id);
    }

    @Test
    public void importedFileProtectionOnDelete() throws Exception {
        // Create actual synthetic file
        File dir = new File(context.getFilesDir(), "test_audio");
        dir.mkdirs();
        File deviceFile = new File(dir, "device_song.mp3");
        try (FileOutputStream fos = new FileOutputStream(deviceFile)) {
            fos.write(new byte[]{1, 2, 3, 4});
        }
        assertTrue(deviceFile.exists());

        Library.Item item = new Library.Item();
        item.id = "imported1234";
        item.title = "Local Device Song";
        item.uri = deviceFile.getAbsolutePath();
        item.imported = true; // Sideloaded / existing device media

        // Verification: Storage.delete throws exception or skips deleting imported files
        try {
            Storage.delete(context, item);
            fail("Expected exception when attempting to delete imported device file");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("existing device file"));
        }
        // File MUST still exist on disk!
        assertTrue("Imported device file must NOT be deleted from disk", deviceFile.exists());

        // Cleanup
        deviceFile.delete();
        dir.delete();
    }

    @Test
    public void incompletePlaylistDetection() {
        String playlistId = "testIncompletePL";
        List<Catalog.Track> tracks = Arrays.asList(
            new Catalog.Track("v1111111111", "Track 1", "Artist", "Album"),
            new Catalog.Track("v2222222222", "Track 2", "Artist", "Album")
        );

        PlaylistIndex.record(playlistId, tracks, false);
        assertFalse(PlaylistIndex.complete(context, playlistId));
        assertEquals(2, PlaylistIndex.total(context, playlistId));

        // When completed
        PlaylistIndex.record(playlistId, tracks, true);
        assertTrue(PlaylistIndex.complete(context, playlistId));
    }
}
