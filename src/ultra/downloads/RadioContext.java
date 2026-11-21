package ultra.downloads;
import android.view.*;import java.lang.reflect.*;import java.util.*;
/** Active Library gate and last actual player id. No synthetic/default seed. */
public final class RadioContext {
 public static volatile boolean libraryVisible;public static volatile String seed="";static final Map<View,String> rebound=new WeakHashMap<>();
 public static void remember(String id){if(id!=null&&id.matches("[A-Za-z0-9_-]{11}")){seed=id;try{Library.prefs(Hooks.context()).edit().putString("radio_last_played",id).apply();}catch(Throwable ignored){}}}
 public static String seed(){try{String id=Hooks.currentId();if(id!=null&&id.matches("[A-Za-z0-9_-]{11}")){seed=id;android.content.Context c=Hooks.context();if(!id.equals(Library.prefs(c).getString("radio_last_played","")))Library.prefs(c).edit().putString("radio_last_played",id).apply();}}catch(Throwable ignored){}if(seed.isEmpty())try{String old=Library.prefs(Hooks.context()).getString("radio_last_played","");if(old.matches("[A-Za-z0-9_-]{11}"))seed=old;}catch(Throwable ignored){}return seed;}
 public static void refresh(View root){String s=seed();if(s.isEmpty()||s.equals(rebound.get(root)))return;try{Class<?> rv=Class.forName("android.support.v7.widget.RecyclerView");int changed=refresh(root,rv,0,new int[]{180});if(changed>0)rebound.put(root,s);}catch(Throwable e){Diagnostics.error("radio_rebind",e);}}
 static int refresh(View v,Class<?> rv,int depth,int[] budget)throws Exception{if(v==null||depth>18||--budget[0]<0)return 0;int n=0;if(rv.isInstance(v)){Object a=rv.getField("n").get(v);if(a!=null){Class.forName("th").getMethod("eb").invoke(a);n++;}}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=refresh(g.getChildAt(i),rv,depth+1,budget);}return n;}
}
