import java.lang.reflect.*;
import java.util.*;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;

/** Finds native library playlist rows without keeping account response bytes. */
public final class UltraPlaylistIndex {
    public static void accept(Object response) {
        if (response instanceof bhbk) ultra.downloads.NativeSnapshot.playlistHeaders(((bhbk) response).toByteArray());
        ArrayList<String> order = new ArrayList<>();
        walk(response, 0, new int[]{20000}, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), order);
        ultra.downloads.PlaylistIndex.nativeOrder(order);
    }

    static String label(blwb text) {
        if (text == null) return "";
        if (!text.d.isEmpty()) return text.d;
        StringBuilder b = new StringBuilder();
        for (Object run : text.c) {
            try {
                Object value = run.getClass().getField("d").get(run);
                if (value instanceof String) b.append(value);
            } catch (Exception ignored) {}
        }
        return b.toString();
    }

    static String art(Object node, int d, Set<Object> seen) {
        if (node == null || d > 10 || !seen.add(node)) return "";
        if (node instanceof String) {
            String s = (String) node;
            return s.startsWith("https://") ? s : "";
        }
        if (node instanceof List) {
            for (Object o : (List<?>) node) {
                String s = art(o, d + 1, seen);
                if (!s.isEmpty()) return s;
            }
            return "";
        }
        if (!(node instanceof bhbk)) return "";
        for (Field f : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            try {
                f.setAccessible(true);
                String s = art(f.get(node), d + 1, seen);
                if (!s.isEmpty()) return s;
            } catch (Throwable ignored) {}
        }
        if (node instanceof bhbg) {
            try {
                Iterator<?> it = ((bhbg) node).j.e();
                while (it.hasNext()) {
                    Object e = it.next();
                    if (e instanceof Map.Entry) {
                        String s = art(((Map.Entry<?, ?>) e).getValue(), d + 1, seen);
                        if (!s.isEmpty()) return s;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return "";
    }

    static void walk(Object node, int d, int[] budget, Set<Object> seen, List<String> order) {
        if (node == null || d > 48 || --budget[0] < 0 || !seen.add(node)) return;
        if (node instanceof List) {
            for (Object x : (List<?>) node) walk(x, d + 1, budget, seen, order);
            return;
        }
        if (!(node instanceof bhbk)) return;

        if (node instanceof bqtu) {
            try {
                bqtu row = (bqtu) node;
                Object ep = row.i == null ? null : row.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);
                if (ep instanceof bizw) {
                    String id = ((bizw) ep).c;
                    String title = label(row.e);
                    if (title.isEmpty()) title = label(row.g);
                    String sub = label(row.g) + " " + label(row.h);
                    String kind = id.startsWith("VL") ? "playlist" : id.startsWith("MPRE") ? "album" : id.startsWith("UC") ? "artist" : "item";
                    ultra.downloads.NativeIdentityRegistry.register(id, title, sub, kind);
                    ultra.downloads.PlaylistIndex.registerNative(id, title, art(row.c, 0, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>())));
                    if (id.startsWith("VL") && !title.isEmpty() && !order.contains(id.substring(2))) {
                        order.add(id.substring(2));
                    }
                }
            } catch (Throwable ignored) {}
            return;
        }

        for (Field f : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class) continue;
            try {
                f.setAccessible(true);
                Object x = f.get(node);
                if (x instanceof bhbk || x instanceof List) walk(x, d + 1, budget, seen, order);
            } catch (Throwable ignored) {}
        }
        if (node instanceof bhbg) {
            try {
                Iterator<?> it = ((bhbg) node).j.e();
                while (it.hasNext()) {
                    Object e = it.next();
                    if (e instanceof Map.Entry) walk(((Map.Entry<?, ?>) e).getValue(), d + 1, budget, seen, order);
                }
            } catch (Throwable ignored) {}
        }
    }
}
