import com.google.protos.youtube.api.innertube.OfflineVideoEndpointOuterClass$OfflineVideoEndpoint;
import com.google.protos.youtube.api.innertube.OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint;
import ultra.downloads.OfflineCommands;
import com.google.protos.youtube.api.innertube.BrowseEndpointOuterClass;
/** Version-bound bridge using direct host calls, not reflection or guessed model helpers. */
public final class UltraDownloadBridge {
    /** Plain native wording. Identity no longer relies on a visible marker; see {@link #independent}. */
    public static final String LABEL="Download";
    /** Rows built by {@link #service}. Weak keys; value-equal copies of a built row also match. */
    static final java.util.Set<Object> built=java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object,Boolean>()));
    public static Object menu(Object input) {
        if (!(input instanceof bpnf)) return input;
        bpnf original=(bpnf)input;
        try {
            String[] target=OfflineCommands.selection(original.toByteArray());
            if(target==null)return input;
            ultra.downloads.Diagnostics.event("menu_transform",target[0]!=null?"song":"playlist");return service(target[0],target[1]);
        } catch(Throwable error) {OfflineCommands.failure(error);return input;}
    }
    public static bpnf service(String video,String playlist) {
        bjue command=command(video,playlist);
        blwb title=(blwb)blwb.a.createBuilder().instance;
        title.b=1;title.d=LABEL;
        bmlk icon=(bmlk)bmlk.a.createBuilder().instance;
        icon.b=1;icon.c=bmlj.o.xl;
        bpnn row=(bpnn)bpnn.a.createBuilder().instance;
        row.b=73;row.c=title;row.d=icon;row.e=command;
        bpnf root=(bpnf)bpnf.a.createBuilder().instance;
        root.b=2;root.d=row;
        built.add(root);
        return root;
    }
    static bjue command(String video,String playlist){
        bhbf builder=(bhbf)bjue.a.createBuilder();
        if(video!=null){
            OfflineVideoEndpointOuterClass$OfflineVideoEndpoint endpoint=(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint)OfflineVideoEndpointOuterClass$OfflineVideoEndpoint.a.createBuilder().instance;
            endpoint.b=1;endpoint.c=1;endpoint.d=video;endpoint.e=1;
            builder.e(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint.offlineVideoEndpoint,endpoint);
        }else{
            OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint endpoint=(OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint)OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint.a.createBuilder().instance;
            endpoint.b=3;endpoint.c=playlist;endpoint.d=1;
            builder.e(OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint.offlinePlaylistEndpoint,endpoint);
        }
        return (bjue)builder.build();
    }
    public static boolean independent(Object item){
        if(!(item instanceof bpnf))return false;
        bpnf root=(bpnf)item;
        if(!(root.b==2&&root.d!=null&&root.d.b==73&&root.d.c!=null&&LABEL.equals(root.d.c.d)))return false;
        // The label is now the plain word, so the shape alone no longer proves the row is ours: it must also be a row
        // we built, or carry exactly the offline endpoint signature that command() writes.
        return built.contains(root)||ownCommand(root.d.e);
    }
    static boolean ownCommand(bjue cmd){
        if(cmd==null)return false;
        try{
            if(cmd.j!=null){
                Object video=cmd.j.l(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint.offlineVideoEndpoint.d);
                if(video instanceof OfflineVideoEndpointOuterClass$OfflineVideoEndpoint){OfflineVideoEndpointOuterClass$OfflineVideoEndpoint e=(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint)video;Object id=e.d;
                    return e.b==1&&e.c==1&&e.e==1&&id instanceof String&&((String)id).matches("[A-Za-z0-9_-]{11}");}
                Object playlist=cmd.j.l(OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint.offlinePlaylistEndpoint.d);
                if(playlist instanceof OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint){OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint e=(OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint)playlist;Object id=e.c;
                    return e.b==3&&e.d==1&&id instanceof String&&((String)id).matches("[A-Za-z0-9_-]{2,200}");}
            }
        }catch(Throwable ignored){}
        // A row re-parsed without the extension registry keeps the endpoint as unknown-field bytes, so the extension
        // lookup above finds nothing. Fall back to an exact wire check: the command must be byte-identical to what
        // command() writes for one of the ids it carries. No structural guess about field numbers is made here.
        return ownWire(cmd);
    }
    static boolean ownWire(bjue cmd){
        try{
            byte[] wire=cmd.toByteArray();
            if(wire.length==0||wire.length>1024)return false;
            for(String id:OfflineCommands.idStrings(wire)){
                if(id.matches("[A-Za-z0-9_-]{11}")&&java.util.Arrays.equals(wire,command(id,null).toByteArray()))return true;
                if(id.matches("[A-Za-z0-9_-]{2,200}")&&java.util.Arrays.equals(wire,command(null,id).toByteArray()))return true;
            }
        }catch(Throwable ignored){}
        return false;
    }
    public static void bind(final android.view.View row,android.widget.FrameLayout mount,final Object input){
        final bpnf item=(bpnf)input;ultra.downloads.Diagnostics.event("native_row","bound");
        android.widget.TextView title=(android.widget.TextView)row.findViewById(0x7f0b0c3c);
        title.setText(LABEL);title.setTextColor(ultra.downloads.NativeUi.color(row.getContext(),"ytm_text_color_primary",android.graphics.Color.WHITE));
        android.view.View spinner=row.findViewById(0x7f0b06a3);if(spinner!=null)spinner.setVisibility(android.view.View.GONE);
        android.widget.ImageView icon=(android.widget.ImageView)row.findViewById(0x7f0b057e);
        if(icon!=null){icon.setVisibility(android.view.View.VISIBLE);
            // Native download icon by name first; the old numeric id and the framework icon are fallbacks only.
            if(ultra.downloads.NativeUi.res(row.getContext(),"yt_outline_download_vd_theme_24","drawable")!=0)icon.setImageDrawable(ultra.downloads.NativeUi.drawable(row.getContext(),"yt_outline_download_vd_theme_24","download",null));
            else{try{icon.setImageResource(0x7f08095c);}catch(android.content.res.Resources.NotFoundException e){icon.setImageResource(android.R.drawable.stat_sys_download);}icon.setColorFilter(android.graphics.Color.WHITE);}}
        row.setEnabled(true);row.setClickable(true);
        row.setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){click(item);}});
        if(row.getParent()!=mount){if(row.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)row.getParent()).removeView(row);mount.removeAllViews();mount.addView(row);}
    }
    public static void click(Object input){ultra.downloads.Diagnostics.event("native_row","click");
        try{bpnf item=(bpnf)input;bjue cmd=item.d.e;Object video=cmd.j.l(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint.offlineVideoEndpoint.d);
            if(video instanceof OfflineVideoEndpointOuterClass$OfflineVideoEndpoint){OfflineVideoEndpointOuterClass$OfflineVideoEndpoint endpoint=(OfflineVideoEndpointOuterClass$OfflineVideoEndpoint)video;if(endpoint.c==1)ultra.downloads.Hooks.download((String)endpoint.d);else throw new IllegalArgumentException("Not a song");return;}
            Object playlist=cmd.j.l(OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint.offlinePlaylistEndpoint.d);
            if(playlist instanceof OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint)ultra.downloads.NativeIntegration.open(((OfflinePlaylistEndpointOuterClass$OfflinePlaylistEndpoint)playlist).c);
            else throw new IllegalArgumentException("No selected song or playlist");
        }catch(Throwable error){OfflineCommands.failure(error);}
    }
    /** Returns before any native router or sheet renderer can launch an offline offer. */
    public static boolean intercept(Object input){return interceptAt(input,"router");}
    public static boolean interceptAt(Object input,String hook){
        if(UltraRadioBridge.intercept(input))return true;
        if(!(input instanceof bjue))return false;
        try{
            bjue cmd=(bjue)input;
            if(cmd.j!=null){
                Object ep=cmd.j.l(BrowseEndpointOuterClass.browseEndpoint.d);
                if(ep instanceof bizw){
                    String id=((bizw)ep).c;
                    if(id!=null&&id.startsWith("ultra_pin:")){
                        String playlistId=id.substring(10);
                        android.content.Context c=ultra.downloads.Hooks.context();
                        if(c!=null){
                            boolean pinned=ultra.downloads.PinManager.togglePin(c,playlistId);
                            ultra.downloads.Hooks.message(pinned?"Pinned to Library":"Unpinned from Library");
                            ultra.downloads.NativeLibrarySelection.notifyAdapterChanged();
                        }
                        return true;
                    }
                }
            }
        }catch(Throwable ignored){}
        byte[] wire=null;
        try{wire=((bjue)input).toByteArray();String[] target=OfflineCommands.selection(wire);if(target!=null){ultra.downloads.Diagnostics.event(hook,target[0]!=null?"song":"playlist");if(target[0]!=null)ultra.downloads.Hooks.download(target[0]);else ultra.downloads.NativeIntegration.open(target[1]);return true;}
            if(OfflineCommands.isOfflineSheet(wire)||OfflineCommands.hasOfflineRequest(wire)){ultra.downloads.Diagnostics.event(hook,"offline-sheet");OfflineCommands.sheetFallback();return true;}
            return false;
        }catch(Throwable error){OfflineCommands.failure(error);if(OfflineCommands.hasOfflineRequest(wire)){OfflineCommands.sheetFallback();return true;}return false;}
    }
    public static boolean blockSheet(Object input){return blockSheetAt(input,"sheet");}
    public static boolean blockSheetAt(Object input,String hook){
        if(!(input instanceof bhbk))return false;
        try{if(!OfflineCommands.isOfflineSheet(((bhbk)input).toByteArray()))return false;ultra.downloads.Diagnostics.event(hook,"blocked");OfflineCommands.sheetFallback();return true;}
        catch(Throwable error){OfflineCommands.failure(error);return false;}
    }
    static final java.util.Map<Object,Object> pageResponses=new java.util.WeakHashMap<>();
    static final java.util.concurrent.ExecutorService snapshots=java.util.concurrent.Executors.newSingleThreadExecutor();
    public static void capturePage(Object input){
        if(!(input instanceof jde))return;
        jde page=(jde)input;kiu model=page.s;if(model==null||!(model.g instanceof amra))return;
        final amra loaded=(amra)model.g;
        synchronized(pageResponses){if(pageResponses.get(page)==loaded)return;pageResponses.put(page,loaded);}
        final String id=model.b();
        snapshots.execute(new Runnable(){public void run(){try{ultra.downloads.NativeSnapshot.accept(id,loaded.a.toByteArray());}catch(Throwable e){ultra.downloads.Diagnostics.error("native_snapshot",e);}}});
    }
    static final java.util.Map<Object,Object> libraryResponses=new java.util.WeakHashMap<>();
    public static void captureLibrary(Object input){
        if(!(input instanceof jde))return;final jde page=(jde)input;kiu model=page.s;
        if(model==null||!"FEmusic_library_landing".equals(model.b())||!(model.g instanceof amra))return;
        final amra loaded=(amra)model.g;synchronized(libraryResponses){if(libraryResponses.get(page)==loaded)return;libraryResponses.put(page,loaded);}
        snapshots.execute(new Runnable(){public void run(){UltraLibraryCapture.accept(loaded.a);UltraPlaylistIndex.accept(loaded.a);ultra.downloads.PlaylistIndex.sync();}});
    }
    public static void dispatch(Object command){ultra.downloads.Diagnostics.event("callback","offline-command");
        try{OfflineCommands.dispatchWire(((bjue)command).toByteArray());}
        catch(Throwable error){OfflineCommands.failure(error);}
    }
}
