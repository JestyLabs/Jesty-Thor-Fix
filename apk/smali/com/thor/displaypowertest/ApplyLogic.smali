.class public Lcom/thor/displaypowertest/ApplyLogic;
.super Ljava/lang/Object;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static apply()V
    .locals 4

    invoke-static {}, Lcom/thor/displaypowertest/ApplyLogic;->readMode()Ljava/lang/String;

    move-result-object v0

    const-string v1, "1"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    const-string v0, "ThorDisplayAuto"

    const-string v1, "Apply TOP -> OFF"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Lcom/thor/displaypowertest/DisplayClient;->off()V

    return-void

    :cond_0
    const-string v0, "ThorDisplayAuto"

    const-string v1, "Apply non-TOP -> ON"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Lcom/thor/displaypowertest/DisplayClient;->on()V

    return-void
.end method

.method public static readMode()Ljava/lang/String;
    .locals 4

    invoke-static {}, Landroid/app/ActivityThread;->currentApplication()Landroid/app/Application;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    const-string v2, "dual_screen_display_mode"

    invoke-static {v1, v2}, Landroid/provider/Settings$System;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    return-object v3
.end method
