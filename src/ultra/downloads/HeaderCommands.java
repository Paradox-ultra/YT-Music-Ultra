package ultra.downloads;
import java.io.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** 8.30.54 Elements header binding. Copies the exact online shuffle command from the SAME browse response. */
public final class HeaderCommands {
 private HeaderCommands(){}
 static final int MAX=3*1024*1024,DEPTH=48,NODES=180000;
 static final byte[] SHARE=text("yt_outline_share_vd_theme_24"),SHUFFLE=text("yt_outline_arrow_shuffle_vd_theme_24");
 public static byte[] text(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static class F {int n,w;long number;byte[] value,raw;F(int n,int w,long number,byte[] value,byte[] raw){this.n=n;this.w=w;this.number=number;this.value=value;this.raw=raw;}}
 static long var(byte[] b,int[] p){long n=0;for(int s=0;s<64;s+=7){if(p[0]>=b.length)throw new IllegalArgumentException();int c=b[p[0]++]&255;n|=(long)(c&127)<<s;if(c<128)return n;}throw new IllegalArgumentException();}
 static List<F> fields(byte[] b){if(b==null||b.length>MAX)throw new IllegalArgumentException();List<F> out=new ArrayList<>();int[] p={0};while(p[0]<b.length){if(out.size()>10000)throw new IllegalArgumentException();int st=p[0];long t=var(b,p);int n=(int)(t>>>3),w=(int)t&7;if(n<1||n>536870911)throw new IllegalArgumentException();long v=0;byte[] a=null;if(w==0)v=var(b,p);else if(w==1||w==5){p[0]+=w==1?8:4;}else if(w==2){long l=var(b,p);if(l<0||l>b.length-p[0])throw new IllegalArgumentException();a=Arrays.copyOfRange(b,p[0],p[0]+(int)l);p[0]+=(int)l;}else throw new IllegalArgumentException();if(p[0]>b.length)throw new IllegalArgumentException();out.add(new F(n,w,v,a,Arrays.copyOfRange(b,st,p[0])));}return out;}
 static byte[] get(List<F> fs,int n){byte[] r=null;for(F f:fs)if(f.n==n&&f.w==2){if(r!=null)return null;r=f.value;}return r;}
 static void putVar(ByteArrayOutputStream o,long n){while((n&~127L)!=0){o.write((int)n&127|128);n>>>=7;}o.write((int)n);}
 public static byte[] field(int n,byte[] b){ByteArrayOutputStream o=new ByteArrayOutputStream();putVar(o,((long)n<<3)|2);putVar(o,b.length);o.write(b,0,b.length);return o.toByteArray();}
 static byte[] encode(List<F> fs){ByteArrayOutputStream o=new ByteArrayOutputStream();for(F f:fs)o.write(f.raw,0,f.raw.length);return o.toByteArray();}
 static byte[] replace(byte[] b,int n,byte[] value){List<F> fs=fields(b);boolean found=false;for(F f:fs)if(f.n==n){if(found||f.w!=2)throw new IllegalArgumentException();f.raw=field(n,value);found=true;}if(!found)fs.add(new F(n,2,0,value,field(n,value)));return encode(fs);}
 static byte[] path(byte[] b,int...ns){for(int n:ns){b=get(fields(b),n);if(b==null)throw new IllegalArgumentException();}return b;}
 static byte[] replacePath(byte[] b,int[] p,int i,byte[] v){if(i==p.length)return v;byte[] sub=path(b,p[i]);return replace(b,p[i],replacePath(sub,p,i+1,v));}
 static final int[] MODEL={2,153515154,172660663,1,168777401,5,373186255};
 static String playlist(byte[] model){try{String key=new String(path(model,7),StandardCharsets.US_ASCII);byte[] entity=java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(key,"UTF-8"));String id=new String(path(entity,2),StandardCharsets.US_ASCII);return id.matches("(?:PL|OLAK5uy_|LM|SE)[A-Za-z0-9_-]{0,198}")?id:null;}catch(Exception e){return null;}}
 static void candidates(byte[] b,String id,Map<String,byte[]> found,int d,int[] budget){if(d>DEPTH||--budget[0]<0||b.length<2)return;List<F> fs;try{fs=fields(b);}catch(Exception e){return;}
  try{byte[] icon=get(fs,2),cmd=get(fs,3);if(icon!=null&&cmd!=null){List<F> ifs=fields(icon);boolean shuffle=false;for(F f:ifs)if(f.n==1&&f.w==0&&(f.number==194||f.number==616))shuffle=true;if(shuffle){byte[] ep;int playlistField,paramsField;try{ep=path(cmd,0x3239f4a);playlistField=1;paramsField=4;}catch(Exception notPlaylist){ep=path(cmd,48687757);playlistField=2;paramsField=5;String video=new String(path(ep,1),StandardCharsets.US_ASCII);if(!video.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException();}String pid=new String(path(ep,playlistField),StandardCharsets.US_ASCII);byte[] params=path(ep,paramsField);if(id.equals(pid)&&params.length>0)found.put(java.util.Base64.getEncoder().encodeToString(cmd),cmd);}}}catch(Exception ignored){}
  for(F f:fs)if(f.w==2)candidates(f.value,id,found,d+1,budget);
 }
 static void playlistKeys(byte[] b,Set<String> ids,int d,int[] budget){if(d>DEPTH||--budget[0]<0)return;List<F> fs;try{fs=fields(b);}catch(Exception e){return;}for(F f:fs)if(f.w==2){try{String key=new String(f.value,StandardCharsets.US_ASCII);if(key.length()>=8&&key.length()<=400&&key.matches("[A-Za-z0-9_+/%=-]+")){byte[] entity=java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(key,"UTF-8"));String id=new String(path(entity,2),StandardCharsets.US_ASCII);if(id.matches("(?:PL|OLAK5uy_|LM|SE)[A-Za-z0-9_-]{0,198}"))ids.add(id);}}catch(Exception ignored){}try{String raw=new String(f.value,StandardCharsets.US_ASCII);if(raw.matches("(?:PL|OLAK5uy_|LM|SE)[A-Za-z0-9_-]{0,198}"))ids.add(raw);}catch(Exception ignored){}playlistKeys(f.value,ids,d+1,budget);}}
 static byte[] buttons(byte[] b,byte[] command,int d,int[] budget,int[] changed){if(d>DEPTH||--budget[0]<0)return b;List<F> fs;try{fs=fields(b);}catch(Exception e){return b;}
  if(Arrays.equals(get(fs,1),SHARE)&&get(fs,6)!=null&&get(fs,7)!=null&&get(fs,18)==null){changed[0]++;b=replace(b,1,SHUFFLE);b=replace(b,7,text("Shuffle"));return replace(b,6,field(169495254,command));}
  try{byte[] icon=get(fs,2),cmd=get(fs,3);if(icon!=null&&cmd!=null){boolean share=false;for(F f:fields(icon))if(f.n==1&&f.w==0&&f.number==65)share=true;if(share){changed[0]++;byte[] newIcon=iconNumber(194);b=replace(b,2,newIcon);b=replace(b,3,command);return labels(b,"Share","Shuffle",0);}}}catch(Exception ignored){}
  boolean modified=false;for(F f:fs)if(f.w==2){byte[] v=buttons(f.value,command,d+1,budget,changed);if(v!=f.value){f.raw=field(f.n,v);modified=true;}}return modified?encode(fs):b;
 }
 static byte[] iconNumber(int n){ByteArrayOutputStream o=new ByteArrayOutputStream();putVar(o,8);putVar(o,n);return o.toByteArray();}
 static byte[] labels(byte[] b,String from,String to,int d){if(Arrays.equals(b,text(from)))return text(to);if(d>DEPTH)return b;try{List<F> fs=fields(b);boolean changed=false;for(F f:fs)if(f.w==2){byte[] n=labels(f.value,from,to,d+1);if(n!=f.value){f.raw=field(f.n,n);changed=true;}}return changed?encode(fs):b;}catch(Exception e){return b;}}
 /** Returns the unchanged original on any host/identity ambiguity. No loaded-row or synthesized-command fallback. */
 public static byte[] shuffle(byte[] header,byte[] response){try{if(header==null||response==null||header.length>MAX||response.length>MAX)return header;
  Set<String> ids=new LinkedHashSet<>();playlistKeys(header,ids,0,new int[]{24000});if(ids.isEmpty()||ids.size()>12)return header;
  Map<String,byte[]> found=new LinkedHashMap<>();int[] budget={NODES};byte[] entities;try{entities=path(response,777,1);}catch(Exception missing){entities=response;}for(String id:ids)candidates(entities,id,found,0,budget);if(found.isEmpty()&&entities!=response)for(String id:ids)candidates(response,id,found,0,budget);if(budget[0]<0||found.size()!=1)return header;byte[] cmd=found.values().iterator().next();int[] changed={0},remaining={24000};byte[] result=buttons(header,cmd,0,remaining,changed);return changed[0]==1&&remaining[0]>=0?result:header;
 }catch(Throwable e){return header;}}
}
