from pathlib import Path
import zipfile,subprocess,hashlib,importlib.util,struct,os
ROOT=Path(__file__).resolve().parent
OUT=Path(os.environ.get("MUSIC_OUTPUT_DIR",str(ROOT/"dist")));OUT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('manifest',str(ROOT/'manifest-merge.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
java=os.environ.get('JAVA','java')
for abi,src,dst in [('arm64',os.environ['MUSIC_BASE_ARM64'],str(OUT/'YT-Music-Ultra-DL32.apk')),('v7a',os.environ['MUSIC_BASE_V7A'],str(OUT/'YT-Music-Ultra-DL32-armeabi-v7a.apk'))]:
 with zipfile.ZipFile(src) as z:base=z.read('AndroidManifest.xml')
 cs,strings,res=m.unpack(base);strings.append('8.30.54-dl32');res.append(0);idx=len(strings)-1
 for c in cs:
  if m.U16(c,0)!=0x102 or strings[m.U32(c,20)]!='manifest':continue
  start=16+m.U16(c,24)
  for n in range(m.U16(c,28)):
   p=start+n*m.U16(c,26);name=strings[m.U32(c,p+4)]
   if name=='versionCode':struct.pack_into('<I',c,p+8,m.NIL);c[p+15]=16;struct.pack_into('<I',c,p+16,83054275)
   if name=='versionName':struct.pack_into('<I',c,p+8,idx);c[p+15]=3;struct.pack_into('<I',c,p+16,idx)
 # Android 8-12 audio import uses READ_EXTERNAL_STORAGE, which the host did not declare.
 def string(value,rid=0):
  for n,v in enumerate(strings):
   if v==value and res[n]==rid:return n
  strings.append(value);res.append(rid);return len(strings)-1
 permission='android.permission.READ_EXTERNAL_STORAGE'
 if permission not in strings:
  value=string(permission);maximum=string('maxSdkVersion',0x01010271)
  template=next(c for c in cs if m.U16(c,0)==0x102 and strings[m.U32(c,20)]=='uses-permission')
  start=16+m.U16(template,24);nameattr=next(bytearray(template[start+n*20:start+(n+1)*20]) for n in range(m.U16(template,28)) if strings[m.U32(template,start+n*20+4)]=='name')
  struct.pack_into('<I',nameattr,8,value);struct.pack_into('<I',nameattr,16,value)
  maxattr=struct.pack('<IIIHBBI',m.U32(nameattr,0),maximum,m.NIL,8,0,16,32)
  added=bytearray(template[:start])+nameattr+maxattr;struct.pack_into('<H',added,28,2);struct.pack_into('<I',added,4,len(added))
  ending=bytearray(struct.pack('<HHIIIII',0x103,16,24,m.U32(template,8),m.NIL,m.NIL,m.U32(template,20)))
  at=next(n for n,c in enumerate(cs) if m.U16(c,0)==0x102 and strings[m.U32(c,20)]=='application');cs[at:at]=[added,ending]
 body=m.pool_encode(strings)+struct.pack('<HHI',0x180,8,8+len(res)*4)+struct.pack('<'+'I'*len(res),*res)+b''.join(c for c in cs if m.U16(c,0) not in [1,0x180]);manifest=struct.pack('<HHI',3,8,len(body)+8)+body
 out=str(OUT/('dl32-'+abi))
 with zipfile.ZipFile(src) as z,zipfile.ZipFile(out+'-unsigned.apk','w') as w:
  for i in z.infolist():
   if i.filename.startswith('META-INF/'):continue
   data=manifest if i.filename=='AndroidManifest.xml' else Path(os.environ.get('MUSIC_DEX',str(ROOT/'dex/classes.dex'))).read_bytes() if i.filename=='classes9.dex' else z.read(i.filename)
   w.writestr(i,data)
 subprocess.run([os.environ['MUSIC_ZIPALIGN'],'-P','16','-f','4',out+'-unsigned.apk',out+'-aligned.apk'],check=True)
 subprocess.run([java,'-jar',os.environ['MUSIC_APKSIGNER_JAR'],'sign','--key',os.environ['MUSIC_SIGNING_KEY'],'--cert',os.environ['MUSIC_SIGNING_CERT'],'--out',dst,out+'-aligned.apk'],check=True)
 subprocess.run([java,'-jar',os.environ['MUSIC_APKSIGNER_JAR'],'verify','--verbose','--print-certs',dst],check=True)
 print(abi,Path(dst).stat().st_size,hashlib.sha256(Path(dst).read_bytes()).hexdigest())
