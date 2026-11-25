package ultra.downloads;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.*;
import java.lang.reflect.*;

public final class NativeIntegration {
    static Bundle restored;
    static boolean restorePending;

    public static void save(Bundle out) {
        if (home != null && homeTab) out.putBoolean("ultra_home_tab", true);
        if (panel != null) {
            Bundle state = new Bundle();
            panel.onSaveInstanceState(state);
            out.putBundle("ultra_downloads_state", state);
        }
    }

    public static void restore(Bundle state) {
        if (state != null && state.getBoolean("ultra_home_tab", false)) homeRestorePending = true;
        if (state != null && state.containsKey("ultra_downloads_state")) {
            restored = state.getBundle("ultra_downloads_state");
            restorePending = true;
        }
    }

    static WeakReference<Activity> activity = new WeakReference<>(null);
    static DownloadsScreen panel;
    static FrameLayout mount;
    static String browse = "";
    static View batchButton;
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static Intent notificationIntent(Context c) {
        Intent i = new Intent();
        i.setClassName(c.getPackageName(), "com.google.android.apps.youtube.music.activities.MusicActivity");
        i.setAction("ultra.downloads.OPEN");
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("ultra_open_downloads", true);
        return i;
    }

    public static void attach(Activity a) {
        Diagnostics.attach(a);
        if (activity.get() != a && panel != null) {
            Bundle state = new Bundle();
            panel.onSaveInstanceState(state);
            closePanel();
            restored = state;
            restorePending = true;
        }
        activity = new WeakReference<>(a);
        NativePages.start(a);
        if (panel != null) panel.onResume();
        if (restorePending) {
            restorePending = false;
            open();
        }
        if (homeRestorePending) {
            homeRestorePending = false;
            openHome(true);
        }
        if (a.getIntent().getBooleanExtra("ultra_open_downloads", false)) {
            a.getIntent().removeExtra("ultra_open_downloads");
            MAIN.post(new Runnable() {
                public void run() {
                    open();
                }
            });
        }
    }

    public static void paused(Activity a) {
        if (activity.get() == a) {
            NativePages.stop();
            if (panel != null) panel.onPause();
        }
    }

    public static void destroyed(Activity a) {
        if (activity.get() == a) {
            NativePages.stop();
            closePanel();
            activity = new WeakReference<>(null);
        }
    }

    static WeakReference<Object> navigation = new WeakReference<>(null);

    /** One-shot guard: the first "View my" selector sheet appearing shortly after the Downloads panel closes is an unwanted auto-popup, so it is dismissed. A manual reopen is left alone. */
    static volatile long sheetGuardUntil;
    static volatile boolean sheetGuardUsed;

    /** True while a freshly closed Downloads panel may still trigger the unwanted selector sheet. Does not spend the guard. */
    public static boolean sheetGuardActive() {
        return !sheetGuardUsed && SystemClock.uptimeMillis() < sheetGuardUntil;
    }

    /** Spends the one-shot guard; returns false when the window already expired or the guard was spent. Call only when the sheet is confirmed. */
    public static boolean consumeSheetGuard() {
        if (!sheetGuardActive()) return false;
        sheetGuardUsed = true;
        return true;
    }

    static void armSheetGuard() {
        sheetGuardUntil = SystemClock.uptimeMillis() + 6000;
        sheetGuardUsed = false;
    }

    public static void fromTab(Object controller) {
        navigation = new WeakReference<>(controller);
    }

    // ------------------------------------------------------------------ DL51 Home timeline (Diagnostics)
    static final java.util.ArrayDeque<String> TIMELINE = new java.util.ArrayDeque<>();
    static final long STARTED = SystemClock.uptimeMillis();
    /** One timestamped line (ms since this module loaded) in the Home timeline; the last 60 are kept. */
    /** timeline() from any thread: posted to the main thread. */
    static void timelineAsync(final String what){MAIN.post(new Runnable(){public void run(){timeline(what);}});}

