.class public Lcom/thor/displaypowertest/OffSocketThread;
.super Ljava/lang/Thread;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Thread;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 0

    const/16 p0, 0x30

    invoke-static {p0}, Lcom/thor/displaypowertest/DisplayClient;->sendByte(I)V

    return-void
.end method
