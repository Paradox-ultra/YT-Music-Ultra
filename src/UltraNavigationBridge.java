import java.lang.reflect.Method;

/** Use the same Library pivot command as tapping Library, not an Activity finish. */
public final class UltraNavigationBridge {
 public static boolean library(Object value)throws Exception{
  if(!(value instanceof lac))return false;
  lac controller=(lac)value;
  if(controller.j==null||controller.i==null)return false;
  for(int n=0;n<controller.i.size();n++){
   Object row=controller.i.get(n);
   if(row instanceof brso&&"FEmusic_library_landing".equals(((brso)row).e)){
    bbxl tab=controller.j.c(n);if(tab==null)return false;
    Method select=lac.class.getDeclaredMethod("t",bbxl.class);select.setAccessible(true);select.invoke(controller,tab);
    controller.j.ultraRestoreContentTab(tab);return true;
   }
  }
  return false;
 }
}
