package paradox.dl;
import java.io.*; import java.net.*; import java.nio.ByteBuffer; import java.nio.channels.FileChannel;
import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.*;
/** Multi-connection range downloader: each stream is split into fixed chunks fetched in parallel
 *  (googlevideo &range=a-b, kept under YouTube's ~10 MB per-request burst limit), written at their offsets
 *  into a pre-allocated file. Connections are kept alive between chunks, finished chunks are recorded in a
 *  small ".map" file next to each part so a paused/failed download resumes where it stopped, and an expired
 *  URL is refreshed once for all threads (same itag). */
public final class Engine {
  public interface UrlSource { String url(int part) throws Exception; String refresh(int part) throws Exception; default String userAgent(int part) throws Exception {return null;} }
  public static int CONNECTIONS=16; public static final int CHUNK=4*1024*1024; static final int RETRIES=8;
  static final int MAGIC=0x50444c32; static final int HDR=20; // magic, length(8), chunk(4), count(4)
  static { try { if (System.getProperty("http.maxConnections")==null) System.setProperty("http.maxConnections","32"); System.setProperty("http.keepAlive","true"); } catch (Throwable ignore) {} }
  public final AtomicLong done=new AtomicLong(); public volatile long total, resumed; public volatile boolean cancelled; public volatile String error;
  static long probeLength(String url) throws IOException {
    HttpURLConnection c=open(url, 0, 0); try { int code=c.getResponseCode(); if (code>=400) throw new IOException("HTTP "+code);
      String cr=c.getHeaderField("Content-Range"); if (cr!=null && cr.contains("/")) { String t=cr.substring(cr.lastIndexOf('/')+1).trim(); if (!t.equals("*")) return Long.parseLong(t); }
      String cl=c.getHeaderField("Content-Length"); return cl==null? -1: Long.parseLong(cl);
    } finally { c.disconnect(); } }
  static boolean google(String u){try{java.net.URI x=new java.net.URI(u);return x.getHost()!=null&&(x.getHost().equals("googlevideo.com")||x.getHost().endsWith(".googlevideo.com"))&&x.getPath().equals("/videoplayback");}catch(Exception e){return false;}}
  static HttpURLConnection open(String url,long a,long b)throws IOException{return open(url,a,b,null);}
  static HttpURLConnection open(String url, long a, long b,String agent) throws IOException {
    String u=url;
    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
    c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setUseCaches(false); c.setRequestProperty("User-Agent", agent==null||agent.isEmpty()?ultra.downloads.NativeStreams.agent(url):agent);
    c.setRequestProperty("Accept-Encoding", "identity"); c.setRequestProperty("Connection", "keep-alive");
    c.setRequestProperty("Range","bytes="+a+"-"+b);
    return c; }
  static void validateReply(HttpURLConnection c,long start,long end,long total,boolean queryRange)throws IOException{
    int code=c.getResponseCode();String type=c.getContentType();if(type!=null&&(type.startsWith("text/")||type.contains("json")||type.contains("xml")))throw new IOException("Non-audio range response");
    if(code==206){String expected="bytes "+start+"-"+end+"/"+total;if(!expected.equals(c.getHeaderField("Content-Range")))throw new IOException("Audio range or size mismatch");}
    else if(code!=200||!queryRange)throw new IOException("Audio server ignored range");
    long n=c.getContentLengthLong();if(n>=0&&n!=end-start+1)throw new IOException("Audio response length mismatch");if(code==200&&n<0)throw new IOException("Unverified audio range length");
  }
  /** Retains the exact failed local path through Android's nested ErrnoException. */
  public static final class FileFailure extends IOException {
    public FileFailure(String action,File path,Throwable cause){super(action+" "+path.getAbsolutePath()+": "+(cause==null?"unavailable":cause.getClass().getSimpleName()+": "+cause.getMessage()),cause);}
  }
  public static void prepare(File file)throws IOException{
    File parent=file.getAbsoluteFile().getParentFile();
    if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs()&&!parent.isDirectory())throw new FileFailure("Create staging directory",parent,null);
    if(file.exists()&&!file.isFile())throw new FileFailure("Open audio file",file,null);
  }
  static void closeParts(FileChannel[] ch,RandomAccessFile[] raf,RandomAccessFile[] map){for(int i=0;i<ch.length;i++){try{if(ch[i]!=null)ch[i].close();}catch(Exception ignored){}try{if(raf[i]!=null)raf[i].close();}catch(Exception ignored){}try{if(map[i]!=null)map[i].close();}catch(Exception ignored){}}}
  public static File mapFile(File part){ return new File(part.getPath()+".map"); }
  /** Chunks already on disk for this part, or null when there is nothing valid to resume. */
  static boolean[] loadMap(File part, long len, int count){
    File m=mapFile(part); if (!part.exists() || part.length()!=len || !m.exists() || m.length()!=HDR+count) return null;
    try { DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(m))); try {
        if (in.readInt()!=MAGIC || in.readLong()!=len || in.readInt()!=CHUNK || in.readInt()!=count) return null;
        boolean[] have=new boolean[count]; for (int k=0;k<count;k++) have[k]= in.read()==1; return have; } finally { in.close(); }
    } catch (IOException e) { return null; } }
  /** Download all parts (index i -> files[i]) concurrently sharing one connection pool. */
  public void run(final UrlSource src, final long[] lens, final File[] files) throws Exception {
    if(src==null||lens==null||files==null||files.length==0||lens.length!=files.length)throw new IllegalArgumentException("Invalid transfer parts");
    for(File file:files){if(file==null)throw new IllegalArgumentException("Missing transfer file");prepare(file);}
    final int n=files.length; final String[] urls=new String[n]; final long[] L=new long[n]; final int[] ver=new int[n];
    for (int i=0;i<n;i++){ urls[i]=src.url(i); L[i]= lens[i]>0? lens[i]: probeLength(urls[i]); if (L[i]<=0) throw new IOException("unknown size"); }
    long t=0; for (long x: L) t+=x; total=t;
    final FileChannel[] ch=new FileChannel[n]; final RandomAccessFile[] raf=new RandomAccessFile[n]; final RandomAccessFile[] map=new RandomAccessFile[n];
    final List<long[]> jobs=new ArrayList<>(); // {part, start, end, chunkIndex}
    File opening=null;
    try{
    for (int i=0;i<n;i++){ int count=(int)((L[i]+CHUNK-1)/CHUNK);
      boolean[] have=loadMap(files[i], L[i], count);
      if (have==null){opening=mapFile(files[i]);if(opening.exists()&&!opening.delete())throw new IOException("Cannot reset range map");opening=files[i];if(opening.exists()&&!opening.delete())throw new IOException("Cannot reset partial audio");have=new boolean[count];
        opening=mapFile(files[i]);try(DataOutputStream o=new DataOutputStream(new FileOutputStream(opening))){o.writeInt(MAGIC);o.writeLong(L[i]);o.writeInt(CHUNK);o.writeInt(count);o.write(new byte[count]);}}
      opening=files[i];raf[i]=new RandomAccessFile(opening,"rw");if(raf[i].length()!=L[i])raf[i].setLength(L[i]);ch[i]=raf[i].getChannel();opening=mapFile(files[i]);map[i]=new RandomAccessFile(opening,"rw");
      for(int k=0;k<count;k++){long a=(long)k*CHUNK,b=Math.min(L[i],a+CHUNK)-1;if(have[k])done.addAndGet(b-a+1);else jobs.add(new long[]{i,a,b,k});}}
    }catch(IOException e){closeParts(ch,raf,map);throw new FileFailure("Prepare transfer file",opening,e);}
    resumed=done.get();
    final AtomicInteger next=new AtomicInteger(); final Object refreshLock=new Object();
    int threads=Math.max(1, Math.min(CONNECTIONS, jobs.size())); ExecutorService ex=Executors.newFixedThreadPool(threads);
    List<Future<?>> fs=new ArrayList<>();
    for (int k=0;k<threads;k++) fs.add(ex.submit(new Callable<Void>(){ public Void call() throws Exception {
      byte[] buf=new byte[128*1024];
      while (!cancelled) { int j=next.getAndIncrement(); if (j>=jobs.size()) return null; long[] job=jobs.get(j); int part=(int)job[0];
        long pos=job[1]; int attempt=0;
        while (pos<=job[2]) { if (cancelled) return null;
          String u,agent; int v; synchronized(refreshLock){ u=urls[part];agent=src.userAgent(part); v=ver[part]; }
          HttpURLConnection c=null; boolean ok=false; try { c=open(u, pos, job[2],agent); int code=c.getResponseCode();
            if (code==403 || code==410) { synchronized(refreshLock){ if (ver[part]==v) { urls[part]=src.refresh(part); ver[part]++; } } throw new IOException("HTTP "+code+" (refreshed)"); }
            if (code>=400) throw new IOException("HTTP "+code);
            validateReply(c,pos,job[2],L[part],false);
            InputStream in=c.getInputStream(); int r;
            while (pos<=job[2] && (r=in.read(buf))>0) { int take=(int)Math.min(r, job[2]-pos+1); ByteBuffer bb=ByteBuffer.wrap(buf,0,take); long p=pos; try{while(bb.hasRemaining())p+=ch[part].write(bb,p);}catch(IOException e){throw new FileFailure("Write audio file",files[part],e);} pos+=take; done.addAndGet(take); if (cancelled) break; }
            if (pos>job[2]) { in.close(); ok=true; } // whole range read: the connection goes back to the pool for the next chunk
            else if (!cancelled) throw new IOException("short read");
          } catch (IOException e) { if(e instanceof FileFailure){cancelled=true;throw e;}if (++attempt>RETRIES) { error=e.getMessage(); cancelled=true; throw e; }
            String m=String.valueOf(e.getMessage()); if (!m.contains("refreshed") && attempt>1) Thread.sleep(Math.min(3000L, 400L*attempt)); }
          finally { if (c!=null && !ok) c.disconnect(); } }
        if (pos>job[2]) synchronized(map[part]) { try{ch[part].force(false);map[part].seek(HDR+job[3]);map[part].write(1);map[part].getFD().sync();}catch(IOException e){throw new FileFailure("Commit range map",mapFile(files[part]),e);} }
      } return null; } }));
    Exception first=null;
    try{for(Future<?> f:fs){try{f.get();}catch(ExecutionException e){if(first==null)first=e.getCause() instanceof Exception?(Exception)e.getCause():new IOException("Transfer failed",e.getCause());cancelled=true;}}}
    finally{ex.shutdownNow();try{if(!ex.awaitTermination(25,TimeUnit.SECONDS))throw new IOException("Audio workers did not stop");}finally{closeParts(ch,raf,map);}}
    if(first!=null)throw first;if(cancelled)throw new InterruptedException("cancelled");
    for (int i=0;i<n;i++) mapFile(files[i]).delete();
  }
}
