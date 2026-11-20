package paradox.dl;
import java.io.*;import java.nio.*;import java.util.concurrent.*;
/** Bounded producer/consumer pipeline. Only one worker touches the continuous LAME stream. */
final class PcmPipeline implements Closeable {
 static final class Block {ByteBuffer data;int bytes,channels;boolean floating,eos;Block(int n){data=ByteBuffer.allocateDirect(n);}}
 final ArrayBlockingQueue<Block> ready=new ArrayBlockingQueue<>(8),free=new ArrayBlockingQueue<>(8);
 final Thread worker;volatile IOException failure;volatile boolean stopped;boolean finished;
 interface Sink extends Closeable{void pcm(ByteBuffer b,int offset,int bytes,int channels,boolean floating)throws IOException;void finish()throws IOException;}
 interface Factory{Sink create()throws IOException;}
 PcmPipeline(final File out,final int rate,final int ch,final int kbps,final byte[] id3){this(new Factory(){public Sink create()throws IOException{final Mp3.Encoder e=new Mp3.Encoder(out,rate,ch,kbps,id3);return new Sink(){public void pcm(ByteBuffer b,int offset,int bytes,int channels,boolean floating)throws IOException{e.pcm(b,offset,bytes,channels,floating);}public void finish()throws IOException{e.finish();}public void close()throws IOException{e.close();}};}});}
 PcmPipeline(final Factory factory){
  for(int n=0;n<8;n++)free.add(new Block(65536));
  worker=new Thread(new Runnable(){public void run(){Sink encoder=null;try{
   encoder=factory.create();
   while(!stopped){Block b=ready.poll(200,TimeUnit.MILLISECONDS);if(b==null)continue;if(b.eos){encoder.finish();return;}encoder.pcm(b.data,0,b.bytes,b.channels,b.floating);free.put(b);}
  }catch(InterruptedException e){if(!stopped)failure=new InterruptedIOException("MP3 pipeline interrupted");Thread.currentThread().interrupt();}
   catch(IOException e){failure=e;}catch(Throwable e){failure=new IOException("MP3 pipeline failed",e);}finally{if(encoder!=null)try{encoder.close();}catch(IOException e){if(failure==null)failure=e;}}}},"UltraMp3Encoder");worker.start();
 }
 void check()throws IOException{if(failure!=null)throw failure;if(stopped)throw new InterruptedIOException("cancelled");}
 void pcm(ByteBuffer input,int offset,int bytes,int channels,boolean floating)throws IOException{
  check();Block b=null;try{while((b=free.poll(100,TimeUnit.MILLISECONDS))==null){check();if(!worker.isAlive())throw new IOException("MP3 worker stopped");}
   if(b.data.capacity()<bytes)b.data=ByteBuffer.allocateDirect(bytes);b.data.clear();ByteBuffer src=input.duplicate();src.position(offset);src.limit(offset+bytes);b.data.put(src);b.bytes=bytes;b.channels=channels;b.floating=floating;
   while(!ready.offer(b,100,TimeUnit.MILLISECONDS))check();
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("cancelled");}
 }
 void finish()throws IOException{check();Block end=new Block(0);end.eos=true;try{while(!ready.offer(end,100,TimeUnit.MILLISECONDS))check();while(worker.isAlive()){worker.join(100);check();}check();finished=true;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("cancelled");}}
 public void close()throws IOException{stopped=true;if(worker.isAlive()){worker.interrupt();try{worker.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}if(worker.isAlive())throw new IOException("MP3 worker did not stop");if(!finished&&failure!=null)throw failure;}
}
