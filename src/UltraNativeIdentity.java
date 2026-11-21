import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import com.google.protos.youtube.api.innertube.WatchEndpointOuterClass;
import com.google.protos.youtube.api.innertube.OfflineWatchEndpointOuterClass;
import java.util.*;
import android.content.Context;
/** Exact host model endpoints and a reversible positional Library view. Native sources are never mutated. */
public final class UltraNativeIdentity {
 public static String endpoint(bjue e) {
  if(e==null)return null;
  try {Object p=e.j.l(BrowseEndpointOuterClass.browseEndpoint.d);if(p instanceof bizw){String s=((bizw)p).c;if(s.startsWith("MPLAUC"))return s.substring(4);if("FEmusic_ultra_radio".equals(s))return "ULTRA_RADIO";if(s.startsWith("VL")||s.startsWith("MPRE")||s.startsWith("UC")||s.startsWith("RD")||"LM".equals(s))return s;}}catch(Throwable ignored){}
  try {Object p=e.j.l(WatchEndpointOuterClass.watchEndpoint.d);if(p instanceof bwmb){String s=((bwmb)p).d;if(s.matches("[A-Za-z0-9_-]{11}"))return s;}}catch(Throwable ignored){}
  try {Object p=e.j.l(OfflineWatchEndpointOuterClass.offlineWatchEndpoint.d);if(p instanceof brka){brka r=(brka)p;String s=r.c;if(s!=null&&s.matches("[A-Za-z0-9_-]{11}"))return s;String local=ultra.downloads.LocalAudio.nativeUriId(s);if(local!=null)return local;return ultra.downloads.LocalAudio.nativeUriId(r.d);}}catch(Throwable ignored){}
  try {Object p=e.j.l(buea.b.d);if(p instanceof buea)return ultra.downloads.LocalAudio.nativeUriId(((buea)p).d);}catch(Throwable ignored){}
  return null;
 }
 public static String id(Object m) {
  if(m instanceof bads)m=((bads)m).b;
  if(m instanceof oez)m=((oez)m).a;
  if(m instanceof bqtu){bqtu r=(bqtu)m;String s=endpoint(r.i);return s!=null?s:endpoint(r.k);}
  if(m instanceof bsca){String s=((bsca)m).c;return s.matches("[A-Za-z0-9_-]{11}")?s:null;}
  if(m instanceof amrt)return endpoint(((amrt)m).d);
  if(m instanceof bsez)return endpoint(((bsez)m).k);
  return null;
 }
 public static void reorderAdapter(Object input,Context c){
  if(!(input instanceof azwh))return;azwh a=(azwh)input;
  if(a.f instanceof Ordered){((Ordered)a.f).refresh(c);return;}
  a.f=new Ordered(a.f,c);
 }
 public static boolean bindingMatches(Object input,int position,Object model){
  if(!(input instanceof azwh)||position<0)return true;azwh a=(azwh)input;if(position>=a.a())return true;String expected=id(a.getItem(position)),actual=id(model);return expected==null||actual==null||expected.equals(actual);
 }
 public static boolean maintainAdapterOrder(Object input,Context c){
  if(!(input instanceof azwh))return false;final azwh a=(azwh)input;if(a.f instanceof Ordered)return false;
  reorderAdapter(a,c);new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){public void run(){try{a.eb();}catch(Throwable ignored){}}});return true;
 }
 static final class Ordered implements azum {
  final azum original;int[] order;String[] sourceKeys;String signature="";Context context;
  Ordered(azum o,Context c){original=o;refresh(c);}
  void refresh(Context c){context=c;signature=ultra.downloads.PinManager.getPinnedList(c).toString();int n=original.a();order=new int[n];sourceKeys=new String[n];for(int i=0;i<n;i++)order[i]=i;
   ArrayList<Integer> slots=new ArrayList<>();ArrayList<Integer> first=new ArrayList<>();ArrayList<Integer> pinned=new ArrayList<>();ArrayList<Integer> rest=new ArrayList<>();ArrayList<Integer> liked=new ArrayList<>();Set<String> selected=ultra.downloads.PinManager.getPinnedList(c);
   for(int i=0;i<n;i++){String key=id(original.d(i));sourceKeys[i]=key;if(key==null)continue;slots.add(i);String k=key.startsWith("VL")?key.substring(2):key;if("ULTRA_RADIO".equals(k))first.add(i);else if(selected.contains(k))pinned.add(i);else if("LM".equals(k))liked.add(i);else rest.add(i);}
   first.addAll(pinned);int likedAt=rest.size();for(int j=rest.size()-1;j>=0;j--){String key=id(original.d(rest.get(j)));if(key!=null&&(key.startsWith("VLPL")||key.startsWith("PL"))){likedAt=j+1;break;}}rest.addAll(likedAt,liked);
   first.addAll(rest);for(int i=0;i<slots.size();i++)order[slots.get(i)]=first.get(i);
  }
  int mapped(int i){if(order.length!=original.a())refresh(context);if(i>=0&&i<order.length){int target=order[i];if(!Objects.equals(sourceKeys[i],id(original.d(i)))||!Objects.equals(sourceKeys[target],id(original.d(target))))refresh(context);return order[i];}return i;}
  public int a(){return original.a();}public long c(int i){return original.c(mapped(i));}public boolean contains(Object m){return original.contains(m);}public Object d(int i){return original.d(mapped(i));}public void e(azvr v){original.e(v);}public void f(azvq v,int i){original.f(v,mapped(i));}public void h(azul l){original.h(l);}public void j(azul l){original.j(l);}public boolean isEmpty(){return original.isEmpty();}
 }
 /** Called after the real native menu list has been built, rather than guessing fields. */
 public static void appendPin(List list,Object model){
  try {String key=id(model);if(key!=null&&key.matches("[A-Za-z0-9_-]{11}")&&!key.startsWith("VL")&&!key.startsWith("MPRE")&&!key.startsWith("UC"))return;
   if(key==null)for(Object item:list){if(!(item instanceof bpnf))continue;bpnn service=((bpnf)item).d;if(service==null||service.e==null)continue;Class<?> ec=Class.forName("com.google.protos.youtube.api.innertube.OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint");bhbi ext=(bhbi)ec.getField("offlinePlaylistEndpoint").get(null);Object p=service.e.j.l(ext.d);if(ec.isInstance(p)){String k=(String)ec.getField("c").get(p);if(k!=null&&!k.isEmpty()){key=k;break;}}}
   if(key==null||!(key.startsWith("VL")||key.startsWith("PL")||key.startsWith("MPRE")||key.startsWith("UC")||key.startsWith("RD")||"LM".equals(key)))return;
   Context c=ultra.downloads.Hooks.context();Object pin=ultra.downloads.NativeModels.createPinMenuItem(c,key);if(pin!=null)list.add(pin);
  }catch(Throwable t){ultra.downloads.Diagnostics.error("pin_menu",t);}
 }
}
