from pathlib import Path
import struct,zipfile
R=Path('/tmp/music-downloads-native')
U16=lambda b,p:struct.unpack_from('<H',b,p)[0]
U32=lambda b,p:struct.unpack_from('<I',b,p)[0]
NIL=0xffffffff

def chunks(data):
 p=8;out=[]
 while p<len(data):
  kind,header,size=struct.unpack_from('<HHI',data,p);assert size>=8 and p+size<=len(data);out.append(bytearray(data[p:p+size]));p+=size
 assert p==len(data)
 return out

def pool_decode(data):
 header=U16(data,2);count=U32(data,8);styles=U32(data,12);flags=U32(data,16);start=U32(data,20);assert not styles
 def length(p):
  if flags&256:
   n=data[p];return ((((n&127)<<8)|data[p+1],p+2) if n&128 else (n,p+1))
  n=U16(data,p);return ((((n&32767)<<16)|U16(data,p+2),p+4) if n&32768 else (n,p+2))
 out=[]
 for n in range(count):
  p=start+U32(data,header+n*4);size,p=length(p)
  if flags&256:size,p=length(p);out.append(bytes(data[p:p+size]).decode('utf-8'))
  else:out.append(bytes(data[p:p+size*2]).decode('utf-16le'))
 return out

def pool_encode(strings):
 offsets=[];data=bytearray()
 for s in strings:
  b=s.encode('utf-16le');n=len(b)//2;assert n<32768;offsets.append(len(data));data+=struct.pack('<H',n)+b+b'\0\0'
 data+=b'\0'*((-len(data))%4);start=28+4*len(strings);size=start+len(data)
 return bytearray(struct.pack('<HHI5I',1,28,size,len(strings),0,0,start,0)+struct.pack('<'+'I'*len(offsets),*offsets)+data)

def unpack(data):
 cs=chunks(data);strings=pool_decode(next(c for c in cs if U16(c,0)==1));rm=next((c for c in cs if U16(c,0)==0x180),None);res=list(struct.unpack_from('<'+'I'*((len(rm)-8)//4),rm,8)) if rm else [];res += [0]*(len(strings)-len(res));return cs,strings,res

def patch(base,addition):
 cs,strings,res=unpack(base);ac,astr,ares=unpack(addition);mapping={}
 for i,s in enumerate(astr):
  found=next((n for n,v in enumerate(strings) if v==s and res[n]==ares[i]),None)
  if found is None:found=len(strings);strings.append(s);res.append(ares[i])
  mapping[i]=found
 def add_string(s,rid=0):
  found=next((n for n,v in enumerate(strings) if v==s and res[n]==rid),None)
  if found is None:found=len(strings);strings.append(s);res.append(rid)
  return found
 ns=add_string('http://schemas.android.com/apk/res/android');enabled=add_string('enabled',0x0101000e)
 def remap(c):
  c=bytearray(c);kind=U16(c,0)
  def index(p):
   old=U32(c,p)
   if old!=NIL:struct.pack_into('<I',c,p,mapping[old])
  index(12)
  if kind==0x102:
   index(16);index(20);start=16+U16(c,24)
   for n in range(U16(c,28)):
    p=start+n*U16(c,26);index(p);index(p+4);index(p+8)
    if c[p+15]==3:index(p+16)
  elif kind==0x103:index(16);index(20)
  return c
 additions=[];depth=0;in_app=False
 for c in ac:
  k=U16(c,0)
  if k==0x102:
   tag=astr[U32(c,20)]
   if in_app:additions.append(remap(c));depth+=1
   elif tag=='application':in_app=True;depth=0
  elif k==0x103 and in_app:
   if depth==0:in_app=False
   else:additions.append(remap(c));depth-=1
 disabled=[];output=[]
 targets={'com.google.android.libraries.youtube.offline.transfer.service.OfflineKeepAliveService','com.google.android.libraries.youtube.offline.transfer.service.OfflineUserInitiatedDataTransferJobService'}
 for c in cs:
  k=U16(c,0)
  if k in (1,0x180):continue
  if k==0x102 and strings[U32(c,20)]=='service':
   start=16+U16(c,24);attrs=[bytearray(c[start+i*20:start+(i+1)*20]) for i in range(U16(c,28))];name=''
   for a in attrs:
    if strings[U32(a,4)]=='name' and a[15]==3:name=strings[U32(a,16)]
   if name in targets:
    found=False
    for a in attrs:
     if strings[U32(a,4)]=='enabled':a[15]=18;struct.pack_into('<I',a,16,0);struct.pack_into('<I',a,8,NIL);found=True
    if not found:attrs.append(bytearray(struct.pack('<IIIHBBI',ns,enabled,NIL,8,0,18,0)))
    prefix=c[:start];struct.pack_into('<H',prefix,28,len(attrs));c=prefix+b''.join(attrs);struct.pack_into('<I',c,4,len(c));disabled.append(name)
  if k==0x103 and strings[U32(c,20)]=='application':output.extend(additions)
  output.append(c)
 assert len(disabled)==2,disabled
 pool=pool_encode(strings);rm=struct.pack('<HHI',0x180,8,8+len(res)*4)+struct.pack('<'+'I'*len(res),*res)
 body=pool+rm+b''.join(output);return struct.pack('<HHI',3,8,len(body)+8)+body,disabled

if __name__=='__main__':
 with zipfile.ZipFile('/downloads/YT-Music-Ultra-Search-Fix.apk') as z:base=z.read('AndroidManifest.xml')
 with zipfile.ZipFile(R/'components.ap_') as z:addition=z.read('AndroidManifest.xml')
 result,disabled=patch(base,addition);(R/'AndroidManifest.bin.xml').write_bytes(result);print('Added new downloads activity and services; disabled:',disabled)
