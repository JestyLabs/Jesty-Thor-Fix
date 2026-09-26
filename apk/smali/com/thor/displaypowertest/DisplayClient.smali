.class public Lcom/thor/displaypowertest/DisplayClient;
.super Ljava/lang/Object;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static off()V
    .locals 2

    const-string v0, "ThorDisplayAuto"

    const-string v1, "SEND OFF"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    new-instance v0, Lcom/thor/displaypowertest/OffSocketThread;

    invoke-direct {v0}, Lcom/thor/displaypowertest/OffSocketThread;-><init>()V

    invoke-virtual {v0}, Ljava/lang/Thread;->start()V

    return-void
.end method

.method public static on()V
    .locals 2

    const-string v0, "ThorDisplayAuto"

    const-string v1, "SEND ON"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    new-instance v0, Lcom/thor/displaypowertest/OnSocketThread;

    invoke-direct {v0}, Lcom/thor/displaypowertest/OnSocketThread;-><init>()V

    invoke-virtual {v0}, Ljava/lang/Thread;->start()V

    return-void
.end method

.method public static sendByte(I)V
    .locals 4

    invoke-static {p0}, Lcom/thor/displaypowertest/DisplayClient;->trySend(I)Z

    move-result v0

    if-nez v0, :cond_0

    const-string v0, "ThorDisplayAuto"

    const-string v1, "SOCKET_RETRY"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    const-wide/16 v0, 0x14

    invoke-static {v0, v1}, Ljava/lang/Thread;->sleep(J)V

    invoke-static {p0}, Lcom/thor/displaypowertest/DisplayClient;->trySend(I)Z

    move-result v0

    if-nez v0, :cond_0

    const-string v0, "ThorDisplayAuto"

    const-string v1, "SOCKET_ERROR_FINAL"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_0
    return-void
.end method

.method private static trySend(I)Z
    .locals 4

    :try_start_0
    new-instance v0, Ljava/net/Socket;

    const-string v1, "127.0.0.1"

    const/16 v2, 0xedc

    invoke-direct {v0, v1, v2}, Ljava/net/Socket;-><init>(Ljava/lang/String;I)V

    invoke-virtual {v0}, Ljava/net/Socket;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v1

    invoke-virtual {v1, p0}, Ljava/io/OutputStream;->write(I)V

    invoke-virtual {v1}, Ljava/io/OutputStream;->flush()V

    invoke-virtual {v0}, Ljava/net/Socket;->close()V

    const/4 v0, 0x1
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return v0

    :catch_0
    move-exception v0

    const/4 v0, 0x0

    return v0
.end method
