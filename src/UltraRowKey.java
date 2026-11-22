import com.google.protobuf.ExtensionRegistryLite;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;

/** Parses a native Library row's playlist key in the default package, where the obfuscated host models live. */
public final class UltraRowKey {
    public static String of(byte[] row) {
        try {
            bqtu r = (bqtu) bhbk.parseFrom(bqtu.a, row, ExtensionRegistryLite.getGeneratedRegistry());
            Object ep = r.i == null ? null : r.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);
            if (ep instanceof bizw) return ultra.downloads.LibraryPinOrder.key(((bizw) ep).c);
        } catch (Throwable ignored) {}
        return null;
    }
}
