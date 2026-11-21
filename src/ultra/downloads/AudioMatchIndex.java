package ultra.downloads;
import java.util.*;
import java.util.regex.*;
/** One immutable name index per scan. The same conservative matching rules, without re-normalizing the catalog for every device file. */
final class AudioMatchIndex {
 static final Pattern BRACKET=Pattern.compile("\\[([A-Za-z0-9_-]{11})\\](?:\\s*\\([0-9]+\\))?\\.[A-Za-z0-9]+$"), SUFFIX=Pattern.compile("(?:^|[ _-])([A-Za-z0-9_-]{11})\\.[A-Za-z0-9]+$");
 static final class Entry {
  final Catalog.Track original;final String id,title,artist,name;final Set<String> titleWords,artistWords;
  Entry(Catalog.Track t){original=t;id=t.id;title=LocalAudio.clean(t.title);artist=LocalAudio.artist(t.artist);name=LocalAudio.titleName(t.title);titleWords=LocalAudio.tokens(name);artistWords=LocalAudio.artistTokens(artist);}
 }
 final List<Entry> entries=new ArrayList<>();final Map<String,List<Entry>> exact=new HashMap<>(), names=new HashMap<>();final Set<String> ids=new HashSet<>();
 AudioMatchIndex(List<Catalog.Track> tracks){for(Catalog.Track t:tracks){Entry e=new Entry(t);entries.add(e);ids.add(e.id);exact.computeIfAbsent(e.title+'\u0000'+e.artist,k->new ArrayList<>()).add(e);names.computeIfAbsent(e.name,k->new ArrayList<>()).add(e);}}
 static boolean overlap(String a,String b){if(a.isEmpty()||b.isEmpty())return false;for(String pa:a.split("\\|"))for(String pb:b.split("\\|")){if(pa.equals(pb))return true;Set<String> ta=LocalAudio.tokens(pa),tb=LocalAudio.tokens(pb);if(!ta.isEmpty()&&!tb.isEmpty()&&(tb.containsAll(ta)||ta.containsAll(tb)))return true;}return false;}
 String match(String filename,String title,String performer){String file=filename==null?"":filename;Matcher m=BRACKET.matcher(file);if(m.find())return m.group(1);m=SUFFIX.matcher(file);if(m.find()&&ids.contains(m.group(1)))return m.group(1);
  String t=LocalAudio.clean(title),a=LocalAudio.artist(performer),found=null;
  if(!t.isEmpty()&&!a.isEmpty()&&!a.equals("unknown")){List<Entry> hits=exact.get(t+'\u0000'+a);if(hits!=null){for(Entry e:hits){if(found!=null&&!found.equals(e.id))return null;found=e.id;}if(found!=null)return found;}}
  String want=LocalAudio.titleName(title);List<Entry> same=want.isEmpty()?null:names.get(want);
  if(same!=null&&same.size()==1){Entry only=same.get(0);if(a.isEmpty()||a.equals("unknown")||only.artist.isEmpty()||overlap(a,only.artist))return only.id;}
  else if(same!=null&&same.size()>1){found=null;for(Entry e:same)if(!a.isEmpty()&&!a.equals("unknown")&&overlap(a,e.artist)){if(found!=null&&!found.equals(e.id))return null;found=e.id;}if(found!=null)return found;}
  if(!want.isEmpty()&&!LocalAudio.tokens(LocalAudio.stripped(LocalAudio.clean(file.replaceFirst("\\.[A-Za-z0-9]{2,5}$","")))).containsAll(LocalAudio.tokens(want)))return null;
  return filename(file);
 }
 String filename(String name){String stem=LocalAudio.clean(name.replaceFirst("\\.[A-Za-z0-9]{2,5}$","")).replaceAll("^[0-9]+ ","");if(stem.isEmpty())return null;Set<String> words=LocalAudio.tokens(stem);String found=null;
  for(Entry e:entries){Set<String> title=e.titleWords;if(title.isEmpty()||!words.containsAll(title))continue;Set<String> rest=new LinkedHashSet<>(words);rest.removeAll(title);Set<String> who=e.artistWords;boolean artistInName=!who.isEmpty()&&words.containsAll(who)&&!Collections.disjoint(words,who);for(String w:new ArrayList<>(rest))if(who.contains(w)||LocalAudio.DESCRIPTORS.contains(w))rest.remove(w);if(!rest.isEmpty())continue;if(!artistInName){boolean unique=true;for(Entry other:names.get(e.name))if(other.original!=e.original){unique=false;break;}if(!unique)continue;}if(found!=null&&!found.equals(e.id))return null;found=e.id;}return found;
 }
}
