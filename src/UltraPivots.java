import java.util.ArrayList;
import java.util.List;

/**
 * DL34 bottom-bar order, kept in the module so the host change stays one small method body. The host's
 * lac.ultraDownloadPivots(List) builds the two module pivots with its own private lac.s(id,label,icon) and calls
 * {@link #order}; see DL34-IMPLEMENTATION.md.
 *
 * Order: Home (FEmusic_ultra_home), Explore (FEmusic_explore), Search (FEmusic_home, which the host already routes to
 * Search), Library (FEmusic_library_landing), Downloads (FEmusic_ultra_downloads), then any other host pivot in its
 * original order. Old module rows already in the list are replaced, so calling it twice is harmless.
 */
public final class UltraPivots {
 public static final String HOME="FEmusic_ultra_home",DOWNLOADS="FEmusic_ultra_downloads";
 static final String[] ORDER={HOME,"FEmusic_explore","FEmusic_home","FEmusic_library_landing",DOWNLOADS};

 public static List order(List input,Object home,Object downloads){
  if(input==null)return null;
  try{
   ArrayList<Object> rest=new ArrayList<>();Object[] slot=new Object[ORDER.length];
   slot[0]=home instanceof brso?home:null;slot[ORDER.length-1]=downloads instanceof brso?downloads:null;
   for(Object row:input){
    String id=row instanceof brso?((brso)row).e:null;
    if(HOME.equals(id)||DOWNLOADS.equals(id))continue;
    int at=-1;for(int n=1;n<ORDER.length-1;n++)if(ORDER[n].equals(id))at=n;
    if(at>0&&slot[at]==null)slot[at]=row;else rest.add(row);
   }
   ArrayList<Object> out=new ArrayList<>();for(Object o:slot)if(o!=null)out.add(o);out.addAll(rest);
   return out;
  }catch(Throwable e){ultra.downloads.Diagnostics.error("pivot_order",e);return input;}
 }
}
