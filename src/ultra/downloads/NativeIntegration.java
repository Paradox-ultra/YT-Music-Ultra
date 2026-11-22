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
        if (panel != null) {
            Bundle state = new Bundle();
            panel.onSaveInstanceState(state);
            out.putBundle("ultra_downloads_state", state);
        }
    }

    public static void restore(Bundle state) {
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

    public static boolean downloadsVisible(){return panel!=null;}

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

    public static void open(final String playlist) {
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
        navigation.clear();
        closePanel();
    }

    public static void closePanel() {
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
