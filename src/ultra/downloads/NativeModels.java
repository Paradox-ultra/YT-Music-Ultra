package ultra.downloads;

import java.lang.reflect.*;
import android.content.Context;

public final class NativeModels {
    private NativeModels() {}

    static Object fresh(String name) throws Exception {
        Constructor<?> ctor = Class.forName(name).getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    static void set(Object o, String n, Object v) throws Exception {
        o.getClass().getField(n).set(o, v);
    }

    static Object command(String video, String playlist) throws Exception {
        String cls = "com.google.protos.youtube.api.innertube.Offline" + (video != null ? "Video" : "Playlist") + "EndpointOuterClass$Offline" + (video != null ? "Video" : "Playlist") + "Endpoint";
        Object endpoint = fresh(cls);
        if (video != null) {
            set(endpoint, "b", 1);
            set(endpoint, "c", 1);
            set(endpoint, "d", video);
            set(endpoint, "e", 1);
        } else {
            set(endpoint, "b", 3);
            set(endpoint, "c", playlist);
            set(endpoint, "d", 1);
        }
        Object extension = Class.forName(cls).getField(video != null ? "offlineVideoEndpoint" : "offlinePlaylistEndpoint").get(null);
        Object base = Class.forName("bjue").getField("a").get(null);
        Object builder = base.getClass().getMethod("createBuilder").invoke(base);
        Class.forName("bhbf").getMethod("e", Class.forName("bhar"), Object.class).invoke(builder, extension, endpoint);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    static Object pinCommand(String playlist) throws Exception {
        Object endpoint = fresh("bizw");
        set(endpoint, "b", 1);
        set(endpoint, "c", "ultra_pin:" + playlist);
        Object extension = Class.forName("com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass").getField("browseEndpoint").get(null);
        Object base = Class.forName("bjue").getField("a").get(null);
        Object builder = base.getClass().getMethod("createBuilder").invoke(base);
        Class.forName("bhbf").getMethod("e", Class.forName("bhar"), Object.class).invoke(builder, extension, endpoint);
        return builder.getClass().getMethod("build").invoke(builder);
    }

    public static Object rewriteUpsell(Object item, Object model, Object helper) {
        try {
            String video = (String) helper.getClass().getMethod("k", Object.class).invoke(helper, model);
            String playlist = (String) helper.getClass().getMethod("m", Object.class).invoke(helper, model);
            if (video == null || !video.matches("[A-Za-z0-9_-]{11}")) video = null;
            if (playlist == null || !playlist.matches("[A-Za-z0-9_-]{2,200}")) playlist = null;
            if (video == null && playlist == null) return item;
            return service(video, video == null ? playlist : null);
        } catch (Throwable e) {
            System.err.println("Native download menu: " + e.getClass().getSimpleName());
            return item;
        }
    }

    public static Object createPinMenuItem(Context context, String playlist) {
        try {
            if (playlist == null || !playlist.matches("[A-Za-z0-9_-]{2,200}")) return null;
            boolean isPinned = PinManager.isPinned(context, playlist);
            String label = isPinned ? "Unpin from Library" : "Pin to Library";
            Object service = fresh("bpnn"), root = fresh("bpnf"), icon = fresh("bmlk");
            set(icon, "b", 1);
            set(icon, "c", Class.forName("bmlj").getField("xl").getInt(Class.forName("bmlj").getField("D").get(null)));
            Object text = fresh("blwb");
            set(text, "b", 1);
            set(text, "d", label);
            set(service, "b", 1 | 8 | 64);
            set(service, "c", text);
            set(service, "d", icon);
            set(service, "e", pinCommand(playlist));
            set(root, "b", 2);
            set(root, "d", service);
            return root;
        } catch (Throwable e) {
            return null;
        }
    }

    static Object service(String video, String playlist) throws Exception {
        Object service = fresh("bpnn"), root = fresh("bpnf"), icon = fresh("bmlk");
        set(icon, "b", 1);
        set(icon, "c", Class.forName("bmlj").getField("xl").getInt(Class.forName("bmlj").getField("o").get(null)));
        Object text = fresh("blwb");
        set(text, "b", 1);
        set(text, "d", "Download");
        set(service, "b", 1 | 8 | 64);
        set(service, "c", text);
        set(service, "d", icon);
        set(service, "e", command(video, video == null ? playlist : null));
        set(root, "b", 2);
        set(root, "d", service);
        return root;
    }
}
