package ultra.downloads;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import java.io.File;
import java.util.*;

/**
 * Designed default covers. Downloaded songs, recordings and playlists without real artwork get a
 * purpose-drawn monochrome cover instead of the plain app icon. Covers are drawn, so they stay sharp
 * at any row size and follow the pitch-black surfaces.
 */
final class Covers {
    private Covers(){}

    /** Voice notes and recorder files, recognised by name only: an unmatched song with no tags is still a song. */
    static boolean isRecording(Library.Item i){
        return VoiceRecordings.isRecording(i);
    }
    static String kindOf(Library.Item i){return isRecording(i)?"recording":"song";}
    /** YT Music's automatic "Top Songs" style, matched by playlist name. */
    static boolean isTopSongs(String name){return name!=null&&name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]"," ").matches(".*\\btop\\s+songs?\\b.*");}

    static Drawable placeholder(Context c,String kind){return new Cover(kind);}

    /** Artwork for a playlist row: a 2x2 collage of member song art when any exists, else the designed default. */
    static Drawable playlist(Context c,String name,List<Library.Item> items,int sizePx){
        Bitmap collage=collage(items,sizePx);
        if(collage!=null)return new BitmapDrawable(c.getResources(),collage);
        return new Cover(isTopSongs(name)?"topsongs":"playlist");
    }

    /** Up to four member covers in a 2x2 grid; null when no member has artwork on disk. */
    static Bitmap collage(List<Library.Item> items,int sizePx){
        if(items==null||sizePx<16)return null;
        ArrayList<File> files=new ArrayList<>();
        for(Library.Item i:items){
            if(files.size()>=4)break;
            if(i==null||i.id==null)continue;
            try{Context c=Hooks.context();if(c==null)return null;File f=Art.file(c,i.id);if(f.isFile()&&!files.contains(f))files.add(f);}catch(Throwable ignored){}
        }
        if(files.isEmpty())return null;
        int half=sizePx/2,gap=Math.max(2,sizePx/96);
        Bitmap out=Bitmap.createBitmap(sizePx,sizePx,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);canvas.drawColor(Color.BLACK);
        int n=0;
        for(File f:files){
            Bitmap b=Art.decode(f,half);if(b==null)continue;
            int x=(n%2)*(half+gap),y=(n/2)*(half+gap);
            Rect dst=new Rect(x,y,x+half,y+half);
            Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            int w=b.getWidth(),h=b.getHeight(),side=Math.min(w,h);
            Rect src=new Rect((w-side)/2,(h-side)/2,(w-side)/2+side,(h-side)/2+side);
            canvas.drawBitmap(b,src,dst,paint);
            if(b!=null&&!b.isRecycled())b.recycle();
            n++;
        }
        return n==0?null:out;
    }

    /** The drawn default cover itself. */
    static final class Cover extends Drawable{
        final String kind;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Cover(String kind){this.kind=kind;}
        @Override public int getIntrinsicWidth(){return -1;}
        @Override public int getIntrinsicHeight(){return -1;}
        @Override public void draw(Canvas canvas){
            Rect r=getBounds();if(r.isEmpty())return;
            int w=r.width(),h=r.height();float s=Math.min(w,h);
            // Quiet vertical gradient on the pitch-black family; every kind shares it.
            paint.setShader(new LinearGradient(0,r.top,0,r.bottom,0xff262626,0xff0b0b0b,Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(r,paint);
            paint.setShader(null);
            canvas.save();
            canvas.translate(r.exactCenterX(),r.exactCenterY());
            if(kind.equals("topsongs"))drawTopSongs(canvas,s);
            else{
                String glyph=kind.equals("recording")?"mic":kind.equals("artist")?"person":kind.equals("album")?"album":kind.equals("playlist")?"playlist":"music";
                NativeUi.Glyph g=new NativeUi.Glyph(glyph,0xe6ffffff);
                int half=Math.round(s*(kind.equals("playlist")?0.30f:0.28f));
                g.setBounds(-half,-half,half,half);
                g.draw(canvas);
                if(kind.equals("playlist"))drawBars(canvas,s,0x59ffffff);
            }
            canvas.restore();
        }
        /** Rising bars behind a play triangle: the Top Songs emblem. */
        private void drawTopSongs(Canvas canvas,float s){
            drawBars(canvas,s,0x66ffffff);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xf2ffffff);
            float t=s*0.20f;
            Path play=new Path();play.moveTo(-t*0.55f,-t*0.8f);play.lineTo(t*0.85f,0);play.lineTo(-t*0.55f,t*0.8f);play.close();
            canvas.drawPath(play,paint);
        }
        private void drawBars(Canvas canvas,float s,int color){
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);
            float bw=s*0.075f,gap=s*0.06f,base=s*0.30f;
            float[] heights={0.14f,0.24f,0.34f};
            for(int n=0;n<3;n++){
                float x=-(bw*1.5f+gap)+n*(bw+gap);
                canvas.drawRoundRect(new RectF(x,base-heights[n]*s,x+bw,base),bw/2,bw/2,paint);
            }
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
