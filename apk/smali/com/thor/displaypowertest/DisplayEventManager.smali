.class public Lcom/thor/displaypowertest/DisplayEventManager;
.super Ljava/lang/Object;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static register()V
    .locals 4

    :try_start_0
    const-string v0, "display"

    invoke-static {v0}, Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;

    move-result-object v0

    invoke-static {v0}, Landroid/hardware/display/IDisplayManager$Stub;->asInterface(Landroid/os/IBinder;)Landroid/hardware/display/IDisplayManager;

    move-result-object v0

    if-eqz v0, :cond_0

    new-instance v1, Lcom/thor/displaypowertest/DisplayEventCallback;

    invoke-direct {v1}, Lcom/thor/displaypowertest/DisplayEventCallback;-><init>()V

    invoke-interface {v0, v1}, Landroid/hardware/display/IDisplayManager;->registerCallback(Landroid/hardware/display/IDisplayManagerCallback;)V

    const-string v2, "ThorDisplayDaemon"

    const-string v3, "EVENT CALLBACK REGISTERED"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void

    :catch_0
    move-exception v0

    const-string v2, "ThorDisplayDaemon"

    const-string v3, "EVENT CALLBACK ERROR"

    invoke-static {v2, v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method
