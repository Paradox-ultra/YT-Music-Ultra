package ultra.downloads;
import java.lang.reflect.*;import java.util.*;
/** Unwrap reflection/future errors. Never persists a URL, authorization value or account response. */
public final class ResolveErrors {
 public static Throwable root(Throwable t){Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());for(int i=0;i<12&&t!=null&&seen.add(t);i++){if(t instanceof paradox.dl.Engine.FileFailure)return t;Throwable next=t instanceof InvocationTargetException?((InvocationTargetException)t).getTargetException():t.getCause();if(next==null||next==t)return t;t=next;}return t;}
 public static String describe(Throwable input){Throwable t=root(input);if(t==null)return "Unknown error";String m=t.getMessage();if(m==null||m.trim().isEmpty())return t.getClass().getSimpleName();m=m.replaceAll("(?i)https?://[^\\s\\\"']+","[url]").replaceAll("(?i)(authorization|bearer|cookie|visitor|token|signature|sparams|lsig|sig)([=: \\t]+)[^\\s,;]+","$1 [redacted]").replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");if(m.length()>360)m=m.substring(0,360);return t.getClass().getSimpleName()+": "+m;}
}
