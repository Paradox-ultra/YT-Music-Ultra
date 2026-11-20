package ultra.downloads;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.text.TextUtils;import android.view.*;import android.view.accessibility.*;import android.widget.*;import java.io.*;import java.lang.ref.*;import java.lang.reflect.*;import java.util.*;
/**
 * Bounded local-only hook evidence. Never stores account data, tokens, titles, UI text or media identifiers.
 * Only event codes and sanitized Java class names are written to the local log.
 *
 * Nothing is drawn over normal app screens: there is no badge and no dialog mirror. The log, the latest event and the
 * latest class line are readable only from Downloads settings > Diagnostics ({@link #show}).
 */
public final class Diagnostics {
 /** Log file name kept from the previous release so existing local evidence stays readable. Not shown in the UI. */
 static final String LOG="ultra-dl8-local.log";
 static Handler handler(){return new Handler(Looper.getMainLooper());}
 static final Map<String,Integer> counts=new LinkedHashMap<>();
 static WeakReference<Activity> owner=new WeakReference<>(null);static volatile String latest="ready";
 /** Latest dialog / custom view / fragment class names (class names only). Shown in the diagnostics screen. */
 static volatile String classLine="";

 /** Remembers the host activity only; no view is added to it. */
 public static void attach(Activity a){owner=new WeakReference<>(a);}

