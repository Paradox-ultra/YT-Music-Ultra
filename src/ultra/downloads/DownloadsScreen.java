package ultra.downloads;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Downloads page mounted inside the host's own fragment_container (see NativeIntegration), so the host bottom navigation
 * and mini-player stay native and in place. Looks follow the host: its live Activity theme, native text appearances,
 * chip_cloud_chip chip contents, music_menu_item rows and native vector icons, all resolved by name through {@link NativeUi}.
 * DL34 surface system (Palette): brand line + big title, coral chips, sort row, gradient hero card, rounded list cards,
 * coral "+ New" FAB, in the app's dark or light theme.
 */
public final class DownloadsScreen extends android.view.ContextThemeWrapper {
    private final Activity host;private final FrameLayout mount;private final Intent sourceIntent;
    public DownloadsScreen(Activity host,FrameLayout mount,Intent intent){this(host,mount,intent,null);}
    public DownloadsScreen(Activity host,FrameLayout mount,Intent intent,Bundle state){super(host,android.R.style.Theme_Material_NoActionBar);this.host=host;this.mount=mount;this.sourceIntent=intent;onCreate(state);onResume();}
    /** Every view, inflated native layout and dialog uses the host Activity's live theme, not a framework theme. */
    @Override public Resources.Theme getTheme(){Resources.Theme t=host==null?null:host.getTheme();return t!=null?t:super.getTheme();}
    public Intent getIntent(){return sourceIntent;}public void finish(){NativeIntegration.returnToContent();}public boolean isFinishing(){return host.isFinishing();}public boolean isDestroyed(){return host.isDestroyed();}public void requestPermissions(String[] p,int code){host.requestPermissions(p,code);}public void startActivityForResult(Intent i,int code){host.startActivityForResult(i,code);}public void setContentView(View v){mount.addView(v,new FrameLayout.LayoutParams(-1,-1));}public void destroy(){onPause();onDestroy();}

