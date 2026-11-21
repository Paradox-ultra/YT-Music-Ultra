package ultra.downloads;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Read-only presentation merge. Original playlist IDs, cache rows and media are retained. */
public final class LikedPlaylists {
    private LikedPlaylists(){}

    public static void preserveAlias(String id, String name) {
        if (id == null || id.isEmpty()) return;
        try {
            Context c = Hooks.context();
            if (c == null) return;
            PlaylistIndex.register(id, name != null && !name.isEmpty() ? name : "Liked Songs");
        } catch (Throwable ignored) {}
    }

    static boolean liked(String id, JSONObject e) {
        String k = PlaylistIndex.key(id);
        if ("LM".equals(k)) return true;
        String n = e == null ? "" : e.optString("name", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return n.equals("likedmusic") || n.equals("likedsongs");
    }

    static ArrayList<String> sources(JSONObject all, Map<String, String> roster) {
        ArrayList<String> ids = new ArrayList<>();
        for (String id : roster.keySet()) {
            if (liked(id, all.optJSONObject(id))) ids.add(id);
        }
        ArrayList<String> older = new ArrayList<>();
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String id = it.next();
            if (liked(id, all.optJSONObject(id)) && !ids.contains(id)) older.add(id);
        }
        Collections.sort(older);
        ids.addAll(older);
        if (ids.remove("LM")) ids.add(0, "LM");
        return ids;
    }

    static Map<String, String> groups(JSONObject all, Map<String, String> roster) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        ArrayList<String> ids = sources(all, roster);
        String primary = ids.isEmpty() ? "" : ids.get(0);
        boolean added = false;
        for (Map.Entry<String, String> row : roster.entrySet()) {
            if (!liked(row.getKey(), all.optJSONObject(row.getKey()))) {
                out.put(row.getKey(), row.getValue());
                continue;
            }
            if (!added) {
                JSONObject e = all.optJSONObject(primary);
                String name = e == null ? row.getValue() : e.optString("name", row.getValue());
                out.put(primary, name);
                added = true;
            }
        }
        return out;
    }

    static ArrayList<String> sources(Context c, String id) {
        JSONObject all = PlaylistIndex.read(c);
        ArrayList<String> out = new ArrayList<>();
        id = PlaylistIndex.key(id);
        if (liked(id, all.optJSONObject(id))) return sources(all, PlaylistIndex.rawGroups(c));
        out.add(id);
        return out;
    }

    static JSONObject entry(Context c, String id) {
        JSONObject all = PlaylistIndex.read(c);
        id = PlaylistIndex.key(id);
        JSONObject direct = all.optJSONObject(id);
        if (!liked(id, direct)) return direct;
        ArrayList<String> ids = sources(all, PlaylistIndex.rawGroups(c));
        if (ids.isEmpty()) return direct;
        try {
            JSONObject first = all.optJSONObject(ids.get(0)), merged = first == null ? new JSONObject() : new JSONObject(first.toString());
            JSONArray tracks = new JSONArray();
            Set<String> seen = new HashSet<>();
            boolean complete = true;
            String art = merged.optString("art", "");
            for (String source : ids) {
                JSONObject e = all.optJSONObject(source);
                if (e == null) {
                    complete = false;
                    continue;
                }
                complete &= e.optBoolean("complete");
                if (art.isEmpty()) art = e.optString("art", "");
                JSONArray a = e.optJSONArray("tracks");
                Set<String> prior = new HashSet<>(seen);
                if (a != null) {
                    for (int n = 0; n < a.length(); n++) {
                        JSONObject t = a.optJSONObject(n);
                        if (t == null) continue;
                        String video = t.optString("id");
                        if (video.isEmpty() || prior.contains(video)) continue;
                        tracks.put(t);
                        seen.add(video);
                    }
                }
            }
            merged.put("tracks", tracks).put("complete", complete).put("art", art);
            return merged;
        } catch (JSONException error) {
            return direct;
        }
    }
}
