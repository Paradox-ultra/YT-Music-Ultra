package ultra.downloads;

import android.content.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Private, read-only catalog membership. Never changes a remote playlist or device audio. */
public final class PlaylistIndex {
    static final Object LOCK = new Object();
    static final ExecutorService IO = Executors.newSingleThreadExecutor(), FOCUS = Executors.newSingleThreadExecutor();
    static final Set<String> requested = new HashSet<>();
    static volatile boolean syncing;
    static volatile int syncDone, syncTotal, syncFailed;
    static volatile String syncStatus = "Waiting for Library";
    static long lastSync;
    static boolean needsRetry = true;

    static String cachedRaw;
    static JSONObject cached;

    // Immutable caching layer on revision
    private static int cachedRevision = -1;
    private static Map<String, String> cachedGroups = null;
    private static final Map<String, Set<String>> cachedMembers = new HashMap<>();

    public static void invalidateCache() {
        synchronized (LOCK) {
            cachedRevision = -1;
            cachedGroups = null;
            cachedMembers.clear();
        }
    }

    static JSONObject read(Context c) {
        synchronized (LOCK) {
            String raw = Library.prefs(c).getString("local_playlist_index", "{}");
            if (cached == null || !raw.equals(cachedRaw)) {
                try {
                    cached = new JSONObject(raw);
                    cachedRaw = raw;
                } catch (Exception e) {
                    cached = new JSONObject();
                    cachedRaw = raw;
                }
            }
            return cached;
        }
    }

    static JSONObject copy(Context c) throws JSONException {
        return new JSONObject(Library.prefs(c).getString("local_playlist_index", "{}"));
    }

    static int revision(Context c) {
        return Library.prefs(c).getString("local_playlist_index", "{}").hashCode() +
               Library.prefs(c).getString("library_playlist_order", "[]").hashCode() +
               Library.prefs(c).getString("native_playlist_order", "[]").hashCode() +
               Library.prefs(c).getString("ultra_local_playlists", "{}").hashCode() +
               Library.prefs(c).getString(LocalPlaylistCopies.PREF, "{}").hashCode() +
               PinManager.getPinnedList(c).hashCode();
    }

    static String key(String id) {
        return id != null && id.startsWith("VL") ? id.substring(2) : id;
    }

    public static void register(String id, String name) {
        register(id, name, "");
    }

    public static void register(String id, String name, String art) {
        try {
            if (id == null || !id.startsWith("VL") || id.length() < 4) return;
            Context c = Hooks.context();
            synchronized (LOCK) {
                JSONObject all = copy(c), entry = all.optJSONObject(key(id));
                if (entry == null) entry = new JSONObject();
                if (name != null && !name.isEmpty()) entry.put("name", name);
                if (art != null && !art.isEmpty() && !art.equals(entry.optString("art"))) {
                    entry.put("art", art);
                    Art.invalidate(c, "playlist_" + key(id));
                }
                all.put(key(id), entry);
                Library.prefs(c).edit().putString("local_playlist_index", all.toString()).apply();
                invalidateCache();
            }
        } catch (Exception ignored) {}
    }

    public static void registerNative(String id, String name, String art) {
        register(id, name, art);
        try {
            Context c = Hooks.context();
            synchronized (LOCK) {
                JSONObject all = copy(c), e = all.optJSONObject(key(id));
                if (e == null) return;
                if (name != null && !name.isEmpty()) e.put("nativeName", name);
                if (art != null && !art.isEmpty()) e.put("nativeArt", art);
                all.put(key(id), e);
                Library.prefs(c).edit().putString("local_playlist_index", all.toString()).apply();
                invalidateCache();
            }
        } catch (Exception ignored) {}
    }

