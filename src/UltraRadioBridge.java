import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
import com.google.protos.youtube.api.innertube.WatchEndpointOuterClass;
import com.google.protos.youtube.api.innertube.MusicThumbnailRendererOuterClass;
/** Native list-row model substitution, only while the exact Library landing page is visible. */
public final class UltraRadioBridge {
 static blwb text(String s){blwb t=(blwb)blwb.a.createBuilder().instance;t.b=1;t.d=s;return t;}
 public static bjue entry(){bizw e=(bizw)bizw.a.createBuilder().instance;e.b=1;e.c="FEmusic_ultra_radio";bhbf b=(bhbf)bjue.a.createBuilder();b.e(BrowseEndpointOuterClass.browseEndpoint,e);return (bjue)b.build();}
 public static boolean intercept(Object input){if(!(input instanceof bjue))return false;try{Object ep=((bjue)input).j.l(BrowseEndpointOuterClass.browseEndpoint.d);if(!(ep instanceof bizw)||!"FEmusic_ultra_radio".equals(((bizw)ep).c))return false;String seed=ultra.downloads.RadioContext.seed();bjue command=radio(seed);if(command==null){ultra.downloads.Hooks.message("Play a song, then tap Radio to start its mix");return true;}String mediaId=kud.f(command);ultra.downloads.NativePlayback.command(ultra.downloads.Hooks.context(),mediaId);return true;}catch(Throwable e){ultra.downloads.Diagnostics.error("radio_start",e);ultra.downloads.Hooks.message("Radio could not start. Play a song and retry.");return true;}}
 public static bjue radio(String seed){if(seed==null||!seed.matches("[A-Za-z0-9_-]{11}"))return null;
  // Exact WatchEndpoint fields and radio pattern observed in 8.30.54 Start radio server response.
  bwmb w=(bwmb)bwmb.a.createBuilder().instance;w.b=1|2|32;w.d=seed;w.e="RDAMVM"+seed;w.g="wAEB";
  bhbf command=(bhbf)bjue.a.createBuilder();command.e(WatchEndpointOuterClass.watchEndpoint,w);return (bjue)command.build();
 }
 static btdg art()throws Exception{android.content.Context c=ultra.downloads.Hooks.context();android.net.Uri uri=new android.net.Uri.Builder().scheme("android.resource").authority(c.getPackageName()).appendPath(String.valueOf(0x7f080933)).build();buzh thumb=azpz.j(uri);bqrx renderer=(bqrx)bqrx.a.createBuilder().instance;renderer.b=1;renderer.c=thumb;bhbf b=(bhbf)btdg.a.createBuilder();b.e(MusicThumbnailRendererOuterClass.musicThumbnailRenderer,renderer);return (btdg)b.build();}
 public static Object row(Object input){return input;}
 public static Object row(Object input,boolean inLibrary,String seed){if(!(input instanceof bqtu)||!inLibrary)return input;try{bqtu original=(bqtu)input;Object ep=original.i==null?null:original.i.j.l(BrowseEndpointOuterClass.browseEndpoint.d);if(!(ep instanceof bizw)||!"VLLM".equals(((bizw)ep).c))return input;bjue command=entry();
  bhbd builder=original.toBuilder();builder.copyOnWrite();bqtu next=(bqtu)builder.instance;next.i=command;next.e=text("");next.g=text("Radio");next.h=text("Based on your last played song");next.k=bjue.a;next.l=btdg.a;next.m=btdg.a;next.p=btdg.a;next.q=bqtq.a;next.b=(next.b|1|4|16|64)&~(32|128|256|512|8192|16384);
  try{next.c=art();}catch(Throwable e){ultra.downloads.Diagnostics.error("radio_art",e);return input;}
  ultra.downloads.Diagnostics.event("library_radio","native-seeded-mix");return builder.build();
 }catch(Throwable e){ultra.downloads.Diagnostics.error("library_radio",e);return input;}}
}
