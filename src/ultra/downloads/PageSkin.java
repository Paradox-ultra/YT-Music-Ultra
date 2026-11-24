package ultra.downloads;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * DL38: the Quiet Premium surfaces (Palette) on the host's own pages, matched against the resource ids seen on the
 * device (uiautomator dumps, DL37/DL39). Every id is resolved BY NAME at runtime with
 * getIdentifier(name, "id", "app.rvx.android.apps.youtube.musiy"); no numeric id or obfuscated class is used, and a
 * name the installed host does not have simply resolves to nothing.
 *
 * <ul>
 *  <li>Library: header_container / app_bar surfaces and header type, chip_cloud chips as coral / soft pills, the sort
 *      row, rows (two_column_item_content_parent) as soft rounded cards with rounded thumbnails, title / subtitle
 *      typography, the Radio row as the featured coral card, floating_action_button coral.</li>
 *  <li>Search: suggestion_list rows (edit_suggestion / text / search_type_icon) recoloured, two_row_item results as
 *      cards with rounded art, shelf_divider hairlines, the query field as a soft pill.</li>
 *  <li>Explore: the feed is Litho and exposes no ids, so it is matched by structure under section_list_content /
 *      browse_content: half-width category cards (2x2 grid), carousel tiles (children of horizontal lists) and
 *      full-width chart rows (thumbnail + text) become cards; images get rounded corners.</li>
 *  <li>Shell: mini_player as a compact card, coral round mini_player_play_pause_replay_button, coral progress, title /
 *      subtitle type, previous / next tinted.</li>
 * </ul>
 * Never touched: pivot_bar (the tab bar is host-side), player_page, the module's own mounts (Downloads, Ultra Home).
 * Rows keep their layout: cards are InsetDrawable backgrounds. Everything replaced is remembered and restored when
 * Download settings > "App page style" is switched off. Each pass reports per-page counts to Diagnostics, e.g.
 * page_skin#12:library=9,search=0,explore=4,shell=5,ids=55/60 — a pass never reports an empty "nothing matched".
 */
public final class PageSkin {
    private PageSkin(){}
    static final String PREF="ultra_page_style",HOST_PACKAGE="app.rvx.android.apps.youtube.musiy";
    static final String[] LIBRARY_IDS={"header_container","app_bar","avatar","avatar_menu_button","chip_cloud","chip_contents","chip_cloud_chip_text","music_sort_filter_button_container","music_sort_filter_button","two_column_item_highlight","two_column_item_content_parent","two_column_item_content","title","subtitle","subtitle_badges_container","thumbnail","thumbnail_parent","thumbnail_overlay_parent","thumbnail_index_container","details_container","contextual_menu_container","contextual_menu_anchor","swipe_layout","music_item_chrome","music_play_button","overlay","floating_action_button"};
    static final String[] SEARCH_IDS={"suggestion_list","edit_suggestion","search_type_icon","text","two_row_item","title_layout","thumbnail_container","thumbnail_outer_container","icon","text1","shelf_divider","voice_search_container","voice_search","sound_search","toolbar_back_navigation"};
    static final String[] EXPLORE_IDS={"browse_content","section_list_content","tabbed_view","tabbed_view_container","app_bar_layout","collapsing_toolbar_layout","toolbar","action_search_button"};
    static final String[] SHELL_IDS={"pivot_bar","mini_player","mini_player_metadata","mini_player_title","mini_player_subtitle","mini_player_previous_button","mini_player_play_pause_replay_button","mini_player_next_button","watch_while_layout","player_page","fragment_container"};

    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static WeakReference<View> decor=new WeakReference<>(null);
    static final WeakHashMap<View,Drawable> original=new WeakHashMap<>();
    static final WeakHashMap<View,Integer> originalColor=new WeakHashMap<>();
    static final WeakHashMap<View,Typeface> originalFace=new WeakHashMap<>();
    static final WeakHashMap<View,ColorStateList> originalTint=new WeakHashMap<>();
    static final WeakHashMap<View,Boolean> chipSelected=new WeakHashMap<>();
    /** Thumbnails this class clipped (rounded or round); unclipped again when the style is switched off. */
    static final WeakHashMap<View,Boolean> rounded=new WeakHashMap<>();
    /** "Ultra" labels added next to host logos (kept from DL37); removed when the style is switched off. */
    static final WeakHashMap<View,Boolean> added=new WeakHashMap<>();
    /** Tests only: name -> id for views created without host resources. */
    static final Map<String,Integer> testIds=new HashMap<>();
    static final int TAG_KEY=0x7e020101,BUDGET=20000;
    static boolean scheduled;
    static long passes;
    static Ids ids;
    /** Per-element counts of the last pass, grouped by page ("library.rows" ...). */
    static final LinkedHashMap<String,Integer> report=new LinkedHashMap<>();

