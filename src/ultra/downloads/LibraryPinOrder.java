package ultra.downloads;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Applies local Library pin state to native rows before presentation: pinned rows first in stable order, and the native pin badge is stripped from rows the user unpinned locally. */
public final class LibraryPinOrder {
    public static final byte[] PIN_ICON = HeaderCommands.text("yt_outline_pin_vd_theme_24");
    private static final int ROW_PARSE_LIMIT = 16384;

    public static String key(String id) {
        if (id == null) return null;
        if ("FEmusic_ultra_radio".equals(id)) return "ULTRA_RADIO";
        if (id.startsWith("MPLAUC")) id = id.substring(4);
        if (id.startsWith("VL")) id = id.substring(2);
        if (id.startsWith("PL") || id.startsWith("UC") || id.startsWith("MPRE") || id.startsWith("RD") || "LM".equals(id)) return id;
        return null;
    }

    // Elements wraps native row renderers. Inspect their browse endpoints rather than
    // assuming the repeated field directly contains the legacy bqtu renderer.
    static final int WORK_NODES = 12000, WORK_BYTES = 16 * 1024 * 1024;
    static boolean spend(int[] budget, int bytes) {
        if (--budget[0] < 0) return false;
        if (budget.length > 1 && (budget[1] -= bytes) < 0) { budget[0] = -1; return false; }
        return true;
    }
    static String rowKey(byte[] row) { return rowKey(row, new int[]{3000, WORK_BYTES}); }
    static String rowKey(byte[] row, int[] budget) {
        if (row == null || row.length > ROW_PARSE_LIMIT) return null;
        Set<String> found = new LinkedHashSet<>();
        rowKeys(row, found, 0, budget);
        return budget[0] >= 0 && found.size() == 1 ? found.iterator().next() : null;
    }
    static void rowKeys(byte[] row, Set<String> found, int depth, int[] budget) {
        rowKeysSlice(row, 0, row.length, found, depth, budget);
    }
    // Read wire slices without copying every candidate subtree. One shared budget
    // covers the entire response, including all repeated row-identity probes.
    static long sliceVar(byte[] bytes, int[] cursor, int end) {
        long value=0;
        for(int shift=0;shift<64;shift+=7){if(cursor[0]>=end)throw new IllegalArgumentException();int x=bytes[cursor[0]++]&255;value|=(long)(x&127)<<shift;if(x<128)return value;}
        throw new IllegalArgumentException();
    }
    static void rowKeysSlice(byte[] bytes,int start,int end,Set<String> found,int depth,int[] budget) {
        if(depth>32 || found.size()>1 || !spend(budget,end-start))return;
        int[] cursor={start};
        try {
            while(cursor[0]<end && budget[0]>=0 && found.size()<=1) {
                long tag=sliceVar(bytes,cursor,end);int field=(int)(tag>>>3),wire=(int)tag&7;
                if(field<1) return;
                if(wire==0)sliceVar(bytes,cursor,end);
                else if(wire==1||wire==5){cursor[0]+=wire==1?8:4;if(cursor[0]>end)return;}
                else if(wire==2){
                    long length=sliceVar(bytes,cursor,end);if(length<0||length>end-cursor[0])return;
                    int from=cursor[0],to=from+(int)length;cursor[0]=to;
                    if(field==0x2e6ea0a){
                        int[] ep={from};String candidate=null;boolean duplicate=false;
                        while(ep[0]<to){long et=sliceVar(bytes,ep,to);int ef=(int)(et>>>3),ew=(int)et&7;
                            if(ef<1)break;
                            if(ew==0)sliceVar(bytes,ep,to);
                            else if(ew==1||ew==5){ep[0]+=ew==1?8:4;if(ep[0]>to)break;}
                            else if(ew==2){long el=sliceVar(bytes,ep,to);if(el<0||el>to-ep[0])break;if(ef==2){if(candidate!=null)duplicate=true;candidate=new String(bytes,ep[0],(int)el,java.nio.charset.StandardCharsets.UTF_8);}ep[0]+=(int)el;}
                            else break;
                        }
                        if(!duplicate && ep[0]==to){String k=key(candidate);if(k!=null)found.add(k);}
                    } else rowKeysSlice(bytes,from,to,found,depth+1,budget);
                } else return;
            }
        } catch(IllegalArgumentException ignored) {}
    }

    static final class Cache {
        final byte[] input,output;final String pins;
        Cache(byte[] i,byte[] o,String p){input=i;output=o;pins=p;}
    }
    private static volatile Cache cache;

    // No class monitor may cover parsing: native constructors also call this on
    // the UI thread while a background response is being transformed.
    public static byte[] reorder(byte[] response, Context c) {
        if (response == null || response.length > HeaderCommands.MAX) return response;
        Set<String> pinned;
        try { pinned = new LinkedHashSet<>(PinManager.getPinnedList(c)); } catch (Throwable t) { return response; }
        String pinSig = pinned.toString();
        Cache hit=cache;
        if(hit!=null && pinSig.equals(hit.pins) && java.util.Arrays.equals(response,hit.input))return hit.output;
        int[] budget = {WORK_NODES, WORK_BYTES};
        byte[] out = walk(response, pinned, 0, budget);
        byte[] result = budget[0] < 0 ? response : out;
        cache=new Cache(response,result,pinSig);
        return result;
    }

