package ultra.downloads;
import java.util.Locale;
/**
 * Pure (Android-free) classifier for the offline-download Premium offer.
 *
 * EXACT requires all of:
 *  - the exact offline-download heading inside ONE text item (never stitched across items),
 *  - a "not now" action and a "trial" word somewhere in the same dialog,
 *  - an untruncated, bounded text corpus.
 * Anything weaker is at most PARTIAL, which callers must treat as diagnostic only.
 * Collected text is never logged or persisted; only the resulting level is.
 */
public final class OfferEvidence {
    public enum Level { NONE, PARTIAL, EXACT }
    static final String[] HEADINGS={
        "download your favourite songs plus more with premium",
        "download your favorite songs plus more with premium"};
    public static final int MAX_CHARS=12000, MAX_ITEM=512, MAX_ITEMS=400;
    private final StringBuilder corpus=new StringBuilder();
    private boolean heading, truncated;
    private int items;

    /** Adds one visible text item. Returns false once the corpus bound is reached. */
    public boolean add(CharSequence raw){
        if(raw==null||raw.length()==0)return !truncated;
        if(truncated)return false;
        if(raw.length()>MAX_ITEM||++items>MAX_ITEMS||corpus.length()+raw.length()+1>MAX_CHARS){truncated=true;return false;}
        String s=normalize(raw);
        for(String h:HEADINGS)if(s.contains(h))heading=true;
        corpus.append(s).append(' ');
        return true;
    }
    public boolean truncated(){return truncated;}
    public Level level(){
        String s=corpus.toString();
        boolean notNow=s.contains("not now"), trial=word(s,"trial"), premium=s.contains("premium");
        if(heading&&notNow&&trial&&!truncated)return Level.EXACT;
        if(heading||(premium&&(notNow||trial)))return Level.PARTIAL;
        return Level.NONE;
    }
    static boolean word(String s,String w){int i=s.indexOf(w);while(i>=0){boolean before=i==0||!Character.isLetter(s.charAt(i-1)),after=i+w.length()>=s.length()||!Character.isLetter(s.charAt(i+w.length()));if(before&&after)return true;i=s.indexOf(w,i+1);}return false;}
    public static String normalize(CharSequence raw){
        StringBuilder b=new StringBuilder(raw.length());boolean space=false;
        for(int i=0;i<raw.length();i++){char c=raw.charAt(i);
            if(Character.isWhitespace(c)||Character.isSpaceChar(c)){if(!space&&b.length()>0)b.append(' ');space=true;continue;}
            if(c=='’'||c=='‘')c='\'';
            b.append(c);space=false;}
        int n=b.length();if(n>0&&b.charAt(n-1)==' ')b.setLength(n-1);
        return b.toString().toLowerCase(Locale.US);
    }
    /** Returns a Java class name if it is a plain identifier path, otherwise "?". Never returns free text. */
    public static String safeClassName(String name){
        if(name==null||name.isEmpty()||name.length()>120)return "?";
        for(int i=0;i<name.length();i++){char c=name.charAt(i);if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='$'||c=='.'))return "?";}
        return name;
    }
}
