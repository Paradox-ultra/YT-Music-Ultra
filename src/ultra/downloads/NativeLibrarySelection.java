package ultra.downloads;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/**
 * Native Library multi-selection binding grounded in shipped host smali:
 * - Observes native presenter bindings via RecyclerView adapter (azwh) observer (azvt).
 * - Dynamic Proxy implements hashCode/equals/toString to prevent HashSet NPEs.
 * - presenter.a() returns exact bound View; actualModel wraps bqtu in oez.a.
 * - Model extraction handled via default-package bridge (UltraLibrarySelectionBridge).
 * - Normal tap behavior outside selection mode is strictly preserved. If hidden API
 *   click lookup fails, native click listener is never overwritten.
 * - Recycled rows with null ID clear selection wrapper, tags, and checkboxes.
 * - Adapter change unregisters old observer and registers on the new adapter.
 * - Select-all covers entire loaded adapter roster via adapter.getItem(index).
 * - Native Play uses existing NativePlayback.start(context, all saved IDs, 0).
 */
public final class NativeLibrarySelection {
    private NativeLibrarySelection() {}
    static final Set<String> evidence=new HashSet<>();static void mark(String code){if(evidence.add(code))Diagnostics.event("selection",code);}

    private static boolean active;
    private static final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private static WeakReference<View> currentRoot = new WeakReference<>(null);
    private static WeakReference<ViewGroup> currentRecycler = new WeakReference<>(null);
    private static WeakReference<Object> currentAdapter = new WeakReference<>(null);
    private static Object registeredObserverProxy = null;
    private static LinearLayout selectionBar;
    private static TextView selectionCountText;
    private static final LinkedHashMap<String,View> actionViews=new LinkedHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final int TAG_ORIGINAL_LISTENER = 0x7e020001;
    private static final int TAG_MODEL_ID = 0x7e020002;
    private static final String TAG_CHECKBOX = "native_lib_cb";
    private static final String TAG_OVERLAY="native_lib_selection_touch";
    private static int originalListPadding;private static boolean padded;

    public static boolean owns(View root) { return currentRoot.get()==root; }

    public static boolean isActive() {
        return active;
    }

    public static int count() {
        return selectedIds.size();
    }

    public static void exit() {
        active = false;
        selectedIds.clear();
        View root = currentRoot.get();
        if (selectionBar != null && selectionBar.getParent() instanceof ViewGroup) {
            ((ViewGroup) selectionBar.getParent()).removeView(selectionBar);
        }
        ViewGroup list=currentRecycler.get();if(padded&&list!=null)list.setPadding(list.getPaddingLeft(),originalListPadding,list.getPaddingRight(),list.getPaddingBottom());padded=false;
        selectionBar = null; actionViews.clear();
        updateCheckboxes(root);
    }

    public static void notifyAdapterChanged() {
        MAIN.post(new Runnable() {
            public void run() {
                Object adapter = currentAdapter.get();
                View root = currentRoot.get();
                Context c = root != null ? root.getContext() : null;
                if (c == null) {
                    try { c = Hooks.context(); } catch (Throwable ignored) {}
                }
                if (adapter != null && c != null) {
                    try {
                        Class<?> bridge = Class.forName("UltraNativeIdentity");
                        bridge.getMethod("reorderAdapter", Object.class, Context.class).invoke(null, adapter, c);
                    } catch (Throwable ignored) {}

                    try {
                        Method eb = adapter.getClass().getMethod("eb");
                        eb.invoke(adapter);
                        return;
                    } catch (Throwable ignored) {}
                    try {
                        Method p = adapter.getClass().getMethod("p");
                        p.invoke(adapter);
                        return;
                    } catch (Throwable ignored) {}
                    try {
                        Method notify = adapter.getClass().getMethod("notifyDataSetChanged");
                        notify.invoke(adapter);
                    } catch (Throwable ignored) {}
                }
                if (root != null) root.requestLayout();
            }
        });
    }