    static final String[] TABS={"Songs","Artists","Albums","Playlists"};
    private LinearLayout root,rows,chipRow,actions,searchBar,summaryCard;private HorizontalScrollView chips;private ScrollView scroll;private TextView summary,summaryLine2,summaryLine3;private EditText search;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String tab="Songs",group="",query="";private int sort=0;private boolean foreground,searchOpen;
    /** Content key of the rows on screen; rows are rebuilt only when it changes, progress is updated in place. */
    private boolean mediaRequested,mediaScanning;private long scannedAt;
    private String structure="";private final Map<String,Holder> holders=new HashMap<>();
    private int inset=-1;Dialog lastSheet;private PermanentDelete pendingDelete;
    private ListView virtualList;private boolean virtualMode;
    private boolean scrollTouch;private long lastScrollAt;private int scrollEpoch;
    private boolean selectionMode;
    private final LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();
    private LinearLayout selectionHeader, selectionActions;
    private TextView selectionTitle;
    private LinearLayout bar;private View homeEntry,fab;private TextView sortLabel;private ImageButton heroPlay;
    private final Runnable tick=new Runnable(){public void run(){if(!foreground)return;mediaAccess(false);render();main.postDelayed(this,1200);}};
    private final ViewTreeObserver.OnGlobalLayoutListener insets=new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){applyInsets();}};
    private int dp(float n){return NativeUi.dp(this,n);}

    public void onCreate(Bundle state){
        int primary=NativeUi.primary(this),secondary=NativeUi.secondary(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(NativeUi.background(this));
        // Library-style header: title only, plus native search and settings icons.
        // DL34 header: brand line ("YouTube Music" + coral "Ultra") with outlined search and settings, then the big title.
        bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);bar.setPadding(dp(16),dp(4),dp(4),0);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);brandRow.setMinimumHeight(dp(52));
        brandRow.addView(NativeUi.brand(this),new LinearLayout.LayoutParams(0,-2,1));
        brandRow.addView(NativeUi.iconButton(this,"yt_outline_search_vd_theme_24","search","Search downloads",new View.OnClickListener(){public void onClick(View v){toggleSearch(!searchOpen);}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        brandRow.addView(NativeUi.iconButton(this,"yt_outline_gear_vd_theme_24","settings","Download settings",new View.OnClickListener(){public void onClick(View v){settings();}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        bar.addView(brandRow,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.setPadding(0,0,dp(12),0);
        TextView title=NativeUi.header(this,"Downloads");title.setTextSize(TypedValue.COMPLEX_UNIT_SP,34);title.setTag("header_title");titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        // Home entry for hosts without the FEmusic_ultra_home pivot (DL33 builds). Hidden once the host bar has Home.
        homeEntry=pill("Home","yt_outline_home_vd_theme_24","home",false,new View.OnClickListener(){public void onClick(View v){NativeIntegration.openHome();}});homeEntry.setContentDescription("Open Home");titleRow.addView(homeEntry,new LinearLayout.LayoutParams(-2,dp(36)));
        homeEntry.setVisibility(NativeIntegration.hostHasHomePivot()?View.GONE:View.VISIBLE);
        bar.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        selectionHeader = new LinearLayout(this);
        selectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        selectionHeader.setPadding(dp(8), dp(4), dp(8), dp(4));
        selectionHeader.setMinimumHeight(dp(56));
        selectionHeader.setBackground(SelectionStyle.panel(this));
        selectionHeader.addView(NativeUi.iconButton(this, "yt_outline_x_mark_vd_theme_24", "close", "Exit selection", new View.OnClickListener(){public void onClick(View v){exitSelection();}}), new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionTitle = NativeUi.header(this, "0 selected");
        selectionTitle.setTag("selection_title");
        selectionHeader.addView(selectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button selectAllBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        selectAllBtn.setText("Select all");
        selectAllBtn.setTextColor(primary);
        selectAllBtn.setTextSize(14);
        selectAllBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selectAll();}});
        selectionHeader.addView(selectAllBtn, new LinearLayout.LayoutParams(-2, -2));
        root.addView(selectionHeader, new LinearLayout.LayoutParams(-1, -2));
        selectionHeader.setVisibility(View.GONE);

        selectionActions = new LinearLayout(this);
        selectionActions.setGravity(Gravity.CENTER_VERTICAL);
        selectionActions.setPadding(dp(16), dp(4), dp(16), dp(8));

        selectionActions.addView(actionBtn("Add to playlist", "yt_outline_playlist_add_vd_theme_24", "playlist", new View.OnClickListener(){public void onClick(View v){handleLocalPlaylistSelection();}}));
        selectionActions.addView(actionBtn("Play", "yt_outline_play_arrow_vd_theme_24", "play", new View.OnClickListener(){public void onClick(View v){handlePlaySelection();}}));
        selectionActions.addView(actionBtn("Download", "yt_outline_download_vd_theme_24", "download", new View.OnClickListener(){public void onClick(View v){handleDownloadSelection();}}));
        selectionActions.addView(actionBtn("Delete", "yt_outline_trash_can_vd_theme_24", "trash", new View.OnClickListener(){public void onClick(View v){handleDeleteSelection();}}));
        selectionActions.addView(actionBtn("Share", "yt_outline_share_vd_theme_24", "share", new View.OnClickListener(){public void onClick(View v){handleShareSelection();}}));


        HorizontalScrollView selectionScroll = new HorizontalScrollView(this);
        selectionScroll.setHorizontalScrollBarEnabled(true);
        selectionScroll.addView(selectionActions, new FrameLayout.LayoutParams(-2, -2));
        root.addView(selectionScroll, new LinearLayout.LayoutParams(-1, -2));
        selectionActions.setVisibility(View.GONE);
        // Search field, shown from the search icon.
        searchBar=new LinearLayout(this);searchBar.setGravity(Gravity.CENTER_VERTICAL);searchBar.setPadding(dp(12),0,0,0);
        searchBar.setBackground(NativeUi.card(this,Palette.of(this).chipOff,24,true));
        searchBar.addView(NativeUi.icon(this,"yt_outline_search_vd_theme_24","search",secondary,20),new LinearLayout.LayoutParams(dp(20),dp(20)));
        search=new EditText(this);search.setSingleLine(true);search.setBackground(null);search.setTextColor(primary);search.setHintTextColor(secondary);search.setTextSize(16);search.setHint("Search downloads");search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setPadding(dp(12),0,dp(4),0);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString().trim().toLowerCase(Locale.ROOT);render(true);}public void afterTextChanged(Editable e){}});
        searchBar.addView(search,new LinearLayout.LayoutParams(0,-1,1));
        searchBar.addView(NativeUi.iconButton(this,"yt_outline_x_mark_vd_theme_24","close","Close search",new View.OnClickListener(){public void onClick(View v){toggleSearch(false);}}),new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(40));sp.setMargins(dp(16),dp(4),dp(16),dp(4));root.addView(searchBar,sp);searchBar.setVisibility(View.GONE);
        // Compact native filter chips.
        chips=new HorizontalScrollView(this);chips.setHorizontalScrollBarEnabled(false);chips.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipRow=new LinearLayout(this);chipRow.setGravity(Gravity.CENTER_VERTICAL);int start=NativeUi.dimen(this,"chip_cloud_start_padding",16);chipRow.setPadding(start,dp(8),start,dp(8));chips.addView(chipRow,new FrameLayout.LayoutParams(-2,-2));
        root.addView(chips,new LinearLayout.LayoutParams(-1,-2));
        // Play / Shuffle, shown only when the current view has saved music.
        // Sort row, like the reference's "Recently saved ⌄": the current order opens the same Sort dialog; Shuffle and Play follow.
        actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(dp(16),dp(4),dp(8),dp(4));
        LinearLayout sortRow=new LinearLayout(this);sortRow.setGravity(Gravity.CENTER_VERTICAL);sortRow.setTag("sort");sortRow.setContentDescription("Sort");sortRow.setMinimumHeight(dp(44));sortRow.setClickable(true);sortRow.setBackground(NativeUi.rowBackground(this));
        sortRow.setOnClickListener(new View.OnClickListener(){public void onClick(View v){sortDialog();}});
        sortLabel=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body1",16,NativeUi.primary(this),false);sortLabel.setTag("sort_label");sortRow.addView(sortLabel,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams chev=new LinearLayout.LayoutParams(dp(20),dp(20));chev.setMarginStart(dp(6));sortRow.addView(NativeUi.icon(this,"yt_outline_chevron_down_vd_theme_24","chevron",NativeUi.primary(this),20),chev);
        actions.addView(sortRow,new LinearLayout.LayoutParams(-2,-2));
        actions.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        actions.addView(pill("Shuffle","yt_outline_arrow_shuffle_vd_theme_24","shuffle",false,new View.OnClickListener(){public void onClick(View v){playAll(true);}}),pillParams());
        actions.addView(pill("Play","yt_outline_play_arrow_vd_theme_24","play",true,new View.OnClickListener(){public void onClick(View v){playAll(false);}}),pillParams());
        root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        // Hero card (the reference's Radio card): gradient, coral icon tile, saved-music summary and a round coral Play.
        summaryCard=new LinearLayout(this);summaryCard.setOrientation(LinearLayout.HORIZONTAL);summaryCard.setGravity(Gravity.CENTER_VERTICAL);summaryCard.setTag("hero");summaryCard.setBackground(NativeUi.hero(this));summaryCard.setPadding(dp(14),dp(14),dp(14),dp(14));NativeUi.clip(summaryCard,20);
        FrameLayout heroIcon=new FrameLayout(this);heroIcon.setBackground(NativeUi.card(this,Palette.blend(Palette.CORAL,Palette.of(this).heroStart,0.18f),14,false));
        heroIcon.addView(NativeUi.icon(this,"yt_outline_download_vd_theme_24","download",Palette.CORAL,28),new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));summaryCard.addView(heroIcon,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout heroText=new LinearLayout(this);heroText.setOrientation(LinearLayout.VERTICAL);
        summary=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title2",17,primary,true);summary.setMaxLines(1);heroText.addView(summary,new LinearLayout.LayoutParams(-1,-2));
        summaryLine2=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",13,secondary,false);summaryLine2.setMaxLines(1);summaryLine2.setPadding(0,dp(3),0,0);heroText.addView(summaryLine2,new LinearLayout.LayoutParams(-1,-2));
        summaryLine3=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",13,secondary,false);summaryLine3.setMaxLines(1);summaryLine3.setPadding(0,dp(3),0,0);heroText.addView(summaryLine3,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,-2,1);htp.setMarginStart(dp(14));htp.setMarginEnd(dp(8));summaryCard.addView(heroText,htp);
        heroPlay=new ImageButton(this);heroPlay.setImageDrawable(NativeUi.drawable(this,"yt_outline_play_arrow_vd_theme_24","play",0xffffffff));heroPlay.setBackground(NativeUi.circle(Palette.CORAL));heroPlay.setScaleType(ImageView.ScaleType.FIT_CENTER);heroPlay.setPadding(dp(10),dp(10),dp(10),dp(10));heroPlay.setContentDescription("Play downloads");heroPlay.setTag("hero_play");
        heroPlay.setOnClickListener(new View.OnClickListener(){public void onClick(View v){playAll(false);}});summaryCard.addView(heroPlay,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(dp(16),dp(4),dp(16),dp(10));root.addView(summaryCard,cardParams);
        scroll=new ScrollView(this){
            @Override public boolean dispatchTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){scrollTouch=true;scrollEpoch++;}lastScrollAt=SystemClock.uptimeMillis();if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)scrollTouch=false;return super.dispatchTouchEvent(e);}
            @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(y!=oldY){lastScrollAt=SystemClock.uptimeMillis();scrollEpoch++;}}
        };scroll.setClipToPadding(false);scroll.setFillViewport(true);rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(16),0,dp(16),0);scroll.addView(rows,new FrameLayout.LayoutParams(-1,-2));FrameLayout content=new FrameLayout(this);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        virtualList=new ListView(this);virtualList.setItemsCanFocus(false);virtualList.setLongClickable(true);virtualList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){public boolean onItemLongClick(AdapterView<?> parent,View row,int position,long id){Object item=parent.getItemAtPosition(position);if(!(item instanceof Library.Item))return false;startSelection(((Library.Item)item).id);return true;}});virtualList.setDivider(new ColorDrawable(Color.TRANSPARENT));virtualList.setDividerHeight(dp(8));virtualList.setSelector(new ColorDrawable(Color.TRANSPARENT));virtualList.setCacheColorHint(Color.TRANSPARENT);virtualList.setClipToPadding(false);virtualList.setVisibility(View.GONE);
        virtualList.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){scrollTouch=state!=SCROLL_STATE_IDLE;lastScrollAt=SystemClock.uptimeMillis();scrollEpoch++;}public void onScroll(AbsListView v,int first,int count,int total){lastScrollAt=SystemClock.uptimeMillis();}});
        content.addView(virtualList,new FrameLayout.LayoutParams(-1,-1));
        // "+ New" coral FAB (Playlists tab): same action as the former "New local playlist" button.
        LinearLayout newButton=new LinearLayout(this);newButton.setGravity(Gravity.CENTER);newButton.setPadding(dp(18),0,dp(22),0);newButton.setBackground(NativeUi.pill(this,Palette.of(this).fab));newButton.setElevation(dp(3));
        newButton.addView(NativeUi.icon(this,"yt_outline_add_vd_theme_24","plus",0xffffffff,22),new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView newText=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title2",16,0xffffffff,true);newText.setText("New");LinearLayout.LayoutParams ntp=new LinearLayout.LayoutParams(-2,-2);ntp.setMarginStart(dp(8));newButton.addView(newText,ntp);
        newButton.setTag("action:New local playlist");newButton.setContentDescription("New local playlist");newButton.setClickable(true);newButton.setFocusable(true);
        newButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){newLocalPlaylist();}});
        fab=newButton;fab.setVisibility(View.GONE);content.addView(fab,new FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM|Gravity.END));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets wi){applyInsets();return wi;}});
        setContentView(root);root.requestApplyInsets();root.setClickable(true);root.setFocusableInTouchMode(true);root.requestFocus();
        mount.getViewTreeObserver().addOnGlobalLayoutListener(insets);
        tab=DownloadTabs.initial(this);
        if(state!=null){String savedTab=state.getString("tab");tab=DownloadTabs.restored(this,savedTab);group=tab.equals(savedTab)?state.getString("group",""):"";sort=state.getInt("sort",0);String q=state.getString("search","");if(state.getBoolean("searchOpen",false)||!q.isEmpty())toggleSearch(true);search.setText(q);selectionMode=state.getBoolean("selectionMode",false);ArrayList<String> sel=state.getStringArrayList("selectedKeys");if(sel!=null)selectedKeys.addAll(sel);}
        if(state!=null)pendingDelete=PermanentDelete.restore(host,this,state.getBundle("permanentDelete"),new Runnable(){public void run(){exitSelection();scannedAt=0;render(true);}});
        mediaAccess(true);PlaylistIndex.sync();
        String playlist=getIntent().getStringExtra("playlist");if(playlist!=null&&state==null)loadPlaylist(playlist);
        if(state==null&&getIntent().hasExtra("tab"))show(getIntent().getStringExtra("tab"),getIntent().getStringExtra("group"));
    }
    /** DL33: Home opens a local playlist or an artist here. Only the tab and group change, exactly as tapping them would. */
    void show(String name,String key){if(name==null||!Arrays.asList(TABS).contains(name))return;if(!DownloadTabs.visible(this).contains(name)){Hooks.message("Restore the "+name+" tab in Download settings to open this here");return;}selectionMode=false;selectedKeys.clear();tab=name;group=key==null?"":key;if(tab.equals("Playlists")&&!group.isEmpty()&&!PlaylistIndex.complete(this,group))PlaylistIndex.focus(group);render(true);}
    protected void onResume(){foreground=true;main.removeCallbacks(tick);tick.run();}
    protected void onPause(){foreground=false;main.removeCallbacks(tick);}
    protected void onDestroy(){io.shutdownNow();try{ViewTreeObserver o=mount.getViewTreeObserver();if(o.isAlive())o.removeOnGlobalLayoutListener(insets);}catch(Throwable ignored){}if(lastSheet!=null&&lastSheet.isShowing())lastSheet.dismiss();}
    protected void onSaveInstanceState(Bundle state){if(pendingDelete!=null&&pendingDelete.waiting)state.putBundle("permanentDelete",pendingDelete.save());state.putString("tab",tab);state.putString("group",group);state.putInt("sort",sort);state.putString("search",search.getText().toString());state.putBoolean("searchOpen",searchOpen);state.putBoolean("selectionMode",selectionMode);state.putStringArrayList("selectedKeys",new ArrayList<>(selectedKeys));}
    public void onBackPressed(){if(selectionMode){exitSelection();return;}if(searchOpen)toggleSearch(false);else if(!group.isEmpty()){group="";render(true);}else finish();}

    // ------------------------------------------------------------------ data
    private ArrayList<Library.Item> visible()throws Exception{
        Set<String> groupMembers=tab.equals("Playlists")&&!group.isEmpty()?PlaylistIndex.members(this,group):Collections.<String>emptySet();
        ArrayList<Library.Item> list=new ArrayList<>();for(Library.Item i:Library.all(this))if(VoiceRecordings.visible(this,i)&&(query.isEmpty()||(i.title+" "+i.artist+" "+i.album).toLowerCase(Locale.ROOT).contains(query))){if(!group.isEmpty()&&(tab.equals("Playlists")?!groupMembers.contains(i.id):!groupKey(i).equals(group)))continue;list.add(i);}
        if(tab.equals("Playlists")&&!group.isEmpty()){ArrayList<Library.Item> ordered=PlaylistIndex.ordered(this,group,list,true);if(!query.isEmpty())for(Iterator<Library.Item> it=ordered.iterator();it.hasNext();){Library.Item i=it.next();if(!(i.title+" "+i.artist).toLowerCase(Locale.ROOT).contains(query))it.remove();}return ordered;}
        Collections.sort(list,new Comparator<Library.Item>(){public int compare(Library.Item a,Library.Item b){if(sort==1)return a.title.compareToIgnoreCase(b.title);if(sort==2)return a.artist.compareToIgnoreCase(b.artist);if(sort==3)return Long.compare(b.total,a.total);return Long.compare(b.created,a.created);}});return list;
    }
    private String groupKey(Library.Item i){return groupKey(i,tab);}
    private static String groupKey(Library.Item i,String tab){String key=tab.equals("Artists")?i.artist:i.album;return key.isEmpty()?(tab.equals("Artists")?"Unknown Artist":"Unknown Album"):key;}
    private boolean isActive(Library.Item i){return Arrays.asList("queued","resolving","downloading","converting","saving").contains(i.state);}

    // ------------------------------------------------------------------ rendering
    private void render(){render(false);}
    private void render(boolean force){
        if(rows==null)return;
        if(!force&&(scrollTouch||SystemClock.uptimeMillis()-lastScrollAt<250))return;
        ArrayList<Library.Item> list,all;
        try{all=VoiceRecordings.filter(this,Library.all(this));list=visible();}catch(Exception e){structure="";holders.clear();rows.removeAllViews();TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body2",14,Palette.of(this).error,false);t.setMaxLines(4);t.setText("Downloads could not be read. "+e.getMessage());t.setPadding(dp(16),dp(16),dp(16),dp(16));rows.addView(t);return;}
        int completed=0,active=0;long bytes=0;boolean any=false,playable=false;
        for(Library.Item i:all){if(i.hidden)continue;any=true;if(i.state.equals("done")){completed++;bytes+=i.total;}if(isActive(i))active++;}
        for(Library.Item i:list)if(i.state.equals("done")){playable=true;break;}
        StringBuilder key=new StringBuilder().append(tab).append('|').append(group).append('|').append(query).append('|').append(sort).append('|').append(any);if(tab.equals("Playlists"))key.append(PlaylistIndex.revision(this));
        for(Library.Item i:list)key.append('|').append(i.id).append(':').append(i.state).append(':').append(i.paused).append(':').append(i.error.isEmpty()).append(':').append(i.title).append(':').append(i.artist).append(':').append(i.album);
        summary.setText(completed==0?(active>0?active+" downloading":""):completed+(completed==1?" song • ":" songs • ")+format(bytes)+(active>0?" • "+active+" downloading":""));
        if(tab.equals("Playlists")&&group.isEmpty()&&completed>0){int[] membership=LocalAudio.membership(this,all);summaryLine2.setText(membership[0]+" in playlists • "+membership[1]+" not matched yet");summaryLine2.setVisibility(View.VISIBLE);String sync=PlaylistIndex.syncStatus;boolean showSync=sync!=null&&!sync.isEmpty();summaryLine3.setText(showSync?sync:"");summaryLine3.setVisibility(showSync?View.VISIBLE:View.GONE);}else{summaryLine2.setVisibility(View.GONE);summaryLine3.setVisibility(View.GONE);}
        summaryCard.setVisibility(completed>0||active>0?View.VISIBLE:View.GONE);if(heroPlay!=null)heroPlay.setVisibility(completed>0?View.VISIBLE:View.GONE);
        if(sortLabel!=null)sortLabel.setText(SORTS[Math.max(0,Math.min(sort,SORTS.length-1))]);
        if(!force&&key.toString().equals(structure)){for(Library.Item i:list){Holder h=holders.get(i.id);if(h!=null)h.bind(i);}return;}
        structure=key.toString();holders.clear();final int y=scroll.getScrollY(),epoch=scrollEpoch;final int first=virtualList.getFirstVisiblePosition();final View firstView=virtualList.getChildAt(0);final int firstTop=firstView==null?0:firstView.getTop();final boolean wasVirtual=virtualMode;rows.removeAllViews();
        virtualMode=false;virtualList.setVisibility(View.GONE);scroll.setVisibility(View.VISIBLE);
        renderChips();
        if(selectionMode){
            bar.setVisibility(View.GONE);
            selectionHeader.setVisibility(View.VISIBLE);
            selectionActions.setVisibility(View.VISIBLE);
            chips.setVisibility(View.GONE);
            actions.setVisibility(View.GONE);
            selectionTitle.setText(selectedKeys.size()+" selected");
            updateSelectionActions();
        } else {
            bar.setVisibility(View.VISIBLE);
            selectionHeader.setVisibility(View.GONE);
            selectionActions.setVisibility(View.GONE);
            chips.setVisibility(any||!PlaylistIndex.groups(this).isEmpty()?View.VISIBLE:View.GONE);
            actions.setVisibility(playable&&!(tab.equals("Playlists")&&group.isEmpty())?View.VISIBLE:View.GONE);
        }
        fab.setVisibility(tab.equals("Playlists")&&group.isEmpty()&&!selectionMode?View.VISIBLE:View.GONE);
        if(tab.equals("Playlists")&&group.isEmpty()){
            LinearLayout createLine=new LinearLayout(this);createLine.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);createLine.setPadding(dp(16),0,dp(8),0);
            rows.addView(createLine);
            int playlistStart=rows.getChildCount();
            for(Map.Entry<String,String> e:PlaylistIndex.groups(this).entrySet()){if(!query.isEmpty()&&!e.getValue().toLowerCase(Locale.ROOT).contains(query))continue;ArrayList<Library.Item> found=new ArrayList<>();Set<String> ids=PlaylistIndex.members(this,e.getKey());for(Library.Item i:all)if(!i.hidden&&i.state.equals("done")&&ids.contains(i.id))found.add(i);playlistRow(e.getKey(),e.getValue(),found);}
            if(rows.getChildCount()==playlistStart)empty("Library playlists",PlaylistIndex.syncing?"Syncing your Library playlists...":Library.prefs(this).getString("library_sync_error","Open Library to sync your playlists."),"yt_outline_library_music_vd_theme_24","music");
        }else if(!any&&list.isEmpty())empty("Music for anywhere","Download songs or a whole playlist.\nYour music will be here, even offline.","yt_outline_download_vd_theme_24","download");
        else if(list.isEmpty()&&tab.equals("Playlists")&&!group.isEmpty()&&!PlaylistIndex.complete(this,group))empty("Playlist contents",PlaylistIndex.syncing?"Syncing this playlist...":"Playlist contents are not synced yet. Open it in Library or retry when online.","yt_outline_library_music_vd_theme_24","music");
        else if(list.isEmpty())empty(query.isEmpty()?"Nothing here yet":"No matching downloads",query.isEmpty()?"Songs you download will show up here.":"Try another song, artist or album.",query.isEmpty()?"yt_outline_download_vd_theme_24":"yt_outline_search_vd_theme_24",query.isEmpty()?"download":"search");
        else{
            if(tab.equals("Playlists")&&!group.isEmpty()&&!UnmatchedDownloads.is(group)){for(Library.Item i:list)if(i.state.equals("unavailable"))missingRow(i);else songRow(i);}
            else if(!tab.equals("Songs")&&group.isEmpty()){

                TreeMap<String,ArrayList<Library.Item>> groups=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for(Library.Item i:list)if(i.state.equals("done")){String k=groupKey(i);ArrayList<Library.Item> g=groups.get(k);if(g==null)groups.put(k,g=new ArrayList<>());g.add(i);}
                for(Map.Entry<String,ArrayList<Library.Item>> e:groups.entrySet())groupRow(e.getKey(),e.getValue());
            }else {
                virtualMode=true;scroll.setVisibility(View.GONE);virtualList.setVisibility(View.VISIBLE);
                final ArrayList<Library.Item> virtualItems=new ArrayList<>(list);
                virtualList.setAdapter(new BaseAdapter(){public int getCount(){return virtualItems.size();}public Object getItem(int position){return virtualItems.get(position);}public long getItemId(int position){return position;}public View getView(int position,View recycled,ViewGroup parent){if(recycled!=null&&recycled.getTag() instanceof String){String old=(String)recycled.getTag();if(old.startsWith("song:"))holders.remove(old.substring(5));}return makeSongRow(virtualItems.get(position));}});
                if(wasVirtual)virtualList.setSelectionFromTop(Math.min(first,Math.max(0,list.size()-1)),firstTop);
            }
        }
        scroll.post(new Runnable(){public void run(){if(!scrollTouch&&scrollEpoch==epoch)scroll.scrollTo(0,y);}});
        applyInsets();
    }
    private void renderChips(){
        chipRow.removeAllViews();
        for(final String name:DownloadTabs.visible(this)){
            View chip=NativeUi.chip(this,name,name.equals(tab),false,new View.OnClickListener(){public void onClick(View v){selectionMode=false;selectedKeys.clear();tab=name;group="";render(true);}});
            chip.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){tabMenu(name);return true;}});
            addChip(chip);
            if(name.equals(tab)&&!group.isEmpty())addChip(NativeUi.chip(this,tab.equals("Playlists")?PlaylistIndex.groups(this).get(group):group,true,true,new View.OnClickListener(){public void onClick(View v){selectionMode=false;selectedKeys.clear();group="";render(true);}}));
        }
    }
    private void tabMenu(final String name){
        final boolean pinned=DownloadTabs.pinned(this,name);
        NativeUi.dialog(this).setTitle(name).setItems(new String[]{pinned?"Unpin":"Pin to front","Delete tab"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            if(w==0){DownloadTabs.pin(DownloadsScreen.this,name,!pinned);render(true);}
            else NativeUi.dialog(DownloadsScreen.this).setTitle("Delete "+name+" tab?").setMessage("This only removes the tab. Your songs and playlists are kept. Restore it from Download settings.").setPositiveButton("Delete tab",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){DownloadTabs.hide(DownloadsScreen.this,name);if(tab.equals(name)){java.util.List<String> remaining=DownloadTabs.visible(DownloadsScreen.this);tab=remaining.isEmpty()?"Songs":remaining.get(0);group="";}render(true);}}).setNegativeButton("Cancel",null).show();
        }}).setNegativeButton("Cancel",null).show();
    }
    private void newLocalPlaylist(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Playlist name");input.setPadding(dp(20),dp(12),dp(20),dp(12));
        final AlertDialog dialog=NativeUi.dialog(this).setTitle("New local playlist").setMessage("Saved on this device. Add songs from Downloads using Add to playlist.").setView(input).setPositiveButton("Create",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface ignored){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Enter a playlist name");return;}JSONObject map=new JSONObject(Library.prefs(DownloadsScreen.this).getString("ultra_local_playlists","{}"));for(Iterator<String> it=map.keys();it.hasNext();)if(it.next().equalsIgnoreCase(name)){input.setError("A local playlist already has this name");return;}map.put(name,new JSONArray());Library.prefs(DownloadsScreen.this).edit().putString("ultra_local_playlists",map.toString()).apply();PlaylistIndex.invalidateCache();dialog.dismiss();render(true);Hooks.message("Created "+name);}catch(Exception e){error(e);}}});}});dialog.show();
    }
    private void addChip(View chip){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(32));p.setMarginEnd(dp(8));chipRow.addView(chip,p);}
    private LinearLayout.LayoutParams pillParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.setMarginEnd(dp(8));return p;}
    /** Play (saffron pill, black ink) and Shuffle (#1A1A1A pill with hairline) with native outlined icons. */
    private View pill(String label,String icon,String glyph,boolean filled,View.OnClickListener l){
        LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);int ink=NativeUi.chipInk(this,filled);
        b.addView(NativeUi.icon(this,icon,glyph,ink,20),new LinearLayout.LayoutParams(dp(20),dp(20)));
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title2",14,ink,true);t.setTextColor(ink);t.setText(label);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginStart(dp(8));b.addView(t,p);
        b.setBackground(NativeUi.chipBackground(this,filled));if(Build.VERSION.SDK_INT>=23){Drawable ripple=NativeUi.rowBackground(this);if(ripple!=null)b.setForeground(ripple);}
        b.setPadding(dp(14),0,dp(18),0);b.setClickable(true);b.setFocusable(true);b.setContentDescription(label);b.setTag("action:"+label);b.setOnClickListener(l);return b;
    }
    /** 56dp artwork with rounded (song/album) or circular (artist) clipping over a native placeholder icon. */
    private View thumb(Library.Item i,final boolean circle,int sizeDp){
        FrameLayout art=new FrameLayout(this);GradientDrawable bg=new GradientDrawable();bg.setColor(NativeUi.placeholder(this));if(circle)bg.setShape(GradientDrawable.OVAL);else bg.setCornerRadius(dp(14));art.setBackground(bg);
        ImageView fallback=new ImageView(this);fallback.setImageDrawable(Covers.placeholder(this,i==null?"song":(circle?"artist":Covers.kindOf(i))));fallback.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        final float radius=dp(14);art.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){if(circle)o.setOval(0,0,v.getWidth(),v.getHeight());else o.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}});art.setClipToOutline(true);
        art.setTag("thumb");if(i!=null)Art.load(this,i,image,dp(sizeDp));return art;
    }
    /** Playlist artwork: a collage of member song covers, or the designed Top Songs / playlist default. */
    private View playlistCover(String name,ArrayList<Library.Item> items,int sizeDp){
        FrameLayout art=new FrameLayout(this);GradientDrawable bg=new GradientDrawable();bg.setColor(NativeUi.placeholder(this));bg.setCornerRadius(dp(14));art.setBackground(bg);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageDrawable(Covers.playlist(this,name,items,dp(sizeDp*2)));art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        final float radius=dp(14);art.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}});art.setClipToOutline(true);
        art.setTag("thumb");return art;
    }
    private final class Holder{TextView subtitle;ProgressBar progress;void bind(Library.Item i){String value=detail(i);if(!value.contentEquals(subtitle.getText()))subtitle.setText(value);if(progress!=null){progress.setIndeterminate(i.total<=0);if(i.total>0)progress.setProgress((int)(i.bytes*100/i.total));}}}
    private String detail(Library.Item i){
        if(i.state.equals("done")){String artist=i.artist.isEmpty()?"Unknown artist":i.artist;String text=i.album.isEmpty()?artist:artist+" • "+i.album;return text+qualityLabel(i);}
        if(i.state.equals("failed"))return i.error.isEmpty()?"Download failed":i.error;
        if(i.state.equals("paused"))return i.error.isEmpty()?"Paused":i.error;
        if(i.state.equals("downloading"))return i.total>0?"Downloading • "+i.bytes*100/i.total+"%":"Downloading";
        if(i.state.equals("converting"))return "Encoding MP3 320 kbps • "+i.bytes+"%";if(i.state.equals("resolving"))return "Preparing";if(i.state.equals("saving"))return "Saving";if(i.state.equals("queued"))return "Waiting";
        return i.state;
    }
    static String qualityLabel(Library.Item i){String format=i.mime.equals("audio/mpeg")?"MP3":i.mime.contains("flac")?"FLAC":i.mime.contains("webm")?"WebM":i.mime.contains("ogg")?"Ogg":i.mime.contains("mp4")?"M4A":"Audio";return i.bitrate>0?" • "+format+" "+Math.round(i.bitrate/1000f)+" kbps":"";}
    private void startSelection(String key){selectionMode=true;selectedKeys.clear();selectedKeys.add(key);render(true);}
    private void rowMenu(final String key,final String title,final Runnable open,final ArrayList<Library.Item> items){
        NativeUi.dialog(this).setTitle(title).setItems(new String[]{"Open","Play","Shuffle","Select"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0){open.run();return;}if(which==3){startSelection(key);return;}ArrayList<String> ids=new ArrayList<>();for(Library.Item i:items)if("done".equals(i.state)&&!i.hidden)ids.add(i.id);if(ids.isEmpty()){Hooks.message("No downloaded songs in this view");return;}if(which==2)Collections.shuffle(ids);try{nativePlay(ids,0);}catch(Exception e){error(e);}}}).show();
    }
    private View more(View.OnClickListener listener){ImageButton b=NativeUi.iconButton(this,"yt_outline_experimental_overflow_vertical_vd_theme_24","more","More options",listener);b.setFocusable(false);b.setFocusableInTouchMode(false);return b;}
    private void downloadMenu(final Library.Item i,final boolean missing){
        if("done".equals(i.state)){trackMenu(i);return;}
        final boolean active=isActive(i);NativeUi.dialog(this).setTitle(i.title).setItems(new String[]{active?"Pause download":missing?"Download song":"Resume download","Select",missing?"Cancel":"Delete download"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w==1){startSelection(missing?"missing:"+i.id:i.id);return;}if(w==2){if(!missing)deleteConfirm(i);return;}try{if(missing)Hooks.download(i.id);else if(active)DownloadsService.pause(DownloadsScreen.this,i);else DownloadsService.enqueue(DownloadsScreen.this,i.id,i.title);render(true);}catch(Exception e){error(e);}}}).show();
    }
    private void songRow(final Library.Item i){rows.addView(makeSongRow(i),cardParams());}
    /** DL34 list card: flat 20dp card in the theme card colour with the theme ripple (light cards get a soft shadow). */
    private void cardRow(View line){line.setBackground(NativeUi.pressableCard(this,20));NativeUi.lift(line);}
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);return p;}
    private View makeSongRow(final Library.Item i){
        final boolean done=i.state.equals("done"),active=isActive(i);int secondary=NativeUi.secondary(this);
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(10),dp(4),dp(10));line.setMinimumHeight(dp(76));
        cardRow(line);
        line.addView(thumb(i,false,60),new LinearLayout.LayoutParams(dp(60),dp(60)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",16,NativeUi.primary(this),true);title.setText(i.title);text.addView(title,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);
        if(done||i.state.equals("failed")){ImageView mark=done?NativeUi.icon(this,"yt_outline_checkmark_circle_vd_theme_24","downloaded",Palette.CORAL,14):NativeUi.icon(this,"yt_outline_alert_circle_vd_theme_24","alert",Palette.of(this).error,14);mark.setContentDescription(done?"Downloaded":"Download failed");mark.setTag(done?"downloaded":"failed");LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(14),dp(14));mp.setMarginEnd(dp(6));meta.addView(mark,mp);}
        Holder h=new Holder();h.subtitle=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,secondary,false);if(i.state.equals("failed")){h.subtitle.setSingleLine(false);h.subtitle.setMaxLines(Integer.MAX_VALUE);h.subtitle.setEllipsize(null);h.subtitle.setTextIsSelectable(false);}meta.addView(h.subtitle,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams metaParams=new LinearLayout.LayoutParams(-1,-2);metaParams.topMargin=dp(2);text.addView(meta,metaParams);
        if(active){h.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);h.progress.setMax(100);h.progress.setProgressTintList(ColorStateList.valueOf(Palette.of(this).progress));h.progress.setProgressBackgroundTintList(ColorStateList.valueOf(Palette.of(this).hairline));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(2));pp.topMargin=dp(6);text.addView(h.progress,pp);}
        h.bind(i);holders.put(i.id,h);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(16));line.addView(text,tp);
        View.OnClickListener act=new View.OnClickListener(){public void onClick(View v){if(done){trackMenu(i);return;}try{if(isActive(i))DownloadsService.pause(DownloadsScreen.this,i);else DownloadsService.enqueue(DownloadsScreen.this,i.id,i.title);render(true);}catch(Exception e){error(e);}}};
        View action=more(new View.OnClickListener(){public void onClick(View v){downloadMenu(i,false);}});
        action.setTag("row_action");line.addView(action,new LinearLayout.LayoutParams(dp(48),dp(48)));
        if(selectionMode){
            CheckBox cb = new CheckBox(this);
            cb.setTag("row_checkbox");
            cb.setChecked(selectedKeys.contains(i.id));
            cb.setClickable(false);
            cb.setFocusable(false);
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
            cbp.gravity = Gravity.CENTER_VERTICAL;
            cbp.setMarginEnd(dp(8));
            line.addView(cb, 0, cbp);
            line.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    if(selectedKeys.contains(i.id)) selectedKeys.remove(i.id);
                    else selectedKeys.add(i.id);
                    if(selectedKeys.isEmpty()) exitSelection();
                    else render(true);
                }
            });
        } else {
            if(done){line.setOnClickListener(new View.OnClickListener(){public void onClick(View v){playOne(i);}});line.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selectionMode=true;selectedKeys.clear();selectedKeys.add(i.id);render(true);return true;}});}
            else{line.setOnClickListener(act);line.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selectionMode=true;selectedKeys.clear();selectedKeys.add(i.id);render(true);return true;}});}
        }
        line.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);line.setTag("song:"+i.id);line.setLayoutParams(new AbsListView.LayoutParams(-1,-2));return line;
    }
    private void groupRow(final String name,final ArrayList<Library.Item> items){
        boolean artist=tab.equals("Artists");
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(10),dp(4),dp(10));line.setMinimumHeight(dp(76));
        cardRow(line);
        line.addView(thumb(items.get(0),artist,60),new LinearLayout.LayoutParams(dp(60),dp(60)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",16,NativeUi.primary(this),true);title.setText(name);text.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,NativeUi.secondary(this),false);
        String kind=artist?"Artist":(items.get(0).artist.isEmpty()?"Album":"Album • "+items.get(0).artist);sub.setText(kind+" • "+items.size()+(items.size()==1?" song":" songs"));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(2);text.addView(sub,sp);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(16));line.addView(text,tp);
        if(selectionMode){
            CheckBox cb = new CheckBox(this);
            cb.setTag("row_checkbox");
            cb.setChecked(selectedKeys.contains(name));
            cb.setClickable(false);
            cb.setFocusable(false);
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
            cbp.gravity = Gravity.CENTER_VERTICAL;
            cbp.setMarginEnd(dp(8));
            line.addView(cb, 0, cbp);
            line.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    if(selectedKeys.contains(name)) selectedKeys.remove(name);
                    else selectedKeys.add(name);
                    if(selectedKeys.isEmpty()) exitSelection();
                    else render(true);
                }
            });
        } else {
            line.setOnClickListener(new View.OnClickListener(){public void onClick(View v){group=name;render(true);}});
            line.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selectionMode=true;selectedKeys.clear();selectedKeys.add(name);render(true);return true;}});
        }
        line.addView(more(new View.OnClickListener(){public void onClick(View v){rowMenu(name,name,new Runnable(){public void run(){group=name;render(true);}},items);}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        line.setTag("group:"+name);rows.addView(line,cardParams());
    }
    private void empty(String heading,String copy,String icon,String glyph){
        LinearLayout e=new LinearLayout(this);e.setOrientation(LinearLayout.VERTICAL);e.setGravity(Gravity.CENTER_HORIZONTAL);e.setPadding(dp(32),dp(72),dp(32),dp(32));e.setTag("empty");
        e.addView(NativeUi.icon(this,icon,glyph,NativeUi.primary(this),48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title1",20,NativeUi.primary(this),true);t.setMaxLines(2);t.setGravity(Gravity.CENTER);t.setText(heading);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.topMargin=dp(20);e.addView(t,tp);
        TextView c=NativeUi.text(this,"TextAppearance.YouTubeMusic.Body3",14,NativeUi.secondary(this),false);c.setMaxLines(4);c.setGravity(Gravity.CENTER);c.setText(copy);c.setLineSpacing(dp(2),1);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.topMargin=dp(8);e.addView(c,cp);
        rows.addView(e,new LinearLayout.LayoutParams(-1,-2));
    }
    private void toggleSearch(boolean open){
        searchOpen=open;searchBar.setVisibility(open?View.VISIBLE:View.GONE);InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(open){search.requestFocus();if(imm!=null)imm.showSoftInput(search,0);}
        else{if(search.getText().length()>0)search.setText("");if(imm!=null)imm.hideSoftInputFromWindow(search.getWindowToken(),0);root.requestFocus();}
    }

    // ------------------------------------------------------------------ insets for the host mini-player and bottom navigation
    private void applyInsets(){if(scroll==null||root==null)return;
        View decor=host.getWindow().getDecorView();WindowInsets wi=decor.getRootWindowInsets();int top=0,bottom=0;
        if(wi!=null){top=wi.getSystemWindowInsetTop();bottom=wi.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&wi.getDisplayCutout()!=null)top=Math.max(top,wi.getDisplayCutout().getSafeInsetTop());}
        if(top==0&&(host.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_FULLSCREEN)==0){Rect visible=new Rect();decor.getWindowVisibleDisplayFrame(visible);top=visible.top;int r=getResources().getIdentifier("status_bar_height","dimen","android");if(top<=0&&r!=0)top=getResources().getDimensionPixelSize(r);}
        Rect area=screen(mount),window=screen(decor);int y=area==null?0:area.top;int safe=uncoveredTop(top,y);
        if(root.getPaddingTop()!=safe)root.setPadding(0,safe,0,0);
        int nav=area==null||window==null?0:Math.max(0,area.bottom-(window.bottom-bottom));
        int want=Math.max(overlayInset(mount),nav)+dp(16);if(want!=inset){inset=want;scroll.setPadding(0,0,0,want);virtualList.setPadding(dp(16),0,dp(16),want);if(fab!=null){FrameLayout.LayoutParams fp=(FrameLayout.LayoutParams)fab.getLayoutParams();fp.setMargins(0,0,dp(16),want-dp(4));fab.setLayoutParams(fp);}}
    }
    /**
     * DL33: the same top/bottom inset computation as {@link #applyInsets}, for other screens mounted in fragment_container
     * (Home). Returns {top padding, bottom padding} in px; the bottom already includes the 16dp breathing room.
     */
    public static int[] contentInsets(Activity host,View mount){
        View decor=host.getWindow().getDecorView();WindowInsets wi=decor.getRootWindowInsets();int top=0,bottom=0;
        if(wi!=null){top=wi.getSystemWindowInsetTop();bottom=wi.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&wi.getDisplayCutout()!=null)top=Math.max(top,wi.getDisplayCutout().getSafeInsetTop());}
        if(top==0&&(host.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_FULLSCREEN)==0){Rect visible=new Rect();decor.getWindowVisibleDisplayFrame(visible);top=visible.top;int r=host.getResources().getIdentifier("status_bar_height","dimen","android");if(top<=0&&r!=0)top=host.getResources().getDimensionPixelSize(r);}
        Rect area=screen(mount),window=screen(decor);int y=area==null?0:area.top;
        int nav=area==null||window==null?0:Math.max(0,area.bottom-(window.bottom-bottom));
        return new int[]{uncoveredTop(top,y),Math.max(overlayInset(mount),nav)+NativeUi.dp(host,16)};
    }
    static int uncoveredTop(int systemTop,int contentTop){return Math.max(0,systemTop-Math.max(0,contentTop));}
    /**
     * Height of host views that are drawn above the bottom of the mount (mini-player, bottom navigation). Only later
     * siblings of the mount's ancestors count, they must span at least half its width and start in its lower half.
     */
    static int overlayInset(View mount){
        Rect mine=screen(mount);if(mine==null)return 0;int inset=0;View child=mount;ViewParent parent=mount.getParent();
        for(int depth=0;parent instanceof ViewGroup&&depth<12;depth++){ViewGroup g=(ViewGroup)parent;int index=g.indexOfChild(child);
            for(int n=index+1;n<g.getChildCount();n++){View s=g.getChildAt(n);if(s.getVisibility()!=View.VISIBLE||s.getAlpha()==0f)continue;Rect r=screen(s);if(r==null)continue;
                if(r.bottom<=mine.top||r.top>=mine.bottom||r.width()<mine.width()/2||r.top<mine.centerY())continue;inset=Math.max(inset,mine.bottom-r.top);}
            child=g;parent=g.getParent();}
        return inset;
    }
    static Rect screen(View v){if(v.getWidth()<=0||v.getHeight()<=0)return null;int[] at=new int[2];v.getLocationOnScreen(at);return new Rect(at[0],at[1],at[0]+v.getWidth(),at[1]+v.getHeight());}

    // ------------------------------------------------------------------ playback
    private ArrayList<String> playable()throws Exception{ArrayList<String> ids=new ArrayList<>();for(Library.Item i:visible())if(i.state.equals("done"))ids.add(i.id);return ids;}
    /**
     * DL58: Play / Shuffle never check files on the main thread. The checks and the queue build run in
     * NativePlayback.startAsync (20 s limit); "Preparing playback" appears after 400 ms and Back cancels the start.
     */
    private NativePlayback.Handle playing;private ProgressDialog preparing;
    private void nativePlay(ArrayList<String> ids,int index){
        if(playing!=null)playing.cancel();if(preparing!=null&&preparing.isShowing())preparing.dismiss();
        final ProgressDialog d=new ProgressDialog(this);d.setTitle("Preparing playback");d.setMessage("Checking "+ids.size()+" saved files…");d.setIndeterminate(true);d.setCancelable(true);d.setCanceledOnTouchOutside(false);preparing=d;
        final NativePlayback.Handle[] h=new NativePlayback.Handle[1];
        d.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface x){if(h[0]!=null)h[0].cancel();Hooks.message("Playback cancelled");}});
        final Runnable show=new Runnable(){public void run(){if(h[0]!=null&&!h[0].cancelled()&&!isFinishing()&&!isDestroyed())try{d.show();}catch(Throwable ignored){}}};
        h[0]=NativePlayback.startAsync(this,ids,index,new NativePlayback.Done(){public void done(int queued,Exception err,boolean limited){
            main.removeCallbacks(show);if(d.isShowing())try{d.dismiss();}catch(Throwable ignored){}
            if(isFinishing()||isDestroyed())return;
            if(err!=null)error(err);else if(limited)Hooks.message("Playing "+queued+" songs (file check stopped after 20 s)");}});
        playing=h[0];main.postDelayed(show,400);
    }
    private void playOne(Library.Item i){try{ArrayList<String> ids=playable();nativePlay(ids,Math.max(0,ids.indexOf(i.id)));}catch(Exception e){error(e);}}
    private void playAll(boolean shuffle){try{ArrayList<String> ids=playable();if(ids.isEmpty()){Hooks.message("No saved audio in this view");return;}if(shuffle)Collections.shuffle(ids);nativePlay(ids,0);}catch(Exception e){error(e);}}
    static final String[] SORTS={"Recently downloaded","Title A to Z","Artist A to Z","Largest first"};
    private void sortDialog(){NativeUi.dialog(this).setTitle("Sort").setSingleChoiceItems(SORTS,sort,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){sort=which;d.dismiss();render(true);}}).show();}

    // ------------------------------------------------------------------ track menu (native music_menu_title + music_menu_item rows)
    private void trackMenu(final Library.Item i){
        sheet(i,i.title,detail(i),new String[]{"Select","Copy or move to playlists","Play","Go to artist","Go to album","Hide from Downloads","Permanently delete"},
            new String[]{"yt_outline_check_vd_theme_24","yt_outline_playlist_add_vd_theme_24","yt_outline_play_arrow_vd_theme_24","yt_outline_person_vd_theme_24","yt_outline_album_vd_theme_24","yt_outline_eye_off_vd_theme_24","yt_outline_trash_can_vd_theme_24"},
            new String[]{"check","playlist","play","person","album","hide","trash"},
            new Runnable[]{
                new Runnable(){public void run(){startSelection(i.id);}},
                new Runnable(){public void run(){PlaylistTransfer.show(DownloadsScreen.this,java.util.Arrays.asList(new Catalog.Track(i.id,i.title,i.artist,i.album)),tab.equals("Playlists")?group:"",new Runnable(){public void run(){render(true);}});}},
                new Runnable(){public void run(){playOne(i);}},
                new Runnable(){public void run(){tab="Artists";group=groupKey(i,"Artists");render(true);}},
                new Runnable(){public void run(){tab="Albums";group=groupKey(i,"Albums");render(true);}},
                new Runnable(){public void run(){NativeUi.dialog(DownloadsScreen.this).setMessage("Hide this item from Downloads? The saved audio file will stay on storage.").setPositiveButton("Hide",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{Library.forget(DownloadsScreen.this,i);render(true);}catch(Exception e){error(e);}}}).setNegativeButton("Cancel",null).show();}},
                new Runnable(){public void run(){deleteConfirm(i);}}});
    }
    private void sheet(Library.Item art,String title,String subtitle,String[] labels,String[] icons,String[] glyphs,final Runnable[] actions){
        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(NativeUi.sheet(this));float r=dp(24);bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});box.setBackground(bg);box.setPadding(0,dp(4),0,dp(16));
        box.addView(sheetTitle(art,title,subtitle),new LinearLayout.LayoutParams(-1,-2));
        for(int n=0;n<labels.length;n++){View row=NativeUi.menuItem(this,icons[n],glyphs[n],labels[n]);final Runnable a=actions[n];row.setTag("menu:"+labels[n]);row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();a.run();}});box.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        d.setContentView(box);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);}
        lastSheet=d;d.show();NativeUi.enter(box);
    }
    private View sheetTitle(Library.Item art,String title,String subtitle){
        int layout=NativeUi.res(this,"music_menu_title","layout");
        if(layout!=0&&!NativeUi.broken.contains("music_menu_title")){
            try{View v=LayoutInflater.from(this).inflate(layout,null,false);View frame=v.findViewById(NativeUi.res(this,"thumbnail_frame","id")),t=v.findViewById(NativeUi.res(this,"title","id")),s=v.findViewById(NativeUi.res(this,"subtitle","id"));
                if(frame instanceof ViewGroup&&t instanceof TextView&&s instanceof TextView){((ViewGroup)frame).addView(thumb(art,false,48),new FrameLayout.LayoutParams(-1,-1));((TextView)t).setText(title);((TextView)s).setText(subtitle);NativeUi.note("menu title","native layout/music_menu_title");return v;}
            }catch(Throwable e){NativeUi.broken.add("music_menu_title");NativeUi.note("menu title","fallback: framework header (music_menu_title threw "+e.getClass().getSimpleName()+")");}
        }
        if(!NativeUi.report.containsKey("menu title"))NativeUi.note("menu title","fallback: framework header (layout/music_menu_title unavailable)");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(16),dp(16),dp(16),dp(16));
        line.addView(thumb(art,false,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        TextView t=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",16,NativeUi.primary(this),true);t.setMaxLines(2);t.setText(title);text.addView(t);
        TextView s=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,NativeUi.secondary(this),false);s.setText(subtitle);text.addView(s);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(12));line.addView(text,tp);box.addView(line);
        View divider=new View(this);divider.setBackgroundColor(Palette.of(this).hairline);box.addView(divider,new LinearLayout.LayoutParams(-1,Math.max(1,dp(1)/2)));
        return box;
    }

    // ------------------------------------------------------------------ actions (unchanged behaviour)
    private void deleteConfirm(final Library.Item i){ArrayList<Library.Item> items=new ArrayList<>();items.add(i);confirmPermanentDelete(items);}
    private void confirmPermanentDelete(final ArrayList<Library.Item> items){
      if(items.isEmpty()){Hooks.message("Select saved songs first");return;}
      final PermanentDelete operation=new PermanentDelete(host,this,items,new Runnable(){public void run(){exitSelection();scannedAt=0;render(true);}});
      String names="";for(int n=0;n<Math.min(items.size(),3);n++)names+=(n==0?"":"\n")+items.get(n).title;
      NativeUi.dialog(this).setTitle(items.size()==1?"Permanently delete song?":"Permanently delete "+items.size()+" songs?")
       .setMessage(names+(items.size()>3?"\n+ "+(items.size()-3)+" more":"")+"\n\nDeletes the selected audio files from this device, including imported songs. This cannot be undone. Online playlists and other files stay unchanged. Android may ask for permission.")
       .setPositiveButton("Delete permanently",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(pendingDelete!=null&&pendingDelete.busy){Hooks.message("Finish Android's current deletion prompt first");return;}pendingDelete=operation;pendingDelete.start();}}).setNegativeButton("Cancel",null).show();
    }
    /** Settings. Diagnostics lives only here (last item); there is no badge or overlay anywhere else. */
    private void settings(){
        final Switch voice=new Switch(this);voice.setText("Show voice recordings");voice.setTextSize(16);voice.setTextColor(NativeUi.primary(this));voice.setPadding(dp(20),dp(12),dp(20),dp(12));voice.setMinimumHeight(dp(64));voice.setTag("show_voice_recordings");voice.setChecked(VoiceRecordings.showing(this));
        voice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton b,boolean checked){VoiceRecordings.set(DownloadsScreen.this,checked);selectedKeys.clear();selectionMode=false;render(true);}});
        NativeUi.dialog(this).setTitle("Download settings").setCustomTitle(voice).setItems(new String[]{"Audio quality","Download location","Music folder name","Folder organization","Scan device audio and playlists","Hide completed history","Delete all downloads","About this downloader","Diagnostics","Restore download tabs",PageSkin.enabled(this)?"App page style: on":"App page style: off"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
        if(which==10){boolean on=!PageSkin.enabled(DownloadsScreen.this);PageSkin.set(DownloadsScreen.this,on);Hooks.message(on?"Quiet Premium style on for Library, Explore and Search":"Library, Explore and Search use the stock look");}
        if(which==0)NativeUi.dialog(DownloadsScreen.this).setTitle("Audio quality").setItems(new String[]{"Best available source audio","AAC / M4A preferred","Data saver"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){Library.prefs(DownloadsScreen.this).edit().putString("quality",new String[]{"best","aac","saver"}[w]).apply();Hooks.message("Applies to new downloads. No audio is converted to fake lossless.");}}).show();
        if(which==1)NativeUi.dialog(DownloadsScreen.this).setTitle("Download location").setMessage("Default: Music/"+Library.prefs(DownloadsScreen.this).getString("folder","YT Music Ultra")+"\nSelect a folder for future downloads, including an SD card. Existing files stay where they are.").setPositiveButton("Choose folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),91);}}).setNeutralButton("Use Music folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){Library.prefs(DownloadsScreen.this).edit().remove("tree").apply();}}).setNegativeButton("Cancel",null).show();
        if(which==2){final EditText name=new EditText(DownloadsScreen.this);name.setSingleLine(true);name.setText(Library.prefs(DownloadsScreen.this).getString("folder","YT Music Ultra"));NativeUi.dialog(DownloadsScreen.this).setTitle("Folder inside Music").setView(name).setPositiveButton("Save",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){Library.prefs(DownloadsScreen.this).edit().putString("folder",Transfer.safeName(name.getText().toString())).apply();}}).setNegativeButton("Cancel",null).show();}
        if(which==3)NativeUi.dialog(DownloadsScreen.this).setTitle("Folder organization").setItems(new String[]{"All songs in one folder","Artist folders","Artist / Album folders"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){Library.prefs(DownloadsScreen.this).edit().putString("organization",new String[]{"flat","artist","album"}[w]).apply();}}).show();
        if(which==9){DownloadTabs.restore(DownloadsScreen.this);render(true);Hooks.message("Download tabs restored");}if(which==8)Diagnostics.show(DownloadsScreen.this);if(which==4){scannedAt=0;mediaAccess(true);PlaylistIndex.sync();}if(which==5)hideHistory();if(which==6)deleteAll();if(which==7)NativeUi.dialog(DownloadsScreen.this).setTitle("Native audio downloads").setMessage("Best available unprotected source audio is encoded to MP3 at 320 kbps with title, artist, album and available cover art. MP3 is lossy, not hi-res or lossless. A 320 kbps output does not improve the source quality.\n\nUses YouTube Ultra's resumable range transfer and MP3 pipeline. LAME 3.100 is LGPL-2.0-or-later. Encoder source, build instructions and license are provided in the accompanying source archive. No LastWave source, paid catalog or DRM extraction is included.\n\nStreams come from this app's current player response or its authenticated stream request. Playback and conversion still depend on the phone's codecs and the source response.").setPositiveButton("Close",null).show();
    }}).show();}
    protected void onActivityResult(int request,int result,Intent data){if(request==PermanentDelete.REQUEST){if(pendingDelete!=null)pendingDelete.result(result);return;}if(request==91&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){try{Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));Library.prefs(this).edit().putString("tree",uri.toString()).apply();Hooks.message("New downloads will use the selected folder");}catch(Exception e){error(e);}}}
    /** DL58: the playlist read may take at most this long; then the songs already loaded are offered, or an error. */
    static volatile long READ_LIMIT_MS=20000;
    /** The playlist read (tests replace it): the songs loaded on the playlist page, else the Catalog read. */
    interface PlaylistReader{ArrayList<Catalog.Track> read(String id)throws Exception;}
    static PlaylistReader reader=new PlaylistReader(){public ArrayList<Catalog.Track> read(String id)throws Exception{NativeSnapshot.Snapshot snapshot=NativeSnapshot.get(id);return snapshot!=null?NativeSnapshot.loaded(id):Catalog.playlist(id);}};
    /**
     * DL58: "Reading playlist" is dismissible with Back (the read is cancelled and nothing is shown or queued), runs on
     * its own thread (not behind the screen's io queue) and gives up after READ_LIMIT_MS, offering the songs already
     * loaded on the playlist page when there are any.
     */
    private void loadPlaylist(final String id){
        final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.FutureTask<ArrayList<Catalog.Track>> read=new java.util.concurrent.FutureTask<>(new java.util.concurrent.Callable<ArrayList<Catalog.Track>>(){public ArrayList<Catalog.Track> call()throws Exception{
            return reader.read(id);}});
        final ProgressDialog busy=new ProgressDialog(this);busy.setTitle("Reading playlist");busy.setMessage("No songs are queued until you confirm the list size.");busy.setIndeterminate(true);busy.setCancelable(true);busy.setCanceledOnTouchOutside(false);
        busy.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){cancelled.set(true);read.cancel(true);}});
        busy.show();
        NativePlayback.WORK.execute(read);
        NativePlayback.WORK.execute(new Runnable(){public void run(){
            ArrayList<Catalog.Track> tracks=null;Exception failure=null;boolean timedOut=false;
            try{tracks=read.get(READ_LIMIT_MS,java.util.concurrent.TimeUnit.MILLISECONDS);}
            catch(java.util.concurrent.TimeoutException e){read.cancel(true);timedOut=true;tracks=NativeSnapshot.loaded(id);}
            catch(java.util.concurrent.CancellationException e){return;}
            catch(java.util.concurrent.ExecutionException e){failure=e.getCause() instanceof Exception?(Exception)e.getCause():e;}
            catch(InterruptedException e){return;}
            if(cancelled.get())return;
            final NativeSnapshot.Snapshot snapshot=NativeSnapshot.get(id);final boolean partial=timedOut||snapshot!=null&&!snapshot.complete;
            final ArrayList<Catalog.Track> found=tracks;
            final Exception error=failure!=null?failure:found==null||found.isEmpty()?new IOException(timedOut?"The playlist could not be read in "+READ_LIMIT_MS/1000+" s. Try again.":"No loaded songs are available yet"):null;
            main.post(new Runnable(){public void run(){
                if(cancelled.get()||isFinishing()||isDestroyed())return;try{busy.dismiss();}catch(Throwable ignored){}
                if(error!=null){error(error);return;}
                final ArrayList<Catalog.Track> tracks=found;
                NativeUi.dialog(DownloadsScreen.this).setTitle(partial?"Download loaded songs?":"Download playlist?").setMessage((partial?"Only the "+tracks.size()+" songs already loaded on this playlist page are available. This is NOT the whole playlist. More songs remain on later pages.\n\n":"All "+tracks.size()+" songs in this playlist are available.\n\n")+"Songs will be added using the selected quality and folder.").setPositiveButton("Download",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){io.execute(new Runnable(){public void run(){int added=0;try{added=Library.addBatch(DownloadsScreen.this,tracks);Intent start=new Intent(DownloadsScreen.this,DownloadsService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(start);else startService(start);Hooks.message(added+" songs added to Downloads");}catch(Exception e){Hooks.message(added+" songs added; remaining songs were not queued. "+e.getMessage());}refresh();}});}}).setNegativeButton("Cancel",null).show();
            }});
        }});
    }
    private void hideHistory(){NativeUi.dialog(this).setTitle("Hide completed history?").setMessage("Saved audio stays on storage. Active downloads stay visible.").setPositiveButton("Hide history",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{for(Library.Item i:Library.all(DownloadsScreen.this))if(i.state.equals("done"))Library.forget(DownloadsScreen.this,i);render(true);}catch(Exception e){error(e);}}}).setNegativeButton("Cancel",null).show();}
    private void deleteAll(){NativeUi.dialog(this).setTitle("Delete all downloads?").setMessage("Active downloads will be paused, then only tracked download files and partials will be deleted. This cannot be undone. Unrelated files are not touched.").setPositiveButton("Delete all",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){io.execute(new Runnable(){public void run(){int failed=0;try{synchronized(Library.LOCK){Library.wiping=true;for(Library.Item i:Library.all(DownloadsScreen.this))i.paused=true;}long until=SystemClock.elapsedRealtime()+60000;while(DownloadsService.runningAny()&&SystemClock.elapsedRealtime()<until)Thread.sleep(200);if(DownloadsService.runningAny())throw new IOException("Download connections have not stopped. Nothing was deleted. Try again.");for(Library.Item i:Library.all(DownloadsScreen.this)){try{if(!i.imported)Storage.delete(DownloadsScreen.this,i);Library.clearPartial(DownloadsScreen.this,i);Library.remove(DownloadsScreen.this,i);}catch(Exception e){failed++;i.state="failed";i.error="Could not delete: "+e.getMessage();Library.save(DownloadsScreen.this);}}Hooks.message(failed==0?"Tracked downloads deleted":failed+" downloads could not be deleted and remain listed");}catch(Exception e){error(e);}finally{synchronized(Library.LOCK){Library.wiping=false;}refresh();}}});}}).setNegativeButton("Cancel",null).show();}
    private void mediaAccess(boolean ask){if(!LocalAudio.allowed(this)){if(ask&&!mediaRequested){mediaRequested=true;requestPermissions(new String[]{LocalAudio.permission()},92);}return;}if(mediaScanning||scannedAt!=0)return;mediaScanning=true;io.execute(new Runnable(){public void run(){try{int n=LocalAudio.scan(DownloadsScreen.this);scannedAt=SystemClock.elapsedRealtime();if(n>0)Hooks.message(n+" existing audio files found");}catch(Exception e){Diagnostics.error("local_scan",e);}finally{mediaScanning=false;refresh();}}});}
    private void playlistRow(final String id,final String name,final ArrayList<Library.Item> items){LinearLayout line=new LinearLayout(this);line.setTag("playlist:"+id);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(10),dp(4),dp(10));line.setMinimumHeight(dp(76));cardRow(line);Library.Item cover=new Library.Item();cover.id="playlist_"+id;cover.artUrl=PlaylistIndex.art(this,id);if(UnmatchedDownloads.is(id))line.addView(SelectionStyle.unmatched(this),new LinearLayout.LayoutParams(dp(60),dp(60)));else if(!cover.artUrl.isEmpty())line.addView(thumb(cover,false,60),new LinearLayout.LayoutParams(dp(60),dp(60)));else line.addView(playlistCover(name,items,60),new LinearLayout.LayoutParams(dp(60),dp(60)));LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.Title2",16,NativeUi.primary(this),false);title.setText(name);texts.addView(title);TextView sub=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,NativeUi.secondary(this),false);int count=PlaylistIndex.total(this,id);sub.setText(UnmatchedDownloads.is(id)?items.size()+" downloaded • Auto playlist":items.size()+" downloaded"+(count>0||PlaylistIndex.complete(this,id)?" • "+count+(PlaylistIndex.complete(this,id)?(count==1?" song":" songs"):" indexed songs • Incomplete"):" • Contents not synced"));if(!UnmatchedDownloads.is(id)&&!id.startsWith("local:"))sub.append(" • Local copy");sub.setSingleLine(false);sub.setMaxLines(2);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);texts.addView(sub);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(16));line.addView(texts,lp);if(selectionMode){
            CheckBox cb = new CheckBox(this);
            cb.setTag("row_checkbox");
            cb.setChecked(selectedKeys.contains(id));
            cb.setClickable(false);
            cb.setFocusable(false);
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
            cbp.gravity = Gravity.CENTER_VERTICAL;
            cbp.setMarginEnd(dp(8));
            line.addView(cb, 0, cbp);
            line.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    if(selectedKeys.contains(id)) selectedKeys.remove(id);
                    else selectedKeys.add(id);
                    if(selectedKeys.isEmpty()) exitSelection();
                    else render(true);
                }
            });
        } else {
            line.setOnClickListener(new View.OnClickListener(){public void onClick(View v){group=id;if(!PlaylistIndex.complete(DownloadsScreen.this,id))PlaylistIndex.focus(id);render(true);}});
            line.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selectionMode=true;selectedKeys.clear();selectedKeys.add(id);render(true);return true;}});
        }
        line.addView(more(new View.OnClickListener(){public void onClick(View v){rowMenu(id,name,new Runnable(){public void run(){group=id;if(!PlaylistIndex.complete(DownloadsScreen.this,id))PlaylistIndex.focus(id);render(true);}},items);}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        rows.addView(line,cardParams());}
    private void missingRow(final Library.Item i){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(10),dp(4),dp(10));line.setMinimumHeight(dp(76));cardRow(line);line.addView(NativeUi.icon(this,"yt_outline_music_note_vd_theme_24","music",NativeUi.secondary(this),24),new LinearLayout.LayoutParams(dp(56),dp(56)));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView title=NativeUi.text(this,"TextAppearance.YouTubeMusic.ListItemTitle",16,NativeUi.secondary(this),true);title.setText(i.title);text.addView(title);TextView sub=NativeUi.text(this,"TextAppearance.YouTubeMusic.Subtitle1.Translucent",14,NativeUi.secondary(this),false);sub.setText("Not downloaded"+(i.artist.isEmpty()?"":" • "+i.artist));text.addView(sub);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(16));line.addView(text,lp);line.addView(more(new View.OnClickListener(){public void onClick(View v){downloadMenu(i,true);}}),new LinearLayout.LayoutParams(dp(48),dp(48)));line.setTag("unavailable:"+i.id);
        final String mKey = "missing:" + i.id;
        if(selectionMode){
            CheckBox cb = new CheckBox(this);
            cb.setTag("row_checkbox");
            cb.setChecked(selectedKeys.contains(mKey));
            cb.setClickable(false);
            cb.setFocusable(false);
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
            cbp.gravity = Gravity.CENTER_VERTICAL;
            cbp.setMarginEnd(dp(8));
            line.addView(cb, 0, cbp);
            line.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    if(selectedKeys.contains(mKey)) selectedKeys.remove(mKey);
                    else selectedKeys.add(mKey);
                    if(selectedKeys.isEmpty()) exitSelection();
                    else render(true);
                }
            });
        } else {
            line.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){selectionMode=true;selectedKeys.clear();selectedKeys.add(mKey);render(true);return true;}});
        }
        rows.addView(line,cardParams());}
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){if(request==92&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){scannedAt=0;mediaAccess(false);PlaylistIndex.sync();}}
    private void refresh(){main.post(new Runnable(){public void run(){if(!isFinishing()&&!isDestroyed())render(true);}});}
    private void error(final Exception e){main.post(new Runnable(){public void run(){if(!isFinishing()&&!isDestroyed())NativeUi.dialog(DownloadsScreen.this).setTitle("Downloads").setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("Close",null).show();}});}
    private static String format(long n){return String.format(Locale.US,n>=1024*1024*1024?"%.1f GB":"%.1f MB",n>=1024*1024*1024?n/1073741824d:n/1048576d);}

    private void exitSelection(){
        selectionMode=false;
        selectedKeys.clear();
        render(true);
    }

    private View actionBtn(String label, String icon, String glyph, View.OnClickListener act) {
        View box=SelectionStyle.action(this,icon,glyph,label,act);
        box.setTag("selection_action:"+label);return box;
    }

    private static boolean localOnly(Library.Item i) {
        if(i==null||!i.imported)return false;
        try{return i.id.equals(LocalAudio.localId(i.uri));}catch(Exception e){return true;}
    }
    private void updateSelectionActions() {
        boolean play=false,download=false,remove=false,share=false,playlist=false;
        try { for(Catalog.Track t:expandSelectedTracks()) { Library.Item i=Library.get(this,t.id);
            boolean saved=i!=null&&i.state.equals("done")&&Storage.exists(this,i.uri);
            play|=saved; remove|=i!=null; playlist=true;
            download|=!saved&&!localOnly(i)&&t.id.matches("[A-Za-z0-9_-]{11}");
            share|=!localOnly(i)||saved&&i.uri.startsWith("content:");
        }}catch(Exception ignored){}
        boolean[] enabled={playlist,play,download,remove,share};
        for(int n=0;n<selectionActions.getChildCount();n++) {View v=selectionActions.getChildAt(n);boolean e=n<enabled.length&&enabled[n];v.setVisibility(e?View.VISIBLE:View.GONE);}
    }

    private void selectAll(){
        try{
            ArrayList<Library.Item> list = visible();
            boolean allSelected = true;
            ArrayList<String> keys = new ArrayList<>();
            if(tab.equals("Playlists") && group.isEmpty()){
                for(Map.Entry<String,String> e:PlaylistIndex.groups(this).entrySet()){
                    if(!query.isEmpty()&&!e.getValue().toLowerCase(Locale.ROOT).contains(query))continue;
                    keys.add(e.getKey());
                    if(!selectedKeys.contains(e.getKey())) allSelected = false;
                }
            } else if(!tab.equals("Songs") && group.isEmpty()){
                Set<String> gKeys = new HashSet<>();
                for(Library.Item i:list) if(i.state.equals("done")) gKeys.add(groupKey(i));
                for(String k:gKeys){
                    keys.add(k);
                    if(!selectedKeys.contains(k)) allSelected = false;
                }
            } else {
                for(Library.Item i:list){
                    String k = i.state.equals("unavailable") ? "missing:"+i.id : i.id;
                    keys.add(k);
                    if(!selectedKeys.contains(k)) allSelected = false;
                }
            }
            if(allSelected) exitSelection();
            else {
                selectedKeys.addAll(keys);
                render(true);
            }
        }catch(Exception e){error(e);}
    }

    private Catalog.Track exactTrack(String id) {
        try { for(Library.Item item:PlaylistIndex.ordered(this,group,Library.all(this),true))if(id.equals(item.id))return new Catalog.Track(id,item.title,item.artist,item.album); } catch(Exception ignored){}
        return null;
    }

    private ArrayList<Catalog.Track> expandSelectedTracks(){
        ArrayList<Catalog.Track> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try{
            ArrayList<Library.Item> all = VoiceRecordings.filter(this,Library.all(this));
            Map<String, Library.Item> map = new HashMap<>();
            for(Library.Item i:all) if(i!=null&&i.id!=null) map.put(i.id, i);

            if(!group.isEmpty()){
                for(String key:selectedKeys){
                    String id = key.startsWith("missing:") ? key.substring(8) : key;
                    if(seen.add(id)){
                        Library.Item i = map.get(id);
                        if(i!=null) out.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                        else { Catalog.Track exact=exactTrack(id); if(exact!=null)out.add(exact); }
                    }
                }
            } else if(tab.equals("Songs")){
                for(String id:selectedKeys){
                    if(seen.add(id)){
                        Library.Item i = map.get(id);
                        if(i!=null) out.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
            } else if(tab.equals("Artists")){
                for(Library.Item i:all){
                    if(i==null||!i.state.equals("done")) continue;
                    String ak = i.artist.isEmpty() ? "Unknown Artist" : i.artist;
                    if(selectedKeys.contains(ak) && seen.add(i.id)){
                        out.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
            } else if(tab.equals("Albums")){
                for(Library.Item i:all){
                    if(i==null||!i.state.equals("done")) continue;
                    String ak = groupKey(i, "Albums");
                    if(selectedKeys.contains(ak) && seen.add(i.id)){
                        out.add(new Catalog.Track(i.id, i.title, i.artist, i.album));
                    }
                }
            } else if(tab.equals("Playlists")){
                for(String plId:selectedKeys){
                    ArrayList<Library.Item> ordered = PlaylistIndex.ordered(this, plId, all, true);
                    for(Library.Item item:ordered){
                        if(item!=null&&item.id!=null&&seen.add(item.id)){
                            out.add(new Catalog.Track(item.id, item.title, item.artist, item.album));
                        }
                    }
                }
            }
        }catch(Exception ignored){}
        return out;
    }

    private void handlePlaySelection(){
        ArrayList<Catalog.Track> tracks = expandSelectedTracks();
        ArrayList<String> playable = new ArrayList<>();
        try{
            for(Catalog.Track t:tracks){
                Library.Item item = Library.get(this, t.id);
                if(item!=null && item.state.equals("done")){
                    playable.add(item.id);
                }
            }
        }catch(Exception ignored){}
        if(playable.isEmpty()){
            Hooks.message("No downloaded songs in selection to play");
            return;
        }
        nativePlay(playable, 0);
        exitSelection();
    }

    private void handleDownloadSelection(){
        final ArrayList<Catalog.Track> tracks = expandSelectedTracks();
        if(tab.equals("Playlists") && group.isEmpty())for(String id:selectedKeys)if(!PlaylistIndex.complete(this,id)){Hooks.message("Sync selected playlists completely before downloading");return;}
        for(Iterator<Catalog.Track> it=tracks.iterator();it.hasNext();) { Catalog.Track t=it.next(); if(!t.id.matches("[A-Za-z0-9_-]{11}"))it.remove(); else try{Library.Item i=Library.get(this,t.id);if(localOnly(i)||i!=null && i.state.equals("done") && Storage.exists(this,i.uri))it.remove();}catch(Exception ignored){} }
        if(tracks.isEmpty()){
            Hooks.message("No downloadable songs selected");
            return;
        }
        NativeUi.dialog(this)
            .setTitle("Download selected")
            .setMessage("Download " + tracks.size() + " songs in 320 kbps MP3?")
            .setPositiveButton("Download", new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d, int w){
                    try{
                        int queued = Library.addBatch(DownloadsScreen.this, tracks);
                        Intent start = new Intent(DownloadsScreen.this, DownloadsService.class);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(start);
                        else startService(start);
                        Hooks.message("Queued " + queued + " songs for download");
                        exitSelection();
                    }catch(Exception e){error(e);}
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void handleDeleteSelection(){
      try{Set<String> ids=new HashSet<>();for(Catalog.Track t:expandSelectedTracks())ids.add(t.id);for(String key:selectedKeys)if(key.matches("[A-Za-z0-9_-]{11}"))ids.add(key);ArrayList<Library.Item> selected=new ArrayList<>();for(Library.Item i:Library.all(this))if(ids.contains(i.id))selected.add(i);confirmPermanentDelete(selected);}catch(Exception e){error(e);}
    }

    private void handleShareSelection(){
        ArrayList<Catalog.Track> tracks = expandSelectedTracks();
        if(tracks.isEmpty() && selectedKeys.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        StringBuilder sb = new StringBuilder();
        ArrayList<Uri> files = new ArrayList<>();
        try { for(Catalog.Track t:tracks){
            Library.Item i=Library.get(this,t.id);
            if(localOnly(i)) {if(i.uri.startsWith("content:")&&Storage.exists(this,i.uri)) files.add(Uri.parse(i.uri));}
            else sb.append("https://music.youtube.com/watch?v=").append(t.id).append("\n");
        }}catch(Exception e){error(e);return;}
        if(files.size()>0) {
            if(sb.length()>0){Hooks.message("Share downloaded files and online song links separately");return;}
            intent.setType("audio/*");
            if(files.size()==1)intent.putExtra(Intent.EXTRA_STREAM,files.get(0));
            else {intent.setAction(Intent.ACTION_SEND_MULTIPLE);intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,files);}
        } else if(sb.length()==0) {Hooks.message("No shareable song links or files in selection");return;}
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString().trim());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivity(Intent.createChooser(intent, "Share"));
            exitSelection();
        }catch(Exception e){Hooks.message("Share failed: " + e.getMessage());}
    }

    private void handleLocalPlaylistSelection(){
        final ArrayList<Catalog.Track> tracks = expandSelectedTracks();
        if(tracks.isEmpty()){
            Hooks.message("Select songs to add to a local playlist");
            return;
        }
        PlaylistTransfer.show(this,tracks,tab.equals("Playlists")?group:"",new Runnable(){public void run(){exitSelection();}});
    }
}
