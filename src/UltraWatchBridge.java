import com.google.protos.youtube.api.innertube.WatchEndpointOuterClass;

/**
 * DL56: play a YouTube Music video id in the host's own player, the same route Radio uses (UltraRadioBridge): build the
 * host's WatchEndpoint command, turn it into the host's media id (kud.f) and ask the host's MusicBrowserService to play
 * it (NativePlayback.command -> MediaController.playFromMediaId). Only the video id is set (no playlist, no params),
 * i.e. a plain "watch?v=<id>". Version-bound direct host calls, like the other bridges.
 */
public final class UltraWatchBridge {
 /** True when the host accepted the command into its player route; false for an invalid id or a host failure. */
 public static boolean play(String videoId){
  if(videoId==null||!videoId.matches("[A-Za-z0-9_-]{11}"))return false;
  try{
   bwmb w=(bwmb)bwmb.a.createBuilder().instance;w.b=1;w.d=videoId;
   bhbf command=(bhbf)bjue.a.createBuilder();command.e(WatchEndpointOuterClass.watchEndpoint,w);
   String mediaId=kud.f((bjue)command.build());if(mediaId==null||mediaId.isEmpty())return false;
   ultra.downloads.NativePlayback.command(ultra.downloads.Hooks.context(),mediaId);
   return true;
  }catch(Throwable e){return false;}// DL57: no Diagnostics here (its lock is shared with the main thread)
 }
}
