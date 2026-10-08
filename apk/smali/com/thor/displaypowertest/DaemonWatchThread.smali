.class public Lcom/thor/displaypowertest/DaemonWatchThread;
.super Ljava/lang/Thread;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Thread;-><init>()V
    return-void
.end method

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

    :watch_loop
    invoke-static {}, Lcom/thor/displaypowertest/WatcherCadence;->beginSample()V
    invoke-interface/range {v8 .. v13}, Landroid/content/IContentProvider;->call(Landroid/content/AttributionSource;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;
    move-result-object v14
    if-eqz v14, :watch_sleep
    const-string v0, "value"
    invoke-virtual {v14, v0}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v14
    if-eqz v14, :watch_sleep
    invoke-static {v14}, Lcom/thor/displaypowertest/DisplayActionCoordinator;->onWatcherSample(Ljava/lang/String;)V
    invoke-static {}, Lcom/thor/displaypowertest/WatcherSupervisor;->noteSample()V

    :watch_sleep
    invoke-static {}, Lcom/thor/displaypowertest/WatcherCadence;->awaitNextSample()V
    goto :watch_loop
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :watch_error

    :watch_error
    move-exception v0
    const-string v5, "ThorDisplayDaemon"
    const-string v6, "WATCHER ERROR"
    invoke-static {v5, v6, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method
