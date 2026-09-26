.class public Lcom/thor/displaypowertest/ScreenReceiver;
.super Landroid/content/BroadcastReceiver;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V

    return-void
.end method


# virtual methods
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 2

    const-string v0, "ThorDisplayAuto"

    const-string v1, "ScreenReceiver wake event"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Lcom/thor/displaypowertest/PServer;->startDaemon()Z

    invoke-static {}, Lcom/thor/displaypowertest/ApplyLogic;->apply()V

    return-void
.end method
