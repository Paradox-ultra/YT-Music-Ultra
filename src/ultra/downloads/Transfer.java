package ultra.downloads;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Independent bounded HTTP transfer implementation. No donor code. */
public final class Transfer {
    public interface Observer { boolean stopped(); void progress(long bytes, long total); }
    public interface Source { String url() throws Exception; default String userAgent(){return "Mozilla/5.0";} }
    public static final int BLOCK = 4*1024*1024;
    private static final Pattern RANGE = Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)", Pattern.CASE_INSENSITIVE);
    public static final class Protocol extends IOException { public Protocol(String s) { super(s); } }
    public static final class HttpError extends IOException { public final int status; HttpError(int s) { super("Audio server returned HTTP " + s); status=s; } }
    static void check(Observer o) throws InterruptedIOException { if (Thread.currentThread().isInterrupted() || o.stopped()) throw new InterruptedIOException("Download paused"); }
    static HttpURLConnection connection(String url, long start, long end) throws IOException {
        URL u=new URL(url);
        if (!u.getProtocol().equals("https") && !u.getProtocol().equals("http")) throw new Protocol("Unsupported audio URL");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(12000); c.setUseCaches(false);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setRequestProperty("Range", "bytes="+start+"-"+end);
        return c;
    }
    static long validate(HttpURLConnection c, long start, long end, long expected) throws IOException {
        int code=c.getResponseCode();
        if (code!=206) { if(code==200) throw new Protocol("Audio server ignored byte ranges"); throw new HttpError(code); }
        String ct=c.getContentType();
        if(ct!=null) { ct=ct.toLowerCase(Locale.ROOT); if(ct.contains("text/") || ct.contains("json") || ct.contains("xml")) throw new Protocol("Server returned an error document instead of audio"); }
        String encoding=c.getHeaderField("Content-Encoding");
        if(encoding!=null && !encoding.equalsIgnoreCase("identity")) throw new Protocol("Unexpected compressed range");
        String raw=c.getHeaderField("Content-Range"); Matcher m=RANGE.matcher(raw==null?"":raw.trim());
        if(!m.matches()) throw new Protocol("Missing audio range information");
        try {
            long a=Long.parseLong(m.group(1)), b=Long.parseLong(m.group(2)), n=Long.parseLong(m.group(3));
            if(a!=start || b!=end || n<=b || (expected>0 && expected!=n)) throw new Protocol("Audio range or file size changed");
            String length=c.getHeaderField("Content-Length");
            if(length!=null && Long.parseLong(length)!=end-start+1) throw new Protocol("Incorrect audio range length");
            return n;
        } catch(NumberFormatException e) { throw new Protocol("Invalid audio range length"); }
    }
    public static long probe(String url, Observer o) throws IOException {return probe(url,o,"Mozilla/5.0");}
    public static long probe(String url, Observer o,String agent) throws IOException {
        check(o); HttpURLConnection c=connection(url,0,0);c.setRequestProperty("User-Agent",agent);
        try { long n=validate(c,0,0,-1); try(InputStream in=c.getInputStream()) {if(in.read()<0)throw new EOFException("Empty audio probe");} return n; }
        finally {c.disconnect();}
    }
    public static void run(final Source source, final File target, final long total, final String identity, final Observer observer) throws Exception {
        if(total<16 || total>20L*1024*1024*1024) throw new Protocol("Audio size is unavailable or unsupported");
        if(target.getParentFile()!=null && !target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Cannot create download staging folder");
        final File state=new File(target.getPath()+".ranges");
        final int count=(int)((total+BLOCK-1)/BLOCK);
        final boolean[] complete=new boolean[count];
        String fingerprint=identity+"\n"+total+"\n"+BLOCK;
        if(target.isFile() && target.length()==total && state.isFile()) {
            try(DataInputStream in=new DataInputStream(new FileInputStream(state))) { if(fingerprint.equals(in.readUTF())) for(int i=0;i<count;i++)complete[i]=in.readBoolean(); }
            catch(IOException e) {Arrays.fill(complete,false);}
        }
        try(RandomAccessFile out=new RandomAccessFile(target,"rw")){out.setLength(total);}
        final AtomicLong bytes=new AtomicLong();
        for(int i=0;i<count;i++)if(complete[i])bytes.addAndGet(Math.min(BLOCK,total-(long)i*BLOCK));
        observer.progress(bytes.get(),total);
        final AtomicInteger cursor=new AtomicInteger(); final AtomicBoolean failed=new AtomicBoolean();
        ExecutorService pool=Executors.newFixedThreadPool(8);
        List<Future<?>> futures=new ArrayList<>();
        try {
            for(int w=0;w<8;w++)futures.add(pool.submit(new Callable<Void>() {public Void call() throws Exception {
                try {
                    int index;
                    while((index=cursor.getAndIncrement())<count) {
                        check(observer); if(failed.get())throw new InterruptedIOException("Transfer stopped");
                        if(complete[index])continue;
                        long first=(long)index*BLOCK, last=Math.min(total,first+BLOCK)-1, position=first;
                        int failures=0;
                        while(position<=last) {
                            check(observer); HttpURLConnection c=null;
                            try {
                                c=connection(source.url(),position,last);c.setRequestProperty("User-Agent",source.userAgent()); validate(c,position,last,total);
                                try(InputStream in=c.getInputStream();RandomAccessFile out=new RandomAccessFile(target,"rw")) {
                                    out.seek(position); byte[] buffer=new byte[128*1024];
                                    while(position<=last) {
                                        check(observer); if(failed.get())throw new InterruptedIOException("Transfer stopped");
                                        int got=in.read(buffer,0,(int)Math.min(buffer.length,last-position+1));
                                        if(got<0)throw new EOFException("Audio connection ended early");
                                        if(got==0)continue;
                                        out.write(buffer,0,got);position+=got;observer.progress(bytes.addAndGet(got),total);
                                    }
                                    out.getFD().sync();
                                }
                            } catch(IOException e) {
                                check(observer);
                                if(failed.get())throw e;
                                if(e instanceof Protocol)throw e;
                                if(e instanceof HttpError) {int s=((HttpError)e).status;if(s!=408&&s!=429&&s<500)throw e;}
                                if(++failures>4)throw e;
                                for(int tick=0;tick<Math.min(40,failures*8);tick++){check(observer);Thread.sleep(125);}
                            } finally {if(c!=null)c.disconnect();}
                        }
                        synchronized(complete) {
                            complete[index]=true;
                            File next=new File(state.getPath()+".new");
                            try(FileOutputStream file=new FileOutputStream(next);DataOutputStream out=new DataOutputStream(file)) {
                                out.writeUTF(fingerprint);for(boolean b:complete)out.writeBoolean(b);out.flush();file.getFD().sync();
                            }
                            if(!next.renameTo(state))throw new IOException("Cannot save download progress");
                        }
                    }
                    return null;
                } catch(Exception e) {failed.set(true);throw e;}
            }}));
            for(Future<?> f:futures)try{f.get();}catch(ExecutionException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw new IOException("Transfer failed",t);}
            check(observer);
            if(bytes.get()!=total || target.length()!=total)throw new Protocol("Incomplete audio file");
        } finally {failed.set(true);pool.shutdownNow(); if(!pool.awaitTermination(15,TimeUnit.SECONDS))throw new IOException("Audio connections did not stop");}
    }
    public static String container(File file) throws IOException {
        if(file.length()<16)throw new Protocol("Audio file is too small");
        byte[] b=new byte[16];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(b);}
        if(b[0]=='I'&&b[1]=='D'&&b[2]=='3')return "mp3";
        if((b[0]&255)==255&&(b[1]&0xe0)==0xe0)return "mp3";
        if(b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p')return "m4a";
        if((b[0]&255)==26&&(b[1]&255)==69&&(b[2]&255)==223&&(b[3]&255)==163)return "webm";
        if(b[0]=='O'&&b[1]=='g'&&b[2]=='g'&&b[3]=='S')return "ogg";
        if(b[0]=='f'&&b[1]=='L'&&b[2]=='a'&&b[3]=='C')return "flac";
        throw new Protocol("Downloaded payload is not a supported audio container");
    }
    public static String safeName(String raw) {
        String s=(raw==null?"":raw).replaceAll("[\\x00-\\x1f/\\\\:*?\"<>|]","_").trim();
        while(s.startsWith("."))s=s.substring(1);if(s.isEmpty())s="Track";
        return s.length()>100?s.substring(0,100):s;
    }
}