    public static boolean enabled(Context c){try{return Library.prefs(c).getBoolean(PREF,true);}catch(Throwable e){return true;}}
    static void set(Context c,boolean on){Library.prefs(c).edit().putBoolean(PREF,on).apply();View d=decor.get();if(on){if(d!=null)apply(d);}else restoreAll();}

    static final Runnable PASS=new Runnable(){public void run(){scheduled=false;View d=decor.get();if(d!=null)apply(d);}};
    static void schedule(){if(scheduled)return;scheduled=true;MAIN.postDelayed(PASS,250);}
    static final ViewTreeObserver.OnGlobalLayoutListener LAYOUT=new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){schedule();}};
    static final ViewTreeObserver.OnScrollChangedListener SCROLL=new ViewTreeObserver.OnScrollChangedListener(){public void onScrollChanged(){schedule();}};

    public static void start(Activity a){
        View next=a.getWindow().getDecorView();if(decor.get()==next)return;stop();
        decor=new WeakReference<>(next);
        try{ViewTreeObserver o=next.getViewTreeObserver();o.addOnGlobalLayoutListener(LAYOUT);o.addOnScrollChangedListener(SCROLL);}catch(Throwable ignored){}
        schedule();
    }
    public static void stop(){
        View d=decor.get();
        if(d!=null)try{ViewTreeObserver o=d.getViewTreeObserver();o.removeOnGlobalLayoutListener(LAYOUT);o.removeOnScrollChangedListener(SCROLL);}catch(Throwable ignored){}
        decor.clear();MAIN.removeCallbacks(PASS);scheduled=false;
    }

    // ------------------------------------------------------------------ ids (by name, at runtime)
    /** The device's id set, resolved once per Resources object. 0 = the installed host has no such id. */
    static final class Ids{
        final Resources res;final HashMap<String,Integer> byName=new HashMap<>();final HashMap<Integer,String> byId=new HashMap<>();int resolved,expected;final ArrayList<String> missing=new ArrayList<>();
        Ids(Context c){
            res=c.getResources();
            for(String[] set:new String[][]{LIBRARY_IDS,SEARCH_IDS,EXPLORE_IDS,SHELL_IDS})for(String n:set){
                if(byName.containsKey(n))continue;expected++;
                int id=resolve(c,n);byName.put(n,id);if(id!=0){resolved++;byId.put(id,n);}else missing.add(n);
            }
        }
        int id(String name){Integer v=byName.get(name);return v==null?0:v;}
        String name(View v){int id=v.getId();return id==View.NO_ID||id==0?null:byId.get(id);}
    }
    static int resolve(Context c,String name){
        Integer t=testIds.get(name);if(t!=null)return t;
        int id=0;try{id=c.getResources().getIdentifier(name,"id",HOST_PACKAGE);}catch(Throwable ignored){}
        if(id==0&&!HOST_PACKAGE.equals(c.getPackageName()))try{id=c.getResources().getIdentifier(name,"id",c.getPackageName());}catch(Throwable ignored){}
        return id;
    }
    static Ids ids(Context c){Ids i=ids;if(i==null||i.res!=c.getResources()){i=new Ids(c);ids=i;}return i;}

    // ------------------------------------------------------------------ pass
    /** One pass over the window. Safe to call repeatedly: every step is idempotent. */
    static void apply(View root){
        if(root==null)return;
        try{
            if(!enabled(root.getContext())){restoreAll();return;}
            Palette p=Palette.of(root.getContext());Ids ids=ids(root.getContext());
            report.clear();for(String page:new String[]{"library","search","explore","shell"})report.put(page,0);contentTotal=0;
            // DL49: walk from the window root. findViewById(fragment_container) returns the FIRST container in the window,
            // which on device was not the page container (shell matched, pages never did). Exclusions keep pivot_bar,
            // player_page and the module's own mounts out; mini_player is styled separately below.
            scan=new Scan(trigger);trigger="layout";
            scan.panel=NativeIntegration.panel!=null;scan.home=NativeIntegration.home!=null;
            countContainers(root,ids,scan);
            int[] budget={BUDGET};walk(root,p,ids,Zone.NONE,budget);scan.visited=BUDGET-budget[0];scan.budgetHit=budget[0]<0;
            for(View c:containers(root,ids))if(c.isShown())surface(c,p.page);
            scan.page=scan.classify();if(!scan.page.equals("other"))report.put(scan.page,contentTotal);
            for(Map.Entry<String,Integer> e:report.entrySet())if(e.getKey().indexOf('.')>0&&!e.getKey().startsWith("shell."))scan.elements.put(e.getKey(),e.getValue());
            int miniId=ids.id("mini_player");View mini=miniId==0?null:root.findViewById(miniId);
            if(mini!=null&&mini.getVisibility()==View.VISIBLE)mini(mini,p,ids);
            passes++;scan.pass=passes;scan.summary=summary(ids);
            if(scan.interesting())history.addFirst(scan);while(history.size()>3)history.removeLast();
            Diagnostics.event("page_skin",summary(ids));
            Diagnostics.event("page_scan",scan.line());
        }catch(Throwable e){Diagnostics.error("page_skin",e);}
    }

    enum Zone{NONE,HEADER,SORT,ROW,SUGGESTIONS,RESULT,FEED}

    static boolean excluded(View v,Ids ids){
        Object tag=v.getTag();if(tag instanceof String&&(((String)tag).startsWith("ultra_")||((String)tag).startsWith("native_lib_selection")))return true;
        String n=ids.name(v);if("pivot_bar".equals(n)||"player_page".equals(n))return true;
        return named(v,"TabLayout")||named(v,"PivotBar");
    }
    static boolean named(View v,String part){for(Class<?> c=v.getClass();c!=null&&c!=View.class;c=c.getSuperclass())if(c.getSimpleName().contains(part))return true;return false;}
    static boolean list(View v){if(!(v instanceof ViewGroup))return false;if(v instanceof ListView)return true;for(Class<?> c=v.getClass();c!=null;c=c.getSuperclass())if(c.getSimpleName().equals("RecyclerView"))return true;return false;}
    static boolean horizontal(ViewGroup g){try{if(g.canScrollHorizontally(1)||g.canScrollHorizontally(-1))return true;if(g.getChildCount()>=2){View a=g.getChildAt(0),b=g.getChildAt(1);return Math.abs(a.getTop()-b.getTop())<4&&b.getLeft()>=a.getRight()-2;}}catch(Throwable ignored){}return false;}

    static void walk(View v,Palette p,Ids ids,Zone zone,int[] budget){
        if(v==null||--budget[0]<0||v.getVisibility()!=View.VISIBLE||excluded(v,ids))return;
        String n=ids.name(v);
        if("mini_player".equals(n))return;
        Scan sc=scan;if(sc!=null){sc.klass(v);if(list(v))sc.lists++;if(n!=null){sc.seen(n);if(v.isShown())sc.shown.add(n);}if(v instanceof EditText&&v.isShown())sc.shown.add("edit_text");}
        if(n!=null)switch(n){
            case "header_container":surface(v,p.page);zone=Zone.HEADER;count("library.header");break;
            case "app_bar":case "app_bar_layout":case "collapsing_toolbar_layout":surface(v,p.page);break;
            case "chip_cloud":surface(v,p.page);break;
            case "chip_cloud_chip_text":chip(v,p);count("library.chips");break;
            case "music_sort_filter_button_container":zone=Zone.SORT;count("library.sort");break;
            case "two_column_item_content_parent":libraryRow(v,p,ids);zone=Zone.ROW;count("library.rows");break;
            case "floating_action_button":fab(v,p);count("library.fab");break;
            case "suggestion_list":surface(v,p.page);zone=Zone.SUGGESTIONS;break;
            case "edit_suggestion":card(v,p,"suggestion",12,2,16,false);count("search.suggestions");break;
            case "toolbar":surface(v,p.page);zone=Zone.HEADER;count("page.toolbar");break;
            case "tabbed_view":case "tabbed_view_container":surface(v,p.page);count("page.tabs");break;
            case "contextual_menu_anchor":menu(v,p);count("library.menus");break;
            case "search_type_icon":tint(v,p.muted);break;
            case "two_row_item":searchResult(v,p,ids);zone=Zone.RESULT;count("search.results");break;
            case "shelf_divider":if(!(v.getBackground() instanceof ColorDrawable&&((ColorDrawable)v.getBackground()).getColor()==p.hairline)){remember(v);v.setBackgroundColor(p.hairline);}count("search.dividers");break;
            case "voice_search":case "sound_search":case "toolbar_back_navigation":case "action_search_button":tint(v,p.text);break;
            case "section_list_content":case "browse_content":surface(v,p.page);if(zone==Zone.NONE)zone=Zone.FEED;break;
            case "title":type((TextView)as(v,TextView.class),p.text,true);break;
            case "subtitle":case "text1":type((TextView)as(v,TextView.class),p.muted,false);break;
            case "text":type((TextView)as(v,TextView.class),zone==Zone.SUGGESTIONS?p.text:p.muted,false);break;
            case "thumbnail":round(v);break;
            default:break;
        }
        if(v instanceof EditText){field(v,p);count("search.field");}
        else if(v instanceof TextView&&n==null){
            if(zone==Zone.HEADER)type((TextView)v,p.text,true);
            else if(zone==Zone.SORT)type((TextView)v,p.text,false);
        }
        if(zone==Zone.SORT&&v instanceof ImageView&&n==null)tint(v,p.text);
        if(!(v instanceof ViewGroup))return;
        ViewGroup g=(ViewGroup)v;
        if(zone==Zone.FEED&&list(g))feed(g,p,ids);
        for(int k=0;k<g.getChildCount();k++)walk(g.getChildAt(k),p,ids,zone,budget);
    }
    static View as(View v,Class<?> type){return type.isInstance(v)?v:null;}

    // ------------------------------------------------------------------ Library
    /** The list child that holds two_column_item_content_parent becomes the card; the Radio row becomes the featured card. */
    static void libraryRow(View contentParent,Palette p,Ids ids){
        View row=listChild(contentParent);
        boolean radio=has(row,"Based on your last played song");
        card(row,p,radio?"radio":"card",12,4,20,radio);
        View thumb=find(row,ids.id("thumbnail"));
        // Artist rows: round art, like the reference (only where the host does not already clip it)
        View sub=find(row,ids.id("subtitle"));
        boolean artist=sub instanceof TextView&&((TextView)sub).getText().toString().startsWith("Artist");
        if(thumb!=null&&artist&&(!thumb.getClipToOutline()||"ultra".equals(thumb.getTag(TAG_KEY))||"ultra-round".equals(thumb.getTag(TAG_KEY)))){
            if(!"ultra-round".equals(thumb.getTag(TAG_KEY))){thumb.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline o){o.setOval(0,0,view.getWidth(),view.getHeight());}});thumb.setClipToOutline(true);thumb.setTag(TAG_KEY,"ultra-round");rounded.put(thumb,true);}
            count("library.artists");
        }else if(thumb!=null&&!artist&&"ultra-round".equals(thumb.getTag(TAG_KEY))){NativeUi.clip(thumb,12);thumb.setTag(TAG_KEY,"ultra");}
        if(thumb instanceof ImageView){
            View tile=thumb.getParent() instanceof View&&thumb.getParent()!=row?(View)thumb.getParent():null;
            if(radio){if(tile!=null){remember(tile);tile.setBackground(NativeUi.card(row.getContext(),Palette.blend(Palette.CORAL,p.heroStart,0.18f),14,false));}((ImageView)thumb).setColorFilter(Palette.CORAL,PorterDuff.Mode.SRC_IN);thumb.setTag(TAG_KEY+1,"radio");count("library.radio");}
            else if("radio".equals(thumb.getTag(TAG_KEY+1))){((ImageView)thumb).clearColorFilter();thumb.setTag(TAG_KEY+1,null);if(tile!=null&&original.containsKey(tile))tile.setBackground(original.remove(tile));}
        }
    }
    /** Climbs to the direct child of the enclosing list (max 8 levels); falls back to the view itself. */
    static View listChild(View v){View cur=v;for(int d=0;d<8&&cur.getParent() instanceof View;d++){View parent=(View)cur.getParent();if(list(parent))return cur;cur=parent;}return v;}

    // ------------------------------------------------------------------ Search
    static void searchResult(View item,Palette p,Ids ids){
        boolean wide=item.getParent() instanceof View&&item.getWidth()>((View)item.getParent()).getWidth()*0.85f;
        card(item,p,"result",wide?12:4,4,16,false);
        View thumb=find(item,ids.id("thumbnail"));if(thumb!=null)round(thumb);
    }

    // ------------------------------------------------------------------ Explore (Litho, structural)
    /**
     * Children of a feed list. Horizontal list (carousel): every tile is a card. Vertical list: a full-width child with
     * a leading thumbnail and text is a chart row card; inside a full-width child, half-width blocks with an image or
     * text are category cards (the 2x2 grid). Headers (text only, no image) only get type. Litho hosts that expose no
     * child views are counted as "explore.opaque" so the tester can see them.
     */
    static void feed(ViewGroup list,Palette p,Ids ids){
        if(list.getWidth()<=0)return;
        boolean carousel=horizontal(list);float density=list.getResources().getDisplayMetrics().density;
        for(int k=0;k<list.getChildCount();k++){
            View child=list.getChildAt(k);if(child==null||excluded(child,ids)||child.getVisibility()!=View.VISIBLE)continue;
            if(ids.name(child)!=null||holds(child,ids,"two_column_item_content_parent")||holds(child,ids,"two_row_item"))continue;
            if(list(child))continue; // a nested list (carousel) gets its own feed pass when the walk reaches it
            float h=child.getHeight()/density;boolean image=firstImage(child)!=null,text=firstText(child)!=null;
            if(carousel){if(h>=64){card(child,p,"tile",4,4,16,false);roundImages(child);count("explore.tiles");}continue;}
            boolean full=child.getWidth()>=list.getWidth()*0.85f;
            // grid first: a 2x2 category row also holds an image and text, but as half-width blocks
            if(full&&child instanceof ViewGroup&&grid((ViewGroup)child,p,list.getWidth())>0)continue;
            if(full&&image&&text&&h>=48&&h<=120){card(child,p,"chart",12,4,20,false);roundImages(child);count("explore.rows");continue;}
            if(full&&text&&!image&&h<=72){headerType(child,p);count("explore.headers");continue;}
            if(!(child instanceof ViewGroup)||((ViewGroup)child).getChildCount()==0)count("explore.opaque");
        }
    }
    /** Half-width blocks (35-60% of the list) side by side: the category grid. Returns how many were styled. */
    static int grid(ViewGroup row,Palette p,int listWidth){
        int styled=0;ArrayDeque<View> q=new ArrayDeque<>();for(int k=0;k<row.getChildCount();k++)q.add(row.getChildAt(k));int left=40;
        while(!q.isEmpty()&&--left>0){View v=q.poll();float w=v.getWidth()/(float)listWidth;
            if(w>=0.35f&&w<=0.6f&&v.getHeight()>0&&(firstImage(v)!=null||firstText(v)!=null||named(v,"ComponentHost"))){card(v,p,"category",0,0,20,false);roundImages(v);tintImages(v,Palette.CORAL);styled++;count("explore.grid");}
            else if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}
        return styled;
    }
    static void headerType(View v,Palette p){if(v instanceof TextView)type((TextView)v,p.text,true);if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)headerType(((ViewGroup)v).getChildAt(k),p);}
    static boolean holds(View v,Ids ids,String name){int id=ids.id(name);return id!=0&&v.findViewById(id)!=null;}

    // ------------------------------------------------------------------ Shell: mini player
    static void mini(View player,Palette p,Ids ids){
        Context c=player.getContext();
        if(!(player.getBackground() instanceof InsetDrawable)){remember(player);player.setBackground(new InsetDrawable(NativeUi.card(c,p.mini,16,false),NativeUi.dp(c,8),NativeUi.dp(c,4),NativeUi.dp(c,8),NativeUi.dp(c,4)));}
        count("shell.mini");
        View play=find(player,ids.id("mini_player_play_pause_replay_button"));
        if(play!=null){if(!(play.getBackground() instanceof GradientDrawable&&original.containsKey(play))){remember(play);play.setBackground(NativeUi.circle(Palette.CORAL));}tint(play,Color.WHITE);count("shell.play");}
        for(String side:new String[]{"mini_player_previous_button","mini_player_next_button"}){View b=find(player,ids.id(side));if(b!=null){tint(b,p.text);count("shell.skip");}}
        View title=find(player,ids.id("mini_player_title"));if(title instanceof TextView){type((TextView)title,p.text,true);count("shell.title");}
        View sub=find(player,ids.id("mini_player_subtitle"));if(sub instanceof TextView)type((TextView)sub,p.muted,false);
        ArrayDeque<View> q=new ArrayDeque<>();q.add(player);int left=300;
        while(!q.isEmpty()&&--left>0){View v=q.poll();
            if(v instanceof ProgressBar){ProgressBar b=(ProgressBar)v;ColorStateList coral=ColorStateList.valueOf(p.progress);if(!coral.equals(b.getProgressTintList())){b.setProgressTintList(coral);b.setIndeterminateTintList(coral);}count("shell.progress");}
            if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}
    }

    // ------------------------------------------------------------------ styling primitives (all idempotent, all remembered)
    static void surface(View v,int color){Drawable d=v.getBackground();if(d instanceof ColorDrawable&&((ColorDrawable)v.getBackground()).getColor()==color)return;if(d!=null&&!(d instanceof ColorDrawable))return;remember(v);v.setBackgroundColor(color);}
    static void remember(View v){if(!original.containsKey(v))original.put(v,v.getBackground());}
    /** InsetDrawable card behind the view: layout, padding and recycling stay the host's. */
    static void card(View v,Palette p,String kind,int insetH,int insetV,int radius,boolean hero){
        if(kind.equals(v.getTag(TAG_KEY))&&v.getBackground() instanceof InsetDrawable)return;
        Context c=v.getContext();remember(v);
        Drawable inner=hero?new NativeUi.Hero(p,NativeUi.dp(c,radius),true):NativeUi.pressableCard(c,radius);
        v.setBackground(new InsetDrawable(inner,NativeUi.dp(c,insetH),NativeUi.dp(c,insetV),NativeUi.dp(c,insetH),NativeUi.dp(c,insetV)));v.setTag(TAG_KEY,kind);
    }
    static void round(View thumb){if(thumb==null||"ultra-round".equals(thumb.getTag(TAG_KEY))||thumb.getClipToOutline()&&!"ultra".equals(thumb.getTag(TAG_KEY)))return;if(!"ultra".equals(thumb.getTag(TAG_KEY))){NativeUi.clip(thumb,12);thumb.setTag(TAG_KEY,"ultra");rounded.put(thumb,true);}}
    static void roundImages(View v){ImageView i=firstImage(v);if(i!=null)round(i);}
    static void tintImages(View v,int color){ImageView i=firstImage(v);if(i!=null&&i.getWidth()<i.getResources().getDisplayMetrics().density*40)tint(i,color);}
    static void tint(View v,int color){if(!(v instanceof ImageView))return;ImageView i=(ImageView)v;ColorStateList want=ColorStateList.valueOf(color);if(want.equals(i.getImageTintList()))return;if(!originalTint.containsKey(i))originalTint.put(i,i.getImageTintList());i.setImageTintList(want);}
    static void type(TextView t,int color,boolean medium){
        if(t==null)return;
        if(t.getCurrentTextColor()!=color){if(!originalColor.containsKey(t))originalColor.put(t,t.getCurrentTextColor());t.setTextColor(color);}
        if(medium){Typeface face=t.getTypeface();if(face==null||!face.equals(MEDIUM)){if(!originalFace.containsKey(t))originalFace.put(t,face);t.setTypeface(MEDIUM);}}
    }
    static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    /** Host chip: coral pill when selected, soft pill otherwise. Selected = view state, or dark ink on the host's white chip. */
    static void chip(View text,Palette p){
        if(!(text instanceof TextView))return;
        View chip=text.getParent() instanceof View?(View)text.getParent():text;
        for(View v=chip;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null){if(v.isClickable()){chip=v;break;}if(list(v))break;}
        TextView t=(TextView)text;int ink=t.getCurrentTextColor();boolean ours=ink==p.chipOnInk||ink==p.chipOffInk;
        Boolean last=chipSelected.get(chip);
        boolean selected=chip.isSelected()||chip.isActivated()||text.isSelected()||(!ours?Palette.luminance(ink)<0.35f:last!=null&&last);
        if(!ours||last==null||last!=selected||!(chip.getBackground() instanceof GradientDrawable)){
            remember(chip);if(!originalColor.containsKey(t)&&!ours)originalColor.put(t,ink);GradientDrawable g=new GradientDrawable();g.setCornerRadius(NativeUi.dp(chip.getContext(),1000));g.setColor(selected?p.chipOn:p.chipOff);
            if(!selected&&!p.dark)g.setStroke(Math.max(1,NativeUi.dp(chip.getContext(),1)),p.hairline);
            chip.setBackground(g);chip.setBackgroundTintList(null);t.setTextColor(selected?p.chipOnInk:p.chipOffInk);chipSelected.put(chip,selected);
        }
    }
    /** Row 3-dot menu: the theme text colour, like the reference. */
    static void menu(View v,Palette p){if(v instanceof ImageView){tint(v,p.text);return;}ImageView i=firstImage(v);if(i!=null)tint(i,p.text);}
    static void field(View v,Palette p){if(v.getBackground() instanceof GradientDrawable&&original.containsKey(v))return;remember(v);v.setBackground(NativeUi.card(v.getContext(),p.chipOff,24,!p.dark));int pad=NativeUi.dp(v.getContext(),14);v.setPadding(Math.max(v.getPaddingLeft(),pad),v.getPaddingTop(),Math.max(v.getPaddingRight(),pad),v.getPaddingBottom());}
    static void fab(View v,Palette p){
        ColorStateList coral=ColorStateList.valueOf(p.fab);if(coral.equals(v.getBackgroundTintList()))return;
        v.setBackgroundTintList(coral);if(v instanceof TextView)((TextView)v).setTextColor(Color.WHITE);if(v instanceof ImageView)((ImageView)v).setImageTintList(ColorStateList.valueOf(Color.WHITE));
        try{v.getClass().getMethod("setIconTint",ColorStateList.class).invoke(v,ColorStateList.valueOf(Color.WHITE));}catch(Throwable ignored){}
    }

    // ------------------------------------------------------------------ helpers
    static View find(View root,int id){return id==0||root==null?null:root.findViewById(id);}
    static boolean has(View v,String text){CharSequence d=v.getContentDescription();if(d!=null&&d.toString().contains(text))return true;if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(has(((ViewGroup)v).getChildAt(n),text))return true;return false;}
    static ImageView firstImage(View v){if(v instanceof ImageView&&v.getVisibility()==View.VISIBLE)return (ImageView)v;if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++){ImageView i=firstImage(((ViewGroup)v).getChildAt(k));if(i!=null)return i;}return null;}
    static TextView firstText(View v){if(v instanceof TextView&&v.getVisibility()==View.VISIBLE)return (TextView)v;if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++){TextView t=firstText(((ViewGroup)v).getChildAt(k));if(t!=null)return t;}return null;}

    static void restoreAll(){
        for(Map.Entry<View,Drawable> e:new ArrayList<>(original.entrySet())){View v=e.getKey();if(v!=null)try{v.setBackground(e.getValue());v.setTag(TAG_KEY,null);}catch(Throwable ignored){}}
        for(Map.Entry<View,Integer> e:new ArrayList<>(originalColor.entrySet())){View v=e.getKey();if(v instanceof TextView)((TextView)v).setTextColor(e.getValue());}
        for(Map.Entry<View,Typeface> e:new ArrayList<>(originalFace.entrySet())){View v=e.getKey();if(v instanceof TextView)((TextView)v).setTypeface(e.getValue());}
        for(Map.Entry<View,ColorStateList> e:new ArrayList<>(originalTint.entrySet())){View v=e.getKey();if(v instanceof ImageView)((ImageView)v).setImageTintList(e.getValue());}
        for(View v:new ArrayList<>(added.keySet()))if(v!=null&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
        for(View v:new ArrayList<>(rounded.keySet()))if(v!=null){v.setClipToOutline(false);v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);v.setTag(TAG_KEY,null);}
        rounded.clear();
        original.clear();originalColor.clear();originalFace.clear();originalTint.clear();chipSelected.clear();added.clear();
    }

    // ------------------------------------------------------------------ DL49 per-pass instrumentation
    static String trigger="layout";
    static Scan scan;
    static final ArrayDeque<Scan> history=new ArrayDeque<>();
    /** What one pass actually saw: candidate page roots, row ids, lists, the most frequent view classes. */
    static final class Scan{
        final String trigger;long pass;String summary="";boolean panel,home,budgetHit;int visited,lists,containers,containersShown;
        final LinkedHashMap<String,Integer> ids=new LinkedHashMap<>();final HashMap<String,Integer> classes=new HashMap<>();final HashSet<String> shown=new HashSet<>();String page="other";
        /** The page on screen, from the ids that are actually shown (DL50): Search, then Library, then Explore. */
        String classify(){
            if(shown.contains("suggestion_list")||shown.contains("edit_text")||shown.contains("voice_search")||shown.contains("voice_search_container")||shown.contains("sound_search"))return "search";
            if(shown.contains("header_container")||shown.contains("two_column_item_content_parent"))return "library";
            if(shown.contains("browse_content")||shown.contains("section_list_content"))return "explore";
            return "other";
        }
        Scan(String trigger){this.trigger=trigger;}
        void seen(String id){Integer v=ids.get(id);ids.put(id,v==null?1:v+1);}
        int n(String id){Integer v=ids.get(id);return v==null?0:v;}
        void klass(View v){String k=v.getClass().getSimpleName();if(k.isEmpty())k=v.getClass().getName();Integer c=classes.get(k);classes.put(k,c==null?1:c+1);}
        boolean interesting(){return n("section_list_content")+n("browse_content")+n("suggestion_list")+n("two_column_item_content_parent")+n("two_row_item")>0;}
        String top(int max){ArrayList<Map.Entry<String,Integer>> all=new ArrayList<>(classes.entrySet());Collections.sort(all,new Comparator<Map.Entry<String,Integer>>(){public int compare(Map.Entry<String,Integer> a,Map.Entry<String,Integer> b){return b.getValue()-a.getValue();}});StringBuilder out=new StringBuilder();for(int i=0;i<Math.min(max,all.size());i++){if(out.length()>0)out.append(", ");out.append(all.get(i).getKey()).append('=').append(all.get(i).getValue());}return out.toString();}
        /** Compact Diagnostics event: trigger, module cover, containers shown/total, page roots, row ids, lists, nodes. */
        String line(){String t=trigger.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");return ("t="+t+",cov="+(panel?"d":"")+(home?"h":"")+(panel||home?"":"0")+",fc="+containersShown+"/"+containers+",slc="+n("section_list_content")+",bc="+n("browse_content")+",sl="+n("suggestion_list")+",tc="+n("two_column_item_content_parent")+",tr="+n("two_row_item")+",ch="+n("chip_cloud_chip_text")+",rv="+lists+",n="+visited+(budgetHit?"+":""));}
        String text(){StringBuilder el=new StringBuilder();for(Map.Entry<String,Integer> e:elements.entrySet()){if(el.length()>0)el.append(", ");el.append(e.getKey()).append('=').append(e.getValue());}
            return "  pass "+pass+" ("+trigger+", page "+page+"): "+summary+"\n    roots: "+line()+"\n    styled: "+(el.length()==0?"-":el)+"\n    top classes: "+top(5)+"\n";}
        final LinkedHashMap<String,Integer> elements=new LinkedHashMap<>();
    }
    static List<View> containers(View root,Ids ids){ArrayList<View> out=new ArrayList<>();int id=ids.id("fragment_container");if(id==0)id=0x7f0b04e1;collect(root,id,out,new int[]{BUDGET});return out;}
    static void collect(View v,int id,List<View> out,int[] budget){if(v==null||--budget[0]<0)return;if(v.getId()==id)out.add(v);if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)collect(((ViewGroup)v).getChildAt(k),id,out,budget);}
    static void countContainers(View root,Ids ids,Scan s){for(View c:containers(root,ids)){s.containers++;if(c.isShown())s.containersShown++;}}
    /** An explicit pass shortly after a page change (host tab switch, page set change), on top of the layout passes. */
    public static void kick(final String why){
        for(long delay:new long[]{300,1200,3000})MAIN.postDelayed(new Runnable(){public void run(){View d=decor.get();if(d==null)return;trigger=why;apply(d);}},delay);
    }

    // ------------------------------------------------------------------ diagnostics
    /** Element counter. shell.* adds to shell; every other element adds to the page on screen (attributed after the walk). */
    static void count(String what){Integer n=report.get(what);report.put(what,n==null?1:n+1);if(what.startsWith("shell."))report.put("shell",report.get("shell")+1);else contentTotal++;}
    static int contentTotal;
    /** Always the full set: library=..,search=..,explore=..,shell=..,ids=resolved/expected. */
    static String summary(Ids ids){Scan s=scan;return "library="+report.get("library")+",search="+report.get("search")+",explore="+report.get("explore")+",shell="+report.get("shell")+",ids="+ids.resolved+"/"+ids.expected+",page="+(s==null?"other":s.page);}
    /** Human-readable last pass for Download settings > Diagnostics: per-page totals, per-element counts, unresolved ids. */
    public static String report(){
        Ids i=ids;if(i==null)return "App page style: no pass yet\n";
        StringBuilder b=new StringBuilder("App page style (pass ").append(passes).append("): ").append(summary(i)).append('\n');
        for(Map.Entry<String,Integer> e:report.entrySet())if(e.getKey().indexOf('.')>0)b.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        if(!i.missing.isEmpty())b.append("  ids not in this host: ").append(i.missing).append('\n');
        Scan last=scan;if(last!=null)b.append("Last pass:\n").append(last.text());
        if(!history.isEmpty()){b.append("Last passes that saw page content:\n");for(Scan h:history)b.append(h.text());}
        else b.append("No pass has seen section_list_content, browse_content, suggestion_list, two_column_item_content_parent or two_row_item yet.\n");
        return b.toString();
    }
}
