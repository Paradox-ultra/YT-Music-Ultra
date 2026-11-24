.class public final LUltraTouch;
.super Ljava/lang/Object;
.implements Landroid/view/View$OnTouchListener;

# Touch-based cancel for the start-tab enforcer: any real finger-down on a tab
# view stops LUltraNav. Covers the case where a tap lands while the bar is
# mid-rebuild and never reaches the selection listeners.


.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


.method public onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 5

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    # ACTION_DOWN(0) -> cancel; anything else -> pass through
    if-eqz v0, :down

    const/4 v0, 0x0

    return v0

    :down
    sget-boolean v0, LUltraNav;->cancel:Z

    # already cancelled -> skip re-logging
    if-nez v0, :ret

    const/4 v0, 0x1

    sput-boolean v0, LUltraNav;->cancel:Z

    const-string v0, "ULTRASTART"

    const-string v1, "navCancelTouch"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    :ret
    const/4 v0, 0x0

    return v0
.end method
