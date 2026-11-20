package ultra.downloads;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/**
 * Native YouTube Music look for the Downloads screen.
 *
 * Every resource is looked up BY NAME in the host APK (getIdentifier / LayoutInflater.inflate), never by a hard-coded id,
 * so the helper follows the installed host. When a name is absent (Robolectric, a renamed resource, an inflation failure)
 * a plain framework view or a drawn glyph is used instead. Each element records which path it took in {@link #report},
 * shown under Downloads settings > Diagnostics, so a framework fallback is never presented as native rendering.
 */
public final class NativeUi {
    private NativeUi(){}
    /** Tests only: behave as if no host resource exists. */
    static volatile boolean forceFallback;
    static final Map<String,String> report=Collections.synchronizedMap(new LinkedHashMap<String,String>());
    /** Layouts that already threw while inflating; they are not retried for the life of the process. */
    static final Set<String> broken=Collections.synchronizedSet(new HashSet<String>());
    private static Typeface headerFace;

    static void note(String element,String source){report.put(element,source);}
    static String report(){StringBuilder b=new StringBuilder();synchronized(report){for(Map.Entry<String,String> e:report.entrySet())b.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');}return b.length()==0?"(nothing rendered yet)\n":b.toString();}
    static void reset(){report.clear();broken.clear();headerFace=null;}

    // ------------------------------------------------------------------ resources
    public static int res(Context c,String name,String type){
        if(forceFallback||c==null||name==null)return 0;
        try{return c.getResources().getIdentifier(name,type,c.getPackageName());}catch(Throwable e){return 0;}
    }
    public static int dp(Context c,float v){return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,c.getResources().getDisplayMetrics()));}
    static float sp(Context c,float v){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,v,c.getResources().getDisplayMetrics());}
    static int dimen(Context c,String name,float fallbackDp){int id=res(c,name,"dimen");if(id!=0)try{return c.getResources().getDimensionPixelSize(id);}catch(Throwable ignored){}return dp(c,fallbackDp);}
    /** Text size in px from a native sp dimen, or the fallback sp. */
    static float textPx(Context c,String name,float fallbackSp){int id=res(c,name,"dimen");if(id!=0)try{return c.getResources().getDimension(id);}catch(Throwable ignored){}return sp(c,fallbackSp);}
    public static int color(Context c,String name,int fallback){
        int id=res(c,name,"color");
        if(id!=0)try{return Build.VERSION.SDK_INT>=23?c.getColor(id):c.getResources().getColor(id);}catch(Throwable ignored){}
        return fallback;
    }
    /** Resolves a theme attribute of the host Activity theme to a drawable resource, e.g. selectableItemBackgroundBorderless. */
    static Drawable themeDrawable(Context c,int attr){
        try{TypedValue v=new TypedValue();if(c.getTheme().resolveAttribute(attr,v,true)&&v.resourceId!=0)return c.getDrawable(v.resourceId);}catch(Throwable ignored){}
        return null;
    }

    // ------------------------------------------------------------------ colours used by the screen
    static int background(Context c){return Color.BLACK;}
    static int primary(Context c){return color(c,"ytm_text_color_primary",0xfffffffe);}
    static int secondary(Context c){return color(c,"ytm_text_color_secondary_translucent",0xaaffffff);}
    static int sheet(Context c){return Color.BLACK;}
    static int placeholder(Context c){return Color.BLACK;}

    static Drawable rowBackground(Context c){GradientDrawable shape=new GradientDrawable();shape.setColor(Color.TRANSPARENT);shape.setCornerRadius(dp(c,12));GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(c,12));return new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape,mask);}
    static void enter(View v){if(Build.VERSION.SDK_INT>=26&&!android.animation.ValueAnimator.areAnimatorsEnabled())return;v.setAlpha(0f);v.setTranslationY(dp(v.getContext(),4));v.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
    static Drawable appIcon(Context c){try{return c.getPackageManager().getApplicationIcon(c.getApplicationInfo()).mutate();}catch(Throwable e){return new Glyph("music",Color.WHITE);}}
    static android.app.AlertDialog.Builder dialog(final Context c){return new android.app.AlertDialog.Builder(c){
      @Override public android.app.AlertDialog show(){android.app.AlertDialog d=super.show();polishDialog(c,d);return d;}
    };}
    static void polishDialog(Context c,android.app.Dialog d){Window w=d.getWindow();if(w==null)return;GradientDrawable bg=new GradientDrawable();bg.setColor(Color.BLACK);bg.setCornerRadius(dp(c,24));bg.setStroke(dp(c,1),0xff303030);w.setBackgroundDrawable(bg);View decor=w.getDecorView();decor.setClipToOutline(true);tintText(decor);if(d instanceof android.app.AlertDialog){android.app.AlertDialog a=(android.app.AlertDialog)d;for(int id:new int[]{-1,-2,-3})if(a.getButton(id)!=null)a.getButton(id).setTextColor(Color.WHITE);}}
    static void tintText(View v){if(v instanceof TextView){((TextView)v).setTextColor(Color.WHITE);if(v instanceof EditText)((EditText)v).setHintTextColor(0xffaaaaaa);}if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)tintText(((ViewGroup)v).getChildAt(n));}

    // ------------------------------------------------------------------ icons
    /** Native vector drawable by name; otherwise a drawn {@link Glyph}. tint==null keeps the native theme tint. */
    public static Drawable drawable(Context c,String name,String glyph,Integer tint){
        int id=res(c,name,"drawable");
        if(id!=0)try{Drawable d=c.getDrawable(id);if(d!=null){d=d.mutate();if(tint!=null)d.setTint(tint);note("icon "+glyph,"native drawable/"+name);return d;}}catch(Throwable ignored){}
        note("icon "+glyph,"fallback: drawn glyph (native drawable/"+name+" unavailable)");
        return new Glyph(glyph,tint==null?primary(c):tint);
    }
    static ImageView icon(Context c,String name,String glyph,Integer tint,int sizeDp){
        ImageView v=new ImageView(c);v.setImageDrawable(drawable(c,name,glyph,tint));v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(c,sizeDp),dp(c,sizeDp)));return v;
    }
    /** 48dp touch target with a 24dp native icon and the host theme's borderless ripple. */
    static ImageButton iconButton(Context c,String name,String glyph,String description,View.OnClickListener l){
        ImageButton b=new ImageButton(c);b.setImageDrawable(drawable(c,name,glyph,null));b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable ripple=themeDrawable(c,android.R.attr.selectableItemBackgroundBorderless);b.setBackground(ripple);
        b.setContentDescription(description);b.setOnClickListener(l);b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        b.setMinimumWidth(dp(c,48));b.setMinimumHeight(dp(c,48));return b;
    }

    // ------------------------------------------------------------------ text
    /**
     * A text view in a native text appearance. The view itself is the host's YouTubeAppCompatTextView, taken from an inflated
     * native music_menu_item, when that layout inflates; otherwise a framework TextView.
     */
    static TextView text(Context c,String appearance,float fallbackSp,int fallbackColor,boolean medium){
        TextView t=null;
        int layout=res(c,"music_menu_item","layout"),textId=res(c,"text","id");
        if(layout!=0&&textId!=0&&!broken.contains("music_menu_item")){
            try{View row=LayoutInflater.from(c).inflate(layout,null,false);View v=row.findViewById(textId);
                if(v instanceof TextView){((ViewGroup)v.getParent()).removeView(v);t=(TextView)v;note("text view","native "+v.getClass().getSimpleName()+" from layout/music_menu_item");}
            }catch(Throwable e){broken.add("music_menu_item");note("text view","fallback: framework TextView (music_menu_item threw "+e.getClass().getSimpleName()+")");}
        }
        if(t==null){t=new TextView(c);if(!report.containsKey("text view"))note("text view","fallback: framework TextView (layout/music_menu_item unavailable)");}
        t.setLayoutParams(new ViewGroup.LayoutParams(-2,-2));t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setIncludeFontPadding(true);
        int style=res(c,appearance,"style");
        if(style!=0){
            try{if(Build.VERSION.SDK_INT>=23)t.setTextAppearance(style);else t.setTextAppearance(c,style);note("style "+appearance,"native");}
            catch(Throwable e){style=0;}
        }
        if(style==0){t.setTextSize(TypedValue.COMPLEX_UNIT_SP,fallbackSp);t.setTextColor(fallbackColor);note("style "+appearance,"fallback: "+fallbackSp+"sp framework text");}
        // robotoFont="medium" is applied by YouTube's own text views only; sans-serif-medium is the same Roboto Medium face.
        if(medium)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }

    /**
     * Library-style header face. Order: host font family font/youtube_sans_bold, then the host's bundled
     * font/youtube_sans_bold_font.ttf, then framework sans-serif bold (reported as NOT YouTube Sans).
     * The downloadable font/youtube_sans (Google Play services provider) is deliberately not requested.
     */
    static Typeface headerTypeface(Context c){
        if(headerFace!=null)return headerFace;
        Typeface t=null;String source=null;
        int family=res(c,"youtube_sans_bold","font"),ttf=res(c,"youtube_sans_bold_font","font");
        if(Build.VERSION.SDK_INT>=26){
            if(family!=0)try{t=usable(c.getResources().getFont(family));if(t!=null)source="native font/youtube_sans_bold";}catch(Throwable ignored){}
            if(t==null&&ttf!=0)try{t=usable(c.getResources().getFont(ttf));if(t!=null)source="native font/youtube_sans_bold_font.ttf (host APK file)";}catch(Throwable ignored){}
        }
        if(t==null&&ttf!=0){t=fromRaw(c,ttf);if(t!=null)source="native font/youtube_sans_bold_font.ttf via cache copy";}
        if(t==null){t=Typeface.create("sans-serif",Typeface.BOLD);source="fallback: framework sans-serif bold (not YouTube Sans)";}
        note("header font",source);headerFace=t;return t;
    }
    private static Typeface usable(Typeface t){return t==null||t.equals(Typeface.DEFAULT)||t.equals(Typeface.DEFAULT_BOLD)?null:t;}
    private static Typeface fromRaw(Context c,int id){
        File out=new File(c.getCacheDir(),"ultra_youtube_sans_bold.ttf");
        try{if(!out.isFile()||out.length()==0){File tmp=new File(out.getPath()+".tmp");try(InputStream in=c.getResources().openRawResource(id);FileOutputStream f=new FileOutputStream(tmp)){byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>4*1024*1024)throw new IOException("font too large");f.write(b,0,n);}}if(!tmp.renameTo(out)){tmp.delete();return null;}}
            return usable(Typeface.createFromFile(out));}catch(Throwable e){return null;}
    }
    static TextView header(Context c,String title){
        TextView t=text(c,"TextAppearance.YouTubeMusic.Display2",28,primary(c),false);
        t.setTypeface(headerTypeface(c));t.setText(title);
        float letter=-0.04f;int spacing=res(c,"youtube_sans_font_letter_spacing","dimen");
        if(spacing!=0){try{TypedValue v=new TypedValue();c.getResources().getValue(spacing,v,true);letter=v.getFloat();}catch(Throwable ignored){}}
        t.setLetterSpacing(letter);
        return t;
    }

    // ------------------------------------------------------------------ chips
    /**
     * A compact filter chip. Contents come from the native layout/chip_cloud_chip (a merge layout) inflated into our own
     * container; the background is drawable/chip_cloud_chip_white_background when selected and
     * drawable/chip_cloud_chip_translucent_background otherwise.
     */
    static FrameLayout chip(Context c,String label,boolean selected,boolean closable,View.OnClickListener l){
        FrameLayout chip=new FrameLayout(c);TextView text=null;ImageView close=null;
        int layout=res(c,"chip_cloud_chip","layout");
        if(layout!=0&&!broken.contains("chip_cloud_chip")){
            try{LayoutInflater.from(c).inflate(layout,chip,true);View t=chip.findViewById(res(c,"chip_cloud_chip_text","id"));View x=chip.findViewById(res(c,"chip_cloud_chip_cancel_icon","id"));
                if(t instanceof TextView){text=(TextView)t;if(x instanceof ImageView)close=(ImageView)x;note("chip","native layout/chip_cloud_chip");}else chip.removeAllViews();
            }catch(Throwable e){broken.add("chip_cloud_chip");chip.removeAllViews();note("chip","fallback: framework chip (chip_cloud_chip threw "+e.getClass().getSimpleName()+")");}
        }
        int ink=selected?color(c,"ytm_color_black",0xff000000):primary(c);
        if(text==null){
            if(!report.containsKey("chip"))note("chip","fallback: framework chip (layout/chip_cloud_chip unavailable)");
            LinearLayout box=new LinearLayout(c);box.setGravity(Gravity.CENTER);box.setOrientation(LinearLayout.HORIZONTAL);
            text=new TextView(c);text.setGravity(Gravity.CENTER);text.setMaxLines(1);text.setMinWidth(dp(c,32));box.addView(text,new LinearLayout.LayoutParams(-2,-1));
            close=new ImageView(c);close.setVisibility(View.GONE);box.addView(close,new LinearLayout.LayoutParams(dp(c,18),dp(c,18)));
            chip.addView(box,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));
        }
        text.setText(label);text.setTextColor(ink);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(c,"chip_cloud_chip_text_size",14));
        text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        if(close!=null){
            if(closable){close.setVisibility(View.VISIBLE);close.setImageDrawable(drawable(c,"yt_outline_x_mark_vd_theme_24","close",ink));close.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ViewGroup.LayoutParams p=close.getLayoutParams();if(p!=null){p.width=dp(c,18);p.height=dp(c,18);if(p instanceof ViewGroup.MarginLayoutParams)((ViewGroup.MarginLayoutParams)p).setMarginStart(dp(c,6));close.setLayoutParams(p);}}
            else close.setVisibility(View.GONE);
        }
        chip.setBackground(chipBackground(c,selected));
        int pad=dimen(c,"chip_cloud_chip_text_padding",10)+dp(c,2);chip.setPadding(pad,0,pad,0);
        chip.setMinimumHeight(dp(c,32));chip.setSelected(selected);chip.setClickable(true);chip.setFocusable(true);
        chip.setContentDescription(label+(selected?", selected":""));chip.setTag("chip:"+label);chip.setOnClickListener(l);
        return chip;
    }

    /** Native chip background: white when selected/primary, translucent otherwise. */
    static Drawable chipBackground(Context c,boolean selected){
        String name=selected?"chip_cloud_chip_white_background":"chip_cloud_chip_translucent_background";int id=res(c,name,"drawable");
        if(id!=0)try{Drawable bg=c.getDrawable(id);if(bg!=null){note(selected?"chip selected background":"chip background","native drawable/"+name);return bg.mutate();}}catch(Throwable ignored){}
        GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(c,1000));g.setColor(selected?0xfffffffe:0x1affffff);if(!selected)g.setStroke(dp(c,1),0x1affffff);
        note(selected?"chip selected background":"chip background","fallback: framework pill shape using the native colour values");return g;
    }

    // ------------------------------------------------------------------ menu sheet
    /** A native music_menu_item row (icon + YouTubeAppCompatTextView) or an equivalent framework row. */
    static View menuItem(Context c,String iconName,String glyph,String label){
        int layout=res(c,"music_menu_item","layout");
        if(layout!=0&&!broken.contains("music_menu_item_row")){
            try{View row=LayoutInflater.from(c).inflate(layout,null,false);View t=row.findViewById(res(c,"text","id")),i=row.findViewById(res(c,"icon","id")),spin=row.findViewById(res(c,"loading_spinner","id"));
                if(t instanceof TextView&&i instanceof ImageView){((TextView)t).setText(label);((ImageView)i).setImageDrawable(drawable(c,iconName,glyph,null));if(spin!=null)spin.setVisibility(View.GONE);row.setContentDescription(label);note("menu row","native layout/music_menu_item");return row;}
            }catch(Throwable e){broken.add("music_menu_item_row");note("menu row","fallback: framework row (music_menu_item threw "+e.getClass().getSimpleName()+")");}
        }
        if(!report.containsKey("menu row"))note("menu row","fallback: framework row (layout/music_menu_item unavailable)");
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dimen(c,"menu_item_min_height",48));
        int h=dimen(c,"menu_item_padding_horizontal",16),v=dimen(c,"menu_item_padding_vertical",8);row.setPadding(h,v,h,v);
        Drawable ripple=rowBackground(c);if(ripple!=null)row.setBackground(ripple);
        ImageView icon=icon(c,iconName,glyph,null,24);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(c,24),dp(c,24));ip.setMarginEnd(dimen(c,"menu_item_leading_accessory_end_margin",24));row.addView(icon,ip);
        TextView t=new TextView(c);t.setText(label);t.setTextColor(primary(c));t.setTextSize(16);row.addView(t,new LinearLayout.LayoutParams(-2,-2));
        row.setContentDescription(label);return row;
    }

    // ------------------------------------------------------------------ fallback glyphs
    /** Drawn stand-ins for native vector icons, used only when the host drawable cannot be resolved. */
    static final class Glyph extends Drawable{
        final String kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int color;
        Glyph(String kind,int color){this.kind=kind;this.color=color;}
        @Override public int getIntrinsicWidth(){return -1;}
        @Override public int getIntrinsicHeight(){return -1;}
        @Override public void draw(Canvas c){
            Rect r=getBounds();if(r.isEmpty())return;float s=Math.min(r.width(),r.height())/24f;
            c.save();c.translate(r.exactCenterX()-12*s,r.exactCenterY()-12*s);c.scale(s,s);
            p.setColor(color);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            switch(kind){
                case "download":c.drawLine(12,4,12,15,p);c.drawLine(7,10,12,15,p);c.drawLine(17,10,12,15,p);c.drawLine(5,20,19,20,p);break;
                case "downloaded":p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,10,p);p.setColor(0xff000000);p.setStyle(Paint.Style.STROKE);path.moveTo(7,12.5f);path.lineTo(10.5f,16);path.lineTo(17,9);c.drawPath(path,p);break;
                case "more":p.setStyle(Paint.Style.FILL);c.drawCircle(12,5,2,p);c.drawCircle(12,12,2,p);c.drawCircle(12,19,2,p);break;
                case "search":c.drawCircle(10,10,6,p);c.drawLine(14.5f,14.5f,20,20,p);break;
                case "settings":c.drawCircle(12,12,3,p);for(int a=0;a<8;a++){double t=a*Math.PI/4;c.drawLine(12+(float)Math.cos(t)*6,12+(float)Math.sin(t)*6,12+(float)Math.cos(t)*9,12+(float)Math.sin(t)*9,p);}c.drawCircle(12,12,6,p);break;
                case "play":p.setStyle(Paint.Style.FILL);path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();c.drawPath(path,p);break;
                case "pause":p.setStyle(Paint.Style.FILL);c.drawRect(6,5,10,19,p);c.drawRect(14,5,18,19,p);break;
                case "shuffle":c.drawLine(4,17,8,17,p);c.drawLine(8,17,15,7,p);c.drawLine(15,7,20,7,p);c.drawLine(4,7,8,7,p);c.drawLine(8,7,10.5f,10.5f,p);c.drawLine(12.5f,13.5f,15,17,p);c.drawLine(15,17,20,17,p);c.drawLine(17,4,20,7,p);c.drawLine(17,10,20,7,p);c.drawLine(17,14,20,17,p);c.drawLine(17,20,20,17,p);break;
                case "alert":c.drawCircle(12,12,9,p);c.drawLine(12,7,12,13,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,16.5f,1.2f,p);break;
                case "close":c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
                case "sort":c.drawLine(4,7,20,7,p);c.drawLine(4,12,15,12,p);c.drawLine(4,17,10,17,p);break;
                case "person":c.drawCircle(12,8,4,p);path.addArc(new RectF(4,14,20,30),180,180);c.drawPath(path,p);break;
                case "album":c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,2,p);break;
                case "mic":c.drawRoundRect(new RectF(9,3,15,13),3,3,p);path.addArc(new RectF(6,9,18,17),0,180);c.drawPath(path,p);c.drawLine(12,17,12,20,p);c.drawLine(8.5f,20,15.5f,20,p);break;
                case "music":c.drawLine(10,17,10,5,p);c.drawLine(10,5,18,4,p);c.drawLine(18,4,18,15,p);p.setStyle(Paint.Style.FILL);c.drawCircle(7.5f,17,2.5f,p);c.drawCircle(15.5f,15,2.5f,p);break;
                case "playlist":c.drawLine(3,6,14,6,p);c.drawLine(3,11,14,11,p);c.drawLine(3,16,10,16,p);c.drawLine(18,12,18,22,p);c.drawLine(13,17,23,17,p);break;
                case "share":path.moveTo(4,18);path.quadTo(5,9,15,9);path.lineTo(15,4);path.lineTo(22,11);path.lineTo(15,18);path.lineTo(15,13);path.quadTo(8,13,4,18);c.drawPath(path,p);break;
                case "trash":c.drawRect(7,8,17,20,p);c.drawLine(5,6,19,6,p);c.drawLine(10,4,14,4,p);break;
                case "hide":c.drawOval(new RectF(3,7,21,17),p);c.drawCircle(12,12,2.5f,p);c.drawLine(4,4,20,20,p);break;
                default:c.drawCircle(12,12,8,p);
            }
            c.restore();
        }
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public void setTint(int tint){color=tint;invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
