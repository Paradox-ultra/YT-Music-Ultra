package ultra.downloads;
import android.graphics.drawable.Drawable;import android.view.*;import android.widget.*;import java.lang.ref.*;import java.lang.reflect.*;import java.util.*;

/**
 * Playlist page action row: replaces the native Share round button (between Play and the overflow button) with a Shuffle
 * round button that runs the page's OWN native shuffle-play command through the page's OWN command dispatcher.
 *
 * <p>Three host facts are needed and none of them is in this project's native sources or resources:
 * <ol>
 *  <li>which view in the playlist header is the Share round button ({@link Binding#share});</li>
 *  <li>where the page keeps its native shuffle-play command for this playlist ({@link Binding#command});</li>
 *  <li>which host object dispatches a command for the page ({@link Binding#dispatch}).</li>
 * </ol>
 * Until a {@link Binding} for the installed host is supplied, {@link #binding} is null and NOTHING changes: Share stays,
 * no button is added, nothing is guessed. There is deliberately no fallback that shuffles only the rows loaded on screen.
 * Albums, artists and every other page are never touched; Share elsewhere in the app is never touched.
 */
public final class NativeShuffle {
 private NativeShuffle(){}
 public interface Binding {
  /** The native Share round button inside this page's playlist action row, or null when it is not a real View. */
  View share(Object page,View root);
  /** The page's own native shuffle-play command for exactly this playlist id (without the VL prefix), or null. */
  Object command(Object page,String playlistId);
  /** Dispatches the command through the page's own native command handling. Returns false if it was not accepted. */
  boolean dispatch(Object page,Object command);
 }
 /** Null in this build: the exact host bindings are not available (see REPORT9.md). */
 static volatile Binding binding;
 public static volatile boolean nativeModelBound;
 public static void modelBound(){nativeModelBound=true;status="active: native Elements Shuffle command replaces playlist Share";}
 static volatile String status="unbound: exact host bindings not supplied; Share left unchanged";
 static final String TAG="ultra_native_shuffle";
 static final class State{final WeakReference<View> share,shuffle;final String playlist;final int visibility;
  State(View share,View shuffle,String playlist,int visibility){this.share=new WeakReference<>(share);this.shuffle=new WeakReference<>(shuffle);this.playlist=playlist;this.visibility=visibility;}}
 static final WeakHashMap<View,State> applied=new WeakHashMap<>();
 static final Set<String> logged=new HashSet<>();

 static String status(){return status;}
 /** Only real playlists (browse id VL...) qualify. Albums (MPRE...), artists and other pages never change. */
 static String playlist(String browseId){if(browseId==null||!browseId.startsWith("VL"))return null;String id=browseId.substring(2);return id.matches("[A-Za-z0-9_-]{2,200}")?id:null;}
 static void once(String what){synchronized(logged){if(!logged.add(what))return;}Diagnostics.event("playlist_shuffle",what);}

 /** Called for the visible browse page on every binding update. Idempotent. */
 public static void apply(final Object page,View root,String browseId){
  if(root==null)return;
  final String playlist=playlist(browseId);
  if(playlist==null){restore(root);return;}
  final Binding b=binding;
  if(b==null){restore(root);status=nativeModelBound?"active: native Elements Shuffle command replaces playlist Share":"native model binding: waiting for a supported playlist response";once("unbound");return;}
  View share;try{share=b.share(page,root);}catch(Throwable e){share=null;Diagnostics.error("playlist_shuffle",e);}
  State s=applied.get(root);
  if(s!=null&&playlist.equals(s.playlist)&&share!=null&&s.share.get()==share){View mine=s.shuffle.get();if(mine!=null&&mine.getParent()==share.getParent()&&share.getVisibility()==View.GONE)return;}
  restore(root);
  if(share==null||!(share.getParent() instanceof ViewGroup)||TAG.equals(share.getTag())){status="bound, but no native Share view found on this playlist page; Share left unchanged";once("no-share-view");return;}
  Object command;try{command=b.command(page,playlist);}catch(Throwable e){command=null;Diagnostics.error("playlist_shuffle",e);}
  if(command==null){status="bound, but this page has no native shuffle command; Share left unchanged";once("no-command");return;}
  ViewGroup row=(ViewGroup)share.getParent();
  View shuffle=button(share,new View.OnClickListener(){public void onClick(View v){run(b,page,playlist);}});
  row.addView(shuffle,row.indexOfChild(share)+1,copy(share.getLayoutParams()));
  int visibility=share.getVisibility();share.setVisibility(View.GONE);
  applied.put(root,new State(share,shuffle,playlist,visibility));status="active: native Shuffle replaces Share on playlist pages";once("applied");
 }
 /** Re-reads the command at tap time so a reloaded page can never run a stale command, then hands it to the host. */
 static void run(Binding b,Object page,String playlist){
  try{Object command=b.command(page,playlist);
   if(command!=null&&b.dispatch(page,command)){Diagnostics.event("playlist_shuffle","dispatched");return;}
   Diagnostics.event("playlist_shuffle","not-dispatched");Hooks.message("Shuffle isn't available for this playlist right now");
  }catch(Throwable e){Diagnostics.error("playlist_shuffle",e);Hooks.message("Shuffle isn't available for this playlist right now");}
 }
 /** Removes our button and shows the original Share button again. */
 public static void restore(View root){
  State s=applied.remove(root);if(s==null)return;
  View mine=s.shuffle.get();if(mine!=null&&mine.getParent() instanceof ViewGroup)((ViewGroup)mine.getParent()).removeView(mine);
  View share=s.share.get();if(share!=null)share.setVisibility(s.visibility);
 }
 /** A round button that copies the Share button's own native background, size and padding, with the native shuffle icon. */
 static View button(View share,View.OnClickListener l){
  ImageButton b=new ImageButton(share.getContext());
  b.setImageDrawable(NativeUi.drawable(share.getContext(),"yt_outline_arrow_shuffle_vd_theme_24","shuffle",null));
  Drawable bg=share.getBackground();Drawable copy=bg==null||bg.getConstantState()==null?null:bg.getConstantState().newDrawable(share.getResources()).mutate();
  b.setBackground(copy);b.setPadding(share.getPaddingLeft(),share.getPaddingTop(),share.getPaddingRight(),share.getPaddingBottom());
  b.setMinimumWidth(share.getMinimumWidth());b.setMinimumHeight(share.getMinimumHeight());b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
  if(share instanceof ImageView){b.setImageTintList(((ImageView)share).getImageTintList());b.setScaleType(((ImageView)share).getScaleType());}
  b.setContentDescription("Shuffle");b.setTag(TAG);b.setOnClickListener(l);return b;
 }
 /** Copy of the Share button's layout params, using the params type's own copy constructor when it has one. */
 static ViewGroup.LayoutParams copy(ViewGroup.LayoutParams p){
  if(p==null)return new ViewGroup.LayoutParams(-2,-2);
  try{Constructor<? extends ViewGroup.LayoutParams> k=p.getClass().getConstructor(p.getClass());return k.newInstance(p);}catch(Throwable ignored){}
  if(p instanceof ViewGroup.MarginLayoutParams)return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams)p);
  return new ViewGroup.LayoutParams(p);
 }
}
