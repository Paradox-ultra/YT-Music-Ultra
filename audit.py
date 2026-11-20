import zipfile,struct,hashlib,os
from pathlib import Path
path=os.environ['MUSIC_AUDIT_APK']
classes={};refs=[];defs_count=0
u32=lambda b,p:struct.unpack_from('<I',b,p)[0]
u16=lambda b,p:struct.unpack_from('<H',b,p)[0]
def leb(b,p):
 value=0;shift=0
 while True:
  v=b[p];p+=1;value|=(v&127)<<shift
  if v<128:return value,p
  shift+=7
with zipfile.ZipFile(path) as z:
 for name in z.namelist():
  if not name.endswith('.dex'):continue
  b=z.read(name);strings=[]
  assert hashlib.sha1(b[32:]).digest()==b[12:32]
  for n in range(u32(b,56)):
   p=u32(b,u32(b,60)+4*n);_,p=leb(b,p);end=b.index(0,p);strings.append(b[p:end].decode('utf-8','replace'))
  types=[strings[u32(b,u32(b,68)+4*n)] for n in range(u32(b,64))]
  protos=[]
  for n in range(u32(b,72)):
   p=u32(b,76)+n*12;off=u32(b,p+8);args=[] if off==0 else [types[u16(b,off+4+2*j)] for j in range(u32(b,off))];protos.append('('+''.join(args)+')'+types[u32(b,p+4)])
  methods=[]
  for n in range(u32(b,88)):
   p=u32(b,92)+8*n;methods.append((types[u16(b,p)],strings[u32(b,p+4)]+protos[u16(b,p+2)]))
  refs+=methods
  for n in range(u32(b,96)):
   p=u32(b,100)+32*n;cls=types[u32(b,p)];sup=u32(b,p+8);sup=None if sup==0xffffffff else types[sup];off=u32(b,p+24);defs={}
   assert cls not in classes,'duplicate '+cls
   if off:
    counts=[]
    for j in range(4):v,off=leb(b,off);counts.append(v)
    for j in range(counts[0]+counts[1]):_,off=leb(b,off);_,off=leb(b,off)
    for count in counts[2:]:
     mid=0
     for j in range(count):
      delta,off=leb(b,off);mid+=delta;access,off=leb(b,off);code,off=leb(b,off);owner,sig=methods[mid];assert owner==cls;defs[sig]=access;defs_count+=1
   classes[cls]=(sup,defs)
 assert ('Ljava/util/regex/Matcher;','replaceAll(Ljava/util/function/Function;)Ljava/lang/String;') not in refs,'Android-incompatible regex Function API'
 for old in ['Lultra/downloads/OfflinePlayer;','Lultra/downloads/DownloadsActivity;']:assert old not in classes
 assert 'Lultra/downloads/NativePlayback;' in classes
 assert not any('RuntimeChecks' in c or 'LegacyToMp3' in c for c in classes)
 activity='Lcom/google/android/apps/youtube/music/activities/MusicActivity;'
 for method in classes[activity][1]:
  if method.startswith('<'):continue
  parent=classes[activity][0]
  while parent in classes:
   assert not (classes[parent][1].get(method,0)&16),'final override '+method
   parent=classes[parent][0]
 for owner,sig in refs:
  if owner in {'Lultra/downloads/NativeIntegration;','Lultra/downloads/NativePages;','Lultra/downloads/NativeModels;','Lultra/downloads/NativePlayback;','Lultra/downloads/OfflineCommands;','LUltraDownloadBridge;','Lultra/downloads/Diagnostics;','Lultra/downloads/NativeSnapshot;','Lultra/downloads/NativeStreams;','LUltraBrowseBridge;','LUltraRadioBridge;','Lultra/downloads/RadioContext;','Lultra/downloads/PlayerFallback;','Lultra/downloads/ResolveErrors;','Lultra/downloads/LibraryEvidence;','Lultra/downloads/LocalAudio;','Lultra/downloads/PlaylistIndex;','LUltraPlaylistIndex;','LUltraBackBridge;','LUltraNavigationBridge;','Lultra/downloads/LocalPlaylistCopies;'}:
   assert owner in classes and sig in classes[owner][1],(owner,sig)
 print('PASS DEX audit:',len(classes),'unique classes;',defs_count,'defined methods; no duplicates, no final Activity override, all native integration references resolve, old components absent.')
with zipfile.ZipFile(os.environ['MUSIC_AUDIT_BASE']) as a,zipfile.ZipFile(path) as b:
 assert b.getinfo('resources.arsc').compress_type==0
 changed=[n for n in a.namelist() if not n.startswith('META-INF/') and a.read(n)!=b.read(n)]
 assert set(changed)=={'AndroidManifest.xml','classes9.dex'},changed
 print('PASS resources/assets/native libraries unchanged byte-for-byte; resource table remains uncompressed.')
