package ultra.downloads;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class Catalog {
    static JSONObject json(String url,JSONObject payload,String auth) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Accept","application/json");
        try {
            if(payload!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");if(auth!=null&&!auth.isEmpty())c.setRequestProperty("Authorization",auth);if(url.startsWith("https://music.youtube.com/youtubei/"))for(Map.Entry<String,String> header:NativeStreams.requestHeaders().entrySet())if(!header.getKey().equalsIgnoreCase("Authorization"))c.setRequestProperty(header.getKey(),header.getValue());byte[] b=payload.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}}
            int status=c.getResponseCode();if(status!=200)throw new IOException("Music catalog returned HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>12*1024*1024)throw new IOException("Music catalog response is too large");out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),"UTF-8"));}
        } finally {c.disconnect();}
    }
    static boolean metadataAllowed(Library.Item i) {
        if(i==null||i.id==null||!i.id.matches("[A-Za-z0-9_-]{11}"))return false;
        if(i.imported)try{if(i.id.equals(LocalAudio.localId(i.uri)))return false;}catch(Exception e){return false;}
        return true;
    }
    static void metadata(Library.Item i) {
        if(!metadataAllowed(i))return;
        try {
            JSONObject j=json("https://www.youtube.com/oembed?format=json&url="+URLEncoder.encode("https://www.youtube.com/watch?v="+i.id,"UTF-8"),null,null);
            if(i.title.isEmpty()||i.title.equals(i.id))i.title=j.optString("title",i.title);
            if(i.artist.isEmpty())i.artist=j.optString("author_name","");i.artUrl=j.optString("thumbnail_url",i.artUrl);
        }catch(Exception ignored){}
    }
    static final class Track {String id,title,artist,album;Track(String id,String title,String artist,String album){this.id=id;this.title=title;this.artist=artist;this.album=album;}}
    static final class Page {ArrayList<Track> ordered=new ArrayList<>();LinkedHashMap<String,Track> tracks=new LinkedHashMap<>();String token="";boolean found,tolerant,incomplete;}
    static String text(JSONObject obj){if(obj==null)return "";String s=obj.optString("simpleText","");if(!s.isEmpty())return s;JSONArray a=obj.optJSONArray("runs");StringBuilder b=new StringBuilder();if(a!=null)for(int n=0;n<a.length();n++)b.append(a.optJSONObject(n).optString("text"));return b.toString();}
    // Only a track shelf owns songs and its own next-page token. Menus, suggestions,
    // frameworkUpdates and section-level continuations are deliberately not scanned.
    static void walk(Object value,Page out,int depth) throws Exception {
        if(depth>40)throw new IOException("Music catalog nesting is too deep");
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int n=0;n<a.length();n++)walk(a.get(n),out,depth+1);return;}
        if(!(value instanceof JSONObject))return;JSONObject j=(JSONObject)value;
        for(String name:new String[]{"musicPlaylistShelfRenderer","musicPlaylistShelfContinuation","musicShelfRenderer","musicShelfContinuation","playlistVideoListRenderer","playlistVideoListContinuation"}){
            JSONObject shelf=j.optJSONObject(name);if(shelf!=null){if(out.found)throw new IOException("Multiple track shelves found. No partial playlist has been queued.");out.found=true;rows(shelf.optJSONArray("contents"),out,depth+1);if(shelf.has("continuations"))next(shelf.optJSONArray("continuations"),out);return;}
        }
        for(String action:new String[]{"appendContinuationItemsAction","reloadContinuationItemsCommand"}){JSONObject a=j.optJSONObject(action);if(a!=null){out.found=true;rows(a.optJSONArray("continuationItems"),out,depth+1);return;}}
        for(String key:new String[]{"onResponseReceivedActions","onResponseReceivedEndpoints","contents","continuationContents","singleColumnBrowseResultsRenderer","twoColumnBrowseResultsRenderer","tabs","tabRenderer","content","sectionListRenderer","secondaryContents"}){
            Object child=j.opt(key);if(child!=null)walk(child,out,depth+1);
        }
    }
    static void next(JSONArray a,Page out)throws Exception{
        if(a==null)return;for(int n=0;n<a.length();n++){JSONObject obj=a.optJSONObject(n),data=obj==null?null:obj.optJSONObject("nextContinuationData");if(data!=null){String token=data.optString("continuation");if(!token.isEmpty()){if(!out.token.isEmpty()&&!out.token.equals(token))throw new IOException("Ambiguous playlist continuation");out.token=token;}}}
    }
    static void rows(JSONArray a,Page out,int depth)throws Exception{
        if(a==null)return;for(int n=0;n<a.length();n++)row(a.optJSONObject(n),out,depth+1);
    }
    static String watchId(JSONObject endpoint){JSONObject watch=endpoint==null?null:endpoint.optJSONObject("watchEndpoint");if(watch!=null)return watch.optString("videoId");JSONObject browse=endpoint==null?null:endpoint.optJSONObject("browseEndpoint");String id=browse==null?"":browse.optString("browseId");return id.matches("MPED[A-Za-z0-9_-]{11}")?id.substring(4):"";}
    static void row(JSONObject j,Page out,int depth)throws Exception{
        if(j==null)return;if(depth>40)throw new IOException("Music row nesting is too deep");
        JSONObject r=j.optJSONObject("musicTwoColumnItemRenderer");
        if(r!=null){String id=watchId(r.optJSONObject("navigationEndpoint"));JSONObject data=r.optJSONObject("playlistItemData");if(id.isEmpty()&&data!=null)id=data.optString("videoId");String artist=text(r.optJSONObject("subtitle"));int split=artist.indexOf(" • ");if(split>=0)artist=artist.substring(0,split);put(out,id,text(r.optJSONObject("title")),artist,"");return;}
        r=j.optJSONObject("musicResponsiveListItemRenderer");
        if(r!=null){String id="",title="",artist="",album="";JSONObject data=r.optJSONObject("playlistItemData");if(data!=null)id=data.optString("videoId","");JSONArray cols=r.optJSONArray("flexColumns");
            if(cols!=null)for(int n=0;n<cols.length();n++){JSONObject wrap=cols.optJSONObject(n),col=wrap==null?null:wrap.optJSONObject("musicResponsiveListItemFlexColumnRenderer"),tx=col==null?null:col.optJSONObject("text");if(n==0){title=text(tx);if(id.isEmpty()&&tx!=null){JSONArray runs=tx.optJSONArray("runs");if(runs!=null)for(int x=0;x<runs.length();x++){JSONObject run=runs.optJSONObject(x);String candidate=watchId(run==null?null:run.optJSONObject("navigationEndpoint"));if(!candidate.isEmpty()){id=candidate;break;}}}}if(tx!=null){JSONArray runs=tx.optJSONArray("runs");if(runs!=null)for(int x=0;x<runs.length();x++){JSONObject run=runs.optJSONObject(x),ep=run==null?null:run.optJSONObject("navigationEndpoint"),browse=ep==null?null:ep.optJSONObject("browseEndpoint");if(browse==null)continue;String bid=browse.optString("browseId","");if(bid.startsWith("UC")){String who=run.optString("text","");if(!who.isEmpty()&&!Arrays.asList(artist.split(", ")).contains(who))artist+=(artist.isEmpty()?"":", ")+who;}if(bid.startsWith("MPRE"))album=run.optString("text","");}}}
            if(artist.isEmpty()&&cols!=null&&cols.length()>1){JSONObject wrap=cols.optJSONObject(1),col=wrap==null?null:wrap.optJSONObject("musicResponsiveListItemFlexColumnRenderer");String fallback=text(col==null?null:col.optJSONObject("text"));int dot=fallback.indexOf(" • ");if(dot>=0)fallback=fallback.substring(0,dot);if(!fallback.matches("[0-9:., ]+")&&!fallback.trim().isEmpty())artist=fallback;}
            put(out,id,title,artist,album);return;}
        r=j.optJSONObject("playlistVideoRenderer");if(r!=null){put(out,r.optString("videoId"),text(r.optJSONObject("title")),text(r.optJSONObject("shortBylineText")),"");return;}
        r=j.optJSONObject("continuationItemRenderer");if(r!=null){JSONObject ep=r.optJSONObject("continuationEndpoint"),cmd=ep==null?null:ep.optJSONObject("continuationCommand");if(cmd!=null)out.token=cmd.optString("token");return;}
        // Wrappers may carry a nested content renderer. Never search row menus or
        // playback commands: those may contain other video IDs or reload tokens.
        for(String key:new String[]{"swipeableRowRenderer","musicSwipeableRowRenderer","elementRenderer"}){JSONObject wrapper=j.optJSONObject(key);if(wrapper!=null){JSONObject content=wrapper.optJSONObject("content");if(content==null)content=wrapper.optJSONObject("renderer");if(content==null)throw new IOException("Playlist uses an unsupported song layout. Nothing was queued.");row(content,out,depth+1);return;}}
        if(j.has("messageRenderer")||j.has("musicSeparatorRenderer"))return;
        if(j.length()!=0){if(out.tolerant){out.incomplete=true;return;}throw new IOException("Playlist uses an unsupported song layout. Nothing was queued.");}
    }
    static void put(Page out,String id,String title,String artist,String album)throws Exception{
        if(!id.matches("[A-Za-z0-9_-]{11}")){if(out.tolerant){out.incomplete=true;return;}throw new IOException("A playlist song is unavailable. No partial playlist has been queued.");}Track t=new Track(id,title,artist,album);out.tracks.put(id,t);out.ordered.add(t);
    }
    static final class Playlist {String id,name,art;Playlist(String i,String n,String a){id=i;name=n;art=a;}}
    static final class Roster {LinkedHashMap<String,Playlist> rows=new LinkedHashMap<>();String token="";boolean shelf;}
    static String artwork(Object o,int d){if(o==null||d>12)return "";if(o instanceof JSONObject){JSONObject j=(JSONObject)o;String url=j.optString("url","");if(url.startsWith("https://"))return url;Iterator<String> it=j.keys();while(it.hasNext()){String v=artwork(j.opt(it.next()),d+1);if(!v.isEmpty())return v;}}else if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int n=a.length()-1;n>=0;n--){String v=artwork(a.opt(n),d+1);if(!v.isEmpty())return v;}}return "";}
    static void roster(Object o,Roster out,int d)throws Exception{if(d>40)throw new IOException("Library response nesting is too deep");if(o instanceof JSONArray){JSONArray a=(JSONArray)o;for(int n=0;n<a.length();n++)roster(a.get(n),out,d+1);return;}if(!(o instanceof JSONObject))return;JSONObject j=(JSONObject)o;
      for(String kind:new String[]{"musicTwoRowItemRenderer","musicResponsiveListItemRenderer","musicTwoColumnItemRenderer","playlistRenderer"}){JSONObject r=j.optJSONObject(kind);if(r==null)continue;JSONObject ep=r.optJSONObject("navigationEndpoint"),be=ep==null?null:ep.optJSONObject("browseEndpoint");String id=be==null?"":be.optString("browseId","");if(id.isEmpty()&&r.has("playlistId"))id="VL"+r.optString("playlistId");String title=text(r.optJSONObject("title"));if(title.isEmpty()){JSONArray cols=r.optJSONArray("flexColumns");JSONObject c=cols==null?null:cols.optJSONObject(0),col=c==null?null:c.optJSONObject("musicResponsiveListItemFlexColumnRenderer");title=text(col==null?null:col.optJSONObject("text"));}if(id.startsWith("VL")&&id.length()>2&&!title.isEmpty())out.rows.put(id,new Playlist(id,title,artwork(r.opt("thumbnailRenderer")!=null?r.opt("thumbnailRenderer"):r.opt("thumbnail"),0)));return;}
      JSONObject next=j.optJSONObject("nextContinuationData");if(next!=null){String t=next.optString("continuation","");if(!t.isEmpty()){if(!out.token.isEmpty()&&!out.token.equals(t))throw new IOException("Multiple Library continuations");out.token=t;}return;}
      JSONObject cont=j.optJSONObject("continuationCommand");if(cont!=null){String t=cont.optString("token","");if(!t.isEmpty()){if(!out.token.isEmpty()&&!out.token.equals(t))throw new IOException("Multiple Library continuations");out.token=t;}return;}
      for(String k:new String[]{"gridRenderer","gridContinuation","musicShelfRenderer","musicShelfContinuation"})if(j.has(k))out.shelf=true;
      for(String k:new String[]{"contents","items","continuations","continuationContents","singleColumnBrowseResultsRenderer","twoColumnBrowseResultsRenderer","tabs","tabRenderer","content","sectionListRenderer","sectionListContinuation","gridRenderer","gridContinuation","musicShelfRenderer","musicShelfContinuation","continuationItemRenderer","continuationEndpoint"})if(j.has(k))roster(j.opt(k),out,d+1);
    }
    static ArrayList<Playlist> library()throws Exception{String auth=NativeStreams.requestHeaders().get("Authorization");if(auth==null||auth.isEmpty())throw new IOException("Open Library once while online to sync playlists");LinkedHashMap<String,Playlist> all=new LinkedHashMap<>();Set<String> seen=new HashSet<>();String token="";do{JSONObject client=new JSONObject().put("clientName","ANDROID_MUSIC").put("clientVersion","8.30.54").put("hl","en").put("gl","IN");JSONObject payload=new JSONObject().put("context",new JSONObject().put("client",client));if(token.isEmpty())payload.put("browseId","FEmusic_liked_playlists");else payload.put("continuation",token);JSONObject j=json("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",payload,auth);Roster r=new Roster();roster(j,r,0);if(!r.shelf)throw new IOException("Library playlist layout is not available to sync yet");all.putAll(r.rows);token=r.token;if(!token.isEmpty()&&!seen.add(token))throw new IOException("Library continuation repeated");if(seen.size()>200||all.size()>1000)throw new IOException("Library exceeds sync limit");}while(!token.isEmpty());return new ArrayList<>(all.values());}
    static ArrayList<Track> playlist(String id)throws Exception{LinkedHashMap<String,Track> unique=new LinkedHashMap<>();for(Track t:playlistOrdered(id))unique.put(t.id,t);if(unique.isEmpty())throw new IOException("Playlist is empty. Nothing was queued.");return new ArrayList<>(unique.values());}
    static final class IndexPage {final ArrayList<Track> tracks;final boolean complete;IndexPage(ArrayList<Track> t,boolean c){tracks=t;complete=c;}}
    static IndexPage index(String id) throws Exception {
        String auth=NativeStreams.requestHeaders().get("Authorization");if(auth==null||auth.isEmpty())throw new IOException("Waiting for the signed-in Music session");
        if(!id.matches("[A-Za-z0-9_-]{2,200}"))throw new IOException("Invalid playlist ID");
        boolean complete=true;ArrayList<Track> all=new ArrayList<>();Set<String> seen=new HashSet<>();String token="";
        do {
            JSONObject client=new JSONObject().put("clientName","ANDROID_MUSIC").put("clientVersion","8.30.54").put("hl","en").put("gl","IN");
            JSONObject payload=new JSONObject().put("context",new JSONObject().put("client",client));
            if(token.isEmpty())payload.put("browseId",id.startsWith("VL")||id.startsWith("MPRE")?id:"VL"+id);else payload.put("continuation",token);
            JSONObject j=json("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",payload,NativeStreams.requestHeaders().get("Authorization"));
            if(j.has("error"))throw new IOException("Playlist is not available in the current account response.");
            Page p=new Page();p.tolerant=true;walk(j,p,0);complete&=!p.incomplete;if(!p.found)throw new IOException("Playlist is private, unavailable, or uses an unsupported catalog response. Nothing was queued.");all.addAll(p.ordered);token=p.token;
            if(!token.isEmpty()&&!seen.add(token))throw new IOException("Playlist continuation repeated; no partial playlist has been queued");
            if(all.size()>10000||seen.size()>200)throw new IOException("Playlist exceeds the safe batch size; no partial playlist has been queued");
        }while(!token.isEmpty());
        return new IndexPage(all,complete);
    }
    static ArrayList<Track> playlistOrdered(String id) throws Exception {
        ArrayList<Track> nativeTracks=NativeSnapshot.complete(id);if(nativeTracks!=null){Diagnostics.event("batch_source","native-complete");return nativeTracks;}
        if(!id.matches("[A-Za-z0-9_-]{2,200}"))throw new IOException("Invalid playlist ID");
        ArrayList<Track> all=new ArrayList<>();Set<String> seen=new HashSet<>();String token="";
        do {
            JSONObject client=new JSONObject().put("clientName","ANDROID_MUSIC").put("clientVersion","8.30.54").put("hl","en").put("gl","IN");
            JSONObject payload=new JSONObject().put("context",new JSONObject().put("client",client));
            if(token.isEmpty())payload.put("browseId",id.startsWith("VL")||id.startsWith("MPRE")?id:"VL"+id);else payload.put("continuation",token);
            JSONObject j=json("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",payload,NativeStreams.requestHeaders().get("Authorization"));
            if(j.has("error"))throw new IOException("Playlist is not available in the current account response.");
            Page p=new Page();walk(j,p,0);if(!p.found)throw new IOException("Playlist is private, unavailable, or uses an unsupported catalog response. Nothing was queued.");all.addAll(p.ordered);token=p.token;
            if(!token.isEmpty()&&!seen.add(token))throw new IOException("Playlist continuation repeated; no partial playlist has been queued");
            if(all.size()>10000||seen.size()>200)throw new IOException("Playlist exceeds the safe batch size; no partial playlist has been queued");
        }while(!token.isEmpty());
        return all;
    }
}
