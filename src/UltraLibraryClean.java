import com.google.protobuf.ExtensionRegistryLite;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import java.util.Locale;

/**
 * Filter for native library rows before adapter layout.
 * Drops exact "Liked Songs" junk alias rows locally regardless of track count,
 * preserving official "Liked Music" and user custom playlists like "Liked Songs Forever".
 * Never deletes remote playlists or drops cached membership.
 */
public final class UltraLibraryClean {

    public static boolean emptyLiked(byte[] bytes) {
        try {
            bqtu row = (bqtu) bhbk.parseFrom(bqtu.a, bytes, ExtensionRegistryLite.getGeneratedRegistry());
            Object ep = row.i == null ? null : row.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);
            if (!(ep instanceof bizw)) return false;
            String playlistId = ((bizw) ep).c;
            if (playlistId == null || !playlistId.startsWith("VL") || "VLLM".equals(playlistId)) {
                return false; // Not a playlist, or official Liked Music (VLLM) which must stay visible!
            }

            String title = UltraPlaylistIndex.label(row.e);
            if (title.isEmpty()) title = UltraPlaylistIndex.label(row.g);
            String trimmedTitle = title.trim();

            // Strictly match exact "Liked Songs" (case-insensitive).
            // Explicitly rejects "Liked Songs Forever" or "Liked Music".
            if (!trimmedTitle.equalsIgnoreCase("Liked Songs")) {
                return false;
            }

            // Must carry playlist metadata (e.g. tracks, Auto playlist) to prevent misidentifying headers
            String sub = (UltraPlaylistIndex.label(row.g) + " " + UltraPlaylistIndex.label(row.h)).toLowerCase(Locale.ROOT);
            boolean hasMetadata = sub.contains("tracks") || sub.contains("auto playlist") || sub.contains("playlist");
            if (!hasMetadata) {
                return false;
            }

            // Preserve alias identity and membership in local catalog before filtering row
            ultra.downloads.LikedPlaylists.preserveAlias(playlistId, trimmedTitle);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static byte[] clean(byte[] bytes) {
        return ultra.downloads.LibraryRows.clean(bytes, new java.util.function.Predicate<byte[]>() {
            public boolean test(byte[] value) {
                return emptyLiked(value);
            }
        });
    }
}
