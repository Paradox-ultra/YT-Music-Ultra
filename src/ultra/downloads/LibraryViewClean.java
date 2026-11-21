package ultra.downloads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Local presentation filter for cached and continuation Library rows.
 * Hides exact "Liked Songs" alias rows locally, preserving official "Liked Music"
 * and user playlists such as "Liked Songs Forever". Never deletes remote playlists.
 */
public final class LibraryViewClean {
    private LibraryViewClean() {}

    private static Handler mainHandler;
    private static Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                Looper l = Looper.getMainLooper();
                if (l != null) mainHandler = new Handler(l);
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }
    private static boolean scheduled = false;
    static View decor;

    private static final Pattern LIKED_SONGS_A11Y_PATTERN =
            Pattern.compile("(?i)^liked songs\\s*[,\\n\\u2022].*");
    private static final Pattern TRACKS_PATTERN =
            Pattern.compile("(?si).*\\b[0-9,]+ tracks?\\b.*");

    private static final Runnable CLEAN_TASK = new Runnable() {
        @Override
        public void run() {
            scheduled = false;
            if (decor != null && NativeIntegration.panel == null) {
                apply(decor);
            }
        }
    };

    static final ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            if (decor == null || NativeIntegration.panel != null) return;
            // Coalesce / debounce cleanup to avoid 97% jank on high-refresh/scrolling displays
            if (!scheduled) {
                scheduled = true;
                Handler h = getMainHandler();
                if (h != null) h.postDelayed(CLEAN_TASK, 120);
            }
        }
    };

    public static void start(Activity a) {
        View next = a.getWindow().getDecorView();
        if (decor == next) return;
        stop();
        decor = next;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        apply(decor);
    }

    public static void stop() {
        if (decor != null) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
            decor = null;
        }
        Handler h = getMainHandler();
        if (h != null) h.removeCallbacks(CLEAN_TASK);
        scheduled = false;
        restoreAll();
    }

    static final WeakHashMap<View, Saved> hidden = new WeakHashMap<>();

    static final class Saved {
        final int height, minHeight, visibility;
        Saved(View v) {
            height = v.getLayoutParams() == null ? -2 : v.getLayoutParams().height;
            minHeight = v.getMinimumHeight();
            visibility = v.getVisibility();
        }
    }

    static String resource(View v) {
        try {
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Exception e) {
            return "";
        }
    }

    static boolean list(View v) {
        if (resource(v).equals("section_list_content")) return true;
        CharSequence role = v.getAccessibilityClassName();
        if (role != null && (role.toString().endsWith(".RecyclerView") || role.toString().equals("android.widget.ListView"))) return true;
        for (Class<?> c = v.getClass(); c != null; c = c.getSuperclass()) {
            String n = c.getName();
            if (n.endsWith(".RecyclerView") || n.equals("android.widget.ListView")) return true;
        }
        return false;
    }

    static void texts(View v, List<String> out, int d, int[] budget) {
        if (v == null || d > 12 || --budget[0] < 0) return;
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) out.add(cd.toString().trim());
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) out.add(t.toString().trim());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int n = 0; n < g.getChildCount(); n++) {
                texts(g.getChildAt(n), out, d + 1, budget);
            }
        }
    }

    public static boolean empty(List<String> labels) {
        if (labels == null || labels.isEmpty()) return false;
        boolean title = false;
        boolean hasMetadata = false;

        for (String raw : labels) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.equalsIgnoreCase("Liked Songs") || LIKED_SONGS_A11Y_PATTERN.matcher(trimmed).matches()) {
                title = true;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("auto playlist") || lower.contains("tracks") || TRACKS_PATTERN.matcher(trimmed).matches()) {
                hasMetadata = true;
            }
        }
        return title && hasMetadata;
    }

    static void restore(View row) {
        Saved s = hidden.remove(row);
        if (s == null) return;
        ViewGroup.LayoutParams p = row.getLayoutParams();
        if (p != null) {
            p.height = s.height;
            row.setLayoutParams(p);
        }
        row.setMinimumHeight(s.minHeight);
        row.setVisibility(s.visibility);
    }

    static void row(View row) {
        List<String> labels = new ArrayList<>();
        texts(row, labels, 0, new int[]{60});
        if (empty(labels)) {
            View target = row;
            ViewParent parent = row.getParent();
            // If row is wrapped in a single-child item container, collapse the container to prevent empty gaps
            if (parent instanceof ViewGroup) {
                ViewGroup pg = (ViewGroup) parent;
                if (pg.getChildCount() == 1 && !list(pg)) {
                    target = pg;
                }
            }
            if (!hidden.containsKey(target)) {
                hidden.put(target, new Saved(target));
            }
            ViewGroup.LayoutParams p = target.getLayoutParams();
            if (p != null && p.height != 0) {
                p.height = 0;
                target.setLayoutParams(p);
            }
            target.setMinimumHeight(0);
            if (target.getVisibility() != View.GONE) {
                target.setVisibility(View.GONE);
            }
        } else {
            restore(row);
            ViewParent parent = row.getParent();
            if (parent instanceof ViewGroup) {
                restore((ViewGroup) parent);
            }
        }
    }

    static boolean nestedList(View v, int d) {
        if (v == null || d > 16) return false;
        if (list(v)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int n = 0; n < g.getChildCount(); n++) {
                if (nestedList(g.getChildAt(n), d + 1)) return true;
            }
        }
        return false;
    }

    static void walk(View v, int d, int[] budget) {
        if (v == null || d > 24 || --budget[0] < 0) return;
        if (resource(v).equals("swipe_layout")) {
            row(v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean rows = list(g);
            for (int n = 0; n < g.getChildCount(); n++) {
                View child = g.getChildAt(n);
                if (rows && !nestedList(child, 0)) {
                    row(child);
                } else {
                    walk(child, d + 1, budget);
                }
            }
        }
    }

    public static void apply(View root) {
        if (root == null) return;
        walk(root, 0, new int[]{1600});
    }

    public static void restoreAll() {
        for (View v : new ArrayList<>(hidden.keySet())) {
            if (v != null) restore(v);
        }
        hidden.clear();
    }
}
