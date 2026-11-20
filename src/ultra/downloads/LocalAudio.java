package ultra.downloads;
import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.database.Cursor;import android.media.MediaMetadataRetriever;import android.net.Uri;import android.os.Build;import android.provider.MediaStore;import java.io.*;import java.security.*;import java.text.Normalizer;import java.util.*;import java.util.regex.*;
/** Indexes readable MediaStore audio in place. No file is renamed, copied, transcoded or deleted. */
public final class LocalAudio {
 static String permission(){return Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;}
 static boolean allowed(Context c){return c.checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED;}
 static String clean(String s){if(s==null)return "";return Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 static String artist(String s){if(s==null)return "";String value=Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("(?i)\\s*[-–]\\s*topic\\s*$","").replaceAll("(?i)\\s+(feat\\.?|featuring|ft\\.?)\\s+",",");TreeSet<String> people=new TreeSet<>();for(String part:value.split("[,;&/]+|\\s+&\\s+")){String person=clean(part).replaceAll(" topic$","").trim();if(!person.isEmpty())people.add(person);}return android.text.TextUtils.join("|",people);}
 /** Filler words in YouTube titles and file names that are never part of the song name. Remix/live/cover are real versions and stay. */
 static final Set<String> DESCRIPTORS=new HashSet<>(Arrays.asList("official","video","audio","lyrics","lyric","lyrical","hd","4k","8k","full","song","songs","music","visualizer","visualiser","mv","hq","remastered","remaster","320kbps","kbps","128kbps","mp3","status","dj","free","download","new","best"));
 static LinkedHashSet<String> tokens(String cleaned){LinkedHashSet<String> out=new LinkedHashSet<>();if(!cleaned.isEmpty())for(String t:cleaned.split(" "))if(!t.isEmpty())out.add(t);return out;}
 /** Song name with filler tokens removed; never empty when the input has real words. */
 static String stripped(String cleaned){StringBuilder b=new StringBuilder();for(String t:tokens(cleaned))if(!DESCRIPTORS.contains(t)){if(b.length()>0)b.append(' ');b.append(t);}return b.length()==0?cleaned:b.toString();}
 static final java.util.regex.Pattern VERSION_WORDS=java.util.regex.Pattern.compile("(?i)remix|live|cover|acoustic|karaoke|mashup|reprise|unplugged|version|edit|mix|medley|lofi|slowed|reverb");
 /** Title with movie/album brackets dropped but real version brackets kept, then cleaned and de-fulfilled. */
 static String titleName(String raw){if(raw==null)return "";Matcher m=Pattern.compile("[\\(\\[]([^\\)\\]]*)[\\)\\]]").matcher(raw);StringBuffer out=new StringBuffer();while(m.find()){String replacement=VERSION_WORDS.matcher(m.group(1)).find()?" "+m.group(1)+" ":" ";m.appendReplacement(out,Matcher.quoteReplacement(replacement));}m.appendTail(out);return stripped(clean(out.toString()));}
 static Set<String> artistTokens(String normalized){LinkedHashSet<String> out=new LinkedHashSet<>();for(String person:normalized.split("\\|"))out.addAll(tokens(person));return out;}
 static boolean artistOverlap(String normalizedA,String rawB){if(normalizedA.isEmpty())return false;String nb=artist(rawB);if(nb.isEmpty())return false;for(String pa:normalizedA.split("\\|"))for(String pb:nb.split("\\|")){if(pa.equals(pb))return true;Set<String> ta=tokens(pa),tb=tokens(pb);if(!ta.isEmpty()&&!tb.isEmpty()&&(tb.containsAll(ta)||ta.containsAll(tb)))return true;}return false;}
 static String match(String filename,String title,String performer,List<Catalog.Track> tracks){String name=filename==null?"":filename;Matcher m=Pattern.compile("\\[([A-Za-z0-9_-]{11})\\](?:\\s*\\([0-9]+\\))?\\.[A-Za-z0-9]+$").matcher(name);if(m.find())return m.group(1);m=Pattern.compile("(?:^|[ _-])([A-Za-z0-9_-]{11})\\.[A-Za-z0-9]+$").matcher(name);if(m.find())for(Catalog.Track x:tracks)if(x.id.equals(m.group(1)))return x.id;String t=clean(title),a=artist(performer),found=null;if(!t.isEmpty()&&!a.isEmpty()&&!a.equals("unknown")){for(Catalog.Track x:tracks)if(t.equals(clean(x.title))&&a.equals(artist(x.artist))){if(found!=null&&!found.equals(x.id))return null;found=x.id;}if(found!=null)return found;}
  // Match by song name; the artist only breaks ties between different songs with the same name.
  String want=titleName(title);ArrayList<Catalog.Track> same=new ArrayList<>();if(!want.isEmpty())for(Catalog.Track x:tracks){String have=titleName(x.title);if(!have.isEmpty()&&have.equals(want))same.add(x);}
  if(same.size()==1){Catalog.Track only=same.get(0);if(a.isEmpty()||a.equals("unknown")||artist(only.artist).isEmpty()||artistOverlap(a,only.artist))return only.id;}
  else if(same.size()>1){found=null;for(Catalog.Track x:same)if(!a.isEmpty()&&!a.equals("unknown")&&artistOverlap(a,x.artist)){if(found!=null&&!found.equals(x.id))return null;found=x.id;}if(found!=null)return found;}
  // A known song name that did not match must not be overridden by a vaguer file name guess.
  if(!want.isEmpty()&&!tokens(stripped(clean(name.replaceFirst("\\.[A-Za-z0-9]{2,5}$","")))).containsAll(tokens(want)))return null;
  return filenameMatch(name,tracks);}
 static String filenameMatch(String name,List<Catalog.Track> tracks){String stem=clean(name.replaceFirst("\\.[A-Za-z0-9]{2,5}$","")).replaceAll("^[0-9]+ ","");if(stem.isEmpty())return null;Set<String> words=tokens(stem);String found=null;for(Catalog.Track x:tracks){Set<String> title=tokens(titleName(x.title));if(title.isEmpty()||!words.containsAll(title))continue;Set<String> rest=new LinkedHashSet<>(words);rest.removeAll(title);Set<String> who=artistTokens(artist(x.artist));boolean artistInName=!who.isEmpty()&&words.containsAll(who)&&!Collections.disjoint(words,who);for(String w:new ArrayList<>(rest))if(who.contains(w)||DESCRIPTORS.contains(w))rest.remove(w);if(!rest.isEmpty())continue;if(!artistInName){boolean unique=true;for(Catalog.Track y:tracks)if(y!=x&&titleName(y.title).equals(titleName(x.title))){unique=false;break;}if(!unique)continue;}if(found!=null&&!found.equals(x.id))return null;found=x.id;}return found;}
 static int[] membership(Context c,List<Library.Item> items){Set<String> ids=new HashSet<>();for(String group:PlaylistIndex.groups(c).keySet())if(!UnmatchedDownloads.is(group))ids.addAll(PlaylistIndex.members(c,group));int matched=0,unmatched=0;for(Library.Item i:items)if(VoiceRecordings.visible(c,i)&&i.state.equals("done")){if(ids.contains(i.id))matched++;else unmatched++;}return new int[]{matched,unmatched};}
 public static String nativeUriId(String uri){
  if(uri==null||!uri.matches("content://media/(external|external_primary|internal)/audio/media/[0-9]+"))return null;
  String normalized=uri.replace("/external_primary/","/external/");
  try{Context c=Hooks.context();if(c!=null)for(Library.Item i:Library.all(c))if(normalized.equals(i.uri))return i.id;return localId(normalized);}catch(Exception e){return null;}
 }
 static String localId(String uri)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(uri.getBytes("UTF-8"));return android.util.Base64.encodeToString(java.util.Arrays.copyOf(d,8),android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);}
 static String str(Cursor c,int n){String s=c.getString(n);return s==null||"<unknown>".equals(s)?"":s;}
 static String stamp(String catalog,String... fields)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(catalog.getBytes("UTF-8"));for(String f:fields){d.update((byte)0);d.update(f.getBytes("UTF-8"));}return android.util.Base64.encodeToString(d.digest(),android.util.Base64.NO_WRAP);}
 public static synchronized int scan(Context c)throws Exception{
  if(!allowed(c))throw new SecurityException("Allow music and audio access to find existing songs");
  long started=android.os.SystemClock.elapsedRealtime();ArrayList<Catalog.Track> tracks=PlaylistIndex.tracks(c);AudioMatchIndex index=new AudioMatchIndex(tracks);
  StringBuilder catalog=new StringBuilder();for(Catalog.Track t:tracks)catalog.append(t.id).append('\u0000').append(t.title).append('\u0000').append(t.artist).append('\u0000');String revision=stamp("recording-v1",catalog.toString());
  HashMap<String,Library.Item> byUri=new HashMap<>();for(Library.Item x:Library.all(c))if(!x.uri.isEmpty())byUri.put(x.uri,x);
  ArrayList<Library.Item> incoming=new ArrayList<>();int unchanged=0,tagReads=0;
  String[] cols={"_id","_display_name","title","artist","album","_size","mime_type","duration",Build.VERSION.SDK_INT>=29?"relative_path":"_data"};
  try(Cursor rows=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,"_size>0",null,"_id ASC")){
   int visited=0;if(rows!=null)while(rows.moveToNext()&&visited++<20000){
    String uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,rows.getLong(0)).toString(),name=str(rows,1),title=str(rows,2),performer=str(rows,3),album=str(rows,4),mime=str(rows,6);
    long size=rows.getLong(5),duration=rows.getLong(7);Library.Item known=byUri.get(uri);if(known!=null&&!known.imported)continue;
    String key=stamp(revision,name,title,performer,album,Long.toString(size),mime,Long.toString(duration));if(known!=null&&key.equals(known.localStamp)){unchanged++;continue;}
    String matched=index.match(name,title,performer);MediaMetadataRetriever metadata=null;
    try{
     if(matched==null){metadata=new MediaMetadataRetriever();tagReads++;try{metadata.setDataSource(c,Uri.parse(uri));String tt=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),aa=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(tt!=null&&aa!=null)matched=index.match(name,tt,aa);}catch(Exception ignored){}}
     String id=matched==null?localId(uri):matched;Library.Item old=Library.get(c,id);if(old!=null&&!old.imported)continue;if(old!=null&&!old.uri.equals(uri))continue;
     Library.Item i=new Library.Item();i.id=id;i.title=title.isEmpty()?name:title;i.artist=performer;i.album=album;i.total=size;i.bytes=size;i.mime=mime;i.duration=duration;i.uri=uri;i.state="done";i.imported=true;i.recording=VoiceRecordings.named(name,(rows.getColumnCount()>8?str(rows,8):""))||VoiceRecordings.named(title,(rows.getColumnCount()>8?str(rows,8):""));i.localStamp=key;
     if(known!=null){i.hidden=known.hidden;i.created=known.created;i.bitrate=known.bitrate;}
     // Unchanged files need no second tag read merely because the catalog changed.
     if(known==null||known.total!=size||known.duration!=duration){try{if(metadata==null){metadata=new MediaMetadataRetriever();tagReads++;metadata.setDataSource(c,Uri.parse(uri));}String rate=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);if(rate!=null)i.bitrate=Integer.parseInt(rate);byte[] art=metadata.getEmbeddedPicture();if(art!=null&&art.length<2*1024*1024){File artwork=Art.file(c,id);artwork.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(artwork)){out.write(art);}}}catch(Exception ignored){}}
     incoming.add(i);
    }finally{if(metadata!=null)try{metadata.release();}catch(Exception ignored){}}
   }
  }
  int changed=Library.applyImports(c,incoming);Diagnostics.event("local_audio","indexed-"+changed+"-cached-"+unchanged+"-tags-"+tagReads+"-ms-"+(android.os.SystemClock.elapsedRealtime()-started));return changed;
 }
}
