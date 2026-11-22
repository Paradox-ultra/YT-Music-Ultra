package ultra.downloads;
import android.content.Context;import java.io.*;
/** App-private schema evidence only; no response bytes, primitive values, strings or credentials. */
public final class LibraryEvidence {
 static final String FILE="ultra-library-schema.txt";static volatile String latest="Library schema has not been captured yet. Open Library once.";
 public static void store(String safe){if(safe==null||safe.length()>12000||!safe.matches("[A-Za-z0-9_.$:\\[\\]\\n-]+"))return;latest=safe;try{Context c=Hooks.context();if(c==null)return;try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),FILE))){out.write(safe.getBytes("UTF-8"));}Diagnostics.event("library_schema",safe.contains("status:captured")?"captured":"no-matching-command");}catch(Throwable e){Diagnostics.error("library_schema",e);}}
 static String read(Context c){try{File f=new File(c.getFilesDir(),FILE);if(f.length()>12000)return latest;ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1024];int n;while((n=in.read(b))>0){if(out.size()+n>12000)return latest;out.write(b,0,n);}}String text=new String(out.toByteArray(),"UTF-8");return text.matches("[A-Za-z0-9_.$:\\[\\]\\n-]+")?text:latest;}catch(Throwable e){return latest;}}
}
