package ultra.downloads;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
/** Protobuf length-delimited field writer shared by snapshot header tests (recreated helper). */
public class NativeSnapshotTest {
    static void varint(ByteArrayOutputStream out,long v){while((v&~0x7FL)!=0){out.write((int)((v&0x7F)|0x80));v>>>=7;}out.write((int)v);}
    static void field(ByteArrayOutputStream out,int n,byte[] value){varint(out,((long)n<<3)|2);varint(out,value.length);out.write(value,0,value.length);}
    @Test public void fieldWriterEncodesTagLengthAndBytes(){ByteArrayOutputStream o=new ByteArrayOutputStream();field(o,1,"ab".getBytes());byte[] b=o.toByteArray();assertArrayEquals(new byte[]{0x0A,0x02,'a','b'},b);}
}
