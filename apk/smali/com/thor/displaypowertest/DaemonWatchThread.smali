.class public Lcom/thor/displaypowertest/DaemonWatchThread;
.super Ljava/lang/Thread;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Thread;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 15

    :try_start_0
    invoke-static {}, Landroid/os/Looper;->prepare()V

    invoke-static {}, Landroid/app/ActivityManager;->getService()Landroid/app/IActivityManager;

    move-result-object v0

    new-instance v1, Landroid/os/Binder;

    invoke-direct {v1}, Landroid/os/Binder;-><init>()V

    const-string v2, "settings"

    const/4 v3, 0x0

    const-string v4, "*cmd*"

    invoke-interface {v0, v2, v3, v1, v4}, Landroid/app/IActivityManager;->getContentProviderExternal(Ljava/lang/String;ILandroid/os/IBinder;Ljava/lang/String;)Landroid/app/ContentProviderHolder;

    move-result-object v5

    invoke-virtual {v5}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v6

    const-string v7, "provider"

    invoke-virtual {v6, v7}, Ljava/lang/Class;->getField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v6

    invoke-virtual {v6, v5}, Ljava/lang/reflect/Field;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Landroid/content/IContentProvider;

    new-instance v9, Landroid/content/AttributionSource;

    const/4 v3, 0x0

    const-string v2, "root"

    const/4 v4, 0x0

    invoke-direct {v9, v3, v2, v4}, Landroid/content/AttributionSource;-><init>(ILjava/lang/String;Ljava/lang/String;)V

    const-string v10, "settings"

    const-string v11, "GET_system"

    const-string v12, "dual_screen_display_mode"

    new-instance v13, Landroid/os/Bundle;

    invoke-direct {v13}, Landroid/os/Bundle;-><init>()V

    const-string v2, "_user"

    const/4 v3, 0x0

    invoke-virtual {v13, v2, v3}, Landroid/os/Bundle;->putInt(Ljava/lang/String;I)V

    const/4 v7, 0x0

    const-string v5, "ThorDisplayDaemon"

    const-string v6, "WATCHER START 0.31.1-stable"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :goto_0
    invoke-interface/range {v8 .. v13}, Landroid/content/IContentProvider;->call(Landroid/content/AttributionSource;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;

    move-result-object v14

    if-eqz v14, :cond_8

    const-string p0, "value"

    invoke-virtual {v14, p0}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v14

    if-eqz v14, :cond_8

    invoke-static {v14}, Lcom/thor/displaypowertest/DaemonState;->setMode(Ljava/lang/String;)V

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->isEnabled()Z

    move-result p0

    if-nez p0, :cond_enabled

    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->cancel()V

    const/4 v7, 0x0

    goto/16 :cond_8

    :cond_enabled

    invoke-static {}, Lcom/thor/displaypowertest/BootSafety;->isHeld()Z

    move-result p0

    if-eqz p0, :cond_8

    if-eqz v7, :cond_1

    invoke-virtual {v14, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_1

    const-string p0, "1"

    invoke-virtual {p0, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_5

    const-string v5, "display.power.state"

    invoke-static {v5}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v6

    const-string v5, "0"

    invoke-virtual {v5, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-nez p0, :cond_5

    invoke-static {}, Lcom/thor/displaypowertest/LidGuard;->onWakeEvent()Z

    move-result p0

    if-nez p0, :cond_5

    const-string v5, "display.power.state"

    const-string v6, "0"

    invoke-static {v5, v6}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->scheduleFromWake()V

    goto :cond_5

    :cond_1
    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->cancel()V

    invoke-static {v14}, Lcom/thor/displaypowertest/BootSafety;->shouldApplyMode(Ljava/lang/String;)Z

    move-result p0

    if-nez p0, :cond_apply_mode

    move-object v7, v14

    goto :cond_5

    :cond_apply_mode

    const-string p0, "1"

    invoke-virtual {p0, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_3

    const-string v5, "display.power.state"

    const-string v6, "0"

    invoke-static {v5, v6}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-wide v2, 0x40446d4a32a16584L

    invoke-static {v2, v3}, Landroid/view/SurfaceControl;->getPhysicalDisplayToken(J)Landroid/os/IBinder;

    move-result-object v4

    if-eqz v4, :cond_2

    const/4 v5, 0x0

    invoke-static {v4, v5}, Landroid/view/SurfaceControl;->setDisplayPowerMode(Landroid/os/IBinder;I)V

    const-string v5, "ThorDisplayDaemon"

    const-string v6, "WATCH OFF MANUAL"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-object v7, v14

    goto :cond_5

    :cond_2
    const-string v5, "ThorDisplayDaemon"

    const-string v6, "TOKEN_NULL MANUAL OFF"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-object v7, v14

    goto :cond_5

    :cond_3
    const-string v5, "display.power.state"

    const-string v6, "1"

    invoke-static {v5, v6}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-wide v2, 0x40446d4a32a16584L

    invoke-static {v2, v3}, Landroid/view/SurfaceControl;->getPhysicalDisplayToken(J)Landroid/os/IBinder;

    move-result-object v4

    if-eqz v4, :cond_4

    const/4 v5, 0x2

    invoke-static {v4, v5}, Landroid/view/SurfaceControl;->setDisplayPowerMode(Landroid/os/IBinder;I)V

    const-string v5, "ThorDisplayDaemon"

    const-string v6, "WATCH ON MANUAL"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-object v7, v14

    goto :cond_5

    :cond_4
    const-string v5, "ThorDisplayDaemon"

    const-string v6, "TOKEN_NULL MANUAL ON"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    move-object v7, v14

    :cond_5
    const-string p0, "1"

    invoke-virtual {p0, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_7

    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->isDue()Z

    move-result p0

    if-eqz p0, :cond_8

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->isEnabled()Z

    move-result p0

    if-eqz p0, :cond_8

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->onRepairBegin()V

    const-string v5, "display.power.state"

    const-string v6, "0"

    invoke-static {v5, v6}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V

    const-wide v2, 0x40446d4a32a16584L

    invoke-static {v2, v3}, Landroid/view/SurfaceControl;->getPhysicalDisplayToken(J)Landroid/os/IBinder;

    move-result-object v4

    if-eqz v4, :cond_6

    const/4 v5, 0x0

    invoke-static {v4, v5}, Landroid/view/SurfaceControl;->setDisplayPowerMode(Landroid/os/IBinder;I)V

    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->markDone()V

    const-string v5, "OFF_OK"

    invoke-static {v5}, Lcom/thor/displaypowertest/DaemonState;->onRepairEnd(Ljava/lang/String;)V

    const-string v5, "ThorDisplayDaemon"

    const-string v6, "WAKE REPAIR OFF"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :cond_8

    :cond_6
    const-string v5, "ThorDisplayDaemon"

    const-string v6, "TOKEN_NULL WAKE REPAIR"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    const-string v5, "TOKEN_NULL"

    invoke-static {v5}, Lcom/thor/displaypowertest/DaemonState;->onRepairEnd(Ljava/lang/String;)V

    goto :cond_8

    :cond_7
    invoke-static {}, Lcom/thor/displaypowertest/WakeRepairScheduler;->cancel()V

    :cond_8
    const-wide/16 v2, 0x14

    invoke-static {v2, v3}, Ljava/lang/Thread;->sleep(J)V

    goto/16 :goto_0
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v5, "ThorDisplayDaemon"

    const-string v6, "WATCHER ERROR"

    invoke-static {v5, v6, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method
