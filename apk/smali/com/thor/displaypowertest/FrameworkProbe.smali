.class public Lcom/thor/displaypowertest/FrameworkProbe;
.super Ljava/lang/Object;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static dumpAll()V
    .locals 8

    :try_start_0
    const-string v0, "android.hardware.display.IDisplayManager"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/Class;->getDeclaredMethods()[Ljava/lang/reflect/Method;

    move-result-object v1

    array-length v2, v1

    const/4 v3, 0x0

    const-string v6, "ThorDisplayDaemon"

    const-string v7, "PROBE METHODS BEGIN"

    invoke-static {v6, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :goto_0
    if-ge v3, v2, :cond_0

    aget-object v4, v1, v3

    invoke-virtual {v4}, Ljava/lang/reflect/Method;->toString()Ljava/lang/String;

    move-result-object v5

    const-string v6, "ThorDisplayDaemon"

    invoke-static {v6, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    add-int/lit8 v3, v3, 0x1

    goto/16 :goto_0

    :cond_0
    const-string v6, "ThorDisplayDaemon"

    const-string v7, "PROBE METHODS END"

    invoke-static {v6, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v6, "ThorDisplayDaemon"

    const-string v7, "PROBE METHODS END"

    invoke-static {v6, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method private static primitive(Ljava/lang/String;)Ljava/lang/Class;
    .locals 5

    const-string v1, "TYPE"

    invoke-static {p0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v2

    invoke-virtual {v2, v1}, Ljava/lang/Class;->getField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v3

    const/4 v4, 0x0

    invoke-virtual {v3, v4}, Ljava/lang/reflect/Field;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Ljava/lang/Class;

    return-object p0
.end method

.method private static probeDisable()V
    .locals 6

    :try_start_0
    const-string v0, "android.hardware.display.IDisplayManager"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    const/4 v1, 0x1

    new-array v2, v1, [Ljava/lang/Class;

    const-string v3, "java.lang.Integer"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x0

    aput-object v3, v2, v4

    const-string v1, "disableConnectedDisplay"

    invoke-virtual {v0, v1, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v5

    const-string v0, "ThorDisplayDaemon"

    const-string v1, "PROBE disableConnectedDisplay(int): FOUND"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v1, "ThorDisplayDaemon"

    const-string v2, "PROBE disableConnectedDisplay(int): MISSING"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method private static probeEnable()V
    .locals 6

    :try_start_0
    const-string v0, "android.hardware.display.IDisplayManager"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    const/4 v1, 0x1

    new-array v2, v1, [Ljava/lang/Class;

    const-string v3, "java.lang.Integer"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x0

    aput-object v3, v2, v4

    const-string v1, "enableConnectedDisplay"

    invoke-virtual {v0, v1, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v5

    const-string v0, "ThorDisplayDaemon"

    const-string v1, "PROBE enableConnectedDisplay(int): FOUND"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v1, "ThorDisplayDaemon"

    const-string v2, "PROBE enableConnectedDisplay(int): MISSING"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method private static probeReqBool()V
    .locals 6

    :try_start_0
    const-string v0, "android.hardware.display.IDisplayManager"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    const/4 v1, 0x2

    new-array v2, v1, [Ljava/lang/Class;

    const-string v3, "java.lang.Integer"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x0

    aput-object v3, v2, v4

    const-string v3, "java.lang.Boolean"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x1

    aput-object v3, v2, v4

    const-string v1, "requestDisplayPower"

    invoke-virtual {v0, v1, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v5

    const-string v0, "ThorDisplayDaemon"

    const-string v1, "PROBE requestDisplayPower(int,boolean): FOUND"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v1, "ThorDisplayDaemon"

    const-string v2, "PROBE requestDisplayPower(int,boolean): MISSING"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method private static probeReqInt()V
    .locals 6

    :try_start_0
    const-string v0, "android.hardware.display.IDisplayManager"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    const/4 v1, 0x2

    new-array v2, v1, [Ljava/lang/Class;

    const-string v3, "java.lang.Integer"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x0

    aput-object v3, v2, v4

    const-string v3, "java.lang.Integer"

    invoke-static {v3}, Lcom/thor/displaypowertest/FrameworkProbe;->primitive(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v3

    const/4 v4, 0x1

    aput-object v3, v2, v4

    const-string v1, "requestDisplayPower"

    invoke-virtual {v0, v1, v2}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    move-result-object v5

    const-string v0, "ThorDisplayDaemon"

    const-string v1, "PROBE requestDisplayPower(int,int): FOUND"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v1, "ThorDisplayDaemon"

    const-string v2, "PROBE requestDisplayPower(int,int): MISSING"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method public static run()V
    .locals 0

    invoke-static {}, Lcom/thor/displaypowertest/FrameworkProbe;->probeDisable()V

    invoke-static {}, Lcom/thor/displaypowertest/FrameworkProbe;->probeEnable()V

    invoke-static {}, Lcom/thor/displaypowertest/FrameworkProbe;->probeReqBool()V

    invoke-static {}, Lcom/thor/displaypowertest/FrameworkProbe;->probeReqInt()V

    invoke-static {}, Lcom/thor/displaypowertest/FrameworkProbe;->dumpAll()V

    return-void
.end method
