.class public final LUltraBack;
.super Lyi;

# Always-enabled back callback registered after the stock one so it runs first.
# On Android 13+ the platform only routes Back to the app while some callback is
# enabled; when the stock callback is disabled Back finishes the activity without
# ever reaching MusicActivity.onBackPressed, so the Home redirect has to live here.

.field private final a:Landroid/app/Activity;


.method public constructor <init>(Landroid/app/Activity;)V
    .registers 3

    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lyi;-><init>(Z)V

    iput-object p1, p0, LUltraBack;->a:Landroid/app/Activity;

    return-void
.end method


.method public final b()V
    .registers 6

    iget-object v0, p0, LUltraBack;->a:Landroid/app/Activity;

    check-cast v0, Lcom/google/android/apps/youtube/music/activities/MusicActivity;

    # module screens (Downloads / Ultra Home panels) first
    invoke-static {}, Lultra/downloads/NativeIntegration;->back()Z

    move-result v1

    # consumed -> done
    if-nez v1, :ret

    invoke-virtual {v0}, Lcom/google/android/apps/youtube/music/activities/MusicActivity;->e()Lijw;

    move-result-object v2

    iget-object v2, v2, Lijw;->e:Lyi;

    iget-boolean v2, v2, Lyi;->c:Z

    # stock callback enabled (it pops the browse stack) -> let it handle Back
    if-nez v2, :pass

    const/4 v3, 0x0

    invoke-static {v3}, Lultra/downloads/NativeIntegration;->backAtRoot(Ljava/lang/Object;)Z

    move-result v3

    # moved to Home -> consumed, re-sync the nav icon
    if-nez v3, :rearm

    :pass
    const/4 v3, 0x0

    invoke-virtual {p0, v3}, Lyi;->f(Z)V

    check-cast v0, Lxw;

    invoke-virtual {v0}, Lxw;->getOnBackPressedDispatcher()Lyn;

    move-result-object v4

    invoke-virtual {v4}, Lyn;->c()V

    const/4 v3, 0x1

    invoke-virtual {p0, v3}, Lyi;->f(Z)V

    :rearm
    invoke-static {}, LUltraNav;->rearm()V

    :ret
    return-void
.end method
