package ultra.downloads;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.Drawable;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

/** Binds to each real browse fragment's current model and toolbar, including restored pages. */
public final class NativePages {
    private NativePages() {}

    static final WeakHashMap<Object, Binding> bindings = new WeakHashMap<>();
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static WeakReference<Activity> host = new WeakReference<>(null);
    static boolean running;
    static String lastSeededIdentity = "";
    static int lastVisible;

    static final Runnable scan = new Runnable() {
        public void run() {
            if (!running) return;
            Activity a = host.get();
            if (a == null || a.isFinishing() || a.isDestroyed()) {
                stop();
                return;
            }
            try {
                Object manager = a.getClass().getMethod("getSupportFragmentManager").invoke(a);
                Set<Object> visible = new HashSet<>();
                RadioContext.libraryVisible = false;
                visit(manager, visible, 0);
                // DL49: a newly visible page (Library, Explore, Search...) gets explicit style passes.
                int signature = visible.size();
                for (Object page : visible) signature += System.identityHashCode(page);
                if (signature != lastVisible) { lastVisible = signature; PageSkin.kick("page"); }
                for (Object page : new ArrayList<>(bindings.keySet())) {
                    if (!visible.contains(page)) detach(page);
                }
            } catch (Throwable problem) { NativeLibrarySelection.mark("scan-error-"+problem.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT)); }
            if (running) MAIN.postDelayed(this, 3000);
        }
    };

    public static void start(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        host = new WeakReference<>(activity);
        LibraryViewClean.start(activity);
        PageSkin.start(activity);
        running = true;
        MAIN.removeCallbacks(scan);
        MAIN.post(scan);
    }

    public static void stop() {
        LibraryViewClean.stop();
        PageSkin.stop();
        NativeLibrarySelection.unbind();
        running = false;
        RadioContext.libraryVisible = false;
        lastSeededIdentity = "";
        MAIN.removeCallbacks(scan);
        for (Object p : new ArrayList<>(bindings.keySet())) detach(p);
        host.clear();
    }

    static void visit(Object manager, Set<Object> found, int depth) throws Exception {
        if (manager == null || depth > 8) return;
        Object fragments = Class.forName("et").getMethod("l").invoke(manager);
        if (!(fragments instanceof List)) return;
        Class<?> browse = Class.forName("jde");
        for (Object page : (List<?>) fragments) {
            if (page == null) continue;
            try {
                if (browse.isInstance(page) && visible(page)) {
                    found.add(page);
                    NativeLibrarySelection.mark("browse-fragment-found");
                    attach(page);
                }
                if (Boolean.TRUE.equals(page.getClass().getMethod("isAdded").invoke(page))) {
                    Object child = page.getClass().getMethod("getChildFragmentManager").invoke(page);
                    visit(child, found, depth + 1);
                }
            } catch (Throwable ignored) {}
        }
    }

    static Object field(Object page, String name) throws Exception {
        Field f = Class.forName("jde").getDeclaredField(name);
        f.setAccessible(true);
        return f.get(page);
    }

    static String current(Object page) throws Exception {
        Object model = field(page, "s");
        return model == null ? "" : (String) model.getClass().getMethod("b").invoke(model);
    }

    static boolean ready(Object page) throws Exception {
        Object model = field(page, "s");
        return model != null && Class.forName("kiw").getField("g").get(model) != null;
    }

    static boolean visible(Object page) throws Exception {
        return Boolean.TRUE.equals(page.getClass().getMethod("isVisible").invoke(page)) &&
               Boolean.TRUE.equals(page.getClass().getMethod("isResumed").invoke(page));
    }

    public static void attach(final Object page) {
        try {
            View view = (View) page.getClass().getMethod("getView").invoke(page);
            if (view == null) return;
            Binding old = bindings.get(page);
            if (old != null && old.root == view) {
                old.update();
                return;
            }
            detach(page);
            Binding b = new Binding(page, view);
            bindings.put(page, b);
            b.update();
        } catch (Throwable ignored) {}
    }

    public static void detach(Object page) {
        try {
            Binding b = bindings.remove(page);
            if (b != null) b.remove();
        } catch (Throwable ignored) {}
    }

    static final Set<String> evidenced = new HashSet<>();

    static void evidence(View root, String id) {
        if (id == null || !id.startsWith("VL") || NativeShuffle.binding != null) return;
        synchronized (evidenced) {
            if (evidenced.size() >= 8 || !evidenced.add(id)) return;
        }
        try {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            Diagnostics.names(root, names, 0, new int[]{Diagnostics.MAX_VIEWS}, null);
            Diagnostics.classes("playlist_page_views", names);
        } catch (Throwable e) {
            Diagnostics.error("playlist_page_views", e);
        }
    }

    static final class Binding {
        final WeakReference<Object> page;
        final View root;
        View button;
        String identity = "";

        Binding(Object p, View v) {
            page = new WeakReference<>(p);
            root = v;
        }

        void removeButton() {
            if (button != null && button.getParent() instanceof ViewGroup) {
                ((ViewGroup) button.getParent()).removeView(button);
            }
            button = null;
            identity = "";
        }

        void remove() {
            removeButton();
            try {
                NativeShuffle.restore(root);
            } catch (Throwable ignored) {}
        }

        void update() {
            Object p = page.get();
            try {
                String id = p == null ? "" : current(p);
                NativeLibrarySelection.mark(id.startsWith("FEmusic_library")?"library-page":id.startsWith("VL")?"playlist-page":"other-browse-page");

                // Bind native multi-selection in all library sections & detail pages
                if (id.startsWith("FEmusic_library") || id.startsWith("FEmusic_liked") || id.startsWith("VL") || id.startsWith("MPRE") || id.startsWith("UC")) NativeLibrarySelection.bind(p, root); else if (NativeLibrarySelection.owns(root)) NativeLibrarySelection.unbind();

                if (p != null && "FEmusic_library_landing".equals(id) && visible(p) && ready(p)) {
                    RadioContext.libraryVisible = true;
                    if (!id.equals(lastSeededIdentity)) {
                        lastSeededIdentity = id;
                        RadioContext.seed();
                    }
                    LibraryViewClean.apply(root);
                    RadioContext.refresh(root);
                    try {
                        Class.forName("UltraDownloadBridge").getMethod("captureLibrary", Object.class).invoke(null, p);
                    } catch (Throwable e) {
                        Diagnostics.error("library_schema", e);
                    }
                    removeButton();
                    NativeShuffle.restore(root);
                    return;
                }
                boolean valid = p != null && visible(p) && ready(p) && root.isLaidOut() &&
                                NativeIntegration.panel == null && (id.startsWith("VL") || id.startsWith("MPRE"));
                ViewGroup toolbar = p == null ? null : (ViewGroup) field(p, "E");
                if (!valid || toolbar == null) {
                    remove();
                    return;
                }
                try {
                    Class.forName("UltraDownloadBridge").getMethod("capturePage", Object.class).invoke(null, p);
                } catch (Throwable e) {
                    Diagnostics.error("snapshot_binding", e);
                }
                if (!(button != null && button.getParent() == toolbar && identity.equals(id))) {
                    header(p, toolbar, id);
                }
                try {
                    NativeShuffle.apply(p, root, id);
                } catch (Throwable e) {
                    Diagnostics.error("playlist_shuffle", e);
                }
            } catch (Throwable e) {
                remove();
            }
        }

        void header(Object p, ViewGroup toolbar, String id) {
            removeButton();
            identity = id;
            evidence(root, id);
            final String exact = id;
            ImageButton b = new ImageButton(toolbar.getContext());
            b.setImageDrawable(NativeUi.drawable(toolbar.getContext(), "yt_outline_download_vd_theme_24", "download", null));
            b.setContentDescription("Download all songs");
            Drawable ripple = NativeUi.themeDrawable(toolbar.getContext(), android.R.attr.selectableItemBackgroundBorderless);
            if (ripple != null) b.setBackground(ripple);
            else b.setBackgroundResource(android.R.drawable.list_selector_background);
            int size = (int) (48 * b.getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams params;
            try {
                Class<?> type = Class.forName("android.support.v7.widget.Toolbar$LayoutParams");
                params = (ViewGroup.LayoutParams) type.getConstructor(int.class, int.class).newInstance(size, size);
                type.getField("gravity").setInt(params, Gravity.END | Gravity.CENTER_VERTICAL);
            } catch (Exception e) {
                params = new ViewGroup.LayoutParams(size, size);
            }
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Object owner = page.get();
                    try {
                        if (owner != null && visible(owner) && exact.equals(current(owner))) {
                            Diagnostics.event("header_button", "playlist");
                            NativeIntegration.open(exact);
                        } else {
                            Hooks.message("Open the playlist again before downloading");
                        }
                    } catch (Throwable ignored) {}
                }
            });
            button = b;
            toolbar.addView(b, params);
        }
    }
}
