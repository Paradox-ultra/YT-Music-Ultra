package ultra.downloads;

import java.util.*;
import java.util.function.Predicate;
import java.io.*;

/** Read-only removal of verified empty alias rows. No remote deletion. */
public final class LibraryRows {
    private static final byte[] LIKED_SONGS_1 = HeaderCommands.text("Liked Songs");
    private static final byte[] LIKED_SONGS_2 = HeaderCommands.text("Liked songs");

    static byte[] walk(byte[] b, Predicate<byte[]> empty, int depth, int[] budget) {
        if (depth > 48 || --budget[0] < 0) return b;
        List<HeaderCommands.F> fields;
        try {
            fields = HeaderCommands.fields(b);
        } catch (Exception e) {
            return b;
        }
        boolean changed = false;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (HeaderCommands.F f : fields) {
            byte[] raw = f.raw;
            if (f.w == 2) {
                // Large avoidable traversal cost fix: only examine/recurse if this branch contains Liked Songs text
                boolean candidate = RadioWire.contains(f.value, LIKED_SONGS_1) || RadioWire.contains(f.value, LIKED_SONGS_2);
                if (!candidate) {
                    out.write(raw, 0, raw.length);
                    continue;
                }
                if (empty.test(f.value)) {
                    changed = true;
                    continue;
                }
                byte[] next = walk(f.value, empty, depth + 1, budget);
                if (next != f.value) {
                    changed = true;
                    if (next.length == 0) continue;
                    raw = HeaderCommands.field(f.n, next);
                }
            }
            out.write(raw, 0, raw.length);
        }
        return changed ? out.toByteArray() : b;
    }

    public static byte[] clean(byte[] bytes, Predicate<byte[]> empty) {
        if (bytes == null || bytes.length > HeaderCommands.MAX) return bytes;
        int[] budget = {180000};
        byte[] out = walk(bytes, empty, 0, budget);
        return budget[0] < 0 ? bytes : out;
    }
}
