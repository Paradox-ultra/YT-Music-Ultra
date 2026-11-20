package ultra.downloads;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class DownloadsService extends Service {
    private static final String CHANNEL="ultra_audio_downloads";
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private static final Map<String,Library.Item> jobs=new ConcurrentHashMap<>();
    static boolean running(String id){return jobs.containsKey(id);}
    static boolean runningAny(){return !jobs.isEmpty();}
    private final Handler main=new Handler(Looper.getMainLooper());
    private int lastStart;private PowerManager.WakeLock wake;
    static Intent intent(Context c){return new Intent(c,DownloadsService.class);}
    public static void enqueue(Context c,String id,String title)throws Exception{
        Library.Item i=Library.add(c,id,title);
        if(i.imported&&!Storage.exists(c,i.uri))throw new IOException("Existing audio is no longer accessible. Scan device audio again.");
        if(running(id)){if(i.paused)throw new IOException("The previous transfer is still stopping. Retry Resume in a moment.");return;}
        synchronized(Library.LOCK){
            if(i.state.equals("done")&&Storage.exists(c,i.uri)){i.hidden=false;Library.save(c);return;}
            if(Arrays.asList("resolving","downloading","converting","saving").contains(i.state))return;
            i.paused=false;i.state="queued";i.error="";Library.save(c);
        }
        try {Intent in=intent(c).putExtra("id",id);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(in);else c.startService(in);}
        catch(Exception e){i.state="failed";i.error="Android did not allow a download service. Open Downloads and retry.";Library.save(c);throw e;}
    }
    public static void pause(Context c,Library.Item i)throws Exception{synchronized(Library.LOCK){if(i.state.equals("done"))return;i.paused=true;i.state="paused";i.error="Paused. Tap Resume.";Library.save(c);}}
    static boolean active(Context c)throws Exception{for(Library.Item i:Library.all(c))if(Arrays.asList("queued","resolving","downloading","converting","saving").contains(i.state))return true;return false;}
    private Notification note(String text,int percent,Library.Item item){
        PendingIntent open=PendingIntent.getActivity(this,0,NativeIntegration.notificationIntent(this),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("YT Music Ultra downloads").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);
        if(percent>=0)b.setProgress(100,percent,false);else b.setProgress(0,0,true);
        if(item!=null){PendingIntent stop=PendingIntent.getService(this,item.id.hashCode(),intent(this).putExtra("pause",item.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.addAction(new Notification.Action.Builder(null,"Pause",stop).build());}
        return b.build();
    }
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Audio downloads",NotificationManager.IMPORTANCE_LOW));}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent in,int flags,int start){
        lastStart=start;
        try{
            if(in!=null&&in.hasExtra("pause")){Library.Item i=Library.get(this,in.getStringExtra("pause"));if(i!=null)pause(this,i);if(jobs.isEmpty())finishIfIdle();return START_NOT_STICKY;}
            Notification first=note("Preparing audio downloads",-1,null);if(Build.VERSION.SDK_INT>=29)startForeground(5201,first,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(5201,first);
            if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ultra:audioDownloads");wake.setReferenceCounted(false);}if(!wake.isHeld())wake.acquire(3L*60*60*1000);
            for(final Library.Item i:Library.all(this))if(i.state.equals("queued")&&!i.paused&&jobs.putIfAbsent(i.id,i)==null){workers.submit(new Runnable(){public void run(){work(i);}});}
            finishIfIdle();
        }catch(Exception e){Hooks.message("Downloads: "+e.getMessage());try{stopForeground(true);stopSelfResult(lastStart);}catch(Exception ignored){}}
        return START_NOT_STICKY;
    }
    private void work(final Library.Item i){
        final long[] last={0};
        Transfer.Observer progress=new Transfer.Observer(){
            public boolean stopped(){return i.paused||Library.wiping;}
            public void progress(long done,long total){
                i.bytes=done;i.total=total;long now=SystemClock.elapsedRealtime();
                if(now-last[0]>700){last[0]=now;try{getSystemService(NotificationManager.class).notify(5201,note(i.title+" · "+(done*100/Math.max(1,total))+"%",(int)(done*100/Math.max(1,total)),i));Library.save(DownloadsService.this);}catch(Exception ignored){}}
            }
        };
        File encoded=null;String stage="prepare";
        try{
            Transfer.check(progress);
            if(!i.uri.isEmpty()){Storage.delete(this,i);i.uri="";Library.save(this);}
            i.state="resolving";Library.save(this);Catalog.metadata(i);Art.fetch(this,i);Transfer.check(progress);
            stage="resolve";i.quality="best";Hooks.Audio audio=Hooks.resolve(i.id,false,i.rendition.isEmpty()?null:i.rendition,"best");
            if(i.total>0&&!i.rendition.isEmpty()&&audio.size>0&&i.total!=audio.size)throw new IOException("Audio file changed. Discard the partial download and retry.");
            i.rendition=audio.key;i.mime=audio.mime;i.bitrate=audio.bitrate;
            stage="probe";long length=audio.size>0?audio.size:Transfer.probe(audio.url,progress,audio.agent);
            if(length<16)throw new IOException("Audio stream has no usable file size");
            File temp=Library.temp(this,i);stage="prepare-staging";paradox.dl.Engine.prepare(temp);long free=getNoBackupFilesDir().getUsableSpace();if(free>0&&free<length+8*1024*1024)throw new IOException("Not enough free space to stage this audio file");
            i.total=length;i.state="downloading";Library.save(this);
            final Hooks.Audio[] live={audio};
            stage="transfer";final paradox.dl.Engine engine=new paradox.dl.Engine();
            ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();monitor.scheduleAtFixedRate(new Runnable(){public void run(){engine.cancelled=engine.cancelled||progress.stopped();progress.progress(engine.done.get(),length);}},0,500,TimeUnit.MILLISECONDS);
            try{engine.run(new paradox.dl.Engine.UrlSource(){public String url(int part){return live[0].url;}public String userAgent(int part){return live[0].agent;}public String refresh(int part)throws Exception{Transfer.check(progress);Hooks.Audio fresh=Hooks.resolve(i.id,true,i.rendition,"best");if(fresh.size>0&&fresh.size!=length)throw new IOException("Audio size changed during transfer");live[0]=fresh;return fresh.url;}},new long[]{length},new File[]{temp});}
            finally{engine.cancelled=true;monitor.shutdownNow();}
            Transfer.check(progress);i.state="saving";Library.save(this);
            stage="verify";String ext=Storage.verify(temp);i.mime=Storage.mime(ext);android.media.MediaMetadataRetriever metadata=new android.media.MediaMetadataRetriever();try{metadata.setDataSource(temp.getAbsolutePath());String duration=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration!=null)i.duration=Long.parseLong(duration);}catch(Exception ignored){}finally{metadata.release();}
            stage="encode-mp3";i.state="converting";i.bytes=0;i.total=100;Library.save(this);final long encodeStarted=SystemClock.elapsedRealtime();encoded=new File(temp.getPath()+".mp3");
            byte[] cover=null;File artwork=Art.file(this,i.id);if(artwork.isFile()&&artwork.length()<2*1024*1024)cover=java.nio.file.Files.readAllBytes(artwork.toPath());
            paradox.dl.ToMp3.convert(temp.getAbsolutePath(),encoded,320,paradox.dl.Id3.tag(i.title,i.artist,i.album,cover),i.duration*1000,new paradox.dl.ToMp3.Progress(){public boolean cancelled(){return progress.stopped();}public void at(int percent){i.bytes=percent;i.total=100;if(percent==100||SystemClock.elapsedRealtime()-last[0]>700){last[0]=SystemClock.elapsedRealtime();try{Library.save(DownloadsService.this);getSystemService(NotificationManager.class).notify(5201,note(i.title+" · Encoding MP3 "+percent+"%",percent,i));}catch(Exception ignored){}}if(percent==100)Diagnostics.event("mp3_encode","finished-ms-"+(SystemClock.elapsedRealtime()-encodeStarted));}});
            Transfer.check(progress);if(!"mp3".equals(Storage.verify(encoded)))throw new IOException("MP3 output validation failed");
            stage="save";i.state="saving";i.mime="audio/mpeg";i.sourceBitrate=i.bitrate;i.bitrate=320000;i.total=encoded.length();Library.save(this);i.uri=Storage.publish(this,i,encoded,"mp3",progress);
            synchronized(Library.LOCK){i.state="done";i.paused=false;i.error="";i.bytes=i.total;Library.save(this);}
            Library.clearPartial(this,i);
            PendingIntent open=PendingIntent.getActivity(this,i.id.hashCode(),NativeIntegration.notificationIntent(this),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            getSystemService(NotificationManager.class).notify(i.id.hashCode(),new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Audio saved").setContentText(i.title).setAutoCancel(true).setContentIntent(open).build());
        }catch(Exception e){
            Diagnostics.event("download_stage",stage+":"+ResolveErrors.root(e).getClass().getSimpleName().toLowerCase(java.util.Locale.US));
            i.state=i.paused||Library.wiping?"paused":"failed";i.error=i.paused?"Paused. Tap Resume.":"["+stage+"] "+ResolveErrors.describe(e);
            try{Library.save(this);if(i.state.equals("failed")){PendingIntent details=PendingIntent.getActivity(this,i.id.hashCode(),NativeIntegration.notificationIntent(this),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);getSystemService(NotificationManager.class).notify(i.id.hashCode(),new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Audio download failed").setContentText(i.title+": "+i.error).setContentIntent(details).setAutoCancel(true).build());}}catch(Exception save){Hooks.message("Download state could not be saved");}
        }finally{if(encoded!=null)encoded.delete();jobs.remove(i.id);main.post(new Runnable(){public void run(){finishIfIdle();}});}
    }
    private void finishIfIdle(){if(!jobs.isEmpty())return;if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);stopSelfResult(lastStart);}
    public void onTimeout(int startId,int type){for(Library.Item i:jobs.values()){i.paused=true;i.state="paused";i.error="Android paused long-running downloads. Tap Resume.";}try{Library.save(this);}catch(Exception ignored){}stopForeground(true);stopSelfResult(lastStart);}
    @Override public void onDestroy(){for(Library.Item i:jobs.values())i.paused=true;workers.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
