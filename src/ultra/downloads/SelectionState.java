package ultra.downloads;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * State machine and group-expansion logic for native-looking multi-selection mode.
 * Enforces section safety: navigating between tabs or groups resets selection so actions
 * never silently target an unintended section.
 * Group expansion preserves exact catalog metadata for missing songs (never fabricating title=id).
 */
public final class SelectionState {
    private boolean active;
    private String activeTab = "";
    private String activeGroup = "";
    private final LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();

    public boolean isActive() {
        return active;
    }

    public String getActiveTab() {
        return activeTab;
    }

    public String getActiveGroup() {
        return activeGroup;
    }

    public int count() {
        return selectedKeys.size();
    }

    public boolean isSelected(String key) {
        return active && selectedKeys.contains(key);
    }

    public void enter(String tab, String group, String initialKey) {
        active = true;
        activeTab = tab != null ? tab : "";
        activeGroup = group != null ? group : "";
        selectedKeys.clear();
        if (initialKey != null && !initialKey.isEmpty()) {
            selectedKeys.add(initialKey);
        }
    }

    public void toggle(String key) {
        if (!active || key == null || key.isEmpty()) return;
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
            if (selectedKeys.isEmpty()) {
                active = false;
            }
        } else {
            selectedKeys.add(key);
        }
    }

    public void selectAll(Collection<String> keys) {
        if (!active || keys == null) return;
        selectedKeys.addAll(keys);
    }

    public void clear() {
        selectedKeys.clear();
    }

    public void exit() {
        active = false;
        activeTab = "";
        activeGroup = "";
        selectedKeys.clear();
    }

    public boolean checkNavigationReset(String newTab, String newGroup) {
        if (!active) return false;
        String t = newTab != null ? newTab : "";
        String g = newGroup != null ? newGroup : "";
        if (!activeTab.equals(t) || !activeGroup.equals(g)) {
            exit();
            return true;
        }
        return false;
    }

    public Set<String> getSelectedKeys() {
        return Collections.unmodifiableSet(selectedKeys);
    }

    private static String albumKey(Library.Item i) {
        String alb = i.album.isEmpty() ? "Unknown album" : i.album;
        return i.artist.isEmpty() ? alb : alb + " • " + i.artist;
    }

    private static Catalog.Track findExactCatalogTrack(Context c, String videoId, String group) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return null;
        // 1. Check if it's in the current group playlist entry
        if (group != null && !group.isEmpty()) {
            if (group.startsWith("local:")) {
                try {
                    String name = group.substring(6);
                    JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
                    JSONArray arr = map.optJSONArray(name);
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject t = arr.optJSONObject(i);
                            if (t != null && videoId.equals(t.optString("id"))) {
                                return new Catalog.Track(videoId, t.optString("title", videoId), t.optString("artist", ""), t.optString("album", ""));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                JSONObject e = LikedPlaylists.entry(c, group);
                JSONArray a = e == null ? null : e.optJSONArray("tracks");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject t = a.optJSONObject(i);
                        if (t != null && videoId.equals(t.optString("id"))) {
                            return new Catalog.Track(videoId, t.optString("title", videoId), t.optString("artist", ""), "");
                        }
                    }
                }
            }
        }
        // 2. Check all catalog tracks
        for (Catalog.Track t : PlaylistIndex.tracks(c)) {
            if (videoId.equals(t.id)) return t;
        }
        // 3. Fallback to Library.get
        try {
            Library.Item item = Library.get(c, videoId);
            if (item != null) return new Catalog.Track(item.id, item.title, item.artist, item.album);
        } catch (Exception ignored) {}
        return null;
    }

    public ArrayList<Catalog.Track> expandToTracks(Context c, List<Library.Item> allItems) {
        ArrayList<Catalog.Track> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        if (!active || selectedKeys.isEmpty()) return result;

        if (!activeGroup.isEmpty()) {
            Map<String, Library.Item> localMap = new HashMap<>();
            for (Library.Item i : allItems) {
                if (i != null && i.id != null) localMap.put(i.id, i);
            }
            for (String key : selectedKeys) {
                String id = key.startsWith("missing:") ? key.substring(8) : key;
                if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                if (seenIds.add(id)) {
                    Library.Item item = localMap.get(id);
                    if (item != null) {
                        result.add(new Catalog.Track(item.id, item.title, item.artist, item.album));
                    } else {
                        Catalog.Track exact = findExactCatalogTrack(c, id, activeGroup);
                        if (exact != null) {
                            result.add(exact);
                        } else {
                            result.add(new Catalog.Track(id, id, "", ""));
                        }
                    }
                }
            }
            return result;
        }

        switch (activeTab) {
            case "Songs":
                Map<String, Library.Item> map = new HashMap<>();
                for (Library.Item i : allItems) {
                    if (i != null && i.id != null) map.put(i.id, i);
                }
                for (String key : selectedKeys) {
                    if (key.matches("[A-Za-z0-9_-]{11}") && seenIds.add(key)) {
                        Library.Item i = map.get(key);
                        if (i != null) {
                            result.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                        }
                    }
                }
                break;

            case "Artists":
                for (Library.Item i : allItems) {
                    if (i == null || i.id == null || !i.state.equals("done")) continue;
                    String artistKey = i.artist.isEmpty() ? "Unknown artist" : i.artist;
                    if (selectedKeys.contains(artistKey) && i.id.matches("[A-Za-z0-9_-]{11}") && seenIds.add(i.id)) {
                        result.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
                break;

            case "Albums":
                for (Library.Item i : allItems) {
                    if (i == null || i.id == null || !i.state.equals("done")) continue;
                    String albKey = albumKey(i);
                    if ((selectedKeys.contains(albKey) || selectedKeys.contains(i.album)) && i.id.matches("[A-Za-z0-9_-]{11}") && seenIds.add(i.id)) {
                        result.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
                break;

            case "Playlists":
                for (String playlistId : selectedKeys) {
                    ArrayList<Library.Item> list = PlaylistIndex.ordered(c, playlistId, allItems, true);
                    for (Library.Item item : list) {
                        if (item != null && item.id != null && item.id.matches("[A-Za-z0-9_-]{11}") && seenIds.add(item.id)) {
                            result.add(new Catalog.Track(item.id, item.title, item.artist, item.album));
                        }
                    }
                }
                break;
        }
        return result;
    }
}
