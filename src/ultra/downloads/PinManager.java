package ultra.downloads;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

/**
 * Manages persistent Library Pin / Unpin states using stable native IDs (e.g. playlist IDs).
 * Pinned items appear first in Library roster (retaining Liked Music and Radio at top),
 * while preserving the native order of all remaining playlists without alphabetizing.
 */
public final class PinManager {
    private static final String PREFS_NAME = "ultra_library_pins";
    private static final String KEY_PINNED = "pinned_ids";
    private static final String KEY_UNPINNED_DEFAULTS = "unpinned_default_ids";
    /** Library rows that start pinned until the user unpins them: Liked Music. The synthetic Radio row has no menu surface, so its default pin is effectively permanent and it always stays on top. */
    private static final java.util.List<String> DEFAULT_PINNED = java.util.Arrays.asList("LM", "ULTRA_RADIO");
    private static final Object LOCK = new Object();

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isPinned(Context c, String id) {
        if (id == null || id.isEmpty()) return false;
        id = PlaylistIndex.key(id);
        synchronized (LOCK) {
            LinkedHashSet<String> set = readStored(c);
            if (set.contains(id)) return true;
            return DEFAULT_PINNED.contains(id) && !getUnpinnedDefaults(c).contains(id);
        }
    }

    public static void setPinned(Context c, String id, boolean pinned) {
        if (id == null || id.isEmpty()) return;
        id = PlaylistIndex.key(id);
        synchronized (LOCK) {
            LinkedHashSet<String> set = readStored(c);
            LinkedHashSet<String> unpinned = getUnpinnedDefaults(c);
            boolean changed;
            if (pinned) {
                changed = set.add(id) | unpinned.remove(id);
            } else {
                changed = set.remove(id);
                if (DEFAULT_PINNED.contains(id)) changed |= unpinned.add(id);
            }
            if (changed) {
                savePinnedList(c, set);
                saveUnpinnedDefaults(c, unpinned);
                PlaylistIndex.invalidateCache();
            }
        }
    }

    public static boolean togglePin(Context c, String id) {
        if (id == null || id.isEmpty()) return false;
        id = PlaylistIndex.key(id);
        synchronized (LOCK) {
            boolean next = !isPinned(c, id);
            setPinned(c, id, next);
            return next;
        }
    }

    /** Effective pins: defaults the user never unpinned first, then explicit pins in order. */
    public static LinkedHashSet<String> getPinnedList(Context c) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        LinkedHashSet<String> stored = readStored(c);
        LinkedHashSet<String> unpinned = getUnpinnedDefaults(c);
        for (String id : DEFAULT_PINNED) {
            if (!unpinned.contains(id)) out.add(id);
        }
        for (String id : stored) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    private static LinkedHashSet<String> readStored(Context c) {
        return readIdList(prefs(c).getString(KEY_PINNED, "[]"));
    }

    private static LinkedHashSet<String> getUnpinnedDefaults(Context c) {
        return readIdList(prefs(c).getString(KEY_UNPINNED_DEFAULTS, "[]"));
    }

    private static LinkedHashSet<String> readIdList(String raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                String id = a.optString(i);
                if (!id.isEmpty()) set.add(id);
            }
        } catch (Exception ignored) {}
        return set;
    }

    private static void saveUnpinnedDefaults(Context c, LinkedHashSet<String> set) {
        JSONArray a = new JSONArray();
        for (String id : set) a.put(id);
        prefs(c).edit().putString(KEY_UNPINNED_DEFAULTS, a.toString()).apply();
    }

    private static void savePinnedList(Context c, LinkedHashSet<String> set) {
        JSONArray a = new JSONArray();
        for (String id : set) a.put(id);
        prefs(c).edit().putString(KEY_PINNED, a.toString()).apply();
    }

    public static Map<String, String> applyPinnedOrder(Context c, Map<String, String> nativeOrder) {
        if (nativeOrder == null || nativeOrder.isEmpty()) return nativeOrder;
        LinkedHashSet<String> pinned = getPinnedList(c);
        if (pinned.isEmpty()) return nativeOrder;

        LinkedHashMap<String, String> result = new LinkedHashMap<>();

        // 2. Pinned items in their stable native identity (Liked Music is simply a default pin)
        for (String pinId : pinned) {
            for (Map.Entry<String, String> entry : nativeOrder.entrySet()) {
                String key = PlaylistIndex.key(entry.getKey());
                if (pinId.equals(key) && !result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 3. Remaining unpinned items in original native order (never alphabetized!)
        for (Map.Entry<String, String> entry : nativeOrder.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Reorders repeated playlist items in protobuf InnerTube browse responses
     * so pinned playlists visibly appear at the top in the host's native Library presentation.
     */
    public static byte[] reorderRoster(byte[] b, Context c) {
        if (b == null || b.length > HeaderCommands.MAX || c == null) return b;
        LinkedHashSet<String> pinned = getPinnedList(c);
        if (pinned.isEmpty()) return b;
        try {
            int[] budget = {180000};
            return walkAndReorder(b, pinned, 0, budget);
        } catch (Throwable e) {
            return b;
        }
    }

    private static byte[] walkAndReorder(byte[] b, LinkedHashSet<String> pinned, int depth, int[] budget) {
        if (depth > 40 || --budget[0] < 0 || b == null) return b;
        List<HeaderCommands.F> fs;
        try {
            fs = HeaderCommands.fields(b);
        } catch (Exception e) {
            return b;
        }

        boolean modified = false;
        for (int i = 0; i < fs.size(); i++) {
            HeaderCommands.F f = fs.get(i);
            if (f.w == 2) {
                // Check for repeated items with the same field number
                int j = i;
                while (j < fs.size() && fs.get(j).n == f.n && fs.get(j).w == 2) {
                    j++;
                }
                int count = j - i;
                if (count > 1) {
                    Map<Integer, String> itemPlaylists = new HashMap<>();
                    for (int k = i; k < j; k++) {
                        Set<String> keys = new HashSet<>();
                        HeaderCommands.playlistKeys(fs.get(k).value, keys, 0, new int[]{1000});
                        for (String key : keys) {
                            itemPlaylists.put(k, key);
                            break;
                        }
                    }

                    if (itemPlaylists.size() >= 2) {
                        List<HeaderCommands.F> pinnedList = new ArrayList<>();
                        List<HeaderCommands.F> unpinned = new ArrayList<>();

                        for (String pinId : pinned) {
                            for (int k = i; k < j; k++) {
                                String pid = itemPlaylists.get(k);
                                if (pid != null && PlaylistIndex.key(pid).equals(PlaylistIndex.key(pinId))) {
                                    pinnedList.add(fs.get(k));
                                    break;
                                }
                            }
                        }

                        for (int k = i; k < j; k++) {
                            HeaderCommands.F item = fs.get(k);
                            if (pinnedList.contains(item)) {
                                // already added to pinnedList
                            } else {
                                unpinned.add(item);
                            }
                        }

                        List<HeaderCommands.F> reordered = new ArrayList<>();
                        reordered.addAll(pinnedList);
                        reordered.addAll(unpinned);

                        if (reordered.size() == count) {
                            for (int k = 0; k < count; k++) {
                                fs.set(i + k, reordered.get(k));
                            }
                            modified = true;
                            i = j - 1;
                            continue;
                        }
                    }
                }

                byte[] next = walkAndReorder(f.value, pinned, depth + 1, budget);
                if (next != f.value) {
                    f.raw = HeaderCommands.field(f.n, next);
                    f.value = next;
                    modified = true;
                }
            }
        }
        return modified ? HeaderCommands.encode(fs) : b;
    }
}
