import com.google.protobuf.ExtensionRegistryLite;

/** Changes native models before the original native presenters see them. */
public final class UltraBrowseBridge {
    public static Object transform(Object input) {
        Object out = transformInner(input);
        try {
            if (out instanceof bmre) {
                android.content.Context c = ultra.downloads.Hooks.context();
                byte[] before = ((bmre) out).toByteArray();
                byte[] after = ultra.downloads.LibraryPinOrder.reorder(before, c);
                if (after != before && !java.util.Arrays.equals(before, after)) {
                    out = bhbk.parseFrom(bmre.a, after, ExtensionRegistryLite.getGeneratedRegistry());
                    ultra.downloads.Diagnostics.event("library_pins", "native-order-applied");
                }
            }
        } catch (Throwable e) {
            ultra.downloads.Diagnostics.error("library_pins", e);
        }
        return out;
    }

    static Object transformInner(Object input) {
        if (!(input instanceof bmre)) return input;
        bmre original = (bmre) input;
        try {
            byte[] originalBytes = original.toByteArray();
            byte[] cleaned = UltraLibraryClean.clean(originalBytes);
            if (cleaned != originalBytes) {
                original = (bmre) bhbk.parseFrom(bmre.a, cleaned, ExtensionRegistryLite.getGeneratedRegistry());
            }

            ultra.downloads.NativeSnapshot.playlistHeaders(original.toByteArray());
            bjue radio = UltraRadioBridge.entry();
            if (radio != null) {
                byte[] before = original.toByteArray();
                byte[] after = ultra.downloads.RadioWire.transform(before, BrowseExtension.id(), radio.toByteArray());
                if (after != before && !java.util.Arrays.equals(before, after)) {
                    UltraLibraryCapture.accept(original);
                    original = (bmre) bhbk.parseFrom(bmre.a, after, ExtensionRegistryLite.getGeneratedRegistry());
                    ultra.downloads.Diagnostics.event("library_radio", "generic-native-row-bound");
                }
            }
            if (original.d == null) return original;
            byte[] before = original.d.toByteArray();
            byte[] after = ultra.downloads.HeaderCommands.shuffle(before, original.toByteArray());
            if (after == before || java.util.Arrays.equals(before, after)) return original;
            bmqs h = (bmqs) bhbk.parseFrom(bmqs.a, after, ExtensionRegistryLite.getGeneratedRegistry());
            bhbd rb = original.toBuilder();
            rb.copyOnWrite();
            ((bmre) rb.instance).d = h;
            ultra.downloads.NativeShuffle.modelBound();
            ultra.downloads.Diagnostics.event("playlist_shuffle", "native-header-command-bound");
            return rb.build();
        } catch (Throwable e) {
            ultra.downloads.Diagnostics.error("browse_commands", e);
            return original;
        }
    }

    static final class BrowseExtension {
        static int id() {
            return 0x2e6ea0a;
        }
    }
}