    public static void timeline(String what) {
        synchronized (TIMELINE) {
            TIMELINE.addLast(String.format(java.util.Locale.ROOT, "%6d ms  %s", SystemClock.uptimeMillis() - STARTED, what));
            while (TIMELINE.size() > 60) TIMELINE.removeFirst();
        }
        // DL61: the same line in logcat (tag ULTRAHOME), outside the lock.
        try { android.util.Log.d("ULTRAHOME", String.valueOf(what)); } catch (Throwable ignored) {}
    }
    /** The two host frames that called into the module (obfuscated names), so a Home re-open can be traced to its caller. */
    static String caller() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder b = new StringBuilder();
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("ultra.") || c.startsWith("java.") || c.startsWith("android.os.Handler") || c.startsWith("dalvik.")) continue;
            if (b.length() > 0) b.append(" < ");
            b.append(c).append('.').append(e.getMethodName());
            if (b.length() > 60 || b.toString().split(" < ").length >= 2) break;
        }
        return b.length() == 0 ? "module" : b.toString();
    }
    /** Repeated fromTab(FEmusic_ultra_home) while Home was already showing: count, first/last time, callers. */
    static int repeatCount; static long repeatFirst, repeatLast;
    static final java.util.LinkedHashMap<String, Integer> repeatCallers = new java.util.LinkedHashMap<>();
    static void repeatHome(String caller) {
        long now = SystemClock.uptimeMillis() - STARTED;
        synchronized (TIMELINE) {
            if (repeatCount == 0) repeatFirst = now;
            repeatCount++; repeatLast = now;
            Integer n = repeatCallers.get(caller); repeatCallers.put(caller, n == null ? 1 : n + 1);
        }
    }
    /** Download settings > Diagnostics: Home sections (current, or the last one shown), then the timeline. */
    public static String homeReport() {
        StringBuilder b = new StringBuilder();
        ultra.home.HomeScreen h = home;
        if (h != null) b.append(h.diagnostics());
        else b.append("Ultra Home: not open now").append(ultra.home.HomeScreen.lastReport.isEmpty() ? "\n" : "; last shown:\n" + ultra.home.HomeScreen.lastReport);
        b.append("Home timeline (ms since module start):\n");
        synchronized (TIMELINE) {
            for (String line : TIMELINE) b.append("  ").append(line).append('\n');
            if (repeatCount > 0) b.append("  repeat fromTab FEmusic_ultra_home while Home was showing (no-op): x").append(repeatCount)
                .append(", first at ").append(repeatFirst).append(" ms, last at ").append(repeatLast).append(" ms, callers ").append(repeatCallers).append('\n');
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ DL34 host pivot contract
    /** Browse id of the Home pivot the host inserts FIRST in lac.ultraDownloadPivots (see DL34-IMPLEMENTATION.md). */
    public static final String HOME_PIVOT = "FEmusic_ultra_home";
    /** Browse id of the existing Downloads pivot. */
    public static final String DOWNLOADS_PIVOT = "FEmusic_ultra_downloads";
    static volatile boolean homePivotSeen;

    /**
     * Single host entry for module-owned pivots. The host's tab-select method (lac.t) calls this with itself and the
     * selected pivot's browse id (brso.e) and returns early when it answers true, without touching its content tab:
     * {@code invoke-static {p0, v0}, Lultra/downloads/NativeIntegration;->fromTab(Ljava/lang/Object;Ljava/lang/String;)Z}.
     * FEmusic_ultra_home opens Home as a tab, FEmusic_ultra_downloads opens Downloads (same as fromTab(Object) +
     * Hooks.open()). Returns true when the pivot is module-owned and was handled; false means the host should treat it
     * as one of its own tabs.
     */
    public static boolean fromTab(Object controller, String pivot) {
        if (HOME_PIVOT.equals(pivot) && homeShowing()) {
            // DL55: Home is built and on top: nothing to do. Counted in one summary line instead of a timeline entry
            // per call, with the host caller frames, so a periodic caller can be identified.
            barController = new WeakReference<>(controller); landedByHomeBack = false;
            repeatHome(caller());
            return true;
        }
        timeline("fromTab " + pivot + " from " + caller());
        if (HOME_PIVOT.equals(pivot) || DOWNLOADS_PIVOT.equals(pivot)) {barController = new WeakReference<>(controller);landedByHomeBack = false;}
        if (HOME_PIVOT.equals(pivot)) {
            navigation = new WeakReference<>(controller);
            homePivotSeen = true;
            openHome(true);
            return true;
        }
        if (DOWNLOADS_PIVOT.equals(pivot)) {
            navigation = new WeakReference<>(controller);
            open();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ DL49 back at a stock tab root
    /** The bar controller from the last fromTab(…, pivot); unlike navigation it survives host tab switches (leave). */
    static WeakReference<Object> barController = new WeakReference<>(null);
    /** Set while Back from the Home tab is pivoting to Library; the leave() that pivot causes turns it into landedByHomeBack. */
    static boolean homeBackPending, landedByHomeBack;
    /** Tests only: replaces the bar call (UltraNavigationBridge.home). */
    interface HomeSelector { boolean select(Object controller); }
    static HomeSelector homeSelector = new HomeSelector() {
        public boolean select(Object controller) {
            try { return Boolean.TRUE.equals(Class.forName("UltraNavigationBridge").getMethod("home", Object.class).invoke(null, controller)); }
            catch (Throwable e) { Diagnostics.error("back_home", e); return false; }
        }
    };

    /**
     * Host hook (MusicActivity), at the point where Back would otherwise finish the activity on a stock top-level tab
     * (Library, Explore, Search root): returns true when the module selected the Home tab instead, so the host must not
     * finish. Returns false (host exits as before) when the module screens are open, when there is no Home pivot or
     * controller, when Home is already selected, and right after Back from Home pivoted to Library, so Back from Home ->
     * Library -> exits instead of looping Home <-> Library.
     */
    public static boolean backAtRoot() { return backAtRoot(null); }
    public static boolean backAtRoot(Object controller) {
        if (panel != null || home != null) return false;
        if (landedByHomeBack) { landedByHomeBack = false; Diagnostics.event("back_home", "exit-after-home-back"); return false; }
        Object c = controller != null ? controller : barController.get();
        if (c == null) { Diagnostics.event("back_home", "no-controller"); return false; }
        boolean done = homeSelector.select(c);
        Diagnostics.event("back_home", done ? "home" : "not-selected");
        return done;
    }

    /** True once the host bottom bar is known to carry the Home pivot; Downloads then hides its own Home entry. */
    public static boolean hostHasHomePivot() {
        if (homePivotSeen) return true;
        Object controller = navigation.get();
        if (controller == null) return false;
        try {
            if (Boolean.TRUE.equals(Class.forName("UltraNavigationBridge").getMethod("hasPivot", Object.class, String.class).invoke(null, controller, HOME_PIVOT))) homePivotSeen = true;
        } catch (Throwable ignored) {}
        return homePivotSeen;
    }

    public static boolean downloadsVisible(){return panel!=null||home!=null;}

    static void backBridge(String method,Activity a){
        if(a==null)return;
        try{Class.forName("UltraBackBridge").getMethod(method,Activity.class).invoke(null,a);}
        catch(Throwable e){Diagnostics.error("downloads_back_dispatcher",e);}
    }

    public static void returnToContent() {
        Object controller=navigation.get();
        closePanel();
        navigation.clear();
        try{
            if(Boolean.TRUE.equals(Class.forName("UltraNavigationBridge").getMethod("library",Object.class).invoke(null,controller)))return;
        }catch(Throwable e){Diagnostics.error("downloads_back_library",e);}
        Activity a=activity.get();
        if(a!=null&&!clickLibrary(a.getWindow().getDecorView()))Hooks.message("Tap Library to return to your library");
    }

    /** Only the bottom Library tab may be tapped. The Library header (title dropdown) opens the "View my" sheet, so never tap it. */
    static boolean clickLibrary(View view){
        View content=view.findViewById(android.R.id.content);
        return clickLibrary(view,extent(content!=null?content:view));
    }

    static boolean clickLibrary(View view,int height){
        CharSequence description=view.getContentDescription();
        if(view.isShown()&&description!=null&&description.toString().equalsIgnoreCase("Library")&&view.isClickable()&&inBottomBar(view,height)){
            if(view.isSelected())return true;
            return view.performClick();
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=group.getChildCount()-1;n>=0;n--)if(clickLibrary(group.getChildAt(n),height))return true;}
        return false;
    }

    static int extent(View view){
        int[] at=new int[2];view.getLocationInWindow(at);int bottom=at[1]+view.getHeight();
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++)bottom=Math.max(bottom,extent(g.getChildAt(n)));}
        return bottom;
    }

    static boolean inBottomBar(View view,int height){
        if(height<=0)return false;
        int[] at=new int[2];view.getLocationInWindow(at);
        return at[1]+view.getHeight()/2>height*0.75f;
    }

    public static void open() {
        open("");
    }

    public static void open(final String playlist) {open(playlist,null,null);}

    /** Opens Downloads on a tab and optional group (DL33: Home hands local playlists and artists back to Downloads). */
    static void open(final String playlist,final String tab,final String group) {
        MAIN.post(new Runnable() {
            public void run() {
                Activity a = activity.get();
                if (a == null || a.isFinishing()) {
                    Hooks.message("Open Music before opening Downloads");
                    return;
                }
                closePanel();
                View target = a.findViewById(id(a, "fragment_container"));
                if (!(target instanceof FrameLayout)) {
                    Hooks.message("The Music content view is not ready");
                    return;
                }
                mount = new FrameLayout(a);
                mount.setTag("ultra_downloads_panel");
                ((FrameLayout) target).addView(mount, new FrameLayout.LayoutParams(-1, -1));
                Intent in = new Intent();
                if (!playlist.isEmpty()) in.putExtra("playlist", playlist);
                if (tab != null) in.putExtra("tab", tab);
                if (group != null) in.putExtra("group", group);
                Bundle state = restored;
                restored = null;
                panel = new DownloadsScreen(a, mount, in, state);
                backBridge("install",a);
            }
        });
    }

    public static boolean back() {
        if (NativeLibrarySelection.isActive()) {
            NativeLibrarySelection.exit();
            return true;
        }
        if (home != null) {
            home.onBackPressed(); return true;
        }
        if (panel == null) return false;
        panel.onBackPressed(); return true;
    }

    public static void permissions(int request, String[] permissions, int[] results) {
        if (panel != null) panel.onRequestPermissionsResult(request, permissions, results);
    }

    public static void result(int request, int result, Intent data) {
        if (panel != null) panel.onActivityResult(request, result, data);
    }

    public static void leave() {
        timeline("leave (host tab switch) from " + caller() + (home != null ? ", closing Home" : ""));
        openEpoch++;
        homeOpenPending = false;
        landedByHomeBack = homeBackPending;
        navigation.clear();
        closePanel();
        PageSkin.kick("tab");
    }

    public static void closePanel() {
        closeHome(false);
        armSheetGuard();
        backBridge("remove",activity.get());
        if (panel != null) {
            panel.destroy();
            panel = null;
        }
        if (mount != null) {
            if (mount.getParent() instanceof ViewGroup) {
                ((ViewGroup) mount.getParent()).removeView(mount);
            }
            mount = null;
        }
    }

    // ------------------------------------------------------------------ DL33 Home
    /**
     * Home is a second layer in the same fragment_container, mounted above the Downloads mount exactly the way Downloads is
     * mounted, so the host bottom navigation and mini-player stay native. A true bottom-bar Home tab needs the host's pivot
     * list (lac.ultraDownloadPivots / lac.t), which lives in the base APK, so Home is entered from the top of Downloads.
     * Back closes Home and shows Downloads again; any host tab change closes both (leave / closePanel).
     */
    static ultra.home.HomeScreen home;
    static FrameLayout homeMount;
    /** True when Home is open as a bottom-bar tab (host pivot), false when it is a layer over Downloads. */
    static boolean homeTab;
    static boolean homeRestorePending;

    public static boolean homeVisible(){return home!=null;}
    /**
     * DL53 host query: true when the Ultra Home screen is built and currently on top and visible (its mount attached,
     * shown, and the topmost child of fragment_container); false otherwise. No arguments, no side effects.
     */
    public static boolean homeShowing() {
        ultra.home.HomeScreen h = home;
        FrameLayout m = homeMount;
        if (h == null || m == null || !m.isAttachedToWindow() || !m.isShown()) return false;
        if (!(m.getParent() instanceof ViewGroup)) return false;
        ViewGroup parent = (ViewGroup) m.getParent();
        return parent.indexOfChild(m) == parent.getChildCount() - 1;
    }
    /** Home opened as its own bottom-bar tab: Back leaves it like Back from Downloads (to Library). */
    public static boolean homeIsTab(){return home!=null&&homeTab;}

    /** Home as a layer above Downloads (the Home entry in the Downloads header). */
    public static void openHome() {openHome(false);}

    /** Home; asTab=true for the host Home pivot: Downloads is closed first and Home is the only module screen. */
    /**
     * DL49: idempotent. A host Home pivot select (fromTab FEmusic_ultra_home) may arrive early, late or repeatedly (the
     * host start-tab enforcer). An open Home tab is reused (brought to the front, data reloaded) instead of being torn
     * down; opens queued at the same moment collapse into one; and two self-checks after every open repair a Home that
     * lost its place: detached mount -> rebuilt, covered by another view -> brought to front, no content -> reloaded.
     * Home never stays a blank shell: it shows a loading state until its data arrives (HomeScreen).
     */
    static volatile boolean homeOpenPending;
    /** Bumped by leave(): a Home open queued before the user switched tabs must not reappear afterwards (DL50). */
    static volatile int openEpoch;
    public static void openHome(final boolean asTab) {
        if (asTab && homeOpenPending) return;
        homeOpenPending = asTab;
        final int epoch = openEpoch;
        timeline("openHome queued (" + (asTab ? "tab" : "layer") + ")");
        MAIN.post(new Runnable() {
            public void run() {
                homeOpenPending = false;
                if (epoch != openEpoch) { Diagnostics.event("home", "open-cancelled"); timeline("openHome cancelled: a host tab switch came first"); return; }
                Activity a = activity.get();
                if (a == null || a.isFinishing()) return;
                View target = a.findViewById(id(a, "fragment_container"));
                if (asTab && home != null && homeTab && homeMount != null && homeMount.getParent() == target && homeMount.isAttachedToWindow()) {
                    // DL52: Home is built and showing (or loading): no reload, no render, at most brought to the front.
                    homeOnTop();
                    if (!home.hasContent() && !home.isLoading()) { home.refresh(); timeline("openHome on the open Home tab: no content, reloading"); }
                    // no-op: not logged (DL55)
                    Diagnostics.event("home", "reuse-tab");
                    return;
                }
                if (asTab) closePanel(); else closeHome(false);
                if (!(target instanceof FrameLayout)) {
                    Hooks.message("The Music content view is not ready");
                    return;
                }
                homeMount = new FrameLayout(a);
                homeMount.setTag("ultra_home_panel");
                ((FrameLayout) target).addView(homeMount, new FrameLayout.LayoutParams(-1, -1));
                if (panel != null) panel.onPause();
                homeTab = asTab || panel == null;
                home = new ultra.home.HomeScreen(a, homeMount);
                if (panel == null) backBridge("install", a);
                Diagnostics.event("home", homeTab ? "open-tab" : "open-layer");
                timeline("openHome built a new Home (" + (homeTab ? "tab" : "layer") + ")");
                healHome(home, 0);
            }
        });
    }

    /** Home mount visible and last in fragment_container, so no host page view added later can cover it. */
    static void homeOnTop() {
        FrameLayout m = homeMount;
        if (m == null) return;
        m.setVisibility(View.VISIBLE);
        if (m.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) m.getParent();
            if (parent.indexOfChild(m) != parent.getChildCount() - 1) m.bringToFront();
        }
    }

    static final long[] HEAL_MS = {800, 2500};
    /** Self-checks after an open, for the same Home instance only; a rebuild happens at most once per open. */
    static void healHome(final ultra.home.HomeScreen screen, final int step) {
        if (step >= HEAL_MS.length) return;
        MAIN.postDelayed(new Runnable() {
            public void run() {
                if (home != screen || screen == null) return;
                FrameLayout m = homeMount;
                if (m == null || !m.isAttachedToWindow() || m.getParent() == null) {
                    Diagnostics.event("home_heal", "rebuild");
                    timeline("self-check: Home mount detached, rebuilding");
                    boolean tab = homeTab;
                    closeHome(false);
                    openHome(tab);
                    return;
                }
                ViewGroup parent = (ViewGroup) m.getParent();
                if (m.getVisibility() != View.VISIBLE || parent.indexOfChild(m) != parent.getChildCount() - 1) {
                    homeOnTop();
                    Diagnostics.event("home_heal", "front");
                    timeline("self-check: Home was covered, brought to front");
                }
                if (!screen.hasContent() && !screen.isLoading()) {
                    screen.refresh();
                    Diagnostics.event("home_heal", "reload");
                    timeline("self-check: Home had no content, reloading");
                }
                healHome(screen, step + 1);
            }
        }, HEAL_MS[step]);
    }

    /** Back from Home: a layer closes and Downloads resumes; the Home tab returns to Library like Downloads does. */
    public static void closeHome() {
        if (home != null && homeTab) {homeBackPending = true; returnToContent(); homeBackPending = false; return;}
        closeHome(true);
    }

    static void closeHome(boolean resumeDownloads) {
        if (home != null) timeline("Home closed" + (resumeDownloads ? " (back to Downloads)" : ""));
        if (home != null) {
            home.destroy();
            home = null;
        }
        homeTab = false;
        if (homeMount != null) {
            if (homeMount.getParent() instanceof ViewGroup) ((ViewGroup) homeMount.getParent()).removeView(homeMount);
            homeMount = null;
            if (resumeDownloads && panel != null) panel.onResume();
            else if (resumeDownloads && panel == null) backBridge("remove", activity.get());
        }
    }

    /** Leaves Home for the Downloads screen on a tab and group, reusing the open Downloads screen when there is one. */
    public static void showDownloads(final String tab, final String group) {
        MAIN.post(new Runnable() {
            public void run() {
                closeHome(false);
                if (panel != null) {panel.show(tab, group); panel.onResume();}
                else open("", tab, group);
            }
        });
    }

    static int id(Activity a, String name) {
        int value = a.getResources().getIdentifier(name, "id", a.getPackageName());
        return value != 0 ? value : name.equals("fragment_container") ? 0x7f0b04e1 : 0;
    }

    public static void browse(String id) {}

    public static Object rewriteUpsell(Object item, Object model, Object helper) {
        try {
            return NativeModels.rewriteUpsell(item, model, helper);
        } catch (Throwable ignored) {
            return item;
        }
    }

    public static void togglePin(String playlistId) {
        Activity a = activity.get();
        Context c = a;
        if (c == null) {
            try {
                c = Hooks.context();
            } catch (Throwable ignored) {}
        }
        if (c != null && playlistId != null && !playlistId.isEmpty()) {
            boolean pinned = PinManager.togglePin(c, playlistId);
            Hooks.message(pinned ? "Pinned to Library" : "Unpinned from Library");
            NativeLibrarySelection.notifyAdapterChanged();
        }
    }
}