    static byte[] walk(byte[] b, Set<String> pinned, int depth, int[] budget) {
        if (depth > HeaderCommands.DEPTH || !spend(budget,b.length)) return b;
        List<HeaderCommands.F> fs;
        try { fs = HeaderCommands.fields(b); } catch (Exception e) { return b; }
        boolean changed = false;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < fs.size()) {
            HeaderCommands.F f = fs.get(i);
            if (f.w == 2) {
                int j = i;
                List<HeaderCommands.F> group = new ArrayList<>();
                while (j < fs.size() && fs.get(j).w == 2 && fs.get(j).n == f.n) { group.add(fs.get(j)); j++; }
                if (group.size() >= 2) {
                    String[] keys = new String[group.size()];
                    int resolvable = 0, pinnedCount = 0;
                    for (int k = 0; k < group.size(); k++) {
                        keys[k] = rowKey(group.get(k).value,budget);
                        if (keys[k] != null) { resolvable++; if (pinned.contains(keys[k])) pinnedCount++; }
                    }
                    if (resolvable >= 2 && pinnedCount >= 1) {
                        List<Integer> order = new ArrayList<>();
                        for (int k = 0; k < group.size(); k++) if ("ULTRA_RADIO".equals(keys[k])) order.add(k);
                        for (int k = 0; k < group.size(); k++) if (keys[k] != null && !"ULTRA_RADIO".equals(keys[k]) && pinned.contains(keys[k])) order.add(k);
                        for (int k = 0; k < group.size(); k++) if ((keys[k] == null || !pinned.contains(keys[k])) && !("LM".equals(keys[k]) && !pinned.contains("LM"))) order.add(k);
                        if(!pinned.contains("LM"))for(int k=0;k<group.size();k++)if("LM".equals(keys[k])){int at=order.size();for(int n=order.size()-1;n>=0;n--){String id=keys[order.get(n)];if(id!=null&&id.startsWith("PL")){at=n+1;break;}}order.add(at,k);}
                        for (int k = 0; k < order.size(); k++) if (order.get(k) != k) { changed = true; break; }
                        for (int k : order) {
                            byte[] raw = group.get(k).raw;
                            if (keys[k] != null && !pinned.contains(keys[k])) {
                                byte[] stripped = stripBadge(group.get(k).value, 0, budget);
                                if (!java.util.Arrays.equals(stripped, group.get(k).value)) { raw = HeaderCommands.field(group.get(k).n, stripped); changed = true; }
                            }
                            out.write(raw, 0, raw.length);
                        }
                        i = j;
                        continue;
                    }
                }
            }
            byte[] raw = f.raw;
            if (f.w == 2) {
                byte[] next = walk(f.value, pinned, depth + 1, budget);
                if (next != f.value) { raw = HeaderCommands.field(f.n, next); changed = true; }
            }
            out.write(raw, 0, raw.length);
            i++;
        }
        return changed ? out.toByteArray() : b;
    }

    static boolean nativePinBadge(byte[] b) { try { byte[] icon=HeaderCommands.path(b,180213378,3);for(HeaderCommands.F f:HeaderCommands.fields(icon))if(f.n==1&&f.w==0&&f.number==274)return true; } catch(Exception ignored){}return false; }

    static boolean isBadge(byte[] b) {
        return b.length <= 512 && (RadioWire.contains(b, PIN_ICON)||nativePinBadge(b)) && rowKey(b) == null;
    }

    static byte[] stripBadge(byte[] b, int depth, int[] budget) {
        if (depth > HeaderCommands.DEPTH || !spend(budget,b.length)) return b;
        List<HeaderCommands.F> fs;
        try { fs = HeaderCommands.fields(b); } catch (Exception e) { return b; }
        boolean changed = false;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < fs.size()) {
            HeaderCommands.F f = fs.get(i);
            if(f.w==2&&nativePinBadge(f.value)){changed=true;i++;continue;}
            if (f.w == 2) {
                int j = i;
                List<HeaderCommands.F> group = new ArrayList<>();
                while (j < fs.size() && fs.get(j).w == 2 && fs.get(j).n == f.n) { group.add(fs.get(j)); j++; }
                if (group.size() >= 2) {
                    boolean hasPin = false, hasOther = false;
                    for (HeaderCommands.F g : group) {
                        if (isBadge(g.value)) hasPin = true; else hasOther = true;
                    }
                    if (hasPin && hasOther) {
                        changed = true;
                        for (HeaderCommands.F g : group) {
                            if (isBadge(g.value)) continue;
                            byte[] raw = g.raw;
                            byte[] next = stripBadge(g.value, depth + 1, budget);
                            if (next != g.value) raw = HeaderCommands.field(g.n, next);
                            out.write(raw, 0, raw.length);
                        }
                        i = j;
                        continue;
                    }
                }
                byte[] raw = f.raw;
                byte[] next = stripBadge(f.value, depth + 1, budget);
                if (next != f.value) { raw = HeaderCommands.field(f.n, next); changed = true; }
                out.write(raw, 0, raw.length);
                i++;
            } else {
                out.write(f.raw, 0, f.raw.length);
                i++;
            }
        }
        return changed ? out.toByteArray() : b;
    }
}
