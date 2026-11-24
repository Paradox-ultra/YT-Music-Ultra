.class public final LUltraNav;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

# One-shot post-restore enforcer: after the tab bar is built, drive the real
# selection path (TabLayout.l -> m -> g -> lac.t -> module render) onto the
# ultra_home tab so cold-start content lands on Home. Ticks a few times to win
# the race against the stock Library landing browse. A real user tap on any
# tab (lac.g with self==false) cancels the remaining ticks.

.field public static cancel:Z
.field public static self:Z
.field public static started:Z

.field public final a:Llac;
.field public b:I
.field public c:Z
.field public static lac:Llac;


.method public constructor <init>(Llac;)V
    .registers 3

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, LUltraNav;->a:Llac;

    sput-object p1, LUltraNav;->lac:Llac;

    const/16 v0, 0x8

    iput v0, p0, LUltraNav;->b:I

    return-void
.end method


.method public run()V
    .registers 9

    # cancelled -> stop
    sget-boolean v0, LUltraNav;->cancel:Z

    if-nez v0, :end

    iget-boolean v0, p0, LUltraNav;->c:Z

    # not a back re-sync -> no guard
    if-eqz v0, :noguard

    invoke-static {}, Lultra/downloads/NativeIntegration;->homeShowing()Z

    move-result v0

    # Home not showing -> do not force the Home tab
    if-eqz v0, :end

    :noguard
    # ticks exhausted -> stop
    iget v0, p0, LUltraNav;->b:I

    if-lez v0, :end

    iget-object v0, p0, LUltraNav;->a:Llac;

    iget-object v1, v0, Llac;->j:Lcom/google/android/material/tabs/TabLayout;

    # bar missing -> retry next tick
    if-eqz v1, :reschedule

    # (re)attach touch-cancel to every tab view; tabs are recreated on rebuilds
    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Lcom/google/android/material/tabs/TabLayout;->getChildAt(I)Landroid/view/View;

    move-result-object v3

    # strip missing -> skip attach
    if-eqz v3, :attachDone

    check-cast v3, Landroid/view/ViewGroup;

    invoke-virtual {v3}, Landroid/view/ViewGroup;->getChildCount()I

    move-result v4

    const/4 v5, 0x0

    :attachLoop
    # i >= count -> done
    if-ge v5, v4, :attachDone

    invoke-virtual {v3, v5}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;

    move-result-object v6

    # child null -> next
    if-eqz v6, :attachNext

    new-instance v2, LUltraTouch;

    invoke-direct {v2}, LUltraTouch;-><init>()V

    invoke-virtual {v6, v2}, Landroid/view/View;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V

    :attachNext
    add-int/lit8 v5, v5, 0x1

    goto :attachLoop

    :attachDone
    iget-object v0, v0, Llac;->i:Ljava/util/List;

    # list missing -> retry next tick
    if-eqz v0, :reschedule

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v2

    const/4 v3, 0x0

    const-string v5, "FEmusic_ultra_home"

    :loop
    # idx >= size -> ultra not in list yet -> retry next tick
    if-ge v3, v2, :reschedule

    invoke-interface {v0, v3}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lbrso;

    iget-object v4, v4, Lbrso;->e:Ljava/lang/String;

    invoke-virtual {v5, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    # match -> found
    if-nez v4, :found

    add-int/lit8 v3, v3, 0x1

    goto :loop

    :found
    const-string v4, "ULTRASTART"

    const-string v5, "navTick"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-result v4

    const/4 v4, 0x1

    sput-boolean v4, LUltraNav;->self:Z

    invoke-virtual {v1, v3}, Lcom/google/android/material/tabs/TabLayout;->c(I)Lbbxl;

    move-result-object v6

    # tab null -> skip select, still reschedule
    if-eqz v6, :skipSel

    invoke-virtual {v1, v6}, Lcom/google/android/material/tabs/TabLayout;->l(Lbbxl;)V

    :skipSel
    # belt and braces: force the filled Home icon on the Ultra tab view
    # tab null -> skip icon
    if-eqz v6, :iconDone

    iget-object v7, v6, Lbbxl;->d:Landroid/view/View;

    # tab view missing -> skip icon
    if-eqz v7, :iconDone

    const v4, 0x7f0b057e

    invoke-virtual {v7, v4}, Landroid/view/View;->findViewById(I)Landroid/view/View;

    move-result-object v7

    instance-of v4, v7, Landroid/widget/ImageView;

    # not an ImageView -> skip icon
    if-eqz v4, :iconDone

    check-cast v7, Landroid/widget/ImageView;

    const v4, 0x7f0808c5

    invoke-virtual {v7, v4}, Landroid/widget/ImageView;->setImageResource(I)V

    const-string v4, "ULTRASTART"

    const-string v5, "navIcon"

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-result v4

    :iconDone
    const/4 v4, 0x0

    sput-boolean v4, LUltraNav;->self:Z

    :reschedule
    iget v0, p0, LUltraNav;->b:I

    add-int/lit8 v0, v0, -0x1

    iput v0, p0, LUltraNav;->b:I

    iget-object v0, p0, LUltraNav;->a:Llac;

    iget-object v1, v0, Llac;->j:Lcom/google/android/material/tabs/TabLayout;

    # bar gone -> stop
    if-eqz v1, :end

    const-wide/16 v2, 0x190

    invoke-virtual {v1, p0, v2, v3}, Lcom/google/android/material/tabs/TabLayout;->postDelayed(Ljava/lang/Runnable;J)Z

    move-result v0

    :end
    return-void
.end method

.method public static touch(Landroid/view/MotionEvent;)V
    .registers 3

    invoke-virtual {p0}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    # action != 0 (not ACTION_DOWN) -> ignore
    if-nez v0, :done

    sget-boolean v0, LUltraNav;->cancel:Z

    # already cancelled -> nothing to do
    if-nez v0, :done

    const/4 v0, 0x1

    sput-boolean v0, LUltraNav;->cancel:Z

    const-string v0, "ULTRASTART"

    const-string v1, "navCancelAny"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    :done
    return-void
.end method

.method public static rearm()V
    .registers 5

    sget-object v0, LUltraNav;->lac:Llac;

    # no tab controller yet -> nothing to sync
    if-eqz v0, :rend

    iget-object v1, v0, Llac;->j:Lcom/google/android/material/tabs/TabLayout;

    # bar missing -> nothing to sync
    if-eqz v1, :rend

    const/4 v2, 0x0

    sput-boolean v2, LUltraNav;->cancel:Z

    new-instance v2, LUltraNav;

    invoke-direct {v2, v0}, LUltraNav;-><init>(Llac;)V

    const/4 v3, 0x2

    iput v3, v2, LUltraNav;->b:I

    const/4 v3, 0x1

    iput-boolean v3, v2, LUltraNav;->c:Z

    const-wide/16 v3, 0x15e

    invoke-virtual {v1, v2, v3, v4}, Lcom/google/android/material/tabs/TabLayout;->postDelayed(Ljava/lang/Runnable;J)Z

    move-result v0

    :rend
    return-void
.end method
