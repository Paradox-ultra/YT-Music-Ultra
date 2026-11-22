package ultra.downloads;
import org.junit.*;import static org.junit.Assert.*;
/** Pure JVM tests: no Android, no host classes. */
public class OfferEvidenceTest {
 static OfferEvidence.Level of(String... items){OfferEvidence e=new OfferEvidence();for(String s:items)e.add(s);return e.level();}
 @Test public void exactOfferBothSpellings(){
  assertEquals(OfferEvidence.Level.EXACT,of("Download your favourite songs plus more with Premium","1-month trial","Not now"));
  assertEquals(OfferEvidence.Level.EXACT,of("Download your favorite songs plus more with Premium","Start free trial","NOT NOW"));
 }
 @Test public void whitespaceAndNbspAreNormalized(){assertEquals(OfferEvidence.Level.EXACT,of("Download your  favourite\nsongs plus more with Premium","1‑month trial","Not now"));}
 @Test public void unrelatedPremiumOffersAreNeverExact(){
  assertNotEquals(OfferEvidence.Level.EXACT,of("Listen to music ad-free with Premium","Start a trial","Not now"));
  assertNotEquals(OfferEvidence.Level.EXACT,of("Get Music Premium to download","1-month trial","Not now"));
  assertNotEquals(OfferEvidence.Level.EXACT,of("Download your favourite songs plus more with Premium","Get Premium"));
  assertNotEquals(OfferEvidence.Level.EXACT,of("Download your favourite songs plus more with Premium","Not now"));
  assertNotEquals(OfferEvidence.Level.EXACT,of("Download your favourite songs plus more with Premium","Trials and tribulations"," not nowhere"));
  assertEquals(OfferEvidence.Level.NONE,of("Settings","Not now"));
  assertEquals(OfferEvidence.Level.NONE,of());
 }
 @Test public void headingMustBeInOneItem(){assertEquals(OfferEvidence.Level.PARTIAL,of("Download your favourite songs","plus more with Premium","1-month trial","Not now"));}
 @Test public void truncatedCorpusIsNeverExact(){OfferEvidence e=new OfferEvidence();e.add("Download your favourite songs plus more with Premium");e.add("1-month trial");e.add("Not now");StringBuilder big=new StringBuilder();for(int i=0;i<400;i++)big.append('x');for(int i=0;i<40&&!e.truncated();i++)e.add(big);assertTrue(e.truncated());assertEquals(OfferEvidence.Level.PARTIAL,e.level());}
 @Test public void oversizedItemTruncates(){OfferEvidence e=new OfferEvidence();StringBuilder s=new StringBuilder("Download your favourite songs plus more with Premium 1-month trial Not now ");while(s.length()<=OfferEvidence.MAX_ITEM)s.append('y');assertFalse(e.add(s));assertEquals(OfferEvidence.Level.NONE,e.level());}
 @Test public void classNamesAreSanitized(){
  assertEquals("android.widget.TextView",OfferEvidence.safeClassName("android.widget.TextView"));
  assertEquals("xz$a",OfferEvidence.safeClassName("xz$a"));
  assertEquals("?",OfferEvidence.safeClassName("Not now"));
  assertEquals("?",OfferEvidence.safeClassName("a/b"));
  assertEquals("?",OfferEvidence.safeClassName(null));
  StringBuilder s=new StringBuilder();for(int i=0;i<121;i++)s.append('a');assertEquals("?",OfferEvidence.safeClassName(s.toString()));
 }
}
