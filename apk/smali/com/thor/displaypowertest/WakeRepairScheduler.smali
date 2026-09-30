.class public Lcom/thor/displaypowertest/WakeRepairScheduler;
.super Ljava/lang/Object;


# static fields
.field private static displayOnNoted:Z
.field private static dueAt:J
.field private static pending:Z


# direct methods
.method static constructor <clinit>()V
    .locals 2

    const-wide/16 v0, -0x1
    sput-wide v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J
    return-void
.end method

.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static declared-synchronized cancel()V
    .locals 3

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    if-eqz v0, :cond_0

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->onRepairCancelled()V

    const-string v1, "ThorDisplayDaemon"
    const-string v2, "WAKE REPAIR CANCELLED"
    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_0
    const/4 v0, 0x0
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->displayOnNoted:Z

    const-wide/16 v1, -0x1
    sput-wide v1, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J
    return-void
.end method

.method public static declared-synchronized isDue()Z
    .locals 5

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    if-eqz v0, :cond_0

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v1

    sget-wide v3, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J
    cmp-long v0, v1, v3
    if-ltz v0, :cond_0

    const/4 v0, 0x1
    return v0

    :cond_0
    const/4 v0, 0x0
    return v0
.end method

.method public static declared-synchronized isPending()Z
    .locals 1

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z

    return v0
.end method

.method public static declared-synchronized markDone()V
    .locals 3

    const/4 v0, 0x0
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->displayOnNoted:Z

    const-wide/16 v1, -0x1
    sput-wide v1, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J
    return-void
.end method

.method public static declared-synchronized noteDisplayOn()V
    .locals 8

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->displayOnNoted:Z
    if-nez v0, :cond_2

    invoke-static {}, Lcom/thor/displaypowertest/DaemonState;->onDisplayOn()V

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v1

    const-wide/16 v3, 0x190
    add-long/2addr v1, v3

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    if-eqz v0, :cond_0

    sget-wide v5, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J
    cmp-long v0, v1, v5
    if-lez v0, :cond_1

    :cond_0
    sput-wide v1, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J

    :cond_1
    const/4 v0, 0x1
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->displayOnNoted:Z

    const-string v6, "ThorDisplayDaemon"
    const-string v7, "EVENT ON CONFIRMED; REPAIR >=400ms"
    invoke-static {v6, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_2
    return-void
.end method

.method public static declared-synchronized scheduleFromWake()V
    .locals 6

    sget-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z

    invoke-static {v0}, Lcom/thor/displaypowertest/DaemonState;->onWake(Z)V

    if-eqz v0, :cond_0

    const-string v4, "ThorDisplayDaemon"
    const-string v5, "WAKE REPAIR RESCHEDULED +700ms"
    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :cond_1

    :cond_0
    const-string v4, "ThorDisplayDaemon"
    const-string v5, "WAKE REPAIR SCHEDULED +700ms"
    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_1

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v1

    const-wide/16 v3, 0x2bc
    add-long/2addr v1, v3
    sput-wide v1, Lcom/thor/displaypowertest/WakeRepairScheduler;->dueAt:J

    const/4 v0, 0x1
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->pending:Z

    const/4 v0, 0x0
    sput-boolean v0, Lcom/thor/displaypowertest/WakeRepairScheduler;->displayOnNoted:Z
    return-void
.end method
