package ultra.downloads;
import org.junit.*;import static org.junit.Assert.*;import java.util.*;
public class HeaderWatchVariantTest {
 byte[] f(int n,byte[] b){return HeaderCommands.field(n,b);}byte[] t(String s){return HeaderCommands.text(s);}byte[] cat(byte[]...bs){java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();for(byte[] b:bs)o.write(b,0,b.length);return o.toByteArray();}
 @Test public void exactWatchShuffleFromNativeMenuReplacesHeaderOnly(){
 byte[] command=f(48687757,cat(f(1,t("abcdefghijk")),f(2,t("PLtest")),f(5,t("wAEB8gECKAE%3D"))));
 byte[] menu=cat(f(2,HeaderCommands.iconNumber(194)),f(3,command));
 byte[] share=cat(f(1,HeaderCommands.SHARE),f(6,f(169495254,f(999,t("share")))),f(7,t("Share")));
 byte[] header=cat(f(1,t("PLtest")),f(7,share),f(8,menu));byte[] result=HeaderCommands.shuffle(header,header);
 assertFalse(Arrays.equals(header,result));assertArrayEquals(command,HeaderCommands.path(result,7,6,169495254));assertArrayEquals(HeaderCommands.SHUFFLE,HeaderCommands.path(result,7,1));
 }
 @Test public void otherPlaylistWatchCommandCannotReplaceShare(){byte[] c=f(48687757,cat(f(1,t("abcdefghijk")),f(2,t("PLother")),f(5,t("shuffle"))));byte[] h=cat(f(1,t("PLtest")),f(7,cat(f(1,HeaderCommands.SHARE),f(6,t("x")),f(7,t("Share")))));assertSame(h,HeaderCommands.shuffle(h,cat(f(2,HeaderCommands.iconNumber(194)),f(3,c))));}
}
