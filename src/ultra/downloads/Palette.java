package ultra.downloads;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;

/**
 * DL34 "Soft Red x Warm White x Charcoal" design tokens, in a dark and a light set.
 *
 * <p>The set follows the host app's own theme: the live Activity theme's {@code android:colorBackground} is resolved and
 * its luminance decides dark or light, so module screens match whatever theme the stock app is showing. When the theme
 * attribute cannot be resolved the system night mode is used. Values are taken verbatim from DL34-DESIGN-SPEC.md.
 */
public final class Palette {
    /** Muted coral red. The only accent; never a brighter red. */
    public static final int CORAL=0xffe5484d;
    /** Tests only: force dark (TRUE) or light (FALSE); null follows the host theme. */
    static volatile Boolean force;

    public final boolean dark;
    public final int page,card,elevated,accent,soft,chipOn,chipOnInk,chipOff,chipOffInk,heroStart,heroEnd,mini,text,muted,progress,fab,hairline,ripple,error;

    private Palette(boolean dark){
        this.dark=dark;accent=CORAL;progress=CORAL;
        if(dark){
            page=0xff121010;card=0xff1c1818;elevated=0xff100d0d;soft=0xffed7978;
            chipOn=blend(CORAL,page,0.30f);chipOnInk=0xfff6f6f6;chipOff=0xff191718;chipOffInk=0xffa9a4a4;
            heroStart=0xff441f20;heroEnd=0xff252020;mini=0xff2a1f20;text=0xfff6f6f6;muted=0xffa9a4a4;fab=CORAL;
            hairline=0x14ffffff;ripple=0x22ffffff;error=0xffed7978;
        }else{
            page=0xfffaf6f7;card=0xffffffff;elevated=0xffffffff;soft=0xfff0575c;
            chipOn=blend(CORAL,page,0.25f);chipOnInk=0xff7a2426;chipOff=0xffffffff;chipOffInk=0xff6b6262;
            heroStart=0xfff7c0c0;heroEnd=0xfffbebeb;mini=0xfffcf8f9;text=0xff0a0808;muted=0xff6b6262;fab=0xffe34b51;
            hairline=0x14000000;ripple=0x1f000000;error=0xffc4383d;
        }
    }
    static final Palette DARK=new Palette(true),LIGHT=new Palette(false);

    public static Palette of(Context c){return dark(c)?DARK:LIGHT;}

    /** True when the host theme is dark. Order: test override, theme colorBackground luminance, system night mode, dark. */
    public static boolean dark(Context c){
        Boolean f=force;if(f!=null)return f;
        if(c==null)return true;
        try{TypedValue v=new TypedValue();
            if(c.getTheme().resolveAttribute(android.R.attr.colorBackground,v,true)&&v.type>=TypedValue.TYPE_FIRST_COLOR_INT&&v.type<=TypedValue.TYPE_LAST_COLOR_INT)return luminance(v.data)<0.5f;
        }catch(Throwable ignored){}
        try{int night=c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;if(night==Configuration.UI_MODE_NIGHT_NO)return false;}catch(Throwable ignored){}
        return true;
    }
    static float luminance(int color){return (0.2126f*Color.red(color)+0.7152f*Color.green(color)+0.0722f*Color.blue(color))/255f;}
    /** {@code top} at {@code alpha} composited over an opaque {@code bottom}: the "coral @ 30% over bg" chip fill. */
    static int blend(int top,int bottom,float alpha){
        int r=Math.round(Color.red(top)*alpha+Color.red(bottom)*(1-alpha)),g=Math.round(Color.green(top)*alpha+Color.green(bottom)*(1-alpha)),b=Math.round(Color.blue(top)*alpha+Color.blue(bottom)*(1-alpha));
        return Color.rgb(r,g,b);
    }
}
