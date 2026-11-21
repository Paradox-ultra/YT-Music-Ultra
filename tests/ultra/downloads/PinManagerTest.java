package ultra.downloads;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class PinManagerTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("ultra_library_pins", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void pinPersistenceUsingStableNativeId() {
        String stableId = "PLcustomPlaylist123";
        assertFalse(PinManager.isPinned(context, stableId));

        PinManager.setPinned(context, stableId, true);
        assertTrue(PinManager.isPinned(context, stableId));

        // VL prefix strip check (key normalization)
        assertTrue(PinManager.isPinned(context, "VL" + stableId));

        // Toggle
        boolean next = PinManager.togglePin(context, stableId);
        assertFalse(next);
        assertFalse(PinManager.isPinned(context, stableId));
    }

    @Test
    public void pinnedOrderPreservesLikedMusicAndRadioWhileNotAlphabetizingRest() {
        LinkedHashMap<String, String> nativeOrder = new LinkedHashMap<>();
        nativeOrder.put("LM", "Liked Music");
        nativeOrder.put("PLzebra", "Zebra Beats");
        nativeOrder.put("PLapple", "Apple Acoustic");
        nativeOrder.put("PLmiddle", "Middle Melodies");
        nativeOrder.put("PLdisco", "Disco Fever");

        // Pin "PLdisco" and "PLmiddle"
        PinManager.setPinned(context, "PLdisco", true);
        PinManager.setPinned(context, "PLmiddle", true);

        Map<String, String> ordered = PinManager.applyPinnedOrder(context, nativeOrder);
        List<String> keys = new ArrayList<>(ordered.keySet());

        assertEquals("LM", keys.get(0)); // Liked Music remains first!
        assertEquals("PLdisco", keys.get(1)); // Pinned first
        assertEquals("PLmiddle", keys.get(2)); // Pinned second
        assertEquals("PLzebra", keys.get(3)); // Unpinned keeps native order (Zebra before Apple, NOT alphabetized!)
        assertEquals("PLapple", keys.get(4));
    }

    @Test
    public void unpinnedReturnsIdenticalNativeOrder() {
        LinkedHashMap<String, String> nativeOrder = new LinkedHashMap<>();
        nativeOrder.put("LM", "Liked Music");
        nativeOrder.put("PLzebra", "Zebra Beats");
        nativeOrder.put("PLapple", "Apple Acoustic");

        Map<String, String> ordered = PinManager.applyPinnedOrder(context, nativeOrder);
        List<String> keys = new ArrayList<>(ordered.keySet());

        assertEquals(Arrays.asList("LM", "PLzebra", "PLapple"), keys);
    }

    @Test
    public void likedMusicIsPinnedByDefaultAndUnpinMovesItIntoNativeOrder() {
        assertTrue(PinManager.isPinned(context, "LM"));
        assertTrue(PinManager.isPinned(context, "VLLM"));
        assertTrue(PinManager.getPinnedList(context).contains("LM"));

        LinkedHashMap<String, String> nativeOrder = new LinkedHashMap<>();
        nativeOrder.put("LM", "Liked Music");
        nativeOrder.put("PLzebra", "Zebra Beats");
        nativeOrder.put("PLapple", "Apple Acoustic");

        // Default: Liked Music first
        assertEquals("LM", new ArrayList<>(PinManager.applyPinnedOrder(context, nativeOrder).keySet()).get(0));

        // Unpin: it keeps its native slot instead of being forced to the top
        assertFalse(PinManager.togglePin(context, "VLLM"));
        assertFalse(PinManager.isPinned(context, "LM"));
        PinManager.setPinned(context, "PLapple", true);
        java.util.List<String> keys = new ArrayList<>(PinManager.applyPinnedOrder(context, nativeOrder).keySet());
        assertEquals(java.util.Arrays.asList("PLapple", "LM", "PLzebra"), keys);

        // Re-pin restores it to the top
        assertTrue(PinManager.togglePin(context, "LM"));
        keys = new ArrayList<>(PinManager.applyPinnedOrder(context, nativeOrder).keySet());
        assertEquals("LM", keys.get(0));
        // State survives re-read (persistence)
        assertTrue(PinManager.isPinned(context, "LM"));
    }

    @Test
    public void radioStyleIdsCanBePinned() {
        assertFalse(PinManager.isPinned(context, "RDAMVMradio123"));
        PinManager.setPinned(context, "RDAMVMradio123", true);
        assertTrue(PinManager.isPinned(context, "RDAMVMradio123"));
        PinManager.setPinned(context, "RDAMVMradio123", false);
        assertFalse(PinManager.isPinned(context, "RDAMVMradio123"));
    }
}