package ultra.downloads;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of exact native Library row model identities captured
 * directly from host InnerTube responses (bqtu navigation endpoints).
 * Enforces identity safety: never infers a video ID from title alone, and explicitly
 * treats an artist as an artist (never a playlist with guessed IDs).
 */
public final class NativeIdentityRegistry {
    private NativeIdentityRegistry() {}

    public static final class Entry {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String kind; // "song", "playlist", "album", "artist"

        public Entry(String id, String title, String subtitle, String kind) {
            this.id = id != null ? id : "";
            this.title = title != null ? title.trim() : "";
            this.subtitle = subtitle != null ? subtitle.trim() : "";
            this.kind = kind != null ? kind : "unknown";
        }

        public boolean isSong() { return "song".equals(kind); }
        public boolean isPlaylist() { return "playlist".equals(kind); }
        public boolean isAlbum() { return "album".equals(kind); }
        public boolean isArtist() { return "artist".equals(kind); }
    }

    private static final Map<String, Entry> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Entry> BY_TITLE = new ConcurrentHashMap<>();

    public static void register(String id, String title, String subtitle, String kind) {
        if (id == null || id.isEmpty()) return;
        Entry e = new Entry(id, title, subtitle, kind);
        BY_ID.put(id, e);
        if (!e.title.isEmpty()) {
            BY_TITLE.put(e.title.toLowerCase(Locale.ROOT), e);
        }
    }

    public static Entry get(String id) {
        return id != null ? BY_ID.get(id) : null;
    }

    public static Entry findByExactTitle(String title) {
        if (title == null) return null;
        return BY_TITLE.get(title.trim().toLowerCase(Locale.ROOT));
    }

    public static void clear() {
        BY_ID.clear();
        BY_TITLE.clear();
    }
}
