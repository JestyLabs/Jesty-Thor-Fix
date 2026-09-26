.class public Lcom/thor/displaypowertest/ModeObserver;
.super Landroid/database/ContentObserver;


# direct methods
.method public constructor <init>(Landroid/os/Handler;)V
    .locals 0

    invoke-direct {p0, p1}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    return-void
.end method


# virtual methods
.method public onChange(Z)V
    .locals 2

    const-string v0, "ThorDisplayAuto"

    const-string v1, "ModeObserver.onChange"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Lcom/thor/displaypowertest/ApplyLogic;->apply()V

    return-void
.end method
