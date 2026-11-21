package ultra.downloads;
import java.util.*;
public final class PinWireChecks {
 static int checks;
 static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static byte[] cat(byte[]... bs){java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();for(byte[] b:bs)o.write(b,0,b.length);return o.toByteArray();}
 static byte[] f(int n,byte[] b){return HeaderCommands.field(n,b);}
 static byte[] text(String s){return HeaderCommands.text(s);}
 static byte[] row(String id){return cat(f(5,f(0x2e6ea0a,f(2,text(id)))),f(60,cat(f(3,f(1,text("Auto playlist"))),f(3,f(9,f(1,LibraryPinOrder.PIN_ICON))))));}
 static int idx(byte[] b,String s){byte[] n=text(s);outer:for(int i=0;i<=b.length-n.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return i;}return -1;}
 public static void main(String[] args){
 byte[] lm=row("VLLM"),radio=row("FEmusic_ultra_radio"),other=row("VLPLtest");
 ok("LM".equals(LibraryPinOrder.rowKey(lm)),"LM identity");
 for(int d=0;d<12;d++){lm=f(123456+d,lm);radio=f(123456+d,radio);}
 ok("LM".equals(LibraryPinOrder.rowKey(lm)),"wrapped identity");
 Set<String> pins=new LinkedHashSet<>(Arrays.asList("ULTRA_RADIO"));
 byte[] in=cat(f(7,lm),f(7,radio),f(7,other));byte[] out=LibraryPinOrder.walk(in,pins,0,new int[]{24000});
 ok(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"),"radio first when LM unpinned");
 ok(idx(out,"VLPLtest")<idx(out,"VLLM"),"unpin loses reserved top position");
 ok(idx(out,"Auto playlist")>=0,"subtitle retained");
 // One badge remains on Radio. Neither LM nor the unpinned ordinary row keeps it.
 try{List<HeaderCommands.F> rows=HeaderCommands.fields(out);ok(!RadioWire.contains(rows.get(2).value,LibraryPinOrder.PIN_ICON),"LM badge removed");ok(RadioWire.contains(rows.get(0).value,LibraryPinOrder.PIN_ICON),"Radio badge kept");}catch(Exception e){throw new AssertionError(e);}
 pins.add("LM");out=LibraryPinOrder.walk(in,pins,0,new int[]{24000});ok(idx(out,"FEmusic_ultra_radio")<idx(out,"VLLM"),"radio first with LM pinned");
 byte[] singleton=cat(f(1,f(3,row("VLLM"))),f(1,text("UNRELATED_CONTENT")));out=LibraryPinOrder.walk(singleton,new HashSet<String>(),0,new int[]{24000});ok(Arrays.equals(singleton,out),"do not strip broad singleton containers");
 byte[] nativeBadge=f(15,f(180213378,f(3,HeaderCommands.iconNumber(274))));
 byte[] numericLm=cat(row("VLLM"),nativeBadge),numericRadio=cat(row("FEmusic_ultra_radio"),nativeBadge);
 ok(LibraryPinOrder.nativePinBadge(f(180213378,f(3,HeaderCommands.iconNumber(274)))),"numeric KEEP badge detected");
 out=LibraryPinOrder.walk(cat(f(7,numericLm),f(7,numericRadio),f(7,row("VLPLtest"))),new HashSet<String>(Arrays.asList("ULTRA_RADIO")),0,new int[]{24000});
 try{List<HeaderCommands.F> rows=HeaderCommands.fields(out);ok(!RadioWire.contains(rows.get(2).value,nativeBadge),"numeric LM badge stripped");ok(RadioWire.contains(rows.get(0).value,nativeBadge),"numeric Radio badge kept");}catch(Exception e){throw new AssertionError(e);}
 System.out.println("PASS "+checks+" pin wire checks");
 }
}
