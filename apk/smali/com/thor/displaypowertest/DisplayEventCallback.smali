.class public Lcom/thor/displaypowertest/DisplayEventCallback;
.super Landroid/hardware/display/IDisplayManagerCallback$Stub;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/hardware/display/IDisplayManagerCallback$Stub;-><init>()V
    return-void
.end method


# virtual methods
.method public onDisplayEvent(II)V
    .locals 8

    :try_start_0
    invoke-static {}, Lcom/thor/displaypowertest/WatcherCostMetrics;->noteDisplayEvent()V
    invoke-static {}, Lcom/thor/displaypowertest/BootSafety;->isHeld()Z
    move-result v0
    if-nez v0, :cond_0

    if-eqz p1, :lid_guard_event
    const/4 v0, 0x4
    if-ne p1, v0, :lid_guard_done

    :lid_guard_event

    invoke-static {}, Lcom/thor/displaypowertest/LidGuard;->onWakeEvent()Z
    move-result v0
    if-nez v0, :cond_0

    :lid_guard_done

    const/4 v0, 0x4
    if-ne p1, v0, :cond_0

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->isEnabled()Z
    move-result v0
    if-eqz v0, :cond_0

    const-string v0, "display.power.state"
    invoke-static {v0}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1

    const-string v2, "0"
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v3
    if-eqz v3, :cond_0

    const-string v0, "display"
    invoke-static {v0}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;
    move-result-object v0

    invoke-static {v0}, Landroid/hardware/display/IDisplayManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/hardware/display/IDisplayManager;
    move-result-object v0
    if-eqz v0, :cond_0

    const/4 v1, 0x4
    invoke-interface {v0, v1}, Landroid/hardware/display/IDisplayManager;->getDisplayInfo(I)Landroid/view/DisplayInfo;
    move-result-object v2
    if-eqz v2, :cond_0

    invoke-virtual {v2}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
    move-result-object v3

    const-string v4, "state"
    invoke-virtual {v3, v4}, Ljava/lang/Class;->getField(Ljava/lang/String;)Ljava/lang/reflect/Field;
    move-result-object v3

    invoke-virtual {v3, v2}, Ljava/lang/reflect/Field;->getInt(Ljava/lang/Object;)I
    move-result v4

    const/4 v5, 0x2
    if-ne v4, v5, :cond_0

    invoke-static {}, Lcom/thor/displaypowertest/DisplayActionCoordinator;->noteDisplayOn()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void

    :catch_0
    move-exception v0

    const-string v6, "ThorDisplayDaemon"
    const-string v7, "EVENT ERROR"
    invoke-static {v6, v7, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