 // ---------------------------------------------------------------- log
 public static void event(String kind,String result){if(kind==null||result==null||!kind.matches("[a-z0-9_.-]{1,40}")||!result.matches("[a-z0-9_.:-]{1,60}"))return;synchronized(counts){Integer n=counts.get(kind);counts.put(kind,n==null?1:n+1);latest=kind+"#"+counts.get(kind)+":"+result;append(kind+" "+result);}}
 /** Logs sanitized class names only. Never text, IDs or account data. */
 static void classes(String scope,Collection<String> names){if(scope==null||!scope.matches("[a-z0-9_.-]{1,40}"))return;StringBuilder b=new StringBuilder();int n=0;for(String s:names){String safe=OfferEvidence.safeClassName(s);if(n++>=40){b.append(",...");break;}if(b.length()>0)b.append(',');b.append(safe);}synchronized(counts){append("classes "+scope+" "+(b.length()==0?"-":b));}}
 static void append(String body){try{Context c=Hooks.context();if(c==null)return;File f=new File(c.getFilesDir(),LOG);if(f.length()>16384)new FileOutputStream(f).close();try(FileOutputStream out=new FileOutputStream(f,true)){out.write((System.currentTimeMillis()/1000+" "+body+"\n").getBytes("UTF-8"));}}catch(Throwable ignored){}}
 public static void error(String scope,Throwable e){event(scope,"error-"+e.getClass().getSimpleName().toLowerCase(Locale.US));}
 /** Plain-text diagnostics: latest event, latest class line, rendering sources and the local log. */
 static String text(Context c){StringBuilder text=new StringBuilder("Local hook log. No account data, song IDs, titles or on-screen text are recorded; dialog entries list class names only.\n\n");
  text.append("Latest event: ").append(latest).append('\n');text.append("Latest classes: ").append(classLine.isEmpty()?"-":classLine).append("\n\n");
  try{int[] m=LocalAudio.membership(c,Library.all(c));text.append("Playlist membership (current cached catalog): ").append(m[0]).append(" matched, ").append(m[1]).append(" unmatched. Syncing: ").append(PlaylistIndex.syncing).append(".\n\n");}catch(Exception ignored){}
  try{text.append("Native Library order rows: ").append(new org.json.JSONArray(Library.prefs(c).getString("native_playlist_order","[]")).length()).append(".\n\n");}catch(Exception ignored){}
  text.append("Downloads screen rendering (native resource or fallback):\n").append(NativeUi.report()).append('\n');
  text.append("Playlist shuffle button: ").append(NativeShuffle.status()).append("\n\n");
  try{File f=new File(c.getFilesDir(),LOG);try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=in.readLine())!=null)text.append(line).append('\n');}}catch(Exception e){text.append("No hook events recorded yet.");}
  text.append("\nLibrary renderer schema (field names/types only):\n").append(LibraryEvidence.read(c));
  return text.toString();}
 public static void show(Context c){TextView view=new TextView(c);view.setText(text(c));view.setTextIsSelectable(true);int pad=(int)(16*c.getResources().getDisplayMetrics().density);view.setPadding(pad,pad,pad,pad);ScrollView scroll=new ScrollView(c);scroll.addView(view);NativeUi.dialog(c).setTitle("Diagnostics").setView(scroll).setPositiveButton("Close",null).show();}

 // ---------------------------------------------------------------- evidence
 static final int MAX_DEPTH=24,MAX_VIEWS=240,MAX_NODES=160,MAX_VIRTUAL=48,MAX_MISSES=8;
 static boolean exactOffer(View root){return scan(root)==OfferEvidence.Level.EXACT;}
 static OfferEvidence.Level scan(View root){if(root==null)return OfferEvidence.Level.NONE;OfferEvidence ev=new OfferEvidence();int[] budget={MAX_VIEWS,MAX_NODES};Rect bounds=screenRect(root);collect(root,ev,0,budget,bounds);return ev.level();}
 static Rect screenRect(View v){if(v.getWidth()<=0||v.getHeight()<=0)return null;int[] at=new int[2];v.getLocationOnScreen(at);return new Rect(at[0],at[1],at[0]+v.getWidth(),at[1]+v.getHeight());}
 static void collect(View v,OfferEvidence ev,int depth,int[] budget,Rect bounds){
  if(v==null||depth>MAX_DEPTH||--budget[0]<0||ev.truncated()||v.getVisibility()!=View.VISIBLE)return;
  if(v instanceof TextView)ev.add(((TextView)v).getText());
  if(v.getContentDescription()!=null)ev.add(v.getContentDescription());
  accessibility(v,ev,budget,bounds);
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&budget[0]>0;i++)collect(g.getChildAt(i),ev,depth+1,budget,bounds);}
 }
 /** Elements/canvas UIs expose text through accessibility nodes. Uses createAccessibilityNodeInfo only and recycles every node. */
 static void accessibility(View v,OfferEvidence ev,int[] budget,Rect bounds){
  if(bounds==null||budget[1]<=0)return; // strict: no on-screen dialog bounds, no node evidence
  AccessibilityNodeProvider p;try{p=v.getAccessibilityNodeProvider();}catch(Throwable e){p=null;}
  if(p==null){
   if(v instanceof TextView)return; // TextView text already collected directly
   AccessibilityNodeInfo n=null;
   try{budget[1]--;n=v.createAccessibilityNodeInfo();node(n,ev,bounds);}catch(Throwable ignored){}
   finally{if(n!=null)try{n.recycle();}catch(Throwable ignored){}}
   return;
  }
  int misses=0;
  for(int id=AccessibilityNodeProvider.HOST_VIEW_ID;id<MAX_VIRTUAL&&budget[1]>0&&!ev.truncated();id++){
   AccessibilityNodeInfo n=null;
   try{budget[1]--;n=p.createAccessibilityNodeInfo(id);if(n==null){if(id>=0&&++misses>=MAX_MISSES)break;continue;}misses=0;node(n,ev,bounds);}
   catch(Throwable e){if(id>=0&&++misses>=MAX_MISSES)break;}
   finally{if(n!=null)try{n.recycle();}catch(Throwable ignored){}}
  }
 }
 static void node(AccessibilityNodeInfo n,OfferEvidence ev,Rect bounds){if(n==null||!n.isVisibleToUser())return;Rect r=new Rect();n.getBoundsInScreen(r);if(r.isEmpty()||!bounds.contains(r))return;ev.add(n.getText());ev.add(n.getContentDescription());}

 // ---------------------------------------------------------------- class-name dump
 /** Native (R8-renamed) fragment manager class and its public List accessor, observed in the host. Mutable only for tests. */
 static String nativeFm="et",nativeFmList="l";
 static final int MAX_FRAGMENTS=24,MAX_REFLECT_METHODS=160;
 /** Picks the "meaningful" classes for the class line (log scope name dialog_badge.* kept for log continuity). Holds class names only. */
 static final class Picks{
  String provider,fragment,fallbackFragment;int fragmentDepth=-1;final Map<String,Integer> freq=new LinkedHashMap<>();
  void note(View v){if(v.getVisibility()!=View.VISIBLE)return;String n=v.getClass().getName();if(framework(n))return;
   if(provider==null){AccessibilityNodeProvider ap=null;try{ap=v.getAccessibilityNodeProvider();}catch(Throwable ignored){}if(ap!=null)provider=n;}
   Integer c=freq.get(n);freq.put(n,c==null?1:c+1);}
  /** Custom view with an accessibility provider (canvas UI) first, otherwise the most frequent non-framework visible view class. */
  String customView(){if(provider!=null)return provider;String best=null;int max=0;for(Map.Entry<String,Integer> e:freq.entrySet())if(e.getValue()>max){max=e.getValue();best=e.getKey();}return best;}
  void fragment(String name,int depth,Boolean visible){if(visible==null){if(depth==0)fallbackFragment=name;}else if(visible&&depth>=fragmentDepth){fragment=name;fragmentDepth=depth;}}
  String visibleFragment(){return fragment!=null?fragment:fallbackFragment;}
  boolean fragmentGuessed(){return fragment==null&&fallbackFragment!=null;}
 }
 static boolean framework(String n){return n.startsWith("android.")||n.startsWith("androidx.")||n.startsWith("com.android.")||n.startsWith("java.")||n.startsWith("com.google.android.material.");}
 static void dump(Dialog d,View root){dump(d,root,"initial");}
 /** Logs class names for one phase (initial|partial|exact|deadline) and refreshes the latest class line. Never text or IDs. */
 static void dump(Dialog d,View root,String phase){
  Picks p=new Picks();
  LinkedHashSet<String> views=new LinkedHashSet<>();views.add(d.getClass().getName());names(root,views,0,new int[]{MAX_VIEWS},p);classes("dialog_views."+phase,views);
  LinkedHashSet<String> frags=new LinkedHashSet<>();Activity a=activity(d.getContext());
  if(a!=null){frags.add(a.getClass().getName());fragments(a,frags,0,p,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));}
  if(frags.size()<=1)frags.add("fragments-unavailable");classes("dialog_fragments."+phase,frags);
  String view=p.customView(),frag=p.visibleFragment();
  classes("dialog_badge."+phase,Arrays.asList(d.getClass().getName(),view==null?"none":view,frag==null?"none":frag));
  classLine=line(d.getClass().getName(),view,frag,p.fragmentGuessed());
 }
 static String line(String dialog,String view,String fragment,boolean guessed){return "d:"+brief(dialog)+" v:"+brief(view)+" f:"+brief(fragment)+(fragment!=null&&guessed?"?":"");}
 /** Sanitized class name without its package, at most 32 chars. */
 static String brief(String n){if(n==null)return "-";String s=OfferEvidence.safeClassName(n);int dot=s.lastIndexOf('.');if(dot>=0)s=s.substring(dot+1);return s.length()>32?s.substring(0,31)+"~":s;}
 static void names(View v,Set<String> out,int depth,int[] budget,Picks p){if(v==null||depth>MAX_DEPTH||--budget[0]<0)return;if(out.size()<40)out.add(v.getClass().getName());if(p!=null)p.note(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&budget[0]>0;i++)names(g.getChildAt(i),out,depth+1,budget,p);}}
 static Activity activity(Context c){for(int i=0;i<8&&c!=null;i++){if(c instanceof Activity)return (Activity)c;if(!(c instanceof ContextWrapper))return null;c=((ContextWrapper)c).getBaseContext();}return null;}
 /** Best effort fragment walk: support API names first (getSupportFragmentManager/getChildFragmentManager/getFragments), then the native {@link #nativeFm} with {@link #nativeFmList}. Class names only. */
 static void fragments(Object host,Set<String> out,int depth,Picks p,Set<Object> seen){
  if(host==null||depth>2||out.size()>=MAX_FRAGMENTS)return;
  Object fm=manager(host,depth==0,seen);if(fm==null)return;
  List<?> list=list(fm);if(list==null)return;
  int n=0;
  for(Object f:list){if(f==null)continue;if(++n>MAX_FRAGMENTS||out.size()>=MAX_FRAGMENTS)return;String name=f.getClass().getName();out.add(name);p.fragment(name,depth,visible(f));fragments(f,out,depth+1,p,seen);}
 }
 static Object manager(Object host,boolean activity,Set<Object> seen){
  try{Object fm=host.getClass().getMethod(activity?"getSupportFragmentManager":"getChildFragmentManager").invoke(host);if(fm!=null&&seen.add(fm))return fm;}catch(Throwable ignored){}
  Class<?> type=nativeType(host);if(type==null)return null;
  int budget=MAX_REFLECT_METHODS;
  for(Class<?> c=host.getClass();c!=null&&c!=Object.class&&!framework(c.getName());c=c.getSuperclass()){
   Method[] methods;try{methods=c.getDeclaredMethods();}catch(Throwable e){return null;}
   for(Method m:methods){if(--budget<0)return null;int mod=m.getModifiers();
    if(!Modifier.isPublic(mod)||Modifier.isStatic(mod)||m.getParameterTypes().length!=0||!type.isAssignableFrom(m.getReturnType()))continue;
    try{Object fm=m.invoke(host);if(fm!=null&&seen.add(fm))return fm;}catch(Throwable ignored){} // seen: skips the parent manager when looking for a child one
   }
  }
  return null;
 }
 static Class<?> nativeType(Object o){try{return Class.forName(nativeFm,false,o.getClass().getClassLoader());}catch(Throwable e){return null;}}
 static List<?> list(Object fm){
  try{Method m=fm.getClass().getMethod("getFragments");Object l=Modifier.isStatic(m.getModifiers())?null:m.invoke(fm);if(l instanceof List)return copy((List<?>)l);}catch(Throwable ignored){}
  try{Class<?> t=nativeType(fm);if(t==null||!t.isInstance(fm))return null;Method m=t.getMethod(nativeFmList);if(Modifier.isStatic(m.getModifiers())||!List.class.isAssignableFrom(m.getReturnType()))return null;Object l=m.invoke(fm);if(l instanceof List)return copy((List<?>)l);}catch(Throwable ignored){}
  return null;
 }
 static List<Object> copy(List<?> l){ArrayList<Object> out=new ArrayList<>();try{for(Object o:l){out.add(o);if(out.size()>=MAX_FRAGMENTS)break;}}catch(Throwable ignored){}return out;}
 /** Fragment visibility via un-obfuscated isVisible(); null when not recoverable. */
 static Boolean visible(Object f){try{Method m=f.getClass().getMethod("isVisible");if(m.getReturnType()==boolean.class&&m.getParameterTypes().length==0)return (Boolean)m.invoke(f);}catch(Throwable ignored){}return null;}

 // ---------------------------------------------------------------- durable dialog guard
 static final long WATCH_MS=15000,TICK_MS=250,MIN_GAP_MS=120;static final int MAX_TICKS=(int)(WATCH_MS/TICK_MS)+4;
 static final int DUMP_INITIAL=1,DUMP_PARTIAL=2,DUMP_EXACT=4,DUMP_DEADLINE=8;
 static final Map<Dialog,Watch> watches=new WeakHashMap<>();
 /** Called from xz.onStart(), i.e. BEFORE Dialog.isShowing() becomes true. */
 public static void watch(final Dialog dialog){
  if(dialog==null)return;
  if(Looper.myLooper()!=Looper.getMainLooper()){handler().post(new Runnable(){public void run(){watch(dialog);}});return;}
  if(dialog.getWindow()==null)return;
  // DL32: the "View my" selector sheet that pops up on its own right after leaving Downloads is dismissed once.
  // A sheet the user opens by tapping the Library header appears outside the guard window (or after the one-shot
  // guard was spent) and is left alone.
  if(NativeIntegration.sheetGuardActive())guardLibrarySelector(dialog);
  Watch existing=watches.get(dialog);if(existing!=null&&!existing.done)return; // never stack watchers for one dialog
  Watch w=new Watch(dialog);watches.put(dialog,w);w.start();
 }
 /** Resource id of the selector sheet title string ("View my"), fixed in the host package. */
 static final int LIBRARY_SELECTOR_TITLE_ID=0x7f1403d9;
 /** Selector title override; null reads it from the host resources. Mutable only for tests. */
 static String selectorTitle;
 /** Dismisses the dialog once it is confirmed to be the library content selector sheet; a few late checks cover late inflation. */
 static void guardLibrarySelector(final Dialog dialog){
  final Runnable check=new Runnable(){int tries;public void run(){
   Dialog d=dialog; // held strongly by the runnable chain until the last check
   try{
    if(!d.isShowing())return;
    Window w=d.getWindow();if(w==null)return;
    if(isLibrarySelector(w.getDecorView())){
     if(NativeIntegration.consumeSheetGuard()){event("dialog_guard","library-selector-dismissed");try{d.dismiss();}catch(Throwable e){error("dialog_guard",e);}}
     return;
    }
   }catch(Throwable e){error("dialog_guard",e);}
   if(++tries<4)handler().postDelayed(this,150);
  }};
  handler().post(check);
 }
 /** True when the view subtree holds a TextView whose text exactly matches the host's selector title string. */
 static boolean isLibrarySelector(View root){
  try{
   Context c=Hooks.context();if(c==null||root==null)return false;
   String title=selectorTitle!=null?selectorTitle:c.getString(LIBRARY_SELECTOR_TITLE_ID);
   if(title==null||title.length()==0)return false;
   return hasExactText(root,title,0,new int[]{600});
  }catch(Throwable t){return false;}
 }
 static boolean hasExactText(View v,String s,int depth,int[] budget){
  if(v==null||depth>MAX_DEPTH||--budget[0]<0)return false;
  if(v instanceof TextView){CharSequence t=((TextView)v).getText();if(t!=null&&s.equals(t.toString().trim()))return true;}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&budget[0]>0;i++)if(hasExactText(g.getChildAt(i),s,depth+1,budget))return true;}
  return false;
 }
 static final class Watch implements ViewTreeObserver.OnGlobalLayoutListener,ViewTreeObserver.OnPreDrawListener,Runnable{
  final WeakReference<Dialog> dialog;final WeakReference<View> root;final Handler h=handler();final long deadline=SystemClock.uptimeMillis()+WATCH_MS;
  ViewTreeObserver observer;int ticks,dumps;long last;boolean done,shown,partial;
  Watch(Dialog d){dialog=new WeakReference<>(d);root=new WeakReference<>(d.getWindow().getDecorView());}
  void start(){event("dialog_guard","watching");View r=root.get();if(r!=null){observer=r.getViewTreeObserver();observer.addOnGlobalLayoutListener(this);observer.addOnPreDrawListener(this);}h.post(this);h.postDelayed(expire,WATCH_MS+TICK_MS);}
  final Runnable expire=new Runnable(){public void run(){finish("deadline");}};
  public void run(){if(done)return;ticks++;evaluate(true);if(done)return;if(ticks>=MAX_TICKS)finish("deadline");else h.postDelayed(this,TICK_MS);}
  public void onGlobalLayout(){evaluate(false);}
  public boolean onPreDraw(){evaluate(false);return true;}
  /** At most one dump per phase (4 per dialog), so late-loading content is captured without unbounded logging. */
  void snapshot(String phase,int bit){if((dumps&bit)!=0)return;dumps|=bit;Dialog d=dialog.get();View r=root.get();if(d==null||r==null)return;try{dump(d,r,phase);}catch(Throwable ignored){}}
  void evaluate(boolean force){
   if(done)return;long now=SystemClock.uptimeMillis();if(!force&&now-last<MIN_GAP_MS)return;last=now;
   Dialog d=dialog.get();View r=root.get();
   if(d==null||r==null){finish("released");return;}
   if(now>deadline){finish("deadline");return;}
   if(!d.isShowing()){if(shown)finish("hidden");return;} // not shown yet: keep waiting (DL7 removed itself here)
   shown=true;if(!r.isAttachedToWindow())return;
   if((dumps&DUMP_INITIAL)==0)snapshot("initial",DUMP_INITIAL);
   OfferEvidence.Level level;try{level=scan(r);}catch(Throwable e){error("dialog_guard",e);finish("scan-error");return;}
   if(level==OfferEvidence.Level.EXACT){done=true;detach();snapshot("exact",DUMP_EXACT);event("dialog_guard","matched-offline-trial");fallback(d);return;}
   if(level==OfferEvidence.Level.PARTIAL&&!partial){partial=true;snapshot("partial",DUMP_PARTIAL);event("dialog_guard","partial-diagnostic-only");}
  }
  void finish(String why){if(done)return;done=true;detach();if("deadline".equals(why)&&shown){Dialog d=dialog.get();if(d!=null&&d.isShowing())snapshot("deadline",DUMP_DEADLINE);}event("dialog_guard",why);}
  void detach(){h.removeCallbacks(this);h.removeCallbacks(expire);View r=root.get();remove(observer);if(r!=null)remove(r.getViewTreeObserver());Dialog d=dialog.get();if(d!=null&&watches.get(d)==this)watches.remove(d);}
  void remove(ViewTreeObserver o){if(o!=null&&o.isAlive()){o.removeOnGlobalLayoutListener(this);o.removeOnPreDrawListener(this);}}
 }
 /** Scoped fallback: closes only this exact offer dialog and opens the independent Downloads panel. */
 static void fallback(Dialog d){try{if(d.isShowing())d.dismiss();}catch(Throwable e){error("dialog_guard",e);return;}NativeIntegration.open();Hooks.message("Closed the Premium offer for offline downloads. Use a song's Download action.");}
}
