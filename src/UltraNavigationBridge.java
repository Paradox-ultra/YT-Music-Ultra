import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.google.android.material.tabs.TabLayout;

/** Use the same Library pivot command as tapping Library, not an Activity finish. */
public final class UltraNavigationBridge {
 /** DL34: whether the host's pivot list currently holds a pivot with this browse id (e.g. FEmusic_ultra_home). */
 public static boolean hasPivot(Object value,String id){
  if(!(value instanceof lac)||id==null)return false;
  lac controller=(lac)value;if(controller.i==null)return false;
  for(int n=0;n<controller.i.size();n++){Object row=controller.i.get(n);if(row instanceof brso&&id.equals(((brso)row).e))return true;}
  return false;
 }
 /**
  * DL49: select the FEmusic_ultra_home tab through the TabLayout, like a tap (the host listener then calls fromTab).
  * Returns false when value is not the bar controller, there is no Home pivot, or Home is already the selected tab.
  */
 public static boolean home(Object value){
  if(!(value instanceof lac))return false;lac controller=(lac)value;if(controller.i==null||controller.j==null)return false;
  for(int n=0;n<controller.i.size();n++){Object row=controller.i.get(n);
   if(row instanceof brso&&"FEmusic_ultra_home".equals(((brso)row).e)){bbxl tab=controller.j.c(n);if(tab==null)return false;if(tab==selected(controller.j))return false;tab.a();return true;}}
  return false;
 }
 static bbxl selected(TabLayout bar){
  for(Field f:TabLayout.class.getDeclaredFields()){if(f.getType()!=bbxl.class)continue;try{f.setAccessible(true);return (bbxl)f.get(bar);}catch(Throwable ignored){}}
  return null;
 }
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
