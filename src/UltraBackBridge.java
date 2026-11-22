import android.app.Activity;
import java.util.WeakHashMap;

/** Register with the host's actual AndroidX dispatcher, including gesture Back. */
public final class UltraBackBridge {
 private static final WeakHashMap<Activity,yi> callbacks=new WeakHashMap<>();
 public static void install(final Activity activity){
  if(!(activity instanceof yo)||!(activity instanceof bps))return;
  remove(activity);
  yi callback=new yi(true){public void b(){
   if(!ultra.downloads.NativeIntegration.back()){
    f(false);
    try{((yo)activity).getOnBackPressedDispatcher().c();}
    finally{f(ultra.downloads.NativeIntegration.downloadsVisible());}
   }
  }};
  ((yo)activity).getOnBackPressedDispatcher().b((bps)activity,callback);
  callbacks.put(activity,callback);
 }
 public static void remove(Activity activity){yi old=callbacks.remove(activity);if(old!=null)old.e();}
}
