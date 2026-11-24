package ultra.home;

import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import ultra.downloads.DownloadsScreen;
import ultra.downloads.HomeSource;
import ultra.downloads.NativeIntegration;
import ultra.downloads.NativeUi;
import ultra.downloads.Palette;

/**
 * Home (DL34 "Soft Red x Warm White x Charcoal"), mounted inside the host's own fragment_container like Downloads, so the
 * host bottom navigation and mini-player stay native. Opened by the host Home pivot (NativeIntegration.fromTab with
 * FEmusic_ultra_home) or, on hosts without that pivot, from the Home entry in the Downloads header.
 *
 * <p>Same system as the Library reference: brand line, big title, coral chips, a gradient hero card with a round coral
 * Play, rounded list cards. Content is real module data only: last played and recent downloads, quick picks from your
 * downloads, your Library playlists and the artists you downloaded. Follows the app's dark or light theme.
 */
public final class HomeScreen extends ContextThemeWrapper {
    private final Activity host;private final FrameLayout mount;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    /** Covers decode on their own threads (DL51): on the data thread they queued behind the slow playlist phase. */
    private final ExecutorService artIo=Executors.newFixedThreadPool(2);
    /** Decoded covers by tile and size, shared across renders and Home instances, so a re-render shows them at once. */
    public static final android.util.LruCache<String,Bitmap> COVERS=new android.util.LruCache<String,Bitmap>(6*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount());}};
    /** Per-section numbers of the last render, for Download settings > Diagnostics: items, covers wanted, covers shown. */
    final LinkedHashMap<String,int[]> sections=new LinkedHashMap<>();private long renderedAt;private int renders;
    /** Per section: covers by source (file, embedded, fetched, none). */
    final LinkedHashMap<String,LinkedHashMap<String,Integer>> sources=new LinkedHashMap<>();
    static final Map<String,String> COVER_SOURCE=Collections.synchronizedMap(new HashMap<String,String>());
    void source(String section,String what){LinkedHashMap<String,Integer> m=sources.get(section);if(m==null){m=new LinkedHashMap<>();sources.put(section,m);}Integer n=m.get(what);m.put(what,n==null?1:n+1);}
    private LinearLayout root,content,chipRow;private ScrollView scroll;private TextView greeting;
    private String chip=HomeLogic.ALL;private boolean destroyed,loaded,loading,reloadAfter,songsReady;private int generation;private TextView loadingLabel;
    private List<HomeTile> songs=new ArrayList<>(),playlists=new ArrayList<>(),pinned=new ArrayList<>(),artists=new ArrayList<>();private HomeTile last;
    private final ViewTreeObserver.OnGlobalLayoutListener insets=new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){applyInsets();}};
    /** Recent row length and quick-pick count. */
    static final int RECENT=8,PICKS=6;

    public HomeScreen(Activity host,FrameLayout mount){super(host,android.R.style.Theme_Material_NoActionBar);this.host=host;this.mount=mount;onCreate();}
    /** Views use the host Activity's live theme, like DownloadsScreen; Palette reads dark or light from it. */
    @Override public Resources.Theme getTheme(){Resources.Theme t=host==null?null:host.getTheme();return t!=null?t:super.getTheme();}
    private int dp(float n){return NativeUi.dp(this,n);}
    private Palette p(){return Palette.of(this);}

    void onCreate(){
        Palette p=p();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.page);root.setTag("home_root");
        // Header: brand line, then the big "Home" title and the time-of-day greeting.
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);brandRow.setPadding(dp(16),dp(4),dp(4),0);brandRow.setMinimumHeight(dp(52));
        brandRow.addView(NativeUi.brand(this),new LinearLayout.LayoutParams(0,-2,1));
        if(!NativeIntegration.homeIsTab())brandRow.addView(NativeUi.iconButton(this,"yt_outline_download_vd_theme_24","download","Back to Downloads",new View.OnClickListener(){public void onClick(View v){NativeIntegration.closeHome();}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(brandRow,new LinearLayout.LayoutParams(-1,-2));
        TextView title=NativeUi.header(this,"Home");title.setTextSize(TypedValue.COMPLEX_UNIT_SP,34);title.setTag("home_title");title.setPadding(dp(16),0,dp(16),0);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        greeting=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body1",15,p.muted,false);greeting.setText(HomeLogic.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));greeting.setTag("home_greeting");greeting.setPadding(dp(16),dp(2),dp(16),0);
        root.addView(greeting,new LinearLayout.LayoutParams(-1,-2));
        // Coral chips: All, Playlists, Downloads, Artists, Liked.
        HorizontalScrollView chips=new HorizontalScrollView(this);chips.setHorizontalScrollBarEnabled(false);chips.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipRow=new LinearLayout(this);chipRow.setGravity(Gravity.CENTER_VERTICAL);chipRow.setPadding(dp(16),dp(14),dp(16),dp(10));chips.addView(chipRow,new FrameLayout.LayoutParams(-2,-2));
        root.addView(chips,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(4),0,0);scroll.addView(content,new FrameLayout.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets wi){applyInsets();return wi;}});
        mount.addView(root,new FrameLayout.LayoutParams(-1,-1));root.setClickable(true);root.requestApplyInsets();
        mount.getViewTreeObserver().addOnGlobalLayoutListener(insets);
        renderChips();showLoading();load();
        // Long load (large Library on a slow phone): keep the loading state and say so. The empty-state page is only
        // shown when a finished load really has nothing (DL50; DL49 showed it after 5 s while data was still loading).
        main.postDelayed(new Runnable(){public void run(){if(!destroyed&&!loaded&&loadingLabel!=null)loadingLabel.setText("Still loading your music…");}},5000);
    }
    /**
     * Re-reads every source. Coalesced (DL50): a refresh during a running load does not discard it; one more load runs
     * after it finishes, and every finished load is shown.
     */
    public void refresh(){if(destroyed)return;if(loading){reloadAfter=true;return;}load();}
    public boolean isLoading(){return loading;}
    /** True once real sections or the empty state are on screen, i.e. not blank and not still loading. */
    public boolean hasContent(){return loaded&&content!=null&&content.getChildCount()>0&&content.findViewWithTag("home_loading")==null;}
    /** Shown until the first data arrives. */
    private void showLoading(){
        content.removeAllViews();Palette p=p();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(32),dp(72),dp(32),dp(32));box.setTag("home_loading");
        ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Palette.CORAL));box.addView(spinner,new LinearLayout.LayoutParams(dp(36),dp(36)));
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body3",14,p.muted,false);t.setText("Loading your music…");loadingLabel=t;LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.topMargin=dp(12);box.addView(t,tp);
        content.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }

    public void onBackPressed(){NativeIntegration.closeHome();}
    public void destroy(){destroyed=true;generation++;io.shutdownNow();artIo.shutdownNow();try{ViewTreeObserver o=mount.getViewTreeObserver();if(o.isAlive())o.removeOnGlobalLayoutListener(insets);}catch(Throwable ignored){}}

    private void applyInsets(){if(root==null||scroll==null)return;try{int[] in=DownloadsScreen.contentInsets(host,mount);if(root.getPaddingTop()!=in[0])root.setPadding(0,in[0],0,0);if(scroll.getPaddingBottom()!=in[1])scroll.setPadding(0,0,0,in[1]);}catch(Throwable ignored){}}

    // ------------------------------------------------------------------ data
    /** Reads every source off the main thread, then renders once. Each source is independent and fails soft. */
    /**
     * Two phases on the IO thread (DL50): songs, last played and artists first, rendered at once; Library playlists
     * second (large Libraries take longer), rendered when they arrive. Each source fails soft to an empty list.
     */
    void load(){
        if(destroyed)return;loading=true;reloadAfter=false;final Context app=getApplicationContext();
        try{io.execute(new Runnable(){public void run(){
            final List<HomeTile> s=HomeSource.recentSongs(app),a=HomeSource.artists(s);final HomeTile l=HomeSource.lastPlayed(app);
            main.post(new Runnable(){public void run(){if(destroyed)return;songs=s;artists=a;last=l;songsReady=true;if(!s.isEmpty()){loaded=true;requestRender();}}});
            final List<HomeTile> pl=HomeSource.playlists(app);final List<HomeTile> pin=HomeSource.pinned(app,pl);
            main.post(new Runnable(){public void run(){
                if(destroyed)return;playlists=pl;pinned=pin;loaded=true;loading=false;requestRender();
                if(reloadAfter){reloadAfter=false;load();}
            }});
        }});}catch(RejectedExecutionException e){loading=false;}
    }

    // ------------------------------------------------------------------ rendering
    private void renderChips(){
        chipRow.removeAllViews();
        for(final String name:HomeLogic.CHIPS){
            View c=NativeUi.chip(this,name,name.equals(chip),false,new View.OnClickListener(){public void onClick(View v){if(name.equals(chip))return;chip=name;renderChips();render();}});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));lp.setMarginEnd(dp(10));chipRow.addView(c,lp);
        }
    }
    /**
     * Data arrivals ask for a render; renders run at most once per RENDER_GAP_MS (DL52: ~12 renders in 10 s on a cold
     * start caused flicker). Covers never trigger a render: they are set in place on their ImageView.
     */
    static final long RENDER_GAP_MS=500;private boolean renderQueued;private String lastSignature="";private int skipped;
    void requestRender(){
        if(renderQueued||destroyed)return;renderQueued=true;
        long wait=Math.max(0,RENDER_GAP_MS-(android.os.SystemClock.uptimeMillis()-renderedAt));
        main.postDelayed(new Runnable(){public void run(){renderQueued=false;render();}},renders==0?0:wait);
    }
    /** What a render would show; equal signatures mean the page on screen is already right. */
    String signature(){StringBuilder b=new StringBuilder(chip).append('|').append(loading).append('|').append(last==null?"":last.id);for(HomeTile t:songs)b.append(',').append(t.id);b.append('|');for(HomeTile t:playlists)b.append(',').append(t.id).append(t.subtitle);b.append('|').append(pinned.size());return b.toString();}
    void render(){
        if(!loaded||destroyed)return;
        String sig=signature();
        if(sig.equals(lastSignature)&&content.getChildCount()>0&&content.findViewWithTag("home_loading")==null){skipped++;ultra.downloads.NativeIntegration.timeline("render skipped: data unchanged ("+skipped+")");return;}
        lastSignature=sig;
        content.removeAllViews();generation++;sections.clear();sources.clear();noted.clear();renders++;renderedAt=android.os.SystemClock.uptimeMillis();
        boolean music=chip.equals(HomeLogic.ALL)||chip.equals(HomeLogic.DOWNLOADS);
        List<HomeTile> pick=HomeLogic.pickUp(last,songs,pinned,playlists,HomeLogic.PICK_UP_LIMIT);
        HomeTile heroSong=null;for(HomeTile t:pick)if(t.song()){heroSong=t;break;}
        List<HomeTile> recent=music?songs.subList(0,Math.min(RECENT,songs.size())):Collections.<HomeTile>emptyList();
        HashSet<String> shown=new HashSet<>();for(HomeTile t:recent)shown.add(t.id);if(heroSong!=null)shown.add(heroSong.id);
        List<HomeTile> picks=music?HomeLogic.quickPicks(songs,shown,PICKS):Collections.<HomeTile>emptyList();
        List<HomeTile> lists=chip.equals(HomeLogic.ALL)||chip.equals(HomeLogic.PLAYLISTS)?HomeLogic.filter(playlists,HomeLogic.PLAYLISTS):chip.equals(HomeLogic.LIKED)?HomeLogic.filter(playlists,HomeLogic.LIKED):Collections.<HomeTile>emptyList();
        List<HomeTile> people=chip.equals(HomeLogic.ARTISTS)?artists:Collections.<HomeTile>emptyList();
        if((!music||heroSong==null)&&recent.isEmpty()&&picks.isEmpty()&&lists.isEmpty()&&people.isEmpty()){if(loading){showLoading();return;}empty();return;}
        if(music&&heroSong!=null){content.addView(hero(heroSong));stat("hero")[0]++;}
        if(!recent.isEmpty()){
            content.addView(sectionTitle("Recent downloads"));
            HorizontalScrollView row=new HorizontalScrollView(this);row.setHorizontalScrollBarEnabled(false);row.setOverScrollMode(View.OVER_SCROLL_NEVER);row.setTag("home_recent");
            LinearLayout line=new LinearLayout(this);line.setPadding(dp(16),0,dp(6),dp(4));
            for(HomeTile t:recent){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(128),-2);lp.setMarginEnd(dp(10));line.addView(tile(t,"recent"),lp);}
            row.addView(line,new FrameLayout.LayoutParams(-2,-2));content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        if(!picks.isEmpty()){content.addView(sectionTitle("Quick picks"));for(HomeTile t:picks)content.addView(card(t,"quick"),cardParams());}
        if(!lists.isEmpty()){String key=chip.equals(HomeLogic.LIKED)?"liked":"playlists";content.addView(sectionTitle(chip.equals(HomeLogic.LIKED)?"Liked":"Your playlists"));for(HomeTile t:lists)content.addView(card(t,key),cardParams());}
        if(!people.isEmpty()){content.addView(sectionTitle("Artists in your downloads"));for(HomeTile t:people)content.addView(card(t,"artists"),cardParams());}
        settle(generation,1500);settle(generation,4000);
        ultra.downloads.NativeIntegration.timeline("render #"+renders+" "+sectionLine());
    }
    int[] stat(String key){int[] v=sections.get(key);if(v==null){v=new int[3];sections.put(key,v);}return v;}
    String sectionLine(){StringBuilder b=new StringBuilder();for(Map.Entry<String,int[]> e:sections.entrySet()){if(b.length()>0)b.append(' ');b.append(e.getKey()).append('=').append(e.getValue()[0]);if(e.getValue()[1]>0)b.append(" art ").append(e.getValue()[2]).append('/').append(e.getValue()[1]);}return b.length()==0?"(no sections)":b.toString();}
    /**
     * After a render (DL51): every tile and card fully visible, and any cover that is still missing is requested again.
     * Covers may arrive late on a cold start; nothing stays a blank placeholder once its file exists.
     */
    private void settle(final int render,long delay){
        main.postDelayed(new Runnable(){public void run(){
            if(destroyed||render!=generation)return;int again=0;
            ArrayDeque<View> q=new ArrayDeque<>();q.add(content);
            while(!q.isEmpty()){View v=q.poll();if(v.getAlpha()<1f){v.setAlpha(1f);v.setTranslationY(0);}
                if(v instanceof ImageView&&v.getTag() instanceof Object[]){Object[] want=(Object[])v.getTag();if(((ImageView)v).getDrawable()==null){again++;fetch((HomeTile)want[0],(ImageView)v,(Integer)want[1],(String)want[2],false);}}
                if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}
            if(again>0)ultra.downloads.NativeIntegration.timeline("settle: re-requested "+again+" covers");
        }},delay);
    }
    /** Download settings > Diagnostics: one line per section of the last render. */
    public String diagnostics(){
        StringBuilder b=new StringBuilder("Ultra Home: render #").append(renders).append(" (").append(skipped).append(" skipped as unchanged), ").append(renders==0?"not rendered yet":((android.os.SystemClock.uptimeMillis()-renderedAt)/1000)+" s ago").append(loading?", loading":"").append(loaded?"":", no data yet").append('\n');
        // DL53: covers are counted from the screen (cover views of the section that show a bitmap), not from callbacks,
        // so a cover set in place by any path (cache, late arrival, settle re-request) is counted exactly once.
        LinkedHashMap<String,int[]> shown=new LinkedHashMap<>();
        ArrayDeque<View> q=new ArrayDeque<>();if(content!=null)q.add(content);
        while(!q.isEmpty()){View v=q.poll();
            if(v instanceof ImageView&&v.getTag() instanceof Object[]){Object[] want=(Object[])v.getTag();String sec=(String)want[2];int[] c=shown.get(sec);if(c==null){c=new int[2];shown.put(sec,c);}c[0]++;if(((ImageView)v).getDrawable()!=null)c[1]++;}
            if(v instanceof ViewGroup)for(int k=0;k<((ViewGroup)v).getChildCount();k++)q.add(((ViewGroup)v).getChildAt(k));}
        for(Map.Entry<String,int[]> e:sections.entrySet()){
            int[] c=shown.get(e.getKey());
            b.append("  ").append(e.getKey()).append(": items ").append(e.getValue()[0]).append(c!=null&&c[0]>0?", covers "+c[1]+"/"+c[0]:"");
            LinkedHashMap<String,Integer> src=sources.get(e.getKey());if(src!=null&&!src.isEmpty())b.append(" (").append(src.toString().replace("{","").replace("}","")).append(')');
            b.append('\n');}
        return b.toString();
    }
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(16),0,dp(16),dp(8));return lp;}
    private View sectionTitle(String text){
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title1",20,p().text,true);t.setText(text);t.setTag("home_section:"+text);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(16),dp(16),dp(16),dp(10));t.setLayoutParams(lp);return t;
    }
    /** The reference's Radio card, for the song you would pick up: gradient, coral icon tile, title, round coral Play. */
    private View hero(final HomeTile song){
        Palette p=p();
        LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setBackground(NativeUi.hero(this));box.setPadding(dp(14),dp(14),dp(14),dp(14));box.setTag("home_hero");NativeUi.clip(box,20);
        FrameLayout icon=new FrameLayout(this);icon.setBackground(NativeUi.card(this,blend(p),14,false));
        icon.addView(NativeUi.icon(this,"yt_outline_radio_vd_theme_24","radio",Palette.CORAL,28),new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));box.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title2",17,p.text,true);t.setText("Pick up where you left off");text.addView(t,new LinearLayout.LayoutParams(-1,-2));
        TextView s=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",13,p.muted,false);s.setText(song.title+" • "+song.subtitle);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(3);text.addView(s,sp);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(14));tp.setMarginEnd(dp(8));box.addView(text,tp);
        ImageButton play=new ImageButton(this);play.setImageDrawable(NativeUi.drawable(this,"yt_outline_play_arrow_vd_theme_24","play",0xffffffff));play.setBackground(NativeUi.circle(Palette.CORAL));play.setScaleType(ImageView.ScaleType.FIT_CENTER);play.setPadding(dp(10),dp(10),dp(10),dp(10));play.setContentDescription("Play "+song.title);play.setTag("home_hero_play");
        play.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(song);}});box.addView(play,new LinearLayout.LayoutParams(dp(44),dp(44)));
        box.setClickable(true);box.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(song);}});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(16),dp(6),dp(16),dp(4));box.setLayoutParams(lp);return box;
    }
    private static int blend(Palette p){int c=Palette.CORAL,b=p.heroStart;float a=0.18f;return Color.rgb(Math.round(Color.red(c)*a+Color.red(b)*(1-a)),Math.round(Color.green(c)*a+Color.green(b)*(1-a)),Math.round(Color.blue(c)*a+Color.blue(b)*(1-a)));}
    /** Rounded list card: art, title, kind and detail, like the reference rows. Artists get a round crop. */
    private View card(final HomeTile t,String section){stat(section)[0]++;
        Palette p=p();
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(10),dp(16),dp(10));line.setMinimumHeight(dp(76));line.setTag("home_card:"+t.key());line.setContentDescription(t.title);
        line.setBackground(NativeUi.pressableCard(this,20));NativeUi.lift(line);
        line.addView(art(t,56,t.artist(),section),new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",16,p.text,true);title.setText(t.title);text.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,p.muted,false);sub.setText((t.song()?"Song":t.artist()?"Artist":"Playlist")+" • "+t.subtitle);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(2);text.addView(sub,sp);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(14));line.addView(text,tp);
        line.setClickable(true);line.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(t);}});
        return line;
    }
    /** Cover tile for the Recent downloads row: rounded art on a card, title and artist below. */
    private View tile(final HomeTile t,String section){stat(section)[0]++;
        Palette p=p();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setTag("home_tile:"+t.key());box.setContentDescription(t.title);
        box.setBackground(NativeUi.pressableCard(this,20));NativeUi.lift(box);box.setPadding(dp(10),dp(10),dp(10),dp(12));
        SquareFrame frame=new SquareFrame(this);frame.addView(art(t,108,false,section),new FrameLayout.LayoutParams(-1,-1));box.addView(frame,new LinearLayout.LayoutParams(-1,-2));
        TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",14,p.text,true);title.setText(t.title);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);box.addView(title,tp);
        TextView sub=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",12,p.muted,false);sub.setText(t.subtitle);box.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        box.setClickable(true);box.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(t);}});
        return box;
    }
    /** Artwork from disk only (HomeSource.art) over a flat placeholder with an outlined glyph. */
    private View art(HomeTile t,int sizeDp,final boolean round,String section){
        Palette p=p();
        FrameLayout art=new FrameLayout(this);GradientDrawable bg=new GradientDrawable();bg.setColor(p.dark?0xff2a2424:0xfff3eced);if(round)bg.setShape(GradientDrawable.OVAL);else bg.setCornerRadius(dp(12));art.setBackground(bg);
        ImageView glyph=new ImageView(this);glyph.setImageDrawable(NativeUi.drawable(this,t.song()?"yt_outline_music_note_vd_theme_24":t.artist()?"yt_outline_person_vd_theme_24":"yt_outline_library_music_vd_theme_24",t.song()?"music":t.artist()?"person":"playlist",p.muted));
        art.addView(glyph,new FrameLayout.LayoutParams(dp(Math.min(32,sizeDp/2)),dp(Math.min(32,sizeDp/2)),Gravity.CENTER));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        final float radius=dp(12);art.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){if(round)o.setOval(0,0,v.getWidth(),v.getHeight());else o.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}});art.setClipToOutline(true);
        fetch(t,image,dp(Math.max(sizeDp,96)),section,true);return art;
    }
    /**
     * Cover for one tile (DL51): from the shared cache at once, otherwise decoded on the cover threads and set on THIS
     * ImageView when it arrives, as long as it still shows the same tile (no render counter: a re-render does not throw
     * covers away). Disk only, as before.
     */
    private final HashSet<String> noted=new HashSet<>();
    private void fetch(final HomeTile t,final ImageView image,final int px,final String section,boolean count){
        final String key=t.key()+"@"+px;image.setTag(new Object[]{t,px,section,key});
        if(count)stat(section)[1]++;
        Bitmap hit=COVERS.get(key);if(hit!=null){image.setImageBitmap(hit);if(count){stat(section)[2]++;String src=COVER_SOURCE.get(key);source(section,src==null?"file":src);}return;}
        final Context app=getApplicationContext();
        try{artIo.execute(new Runnable(){public void run(){final String[] src={"none"};final Bitmap b=HomeSource.art(app,t,px,src);if(b!=null){COVERS.put(key,b);COVER_SOURCE.put(key,src[0]);}
            main.post(new Runnable(){public void run(){Object tag=image.getTag();if(destroyed||!(tag instanceof Object[])||!key.equals(((Object[])tag)[3]))return;
                if(b==null){if(sections.containsKey(section)&&!noted.contains(key)){noted.add(key);source(section,src[0]);}return;}
                if(image.getDrawable()==null){image.setImageBitmap(b);int[] st=sections.get(section);if(st!=null&&st[2]<st[1])st[2]++;if(!noted.contains(key)){noted.add(key);source(section,src[0]);}}}});}});}catch(RejectedExecutionException ignored){}
    }
    void open(HomeTile t){
        if(t.song())HomeSource.play(this,t.id);
        else if(t.artist())HomeSource.openArtist(t.id);
        else HomeSource.openPlaylist(host,t.id);
    }
    /** One quiet line and an outlined button that pivots natively to Library (same command as tapping Library). */
    private void empty(){
        Palette p=p();
        LinearLayout e=new LinearLayout(this);e.setOrientation(LinearLayout.VERTICAL);e.setGravity(Gravity.CENTER_HORIZONTAL);e.setPadding(dp(32),dp(64),dp(32),dp(32));e.setTag("home_empty");
        TextView line=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body3",14,p.muted,false);line.setMaxLines(3);line.setGravity(Gravity.CENTER);
        line.setText(chip.equals(HomeLogic.ALL)?"Your downloads and Library playlists will show up here.":"Nothing under "+chip+" yet.");e.addView(line,new LinearLayout.LayoutParams(-2,-2));
        TextView button=new TextView(this);button.setText("Open Library");button.setTextColor(p.accent);button.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setGravity(Gravity.CENTER);
        GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(dp(1000));outline.setStroke(Math.max(1,dp(1)),p.accent);button.setBackground(outline);
        button.setPadding(dp(20),0,dp(20),0);button.setTag("home_open_library");button.setContentDescription("Open Library");button.setClickable(true);
        button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){NativeIntegration.returnToContent();}});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(40));bp.topMargin=dp(16);e.addView(button,bp);
        content.addView(e,new LinearLayout.LayoutParams(-1,-2));
    }

    /** A frame whose height always equals its width: square covers at any column width. */
    static final class SquareFrame extends FrameLayout{
        SquareFrame(Context c){super(c);}
        @Override protected void onMeasure(int w,int h){super.onMeasure(w,w);}
    }
}
