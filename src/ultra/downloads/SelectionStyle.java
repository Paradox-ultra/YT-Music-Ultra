package ultra.downloads;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared selection controls with visible hierarchy and full touch targets. */
final class SelectionStyle {
 static final int ACCENT=0xfff5f5f5;
 static GradientDrawable panel(Context c){GradientDrawable d=new GradientDrawable();d.setColor(0xff000000);d.setStroke(NativeUi.dp(c,1),0xff303030);d.setCornerRadius(NativeUi.dp(c,18));return d;}
 static View action(Context c,String icon,String glyph,String label,View.OnClickListener click){
  boolean main=label.equals("Add to playlist");
  LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER);
  box.setMinimumHeight(NativeUi.dp(c,48));box.setPadding(NativeUi.dp(c,14),0,NativeUi.dp(c,14),0);
  GradientDrawable bg=new GradientDrawable();bg.setColor(main?ACCENT:0xff000000);bg.setCornerRadius(NativeUi.dp(c,24));box.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),bg,null));
  int fg=main?0xff000000:NativeUi.primary(c);
  ImageView image=new ImageView(c);image.setImageDrawable(NativeUi.drawable(c,icon,glyph,fg));box.addView(image,new LinearLayout.LayoutParams(NativeUi.dp(c,22),NativeUi.dp(c,22)));
  TextView text=new TextView(c);text.setText(label);text.setTextSize(13);text.setTextColor(fg);text.setTypeface(Typeface.create("sans-serif-medium",0));text.setSingleLine(true);
  LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.setMarginStart(NativeUi.dp(c,8));box.addView(text,tp);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,NativeUi.dp(c,48));lp.setMarginEnd(NativeUi.dp(c,8));box.setLayoutParams(lp);
  box.setContentDescription(label);box.setOnClickListener(click);return box;
 }
 static View unmatched(Context c){
  FrameLayout tile=new FrameLayout(c);tile.setTag("unmatched_artwork");tile.setContentDescription("Unmatched songs, automatic playlist");
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff000000,0xff000000});bg.setCornerRadius(NativeUi.dp(c,12));tile.setBackground(bg);
  ImageView note=new ImageView(c);note.setImageDrawable(new NativeUi.Glyph("music",Color.WHITE));FrameLayout.LayoutParams n=new FrameLayout.LayoutParams(NativeUi.dp(c,29),NativeUi.dp(c,29),Gravity.CENTER);tile.addView(note,n);
  TextView plus=new TextView(c);plus.setText("+");plus.setTextColor(0xff000000);plus.setTextSize(15);plus.setGravity(Gravity.CENTER);plus.setTypeface(Typeface.DEFAULT_BOLD);
  GradientDrawable badge=new GradientDrawable();badge.setShape(GradientDrawable.OVAL);badge.setColor(0xffeeeeee);plus.setBackground(badge);FrameLayout.LayoutParams b=new FrameLayout.LayoutParams(NativeUi.dp(c,18),NativeUi.dp(c,18),Gravity.RIGHT|Gravity.BOTTOM);b.setMargins(0,0,NativeUi.dp(c,4),NativeUi.dp(c,4));tile.addView(plus,b);return tile;
 }
}