    public static void unbind() {
        Object adapter = currentAdapter.get();
        if (adapter != null && registeredObserverProxy != null) {
            try {
                Class<?> azvtClass = Class.forName("azvt");
                Method h = adapter.getClass().getMethod("h", azvtClass);
                h.invoke(adapter, registeredObserverProxy);
            } catch (Throwable ignored) {}
        }
        registeredObserverProxy = null;
        exit();
        currentAdapter.clear();
        currentRecycler.clear();
        currentRoot.clear();
    }

    public static void bind(Object page, View root) {
        if (root == null) return;
        if(currentRoot.get()!=null&&currentRoot.get()!=root)unbind();
        currentRoot = new WeakReference<>(root);
        ViewGroup list = findList(root);
        if (list == null) {mark("list-not-found");return;}
        mark("native-list-found");
        currentRecycler = new WeakReference<>(list);

        try {
            final Object newAdapter = nativeAdapter(list);
            if(newAdapter!=null)mark("adapter-"+newAdapter.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT));
            if (newAdapter == null) return;

            Object oldAdapter = currentAdapter.get();
            Class<?> azvtClass = Class.forName("azvt");

            // If adapter changed, unregister old observer first
            if (oldAdapter != newAdapter && registeredObserverProxy != null) {
                try {
                    Method h = oldAdapter.getClass().getMethod("h", azvtClass);
                    h.invoke(oldAdapter, registeredObserverProxy);
                } catch (Throwable ignored) {}
                registeredObserverProxy = null;
            }
            currentAdapter = new WeakReference<>(newAdapter);
            // Litho has its own RecyclerView adapter and can retain already-bound
            // holders when azwh is replaced. Invalidating azwh does not redraw
            // these holders. Redraw the real outer adapter after a replacement.
            if(oldAdapter!=newAdapter)redrawOuter(list);

            try { Class.forName("UltraNativeIdentity").getMethod("reorderAdapter",Object.class,Context.class).invoke(null,newAdapter,root.getContext()); } catch(Throwable ignored) {}

            // Register dynamic proxy for azvt observer on new adapter
            if (registeredObserverProxy == null) {
                registeredObserverProxy = Proxy.newProxyInstance(
                    azvtClass.getClassLoader(),
                    new Class<?>[]{azvtClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("hashCode".equals(name)) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(name)) {
                                return args != null && args.length > 0 && proxy == args[0];
                            }
                            if ("toString".equals(name)) {
                                return "UltraLibrarySelectionObserver@" + Integer.toHexString(System.identityHashCode(proxy));
                            }
                            if ("a".equals(name) && args != null && args.length >= 2) {
                                Object presenter = args[0];
                                Object model = args[1];
                                onNativeBind(presenter, model);
                                return null;
                            }
                            return null;
                        }
                    }
                );
                Method f = newAdapter.getClass().getMethod("f", azvtClass);
                f.invoke(newAdapter, registeredObserverProxy);
            }

            // Bind initial visible rows via RecyclerView.ff(child) -> uo.c position
            int childCount = list.getChildCount();
            Method ffMethod = null;
            try { ffMethod = list.getClass().getMethod("ff", View.class); } catch (Throwable ignored) {}
            Method getItemMethod = newAdapter.getClass().getMethod("getItem", int.class);

            for (int i = 0; i < childCount; i++) {
                View child = list.getChildAt(i);
                if (child == null) continue;
                if (ffMethod != null) {
                    try {
                        Object holder = ffMethod.invoke(list, child);
                        if (holder != null) {
                            Field fc = holder.getClass().getField("c");
                            int pos = fc.getInt(holder);
                            if (pos >= 0) {
                                Object model = getItemMethod.invoke(newAdapter, pos);
                                bindViewWithModel(child, model);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable problem) {Diagnostics.error("selection_bind",problem);}
    }

    // The vertical Litho list wraps azwh in hru -> hrx -> bame.
    // Binding only direct azwh finds the horizontal header, not Library rows.
    static Object nativeAdapter(View v) {
        try {
            Object a=Class.forName("android.support.v7.widget.RecyclerView").getField("n").get(v);
            if(a==null)return null;
            if(a.getClass().getName().equals("hru")) {
                Object controller=Class.forName("hru").getField("d").get(a);
                Object bridge=Class.forName("hrx").getField("d").get(controller);
                if(!Class.forName("bame").isInstance(bridge))return null;
                a=Class.forName("bame").getField("b").get(bridge);
            }
            return Class.forName("azwh").isInstance(a)?a:null;
        }catch(Throwable ignored){return null;}
    }
    private static ViewGroup findList(View v) {
        if(v==null)return null;ViewGroup best=null;
        if(v instanceof ViewGroup && LibraryViewClean.list(v) && nativeAdapter(v)!=null)best=(ViewGroup)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){
            ViewGroup found=findList(g.getChildAt(i));
            if(found!=null&&(best==null||found.getHeight()>best.getHeight()))best=found;
        }}return best;
    }

    static boolean redrawPending;
    static void redrawOuter(final ViewGroup list) {
        if(list==null||redrawPending)return;redrawPending=true;list.post(new Runnable(){public void run(){redrawPending=false;try{Object outer=Class.forName("android.support.v7.widget.RecyclerView").getField("n").get(list);outer.getClass().getMethod("eb").invoke(outer);}catch(Throwable ignored){}}});
    }
    static void checkBoundOrder(View view,Object model){
        ViewGroup list=currentRecycler.get();if(list==null)return;
        try{View child=view;while(child.getParent() instanceof View && child.getParent()!=list)child=(View)child.getParent();if(child.getParent()!=list)return;Object holder=list.getClass().getMethod("ff",View.class).invoke(list,child);if(holder==null)return;int pos=holder.getClass().getField("c").getInt(holder);Object matches=Class.forName("UltraNativeIdentity").getMethod("bindingMatches",Object.class,int.class,Object.class).invoke(null,currentAdapter.get(),pos,model);if(Boolean.FALSE.equals(matches))redrawOuter(list);}catch(Throwable ignored){}
    }

    private static void onNativeBind(Object presenter, Object model) {
        if (presenter == null) return;
        try {
            Method getA = presenter.getClass().getMethod("a");getA.setAccessible(true);
            Object v = getA.invoke(presenter);
            if (v instanceof View) {
                try{Object changed=Class.forName("UltraNativeIdentity").getMethod("maintainAdapterOrder",Object.class,Context.class).invoke(null,currentAdapter.get(),((View)v).getContext());if(Boolean.TRUE.equals(changed))redrawOuter(currentRecycler.get());}catch(Throwable ignored){}
                checkBoundOrder((View)v,model);
                bindViewWithModel((View) v, model);
            }
        } catch (Throwable ignored) {}
    }

    private static void bindViewWithModel(final View view, Object model) {
        if (view == null) return;
        final String id = extractExactId(model);
        if(model!=null)mark((id==null?"unknown-model-":"known-model-")+model.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT));

        // If recycled row has null ID, clear selection wrapper, tag and checkbox
        if (id == null || id.isEmpty()) {
            view.setTag(TAG_MODEL_ID, null);
            View.OnClickListener orig = (View.OnClickListener) view.getTag(TAG_ORIGINAL_LISTENER);
            if (orig != null) {
                view.setOnClickListener(orig);
                view.setTag(TAG_ORIGINAL_LISTENER, null);
            }
            view.setOnLongClickListener(null);
            View cb = view.findViewWithTag(TAG_OVERLAY);
            if (cb != null) cb.setVisibility(View.GONE);
            return;
        }

        view.setTag(TAG_MODEL_ID, id);

        final View.OnLongClickListener longClick=new View.OnLongClickListener(){public boolean onLongClick(View ignored){String key=(String)view.getTag(TAG_MODEL_ID);if(key==null)return false;if(!active){active=true;selectedIds.clear();selectedIds.add(key);View root=currentRoot.get();if(root!=null)showSelectionBar(root);updateCheckboxes(root);}else toggle(key);return true;}};
        bindLongClicks(view,longClick,true);

        updateRowCheckbox(view, id);
    }

    private static void bindLongClicks(View view,View.OnLongClickListener action,boolean root){
        if(TAG_OVERLAY.equals(view.getTag())||TAG_CHECKBOX.equals(view.getTag()))return;
        // Non-clickable title/art descendants must not consume a normal tap that
        // belongs to the native row. Long-click-only children swallowed ACTION_UP.
        if(root||view.hasOnClickListeners())view.setOnLongClickListener(action);
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)bindLongClicks(g.getChildAt(i),action,false);}
    }

    private static View.OnClickListener getClickListener(View view) {
        try {
            Field f = View.class.getDeclaredField("mListenerInfo");
            f.setAccessible(true);
            Object info = f.get(view);
            if (info != null) {
                Field cf = info.getClass().getDeclaredField("mOnClickListener");
                cf.setAccessible(true);
                return (View.OnClickListener) cf.get(info);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static final class SelectionClickWrapper implements View.OnClickListener {
        private final WeakReference<View> rowRef;

        SelectionClickWrapper(View row) {
            this.rowRef = new WeakReference<>(row);
        }

        @Override
        public void onClick(View v) {
            if (active) {
                String id = (String) v.getTag(TAG_MODEL_ID);
                if (id != null) toggle(id);
            } else {
                View.OnClickListener original = (View.OnClickListener) v.getTag(TAG_ORIGINAL_LISTENER);
                if (original != null) {
                    original.onClick(v);
                } else {
                    // No recursive fallback: retain native listener when lookup is unavailable.
                }
            }
        }
    }

    public static String extractExactId(Object model) {
        if (model == null) return null;
        try {
            Class<?> bridge = Class.forName("UltraNativeIdentity");
            Method m = bridge.getMethod("id", Object.class);
            return (String) m.invoke(null, model);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void toggle(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
            if (selectedIds.isEmpty()) {
                exit();
                return;
            }
        } else {
            selectedIds.add(id);
        }
        updateSelectionTitle();
        updateCheckboxes(currentRoot.get());
    }

    private static void selectAllLoaded() {
        Object adapter = currentAdapter.get();
        if (adapter == null) return;
        try {
            int total = (Integer) adapter.getClass().getMethod("a").invoke(adapter);
            Method getItem = adapter.getClass().getMethod("getItem", int.class);

            boolean allSelected = true;
            ArrayList<String> allIds = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                Object model = getItem.invoke(adapter, i);
                String id = extractExactId(model);
                if (id != null && !id.isEmpty()) {
                    allIds.add(id);
                    if (!selectedIds.contains(id)) allSelected = false;
                }
            }

            if (allSelected) {
                exit();
            } else {
                selectedIds.addAll(allIds);
                updateSelectionTitle();
                updateCheckboxes(currentRoot.get());
            }
        } catch (Throwable ignored) {}
    }

    private static void updateSelectionTitle() {
        if (selectionCountText != null) {
            selectionCountText.setText(selectedIds.size() + " selected");
            updateActions();
        }
    }

    private static void showSelectionBar(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup container = (ViewGroup) root;

        if (selectionBar != null && selectionBar.getParent() instanceof ViewGroup) {
            ((ViewGroup) selectionBar.getParent()).removeView(selectionBar);
        }

        Context c = root.getContext();
        int primary = NativeUi.primary(c);
        int dp4 = NativeUi.dp(c, 4), dp8 = NativeUi.dp(c, 8), dp16 = NativeUi.dp(c, 16);

        selectionBar = new LinearLayout(c);
        selectionBar.setOrientation(LinearLayout.VERTICAL);
        selectionBar.setBackground(SelectionStyle.panel(c));
        selectionBar.setElevation(dp8);
        selectionBar.setClickable(true);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp8, dp4, dp8, dp4);
        top.setMinimumHeight(NativeUi.dp(c, 56));

        ImageButton closeBtn = NativeUi.iconButton(c, "yt_outline_x_mark_vd_theme_24", "close", "Exit selection mode", new View.OnClickListener() {
            public void onClick(View v) { exit(); }
        });
        top.addView(closeBtn, new LinearLayout.LayoutParams(NativeUi.dp(c, 48), NativeUi.dp(c, 48)));

        selectionCountText = NativeUi.header(c, selectedIds.size() + " selected");
        top.addView(selectionCountText, new LinearLayout.LayoutParams(0, -2, 1));

        Button selectAll = new Button(c, null, android.R.attr.borderlessButtonStyle);
        selectAll.setText("Select all");
        selectAll.setTextColor(primary);
        selectAll.setTextSize(14);
        selectAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectAllLoaded(); }
        });
        top.addView(selectAll, new LinearLayout.LayoutParams(-2, -2));
        selectionBar.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // Action icons row
        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp16, dp4, dp16, dp8);

        actions.addView(actionBtn(c, "yt_outline_playlist_add_vd_theme_24", "playlist", "Add to playlist", new View.OnClickListener() {
            public void onClick(View v) { handleLocalPlaylist(v.getContext()); }
        }));
        actions.addView(actionBtn(c, "yt_outline_play_arrow_vd_theme_24", "play", "Play", new View.OnClickListener() {
            public void onClick(View v) { handlePlay(v.getContext()); }
        }));
        actions.addView(actionBtn(c, "yt_outline_download_vd_theme_24", "download", "Download", new View.OnClickListener() {
            public void onClick(View v) { handleDownload(v.getContext()); }
        }));
        actions.addView(actionBtn(c, "yt_outline_pin_vd_theme_24", "pin", "Pin/Unpin", new View.OnClickListener() {
            public void onClick(View v) { handlePin(v.getContext()); }
        }));
        actions.addView(actionBtn(c, "yt_outline_share_vd_theme_24", "share", "Share", new View.OnClickListener() {
            public void onClick(View v) { handleShare(v.getContext()); }
        }));


        HorizontalScrollView scroll = new HorizontalScrollView(c); scroll.setHorizontalScrollBarEnabled(true); scroll.addView(actions,new ViewGroup.LayoutParams(-2,-2)); selectionBar.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        if(container instanceof LinearLayout)container.addView(selectionBar,0,new ViewGroup.LayoutParams(-1,-2));
        else {container.addView(selectionBar,new ViewGroup.LayoutParams(-1,-2));ViewGroup list=currentRecycler.get();if(list!=null&&!padded){originalListPadding=list.getPaddingTop();list.setPadding(list.getPaddingLeft(),originalListPadding+NativeUi.dp(c,112),list.getPaddingRight(),list.getPaddingBottom());padded=true;}}
        selectionBar.bringToFront();updateActions();
    }

    private static View actionBtn(Context c, String icon, String glyph, String label, View.OnClickListener act) {
        View box=SelectionStyle.action(c,icon,glyph,label,act);
        actionViews.put(label,box);box.setTag("native_action:"+label);
        return box;
    }

    static boolean songId(String id){return id!=null&&!id.startsWith("VL")&&!id.startsWith("PL")&&!id.startsWith("UC")&&!id.startsWith("MPRE")&&id.matches("[A-Za-z0-9_-]{11}");}

    private static void updateActions(){
        Context c=currentRoot.get()==null?null:currentRoot.get().getContext();boolean songs=!selectedIds.isEmpty(),saved=songs,pinnable=songs,downloadable=songs;
        for(String id:selectedIds){boolean song=songId(id);songs &= song;downloadable &= song||id.startsWith("VL")||id.startsWith("PL")||id.startsWith("MPRE"); pinnable &= id.startsWith("VL")||id.startsWith("PL")||id.startsWith("MPRE")||id.startsWith("UC");try{Library.Item item=c==null?null:Library.get(c,id);saved &= item!=null&&Storage.exists(c,item.uri);}catch(Throwable e){saved=false;}}
        for(Map.Entry<String,View> e:actionViews.entrySet()){String k=e.getKey();boolean show=k.equals("Share")||k.equals("Pin/Unpin")&&pinnable||k.equals("Play")&&saved||k.equals("Add to playlist")&&songs||k.equals("Download")&&downloadable&&!saved;e.getValue().setVisibility(show?View.VISIBLE:View.GONE);}
    }

    private static void updateCheckboxes(View root) {
        ViewGroup list = currentRecycler.get();
        if (list == null && root != null) list = findList(root);
        if (list == null) return;

        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            String id = (String) child.getTag(TAG_MODEL_ID);
            updateRowCheckbox(child, id);
        }
    }

    private static void updateRowCheckbox(View row, String id) {
        if (row == null || !(row instanceof ViewGroup)) return;
        ViewGroup g=(ViewGroup)row;
        View old=g.findViewWithTag(TAG_OVERLAY);
        if(!active||id==null){if(old!=null)old.setVisibility(View.GONE);return;}
        FrameLayout cover=old instanceof FrameLayout?(FrameLayout)old:null;
        if(cover==null){cover=new FrameLayout(row.getContext());cover.setTag(TAG_OVERLAY);cover.setClickable(true);cover.setOnTouchListener(new SelectionTouch(row));cover.setElevation(NativeUi.dp(row.getContext(),8));cover.setOnClickListener(new View.OnClickListener(){public void onClick(View v){String key=(String)row.getTag(TAG_MODEL_ID);if(key!=null)toggle(key);}});CheckBox cb=new CheckBox(row.getContext());cb.setTag(TAG_CHECKBOX);cb.setClickable(false);cb.setFocusable(false);cb.setBackgroundColor(NativeUi.color(row.getContext(),"ytm_color_background",0xff212121));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(NativeUi.dp(row.getContext(),48),NativeUi.dp(row.getContext(),48),Gravity.START|Gravity.CENTER_VERTICAL);cp.setMarginStart(NativeUi.dp(row.getContext(),8));cover.addView(cb,cp);g.addView(cover,new ViewGroup.LayoutParams(-1,-1));}
        cover.setVisibility(View.VISIBLE);((CheckBox)cover.findViewWithTag(TAG_CHECKBOX)).setChecked(selectedIds.contains(id));cover.bringToFront();
    }

    static ViewGroup scrollTarget(View overlay,ViewGroup fallback){
        View v=overlay;
        for(int depth=0;v!=null&&depth<24;depth++){
            if(v instanceof ViewGroup&&(v.canScrollVertically(1)||v.canScrollVertically(-1)))return (ViewGroup)v;
            v=v.getParent() instanceof View?(View)v.getParent():null;
        }
        return fallback;
    }

    /** Keep the added selection layer from eating the native list's drag gestures. */
    private static final class SelectionTouch implements View.OnTouchListener {
        private final View row; private ViewGroup target; private float downX,downY,lastY; private boolean moved;
        SelectionTouch(View row){this.row=row;}
        public boolean onTouch(View overlay,MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){target=scrollTarget(overlay,currentRecycler.get());Diagnostics.event("selection_touch",target==null?"no-target":target.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT)+":"+target.getHeight()+":"+target.canScrollVertically(1));downX=e.getRawX();downY=e.getRawY();lastY=downY;moved=false;if(overlay.getParent()!=null)overlay.getParent().requestDisallowInterceptTouchEvent(false);}
            if(target==null)return false;
            int slop=ViewConfiguration.get(overlay.getContext()).getScaledTouchSlop();
            if(Math.abs(e.getRawX()-downX)>slop||Math.abs(e.getRawY()-downY)>slop)moved=true;
            if(action==MotionEvent.ACTION_MOVE&&moved){target.scrollBy(0,Math.round(lastY-e.getRawY()));}
            lastY=e.getRawY();
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(overlay.getParent()!=null)overlay.getParent().requestDisallowInterceptTouchEvent(false);
                if(action==MotionEvent.ACTION_UP&&!moved){String id=(String)row.getTag(TAG_MODEL_ID);if(id!=null)toggle(id);}
                target=null;
            }
            return true;
        }
    }

    private static void handlePlay(Context c) {
        final ArrayList<String> playable = new ArrayList<>();
        for (String id : selectedIds) {
            if (songId(id)) {
                playable.add(id);
            }
        }
        if (playable.isEmpty()) {
            Hooks.message("Select songs to play");
            return;
        }
        try {
            NativePlayback.start(c, playable, 0);
            Hooks.message("Playing " + playable.size() + " songs");
            exit();
        } catch (Exception e) {
            Hooks.message("Play failed: " + e.getMessage());
        }
    }

    private static void handleDownload(final Context c) {
        final ArrayList<Catalog.Track> tracks = new ArrayList<>();
        int partialOrMissing = 0;

        for (String id : selectedIds) {
            if (songId(id)) {
                tracks.add(new Catalog.Track(id, id, "", ""));
            } else if (id.startsWith("VL") || id.startsWith("PL") || id.startsWith("MPRE") || id.startsWith("local:")) {
                String norm = PlaylistIndex.key(id);
                if (!PlaylistIndex.complete(c, norm)) {
                    partialOrMissing++;
                }
                ArrayList<Library.Item> list; try { list = PlaylistIndex.ordered(c, norm, Library.all(c), true); } catch(Exception e) { Hooks.message("Playlist cannot be read"); return; }
                for (Library.Item i : list) {
                    if (songId(i.id)) {
                        tracks.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
            }
        }

        if (partialOrMissing > 0) { Hooks.message("Sync selected playlists completely before downloading"); return; }
        if (tracks.isEmpty()) {
            Hooks.message("No downloadable songs in selection");
            return;
        }

        String msg = "Download " + tracks.size() + " songs in 320 kbps MP3?";
        if (partialOrMissing > 0) {
            msg += "\n(Note: " + partialOrMissing + " selected playlists have un-synced or missing items)";
        }

        NativeUi.dialog(c)
            .setTitle("Download selected")
            .setMessage(msg)
            .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        int queued = Library.addBatch(c, tracks);
                        Intent start = new Intent(c, DownloadsService.class);
                        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(start);
                        else c.startService(start);
                        Hooks.message("Queued " + queued + " songs for download");
                        exit();
                    } catch (Exception e) {
                        Hooks.message("Download failed: " + e.getMessage());
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static void handlePin(Context c) {
        int pinned = 0, unpinned = 0;
        for (String id : selectedIds) {
            if (id.startsWith("VL") || id.startsWith("MPRE") || id.startsWith("PL") || id.startsWith("UC") || "LM".equals(id)) {
                boolean now = PinManager.togglePin(c, id);
                if (now) pinned++;
                else unpinned++;
            }
        }
        if (pinned == 0 && unpinned == 0) {
            Hooks.message("Pin/Unpin is available for playlists and albums");
        } else {
            Hooks.message((pinned > 0 ? pinned + " pinned. " : "") + (unpinned > 0 ? unpinned + " unpinned." : ""));
            notifyAdapterChanged();
        }
        exit();
    }

    private static void handleShare(Context c) {
        if (selectedIds.isEmpty()) return;
        Intent intent;
        if (selectedIds.size() == 1) {
            String id = selectedIds.iterator().next();
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            String url = id.startsWith("VL") || id.startsWith("PL") ? "https://music.youtube.com/playlist?list=" + PlaylistIndex.key(id) :
                         id.startsWith("MPRE") ? "https://music.youtube.com/browse/" + id :
                         id.startsWith("UC") ? "https://music.youtube.com/channel/" + id :
                         "https://music.youtube.com/watch?v=" + id;
            intent.putExtra(Intent.EXTRA_TEXT, url);
        } else {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            StringBuilder sb = new StringBuilder();
            for (String id : selectedIds) {
                String url = id.startsWith("VL") || id.startsWith("PL") ? "https://music.youtube.com/playlist?list=" + PlaylistIndex.key(id) :
                             id.startsWith("MPRE") ? "https://music.youtube.com/browse/" + id :
                             id.startsWith("UC") ? "https://music.youtube.com/channel/" + id :
                             "https://music.youtube.com/watch?v=" + id;
                sb.append(url).append("\n");
            }
            intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            c.startActivity(Intent.createChooser(intent, "Share " + selectedIds.size() + " items"));
            exit();
        } catch (Exception e) {
            Hooks.message("Share failed: " + e.getMessage());
        }
    }

    private static void handleLocalPlaylist(final Context c) {
        final ArrayList<Catalog.Track> tracks = new ArrayList<>();
        for (String id : selectedIds) {
            if (songId(id)) {
                try{Library.Item item=Library.get(c,id);tracks.add(item==null?new Catalog.Track(id,id,"",""):new Catalog.Track(item.id,item.title,item.artist,item.album));}catch(Exception e){Hooks.message("Could not read selected songs");return;}
            }
        }
        if (tracks.isEmpty()) {
            Hooks.message("Select songs to add to a local playlist");
            return;
        }

        PlaylistPicker.show(c,tracks,new Runnable(){public void run(){exit();}});
    }
}
