.class public Lcom/thor/displaypowertest/OffReceiver;
.super Landroid/content/BroadcastReceiver;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V

    return-void
.end method


# virtual methods
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 0

    invoke-static {}, Lcom/thor/displaypowertest/PServer;->startDaemon()Z

    invoke-static {}, Lcom/thor/displaypowertest/DisplayClient;->off()V

    return-void
.end method