    public static void record(String id, java.util.List<Catalog.Track> tracks, boolean complete) {
        try {
            Context c = Hooks.context();
            synchronized (LOCK) {
                JSONObject all = copy(c), e = all.optJSONObject(key(id));
                if (e == null) e = new JSONObject();
                JSONArray rows = new JSONArray();
                for (Catalog.Track t : tracks) {
                    rows.put(new JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist));
                }
                if (!complete && (e.optBoolean("complete") || e.optJSONArray("tracks") != null && e.optJSONArray("tracks").length() > rows.length())) {
                    e.put("error", "");
                    all.put(key(id), e);
                    Library.prefs(c).edit().putString("local_playlist_index", all.toString()).apply();
                    invalidateCache();
                    return;
                }
                e.put("tracks", rows).put("complete", complete).put("synced", System.currentTimeMillis()).put("error", "");
                if (!e.has("name")) e.put("name", key(id));
                all.put(key(id), e);
                Library.prefs(c).edit().putString("local_playlist_index", all.toString()).apply();
                invalidateCache();
            }
        } catch (Exception ignored) {}
    }

    static Map<String, String> localGroups(Context c) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            JSONObject obj = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String name = it.next();
                out.put("local:" + name, "Local • " + name);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static Map<String, String> groups(Context c) {
        int rev = revision(c);
        synchronized (LOCK) {
            if (cachedGroups != null && cachedRevision == rev) {
                return cachedGroups;
            }
            Map<String, String> base = LikedPlaylists.groups(read(c), rawGroups(c));
            Map<String, String> withPins = PinManager.applyPinnedOrder(c, LocalPlaylistCopies.groups(c, base));
            LinkedHashMap<String, String> merged = new LinkedHashMap<>(withPins);
            // Integrate local playlists visibly
            merged.putAll(localGroups(c));
            merged.put(UnmatchedDownloads.ID, UnmatchedDownloads.NAME);
            cachedGroups = Collections.unmodifiableMap(merged);
            cachedRevision = rev;
            cachedMembers.clear();
            return cachedGroups;
        }
    }

    static Map<String, String> rawGroups(Context c) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        JSONObject all = read(c);
        JSONArray order;
        try {
            order = new JSONArray(Library.prefs(c).getString("library_playlist_order", "[]"));
        } catch (Exception e) {
            order = new JSONArray();
        }
        for (int n = 0; n < order.length(); n++) {
            String id = order.optString(n);
            JSONObject e = all.optJSONObject(id);
            if (e != null) out.put(id, e.optString("name", "Playlist"));
        }
        if (order.length() == 0 && !Library.prefs(c).getBoolean("library_roster_complete", false)) {
            Iterator<String> it = all.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONObject e = all.optJSONObject(id);
                if (e != null && e.has("name")) out.put(id, id.equals(e.optString("name", id)) ? "Playlist" : e.optString("name", "Playlist"));
            }
        }
        return nativeSorted(c, out);
    }

    public static void nativeOrder(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        try {
            Context c = Hooks.context();
            synchronized (LOCK) {
                JSONArray a = new JSONArray();
                Set<String> seen = new HashSet<>();
                for (String id : ids) {
                    id = key(id);
                    if (id != null && !id.isEmpty() && seen.add(id)) a.put(id);
                }
                String value = a.toString();
                if (!value.equals(Library.prefs(c).getString("native_playlist_order", "[]"))) {
                    Library.prefs(c).edit().putString("native_playlist_order", value).apply();
                    invalidateCache();
                }
            }
        } catch (Exception ignored) {}
    }

    static Map<String, String> nativeSorted(Context c, Map<String, String> roster) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(Library.prefs(c).getString("native_playlist_order", "[]"));
            for (int n = 0; n < a.length(); n++) {
                String id = a.optString(n);
                if (roster.containsKey(id)) out.put(id, roster.get(id));
            }
        } catch (Exception ignored) {}
        for (Map.Entry<String, String> e : roster.entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    static void roster(Context c, List<Catalog.Playlist> rows) throws Exception {
        synchronized (LOCK) {
            JSONArray order = new JSONArray();
            JSONObject all = copy(c);
            for (Catalog.Playlist p : rows) {
                String id = key(p.id);
                JSONObject e = all.optJSONObject(id);
                if (e == null) e = new JSONObject();
                e.put("name", e.optString("nativeName", p.name));
                String art = e.optString("nativeArt", p.art);
                if (!art.isEmpty() && !art.equals(e.optString("art"))) {
                    e.put("art", art);
                    Art.invalidate(c, "playlist_" + id);
                }
                all.put(id, e);
                order.put(id);
            }
            Library.prefs(c).edit()
                .putString("local_playlist_index", all.toString())
                .putString("library_playlist_order", order.toString())
                .putBoolean("library_roster_complete", true)
                .putString("library_sync_error", "")
                .apply();
            invalidateCache();
        }
    }

    static String art(Context c, String id) {
        if (UnmatchedDownloads.is(id) || id != null && id.startsWith("local:")) return "";
        JSONObject e = LocalPlaylistCopies.entry(c, id);
        return e == null ? "" : e.optString("art", "");
    }

    static int total(Context c, String id) {
        if (UnmatchedDownloads.is(id)) return UnmatchedDownloads.items(c).size();
        if (id != null && id.startsWith("local:")) {
            try {
                String name = id.substring(6);
                JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
                JSONArray arr = map.optJSONArray(name);
                return arr != null ? arr.length() : 0;
            } catch (Exception e) {
                return 0;
            }
        }
        JSONObject e = LocalPlaylistCopies.entry(c, id);
        JSONArray a = e == null ? null : e.optJSONArray("tracks");
        return a == null ? 0 : a.length();
    }

    static Map<String, Integer> order(Context c, String id) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        if (id != null && id.startsWith("local:")) {
            try {
                String name = id.substring(6);
                JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
                JSONArray arr = map.optJSONArray(name);
                if (arr != null) {
                    for (int n = 0; n < arr.length(); n++) {
                        JSONObject t = arr.optJSONObject(n);
                        if (t != null && !out.containsKey(t.optString("id"))) out.put(t.optString("id"), n);
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
        JSONObject e = LocalPlaylistCopies.entry(c, id);
        JSONArray a = e == null ? null : e.optJSONArray("tracks");
        if (a != null) {
            for (int n = 0; n < a.length(); n++) {
                JSONObject t = a.optJSONObject(n);
                if (t != null && !out.containsKey(t.optString("id"))) out.put(t.optString("id"), n);
            }
        }
        return out;
    }

    static ArrayList<Library.Item> ordered(Context c, String id, List<Library.Item> local, boolean missing) {
        if (UnmatchedDownloads.is(id)) return UnmatchedDownloads.filter(VoiceRecordings.filter(c,local), UnmatchedDownloads.matched(c));
        HashMap<String, Library.Item> byId = new HashMap<>();
        for (Library.Item i : local) byId.put(i.id, i);
        try { for (Library.Item i : Library.all(c)) if (!VoiceRecordings.visible(c,i)) byId.put(i.id,i); } catch(Exception ignored) {}
        ArrayList<Library.Item> out = new ArrayList<>();

        if (id != null && id.startsWith("local:")) {
            try {
                String name = id.substring(6);
                JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
                JSONArray arr = map.optJSONArray(name);
                if (arr != null) {
                    for (int n = 0; n < arr.length(); n++) {
                        JSONObject t = arr.optJSONObject(n);
                        if (t == null) continue;
                        String songId = t.optString("id");
                        Library.Item i = byId.get(songId);
                        if (i == null && missing) {
                            i = new Library.Item();
                            i.id = songId;
                            i.title = t.optString("title", songId);
                            i.artist = t.optString("artist", "");
                            i.album = t.optString("album", "");
                            i.state = "unavailable";
                        }
                        if (i != null && VoiceRecordings.visible(c,i)) out.add(i);
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }

        JSONObject e = LocalPlaylistCopies.entry(c, id);
        JSONArray a = e == null ? null : e.optJSONArray("tracks");
        if (a != null) {
            for (int n = 0; n < a.length(); n++) {
                JSONObject t = a.optJSONObject(n);
                if (t == null) continue;
                String songId = t.optString("id");
                Library.Item i = byId.get(songId);
                if (i == null && missing) {
                    i = new Library.Item();
                    i.id = songId;
                    i.title = t.optString("title", songId);
                    i.artist = t.optString("artist", "");
                    i.album = t.optString("album", "");
                    i.state = "unavailable";
                }
                if (i != null && VoiceRecordings.visible(c,i)) out.add(i);
            }
        }
        return out;
    }

    static Set<String> members(Context c, String id) {
        if (UnmatchedDownloads.is(id)) return UnmatchedDownloads.ids(c);
        synchronized (LOCK) {
            Set<String> cachedSet = cachedMembers.get(id);
            if (cachedSet != null) return cachedSet;
        }
        HashSet<String> out = new HashSet<>();
        if (id != null && id.startsWith("local:")) {
            try {
                String name = id.substring(6);
                JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
                JSONArray arr = map.optJSONArray(name);
                if (arr != null) {
                    for (int n = 0; n < arr.length(); n++) {
                        JSONObject t = arr.optJSONObject(n);
                        if (t != null) out.add(t.optString("id"));
                    }
                }
            } catch (Exception ignored) {}
        } else {
            JSONObject e = LocalPlaylistCopies.entry(c, id);
            JSONArray a = e == null ? null : e.optJSONArray("tracks");
            if (a != null) {
                for (int n = 0; n < a.length(); n++) {
                    JSONObject t = a.optJSONObject(n);
                    if (t != null) out.add(t.optString("id"));
                }
            }
        }
        synchronized (LOCK) {
            cachedMembers.put(id, out);
        }
        return out;
    }

    static ArrayList<Catalog.Track> tracks(Context c) {
        LinkedHashMap<String, Catalog.Track> out = new LinkedHashMap<>();
        JSONObject all = read(c);
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            JSONObject e = all.optJSONObject(it.next());
            JSONArray a = e == null ? null : e.optJSONArray("tracks");
            if (a != null) {
                for (int n = 0; n < a.length(); n++) {
                    JSONObject t = a.optJSONObject(n);
                    if (t != null) {
                        String id = t.optString("id");
                        out.put(id, new Catalog.Track(id, t.optString("title"), t.optString("artist"), ""));
                    }
                }
            }
        }
        // Include local playlist tracks
        try {
            JSONObject map = new JSONObject(Library.prefs(c).getString("ultra_local_playlists", "{}"));
            Iterator<String> lIt = map.keys();
            while (lIt.hasNext()) {
                JSONArray arr = map.optJSONArray(lIt.next());
                if (arr != null) {
                    for (int n = 0; n < arr.length(); n++) {
                        JSONObject t = arr.optJSONObject(n);
                        if (t != null) {
                            String id = t.optString("id");
                            if (!out.containsKey(id)) {
                                out.put(id, new Catalog.Track(id, t.optString("title", id), t.optString("artist", ""), t.optString("album", "")));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(out.values());
    }

    static void error(Context c, String id, Exception error) {
        try {
            synchronized (LOCK) {
                JSONObject all = copy(c), e = all.optJSONObject(key(id));
                if (e == null) return;
                e.put("error", error.getMessage() == null ? "Playlist sync unavailable" : error.getMessage());
                all.put(key(id), e);
                Library.prefs(c).edit().putString("local_playlist_index", all.toString()).apply();
                invalidateCache();
            }
        } catch (Exception ignored) {}
    }

    static boolean complete(Context c, String id) {
        if (UnmatchedDownloads.is(id) || id != null && id.startsWith("local:")) return true;
        JSONObject e = LocalPlaylistCopies.entry(c, id);
        return e != null && (e.optBoolean("complete") || e.optBoolean("edited"));
    }

    static void focus(final String id) {
        if (UnmatchedDownloads.is(id) || id != null && id.startsWith("local:")) return;
        synchronized (requested) {
            if (!requested.add(id)) return;
        }
        FOCUS.execute(new Runnable() {
            public void run() {
                try {
                    Context c = Hooks.context();
                    try {
                        for (String source : LikedPlaylists.sources(c, id)) {
                            try {
                                Catalog.IndexPage page = Catalog.index(source);
                                record(source, page.tracks, page.complete);
                            } catch (Exception e) {
                                error(c, source, e);
                            }
                        }
                        if (LocalAudio.allowed(c)) LocalAudio.scan(c);
                    } catch (Exception e) {
                        error(c, id, e);
                    }
                } catch (Exception e) {
                    Diagnostics.error("playlist_index", e);
                } finally {
                    synchronized (requested) {
                        requested.remove(id);
                    }
                }
            }
        });
    }

    public static void accountReady() {
        try {
            Context c = Hooks.context();
            if (needsRetry && !syncing && (lastSync == 0 || android.os.SystemClock.elapsedRealtime() - lastSync > 60000)) {
                lastSync = 0;
                sync();
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void sync() {
        if (syncing || (android.os.SystemClock.elapsedRealtime() - lastSync < (needsRetry ? 60000 : 3600000) && lastSync != 0)) return;
        syncing = true;
        syncDone = 0;
        syncTotal = 0;
        syncFailed = 0;
        syncStatus = "Loading Library playlists";
        lastSync = android.os.SystemClock.elapsedRealtime();
        IO.execute(new Runnable() {
            public void run() {
                try {
                    Context c = Hooks.context();
                    boolean rosterFailed = false;
                    try {
                        roster(c, Catalog.library());
                        Diagnostics.event("library_sync", "playlist-roster-complete");
                    } catch (Exception e) {
                        rosterFailed = true;
                        Library.prefs(c).edit().putString("library_sync_error", e.getMessage() == null ? "Library sync unavailable" : e.getMessage()).apply();
                        Diagnostics.error("library_sync", e);
                    }
                    ArrayList<String> ids = new ArrayList<>(rawGroups(c).keySet());
                    syncTotal = ids.size();
                    int partial = 0;
                    for (String id : ids) {
                        syncStatus = "Syncing playlists " + syncDone + "/" + syncTotal;
                        try {
                            Catalog.IndexPage page = Catalog.index(id);
                            record(id, page.tracks, page.complete);
                            if (!page.complete) partial++;
                        } catch (Exception e) {
                            syncFailed++;
                            error(c, id, e);
                            Diagnostics.error("playlist_index", e);
                        } finally {
                            syncDone++;
                        }
                    }
                    partial = 0;
                    for (String group : groups(c).keySet()) {
                        if (!complete(c, group)) partial++;
                    }
                    syncStatus = "Matching device songs";
                    if (LocalAudio.allowed(c)) LocalAudio.scan(c);
                    needsRetry = rosterFailed || syncFailed > 0;
                    syncStatus = rosterFailed ? "Library sync needs retry" :
                                 syncFailed > 0 ? syncFailed + " playlists need retry" :
                                 partial > 0 ? "Available songs synced • " + partial + " playlists partly synced" :
                                 "Playlist sync finished";
                } catch (Exception e) {
                    needsRetry = true;
                    syncStatus = "Playlist sync needs retry";
                    Diagnostics.error("playlist_index", e);
                } finally {
                    syncing = false;
                }
            }
        });
    }
}
