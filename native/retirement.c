#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>
#ifdef __linux__
#include <sys/syscall.h>
#endif

#define JNI_METHOD(name) Java_com_thor_displaypowertest_RetirementNative_##name
#define MAX_LINE 16384
#define MAX_OWNED 16
#define MAX_TIMEOUT_MS 12000
#define MAX_CMDLINE 4096
#define IDENTITY_TIMEOUT_MS 1000
#define FD_MARKER ((unsigned char) 'F')
#define APPLICATION_DIRECTORY "/data/user/0/com.thor.displaypowertest"
#define APPLICATION_FILES APPLICATION_DIRECTORY "/files"
#define LEGACY_SOCKET_NAME "jesty-thor-control-v2.sock"
#define LEGACY_ENDPOINT APPLICATION_FILES "/" LEGACY_SOCKET_NAME

typedef struct {
    int used;
    int fd;
    int directory;
    dev_t device;
    ino_t inode;
    dev_t socket_device;
    ino_t socket_inode;
    char path[sizeof(((struct sockaddr_un *) 0)->sun_path)];
    char name[40];
} server_entry;

typedef struct {
    int used;
    int fd;
    pid_t pid;
    dev_t device;
    ino_t inode;
} process_entry;

static pthread_mutex_t ownership_lock = PTHREAD_MUTEX_INITIALIZER;
static server_entry servers[MAX_OWNED];
static process_entry processes[MAX_OWNED];

static void fail(JNIEnv *env, const char *message) {
    if ((*env)->ExceptionCheck(env)) return;
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) (*env)->ThrowNew(env, type, message);
}

static void fail_errno(JNIEnv *env, const char *operation) {
    int saved = errno;
    char message[192];
    (void) snprintf(message, sizeof(message), "%s: errno=%d", operation, saved);
    fail(env, message);
}

static int64_t monotonic_ns(JNIEnv *env) {
    struct timespec time;
    if (clock_gettime(CLOCK_MONOTONIC, &time) != 0) {
        fail_errno(env, "clock_gettime");
        return -1;
    }
    return (int64_t) time.tv_sec * INT64_C(1000000000) + time.tv_nsec;
}

static int64_t deadline(JNIEnv *env, jint timeout) {
    if (timeout <= 0) {
        fail(env, "Timeout must be positive");
        return -1;
    }
    if (timeout > MAX_TIMEOUT_MS) {
        fail(env, "Timeout exceeds native 12000ms bound");
        return -1;
    }
    int64_t now = monotonic_ns(env);
    return now < 0 ? -1 : now + (int64_t) timeout * INT64_C(1000000);
}

static int remaining(JNIEnv *env, int64_t end) {
    int64_t now = monotonic_ns(env);
    if (now < 0) return -1;
    if (now >= end) {
        fail(env, "Retirement I/O timed out");
        return -1;
    }
    int64_t ms = (end - now + INT64_C(999999)) / INT64_C(1000000);
    return ms > INT_MAX ? INT_MAX : (int) ms;
}

static int wait_ready(JNIEnv *env, int fd, short events, int64_t end) {
    if (fd < 0) {
        fail(env, "Invalid descriptor");
        return -1;
    }
    for (;;) {
        int ms = remaining(env, end);
        if (ms < 0) return -1;
        struct pollfd item = {fd, events, 0};
        int result = poll(&item, 1, ms);
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) {
            fail_errno(env, "poll");
            return -1;
        }
        if (result == 0) continue;
        if (remaining(env, end) < 0) return -1;
        if ((item.revents & POLLNVAL) != 0) {
            fail(env, "Invalid polled descriptor");
            return -1;
        }
        if ((item.revents & events) != 0) return 0;
        fail(env, "Socket closed or failed");
        return -1;
    }
}

static int cloexec(JNIEnv *env, int fd) {
    int flags = fcntl(fd, F_GETFD);
    if (flags < 0 || fcntl(fd, F_SETFD, flags | FD_CLOEXEC) < 0) {
        fail_errno(env, "FD_CLOEXEC");
        return -1;
    }
    return 0;
}

static int nonblocking(JNIEnv *env, int fd) {
    int flags = fcntl(fd, F_GETFL);
    if (flags < 0 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        fail_errno(env, "O_NONBLOCK");
        return -1;
    }
    return 0;
}

static int unix_stream(JNIEnv *env, int fd, int connected) {
    struct sockaddr_un address;
    socklen_t length = sizeof(address);
    int type = 0;
    socklen_t type_length = sizeof(type);
    if (getsockopt(fd, SOL_SOCKET, SO_TYPE, &type, &type_length) != 0
            || type != SOCK_STREAM
            || getsockname(fd, (struct sockaddr *) &address, &length) != 0
            || address.sun_family != AF_UNIX) {
        fail(env, "Expected Unix stream descriptor");
        return -1;
    }
    if (connected) {
        length = sizeof(address);
        if (getpeername(fd, (struct sockaddr *) &address, &length) != 0
                || address.sun_family != AF_UNIX) {
            fail(env, "Expected connected Unix stream");
            return -1;
        }
    }
    return 0;
}

typedef struct { pid_t pid; uid_t uid; } peer;

static int private_nonce_endpoint(const char *path) {
    static const char prefix[] = APPLICATION_FILES "/retire-";
    if (strncmp(path, prefix, sizeof(prefix) - 1) != 0) return 0;
    const char *nonce = path + sizeof(prefix) - 1;
    if (strlen(nonce) != 32) return 0;
    for (int i = 0; i < 32; ++i) {
        if (!(nonce[i] >= '0' && nonce[i] <= '9') && !(nonce[i] >= 'a' && nonce[i] <= 'f')) return 0;
    }
    return 1;
}

#if defined(__linux__) || defined(RETIREMENT_HOST_CONNECT_TEST)
enum { CONNECT_APP_DAEMON = 1, CONNECT_ROOT_CONTROL = 2 };

static int connect_role(const char *path, uid_t real_uid, uid_t effective_uid,
        uid_t application_owner, int expected_uid) {
    if (application_owner == 0 || (uint64_t) application_owner > INT_MAX || expected_uid < 0) return 0;
    if (real_uid == application_owner && effective_uid == application_owner && expected_uid == 0
            && strcmp(path, LEGACY_ENDPOINT) == 0) return CONNECT_APP_DAEMON;
    if (real_uid == 0 && effective_uid == 0 && (uid_t) expected_uid == application_owner
            && private_nonce_endpoint(path)) return CONNECT_ROOT_CONTROL;
    return 0;
}

static int private_endpoint_file(const struct stat *file, uid_t owner) {
    return S_ISSOCK(file->st_mode) && file->st_uid == owner
            && (file->st_mode & 07777) == 0600;
}

static int connected_endpoint_allowed(int role, uid_t application_owner, int expected_uid,
        const struct stat *before, const struct stat *after, const peer *credentials) {
    return (role == CONNECT_APP_DAEMON || role == CONNECT_ROOT_CONTROL)
            && private_endpoint_file(before, application_owner)
            && private_endpoint_file(after, application_owner)
            && before->st_dev == after->st_dev && before->st_ino == after->st_ino
            && credentials->uid == (uid_t) expected_uid && credentials->pid > 0
            && (role != CONNECT_APP_DAEMON || credentials->pid > 100);
}
#endif

static int peer_credentials(JNIEnv *env, int fd, peer *out) {
#ifdef __linux__
    struct ucred credentials;
    socklen_t length = sizeof(credentials);
    if (unix_stream(env, fd, 1) != 0) return -1;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &credentials, &length) != 0) {
        fail_errno(env, "SO_PEERCRED");
        return -1;
    }
    if (length != sizeof(credentials) || credentials.pid <= 0) {
        fail(env, "Invalid kernel peer credentials");
        return -1;
    }
    out->pid = credentials.pid;
    out->uid = credentials.uid;
    return 0;
#else
    (void) fd;
    (void) out;
    fail(env, "Kernel PID/UID peer credentials unsupported on this platform");
    return -1;
#endif
}

static int ascii(JNIEnv *env, jstring value, char *out, size_t capacity) {
    if (value == NULL) {
        fail(env, "Null string");
        return -1;
    }
    jsize length = (*env)->GetStringLength(env, value);
    if ((size_t) length >= capacity) {
        fail(env, "String exceeds native bound");
        return -1;
    }
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (characters == NULL) return -1;
    int valid = 1;
    for (jsize i = 0; i < length; ++i) {
        if (characters[i] < 0x20 || characters[i] > 0x7e) {
            valid = 0;
            break;
        }
        out[i] = (char) characters[i];
    }
    (*env)->ReleaseStringChars(env, value, characters);
    if (!valid) {
        fail(env, "Only printable ASCII without newline is permitted");
        return -1;
    }
    out[length] = '\0';
    return (int) length;
}

static int channel_path(JNIEnv *env, jstring value, char *path, char *directory,
        char *name) {
    int length = ascii(env, value, path, sizeof(((struct sockaddr_un *) 0)->sun_path));
    if (length < 0) return -1;
    char *slash = strrchr(path, '/');
    if (path[0] != '/' || slash == NULL || slash == path
            || strlen(slash + 1) != 39 || strncmp(slash + 1, "retire-", 7) != 0) {
        fail(env, "Expected private retire-<32hex> endpoint");
        return -1;
    }
    for (const char *p = slash + 8; *p != '\0'; ++p) {
        if (!(*p >= '0' && *p <= '9') && !(*p >= 'a' && *p <= 'f')) {
            fail(env, "Invalid endpoint nonce");
            return -1;
        }
    }
    size_t directory_length = (size_t) (slash - path);
    memcpy(directory, path, directory_length);
    directory[directory_length] = '\0';
    memcpy(name, slash + 1, 40);
    return 0;
}

/* Android files directories normally use 0771. Group write is confined to
 * the app's own GID; other write, foreign ownership and special modes fail.
 * Socket permissions, peer credentials and process binding remain separate.
 */
static int trusted_app_directory(const struct stat *value, uid_t owner) {
    return S_ISDIR(value->st_mode) && value->st_uid == owner
            && (uint64_t) value->st_gid == (uint64_t) owner
            && (value->st_mode & 0002) == 0 && (value->st_mode & 07000) == 0;
}

static int trusted_directory(JNIEnv *env, const char *path, uid_t owner) {
    struct stat before, opened;
    if (lstat(path, &before) != 0) {
        fail_errno(env, "lstat private directory");
        return -1;
    }
    if (!trusted_app_directory(&before, owner)) {
        fail(env, "Endpoint directory is not private and owned by expected UID");
        return -1;
    }
    int fd = open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) {
        fail_errno(env, "open private directory");
        return -1;
    }
    if (fstat(fd, &opened) != 0 || opened.st_dev != before.st_dev
            || opened.st_ino != before.st_ino || !trusted_app_directory(&opened, owner)) {
        fail(env, "Endpoint directory changed");
        (void) close(fd);
        return -1;
    }
    return fd;
}

static int same_socket_file(const server_entry *entry) {
    struct stat current;
    return fstatat(entry->directory, entry->name, &current, AT_SYMLINK_NOFOLLOW) == 0
            && S_ISSOCK(current.st_mode) && current.st_dev == entry->device
            && current.st_ino == entry->inode;
}

static int application_uid(JNIEnv *env, uid_t *owner) {
#ifdef __linux__
    static const char application[] = APPLICATION_DIRECTORY;
    static const char files[] = APPLICATION_FILES;
    struct stat application_before, files_before, application_opened, files_opened;
    if (lstat(application, &application_before) != 0 || lstat(files, &files_before) != 0) {
        fail_errno(env, "lstat application directories");
        return -1;
    }
    if (application_before.st_uid == 0
            || (uint64_t) application_before.st_uid > INT_MAX
            || !trusted_app_directory(&application_before, application_before.st_uid)
            || !trusted_app_directory(&files_before, application_before.st_uid)) {
        fail(env, "Application directories are not trusted UID-owned directories");
        return -1;
    }
    int application_fd = open(application, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (application_fd < 0) {
        fail_errno(env, "open application directory");
        return -1;
    }
    int files_fd = openat(application_fd, "files", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (files_fd < 0) {
        fail_errno(env, "open application files directory");
        (void) close(application_fd);
        return -1;
    }
    int valid = fstat(application_fd, &application_opened) == 0
            && fstat(files_fd, &files_opened) == 0
            && application_before.st_dev == application_opened.st_dev
            && application_before.st_ino == application_opened.st_ino
            && files_before.st_dev == files_opened.st_dev
            && files_before.st_ino == files_opened.st_ino
            && trusted_app_directory(&application_opened, application_before.st_uid)
            && trusted_app_directory(&files_opened, application_before.st_uid);
    (void) close(files_fd);
    (void) close(application_fd);
    if (!valid) {
        fail(env, "Application directory identity changed");
        return -1;
    }
    *owner = application_before.st_uid;
    return 0;
#else
    (void) owner;
    fail(env, "Application UID discovery unsupported on this platform");
    return -1;
#endif
}

JNIEXPORT jint JNICALL JNI_METHOD(applicationUid)(JNIEnv *env, jclass type) {
    (void) type;
    uid_t owner = 0;
    return application_uid(env, &owner) != 0 ? -1 : (jint) owner;
}

static int remove_server(JNIEnv *env, server_entry *entry) {
    int result = 0;
    struct stat descriptor;
    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    socklen_t length = sizeof(address);
    if (fstat(entry->fd, &descriptor) != 0
            || descriptor.st_dev != entry->socket_device
            || descriptor.st_ino != entry->socket_inode
            || getsockname(entry->fd, (struct sockaddr *) &address, &length) != 0
            || address.sun_family != AF_UNIX
            || strncmp(address.sun_path, entry->path, sizeof(address.sun_path)) != 0) {
        fail(env, "Owned listener descriptor changed; refusing close/unlink");
        result = -1;
    } else {
        if (!same_socket_file(entry)) {
            fail(env, "Owned endpoint changed; refusing unlink");
            result = -1;
        } else if (unlinkat(entry->directory, entry->name, 0) != 0) {
            fail_errno(env, "unlink owned endpoint");
            result = -1;
        }
        /* Never retry close(EINTR): the descriptor might already have been reused. */
        if (close(entry->fd) != 0 && errno != EINTR) {
            fail_errno(env, "close listener");
            result = -1;
        }
    }
    if (close(entry->directory) != 0 && errno != EINTR) {
        fail_errno(env, "close endpoint directory");
        result = -1;
    }
    entry->used = 0;
    return result;
}

JNIEXPORT jint JNICALL JNI_METHOD(listen)(JNIEnv *env, jclass type, jstring value) {
    (void) type;
    server_entry entry;
    memset(&entry, 0, sizeof(entry));
    char directory[sizeof(entry.path)];
    if (channel_path(env, value, entry.path, directory, entry.name) != 0) return -1;
    if (!private_nonce_endpoint(entry.path)) {
        fail(env, "Listener must be a nonce endpoint in the installed application files directory");
        return -1;
    }
    uid_t application_owner = 0;
    if (application_uid(env, &application_owner) != 0) return -1;
    if (getuid() != application_owner || geteuid() != application_owner) {
        fail(env, "Only the installed application UID may own the listener");
        return -1;
    }
    entry.directory = trusted_directory(env, directory, application_owner);
    if (entry.directory < 0) return -1;
    struct stat path_stat;
    if (fstatat(entry.directory, entry.name, &path_stat, AT_SYMLINK_NOFOLLOW) == 0
            || errno != ENOENT) {
        fail(env, "Endpoint already exists or cannot be verified absent");
        (void) close(entry.directory);
        return -1;
    }
#ifdef __linux__
    entry.fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
#else
    entry.fd = socket(AF_UNIX, SOCK_STREAM, 0);
#endif
    if (entry.fd < 0) {
        fail_errno(env, "socket");
        (void) close(entry.directory);
        return -1;
    }
    int bound = 0;
    if (cloexec(env, entry.fd) != 0 || nonblocking(env, entry.fd) != 0) goto failed;
    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, entry.path, strlen(entry.path) + 1);
    if (bind(entry.fd, (struct sockaddr *) &address, sizeof(address)) != 0) {
        fail_errno(env, "bind fresh endpoint");
        goto failed;
    }
    if (fstatat(entry.directory, entry.name, &path_stat, AT_SYMLINK_NOFOLLOW) != 0
            || !S_ISSOCK(path_stat.st_mode) || path_stat.st_uid != geteuid()) {
        fail(env, "Bound endpoint identity unavailable");
        goto failed;
    }
    entry.device = path_stat.st_dev;
    entry.inode = path_stat.st_ino;
    bound = 1;
    struct stat descriptor, directory_stat, current_directory;
    if (fstat(entry.fd, &descriptor) != 0
            || fstat(entry.directory, &directory_stat) != 0
            || lstat(directory, &current_directory) != 0
            || directory_stat.st_dev != current_directory.st_dev
            || directory_stat.st_ino != current_directory.st_ino
            || !trusted_app_directory(&directory_stat, geteuid())
            || !trusted_app_directory(&current_directory, geteuid())
            || fchmodat(entry.directory, entry.name, 0600, 0) != 0
            || !same_socket_file(&entry)) {
        fail(env, "Bound endpoint or directory changed");
        goto failed;
    }
    entry.socket_device = descriptor.st_dev;
    entry.socket_inode = descriptor.st_ino;
    if (listen(entry.fd, 1) != 0) {
        fail_errno(env, "listen");
        goto failed;
    }
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (!servers[i].used) {
            entry.used = 1;
            servers[i] = entry;
            pthread_mutex_unlock(&ownership_lock);
            return entry.fd;
        }
    }
    pthread_mutex_unlock(&ownership_lock);
    fail(env, "Too many retirement listeners");
failed:
    if (bound && same_socket_file(&entry)) (void) unlinkat(entry.directory, entry.name, 0);
    (void) close(entry.fd);
    (void) close(entry.directory);
    return -1;
}

JNIEXPORT jint JNICALL JNI_METHOD(accept)(JNIEnv *env, jclass type, jint listener,
        jint expected_uid, jint timeout) {
    (void) type;
    int64_t end = deadline(env, timeout);
    if (end < 0) return -1;
#ifndef __linux__
    (void) listener;
    (void) expected_uid;
    fail(env, "Kernel PID/UID peer credentials unsupported on this platform");
    return -1;
#else
    if (expected_uid != 0) {
        fail(env, "Only root may connect to retirement listener");
        return -1;
    }
    int retained_listener = -1;
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (servers[i].used && servers[i].fd == listener) {
            struct stat identity;
            if (fstat(listener, &identity) == 0
                    && identity.st_dev == servers[i].socket_device
                    && identity.st_ino == servers[i].socket_inode) {
                retained_listener = fcntl(listener, F_DUPFD_CLOEXEC, 0);
            }
            break;
        }
    }
    pthread_mutex_unlock(&ownership_lock);
    if (retained_listener < 0) {
        fail(env, "Listener is not owned");
        return -1;
    }
    for (;;) {
        if (wait_ready(env, retained_listener, POLLIN, end) != 0) {
            (void) close(retained_listener);
            return -1;
        }
        int fd = accept4(retained_listener, NULL, NULL, SOCK_CLOEXEC | SOCK_NONBLOCK);
        if (fd < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        (void) close(retained_listener);
        if (fd < 0) {
            fail_errno(env, "accept");
            return -1;
        }
        peer credentials;
        if (cloexec(env, fd) != 0 || nonblocking(env, fd) != 0
                || peer_credentials(env, fd, &credentials) != 0) {
            (void) close(fd);
            return -1;
        }
        if (credentials.uid != (uid_t) expected_uid) {
            (void) close(fd);
            fail(env, "Retirement peer UID mismatch");
            return -1;
        }
        return fd;
    }
#endif
}

JNIEXPORT jint JNICALL JNI_METHOD(connect)(JNIEnv *env, jclass type, jstring value,
        jint expected_uid, jint timeout) {
    (void) type;
    int64_t end = deadline(env, timeout);
    if (end < 0) return -1;
#ifndef __linux__
    (void) value;
    (void) expected_uid;
    fail(env, "Kernel PID/UID peer credentials unsupported on this platform");
    return -1;
#else
    char path[sizeof(((struct sockaddr_un *) 0)->sun_path)], directory[sizeof(path)], name[40];
    if (ascii(env, value, path, sizeof(path)) < 0) return -1;
    uid_t application_owner = 0;
    if (application_uid(env, &application_owner) != 0) return -1;
    int role = connect_role(path, getuid(), geteuid(), application_owner, expected_uid);
    if (role == 0) {
        fail(env, "Retirement connect role/path rejected");
        return -1;
    }
    const char *slash = strrchr(path, '/');
    size_t directory_length = (size_t) (slash - path);
    memcpy(directory, path, directory_length);
    directory[directory_length] = '\0';
    memcpy(name, slash + 1, strlen(slash + 1) + 1);
    int directory_fd = trusted_directory(env, directory, application_owner);
    if (directory_fd < 0) return -1;
    struct stat before, after;
    if (fstatat(directory_fd, name, &before, AT_SYMLINK_NOFOLLOW) != 0
            || !private_endpoint_file(&before, application_owner)) {
        fail(env, "Endpoint is not a private application-owned socket");
        (void) close(directory_fd);
        return -1;
    }
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (fd < 0) {
        fail_errno(env, "socket");
        (void) close(directory_fd);
        return -1;
    }
    if (cloexec(env, fd) != 0 || nonblocking(env, fd) != 0) goto failed;
    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, path, strlen(path) + 1);
    if (connect(fd, (struct sockaddr *) &address, sizeof(address)) != 0) {
        if (errno != EINPROGRESS && errno != EAGAIN && errno != EINTR) {
            fail_errno(env, "connect");
            goto failed;
        }
        if (wait_ready(env, fd, POLLOUT, end) != 0) goto failed;
        int error = 0;
        socklen_t length = sizeof(error);
        if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &length) != 0) {
            fail_errno(env, "connect SO_ERROR");
            goto failed;
        }
        if (error != 0) {
            errno = error;
            fail_errno(env, "connect completion");
            goto failed;
        }
    }
    peer credentials;
    if (peer_credentials(env, fd, &credentials) != 0) goto failed;
    if (fstatat(directory_fd, name, &after, AT_SYMLINK_NOFOLLOW) != 0
            || !connected_endpoint_allowed(role, application_owner, expected_uid,
                    &before, &after, &credentials)) {
        fail(env, "Peer UID/PID or private endpoint changed");
        goto failed;
    }
    (void) close(directory_fd);
    return fd;
failed:
    (void) close(fd);
    (void) close(directory_fd);
    return -1;
#endif
}

JNIEXPORT jint JNICALL JNI_METHOD(peerPid)(JNIEnv *env, jclass type, jint fd) {
    (void) type;
    peer credentials;
    return peer_credentials(env, fd, &credentials) != 0 ? -1 : (jint) credentials.pid;
}

JNIEXPORT jint JNICALL JNI_METHOD(peerUid)(JNIEnv *env, jclass type, jint fd) {
    (void) type;
    peer credentials;
    if (peer_credentials(env, fd, &credentials) != 0) return -1;
    if ((uint64_t) credentials.uid > INT_MAX) {
        fail(env, "Peer UID out of Java range");
        return -1;
    }
    return (jint) credentials.uid;
}

static int send_flags(JNIEnv *env, int fd) {
#ifdef MSG_NOSIGNAL
    (void) env;
    (void) fd;
    return MSG_DONTWAIT | MSG_NOSIGNAL;
#elif defined(SO_NOSIGPIPE)
    int enabled = 1;
    if (setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &enabled, sizeof(enabled)) != 0) {
        fail_errno(env, "SO_NOSIGPIPE");
        return -1;
    }
    return MSG_DONTWAIT;
#else
    (void) fd;
    fail(env, "Safe socket send unsupported");
    return -1;
#endif
}

JNIEXPORT void JNICALL JNI_METHOD(sendFd)(JNIEnv *env, jclass type, jint control,
        jint socket_fd, jint timeout) {
    (void) type;
    int64_t end = deadline(env, timeout);
    if (end < 0 || unix_stream(env, control, 1) != 0
            || unix_stream(env, socket_fd, 1) != 0) return;
    int flags = send_flags(env, control);
    if (flags < 0) return;
    unsigned char marker = FD_MARKER;
    struct iovec payload = {&marker, 1};
    union { struct cmsghdr alignment; unsigned char bytes[CMSG_SPACE(sizeof(int))]; } ancillary;
    memset(&ancillary, 0, sizeof(ancillary));
    struct msghdr message;
    memset(&message, 0, sizeof(message));
    message.msg_iov = &payload;
    message.msg_iovlen = 1;
    message.msg_control = ancillary.bytes;
    message.msg_controllen = sizeof(ancillary.bytes);
    struct cmsghdr *header = CMSG_FIRSTHDR(&message);
    header->cmsg_level = SOL_SOCKET;
    header->cmsg_type = SCM_RIGHTS;
    header->cmsg_len = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(header), &socket_fd, sizeof(int));
    for (;;) {
        if (wait_ready(env, control, POLLOUT, end) != 0) return;
        ssize_t count = sendmsg(control, &message, flags);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count < 0) fail_errno(env, "sendmsg SCM_RIGHTS");
        else if (count != 1) fail(env, "Incomplete descriptor transfer");
        return;
    }
}

JNIEXPORT jint JNICALL JNI_METHOD(receiveFd)(JNIEnv *env, jclass type, jint control,
        jint timeout) {
    (void) type;
    int64_t end = deadline(env, timeout);
    if (end < 0 || unix_stream(env, control, 1) != 0) return -1;
    for (;;) {
        if (wait_ready(env, control, POLLIN, end) != 0) return -1;
        unsigned char marker = 0;
        struct iovec payload = {&marker, 1};
        union {
            struct cmsghdr alignment;
            unsigned char bytes[CMSG_SPACE(8 * sizeof(int))];
        } ancillary;
        memset(&ancillary, 0, sizeof(ancillary));
        struct msghdr message;
        memset(&message, 0, sizeof(message));
        message.msg_iov = &payload;
        message.msg_iovlen = 1;
        message.msg_control = ancillary.bytes;
        message.msg_controllen = sizeof(ancillary.bytes);
        int flags = MSG_DONTWAIT;
#ifdef MSG_CMSG_CLOEXEC
        flags |= MSG_CMSG_CLOEXEC;
#endif
        ssize_t count = recvmsg(control, &message, flags);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count < 0) {
            fail_errno(env, "recvmsg SCM_RIGHTS");
            return -1;
        }
        int received[8], total = 0;
        int valid = count == 1 && marker == FD_MARKER
                && (message.msg_flags & (MSG_CTRUNC | MSG_TRUNC)) == 0;
        for (struct cmsghdr *header = CMSG_FIRSTHDR(&message); header != NULL;
                header = CMSG_NXTHDR(&message, header)) {
            size_t offset = (size_t) ((unsigned char *) header - ancillary.bytes);
            if (header->cmsg_len < CMSG_LEN(0)
                    || header->cmsg_len > message.msg_controllen - offset) {
                valid = 0;
                break;
            }
            if (header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS) {
                valid = 0;
                continue;
            }
            size_t size = header->cmsg_len - CMSG_LEN(0);
            if (size % sizeof(int) != 0) valid = 0;
            for (size_t i = 0; i < size / sizeof(int); ++i) {
                int fd;
                memcpy(&fd, (unsigned char *) CMSG_DATA(header) + i * sizeof(int), sizeof(fd));
                if (total < 8) received[total++] = fd;
                else { (void) close(fd); valid = 0; }
            }
        }
        if (total != 1) valid = 0;
        if (valid && (cloexec(env, received[0]) != 0
                || unix_stream(env, received[0], 1) != 0)) valid = 0;
        if (!valid) {
            for (int i = 0; i < total; ++i) (void) close(received[i]);
            fail(env, "Rejected descriptor frame");
            return -1;
        }
        return received[0];
    }
}

JNIEXPORT void JNICALL JNI_METHOD(writeLine)(JNIEnv *env, jclass type, jint fd,
        jstring value, jint timeout) {
    (void) type;
    char buffer[MAX_LINE + 2];
    int length = ascii(env, value, buffer, MAX_LINE + 1);
    if (length < 0) return;
    int64_t end = deadline(env, timeout);
    if (end < 0) return;
    int flags = send_flags(env, fd);
    if (flags < 0) return;
    buffer[length++] = '\n';
    size_t sent = 0;
    while (sent < (size_t) length) {
        if (wait_ready(env, fd, POLLOUT, end) != 0) return;
        ssize_t count = send(fd, buffer + sent, (size_t) length - sent, flags);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count <= 0) {
            if (count < 0) fail_errno(env, "send line");
            else fail(env, "No progress sending line");
            return;
        }
        sent += (size_t) count;
    }
}

static jstring read_ascii_line(JNIEnv *env, int fd, int limit, int64_t end) {
    char buffer[MAX_LINE + 1];
    int length = 0;
    for (;;) {
        if (wait_ready(env, fd, POLLIN, end) != 0) return NULL;
        unsigned char byte;
        ssize_t count = recv(fd, &byte, 1, MSG_DONTWAIT);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count <= 0) {
            if (count < 0) fail_errno(env, "recv line");
            else fail(env, "EOF before complete line");
            return NULL;
        }
        if (byte == '\n') {
            buffer[length] = '\0';
            return (*env)->NewStringUTF(env, buffer);
        }
        if (byte < 0x20 || byte > 0x7e || length >= limit) {
            fail(env, "Invalid or oversized line");
            return NULL;
        }
        buffer[length++] = (char) byte;
    }
}

JNIEXPORT jstring JNICALL JNI_METHOD(readLine)(JNIEnv *env, jclass type, jint fd,
        jint limit, jint timeout) {
    (void) type;
    if (limit < 1 || limit > MAX_LINE) {
        fail(env, "Invalid line limit");
        return NULL;
    }
    int64_t end = deadline(env, timeout);
    return end < 0 ? NULL : read_ascii_line(env, fd, limit, end);
}

JNIEXPORT jstring JNICALL JNI_METHOD(request)(JNIEnv *env, jclass type, jint fd,
        jchar command, jint timeout) {
    (void) type;
    if (command != 'I' && command != 'Q') {
        fail(env, "Only I or Q may be requested");
        return NULL;
    }
    int64_t end = deadline(env, timeout);
    if (end < 0 || unix_stream(env, fd, 1) != 0) return NULL;
    int flags = send_flags(env, fd);
    if (flags < 0) return NULL;
    unsigned char byte = (unsigned char) command;
    for (;;) {
        if (wait_ready(env, fd, POLLOUT, end) != 0) return NULL;
        ssize_t count = send(fd, &byte, 1, flags);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count != 1) {
            if (count < 0) fail_errno(env, "send legacy request");
            else fail(env, "Incomplete legacy request");
            return NULL;
        }
        break;
    }
    return read_ascii_line(env, fd, MAX_LINE, end);
}

#if defined(__linux__) && (defined(__aarch64__) || defined(__arm__) \
        || defined(__i386__) || defined(__x86_64__))
#define HAS_PIDFD_SYSCALLS 1
#ifndef SYS_pidfd_open
#define SYS_pidfd_open 434
#endif
#ifndef SYS_pidfd_send_signal
#define SYS_pidfd_send_signal 424
#endif
#endif

#if defined(HAS_PIDFD_SYSCALLS) || defined(RETIREMENT_HOST_IMAGE_TEST)
static int daemon_cmdline(const unsigned char *bytes, size_t length) {
    if (length == 0 || length > MAX_CMDLINE || bytes[length - 1] != '\0') return 0;
    for (size_t i = 0; i < length; ++i) {
        if (bytes[i] != '\0' && (bytes[i] < 0x20 || bytes[i] > 0x7e)) return 0;
    }
    size_t offset = 0;
    for (int argument = 0; argument < 7; ++argument) {
        if (offset >= length) return 0;
        const unsigned char *end = memchr(bytes + offset, '\0', length - offset);
        if (end == NULL) return 0;
        size_t size = (size_t) (end - (bytes + offset));
        const unsigned char *value = bytes + offset;
        if (argument == 0 && (size != 11 || memcmp(value, "app_process", 11) != 0)) return 0;
        if (argument == 1 && (size != 1 || value[0] != '/')) return 0;
        if (argument == 2 && (size != 1 || value[0] != 'D')) return 0;
        if ((argument == 3 || argument == 5 || argument == 6)
                && (size != 1 || (value[0] != '0' && value[0] != '1'))) return 0;
        if (argument == 4 && !((size == 4 && memcmp(value, "hold", 4) == 0)
                || (size == 3 && memcmp(value, "run", 3) == 0))) return 0;
        offset += size + 1;
    }
    return 1;
}
#endif

#ifdef HAS_PIDFD_SYSCALLS
static int alive(JNIEnv *env, int fd) {
    for (int attempt = 0; attempt < 8; ++attempt) {
        struct pollfd item = {fd, POLLIN, 0};
        int result = poll(&item, 1, 0);
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) fail_errno(env, "poll process descriptor");
        else if (result != 0) fail(env, "Bound process already exited or descriptor failed");
        return result == 0 ? 0 : -1;
    }
    fail(env, "Process liveness check repeatedly interrupted");
    return -1;
}

static int verified_process_image(JNIEnv *env, int pidfd, pid_t pid, int64_t end) {
    if (end < 0 || alive(env, pidfd) != 0 || remaining(env, end) < 0) return -1;
    char path[64];
    int length = snprintf(path, sizeof(path), "/proc/%ld", (long) pid);
    if (pid <= 100 || length <= 0 || (size_t) length >= sizeof(path)) {
        fail(env, "Invalid retained process identity");
        return -1;
    }
    int directory = open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (directory < 0) {
        fail_errno(env, "open retained process directory");
        return -1;
    }
    int fd = -1, result = -1;
    struct stat identity;
    if (fstat(directory, &identity) != 0 || !S_ISDIR(identity.st_mode) || identity.st_uid != 0) {
        fail(env, "Retained process directory is not root-owned");
        goto done;
    }
    fd = openat(directory, "cmdline", O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0) {
        fail_errno(env, "open retained process cmdline");
        goto done;
    }
    if (fstat(fd, &identity) != 0 || !S_ISREG(identity.st_mode) || identity.st_uid != 0) {
        fail(env, "Retained process cmdline is not root-owned");
        goto done;
    }
    unsigned char buffer[MAX_CMDLINE + 1];
    size_t used = 0;
    for (;;) {
        if (remaining(env, end) < 0) goto done;
        ssize_t count = read(fd, buffer + used, sizeof(buffer) - used);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            if (wait_ready(env, fd, POLLIN, end) != 0) goto done;
            continue;
        }
        if (count < 0) {
            fail_errno(env, "read retained process cmdline");
            goto done;
        }
        if (count == 0) break;
        used += (size_t) count;
        if (used > MAX_CMDLINE) {
            fail(env, "Retained process cmdline exceeds bound");
            goto done;
        }
    }
    if (alive(env, pidfd) != 0 || remaining(env, end) < 0) goto done;
    if (fstat(directory, &identity) != 0 || identity.st_uid != 0
            || fstat(fd, &identity) != 0 || identity.st_uid != 0
            || !daemon_cmdline(buffer, used)) {
        fail(env, "Retained process does not match app_process / D flags");
        goto done;
    }
    result = 0;
done:
    if (fd >= 0 && close(fd) != 0 && errno != EINTR) {
        fail_errno(env, "close retained process cmdline");
        result = -1;
    }
    if (close(directory) != 0 && errno != EINTR) {
        fail_errno(env, "close retained process directory");
        result = -1;
    }
    return result;
}

static int quiet_control(JNIEnv *env, int fd) {
    if (unix_stream(env, fd, 1) != 0) return -1;
    unsigned char byte;
    ssize_t count = recv(fd, &byte, 1, MSG_PEEK | MSG_DONTWAIT);
    if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return 0;
    if (count < 0) fail_errno(env, "control liveness");
    else fail(env, count == 0 ? "Control EOF" : "Pending control data/cancellation");
    return -1;
}

static int retained_process(JNIEnv *env, int fd, pid_t *pid) {
    int duplicate = -1;
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (processes[i].used && processes[i].fd == fd) {
            struct stat current;
            if (fstat(fd, &current) == 0 && current.st_dev == processes[i].device
                    && current.st_ino == processes[i].inode) {
                duplicate = fcntl(fd, F_DUPFD_CLOEXEC, 0);
                *pid = processes[i].pid;
            }
            break;
        }
    }
    pthread_mutex_unlock(&ownership_lock);
    if (duplicate < 0) fail(env, "No retained native process descriptor");
    return duplicate;
}
#endif

JNIEXPORT jint JNICALL JNI_METHOD(bindProcess)(JNIEnv *env, jclass type, jint socket_fd) {
    (void) type;
#ifdef HAS_PIDFD_SYSCALLS
    if (geteuid() != 0) {
        fail(env, "Process binding requires root helper");
        return -1;
    }
    peer credentials;
    if (peer_credentials(env, socket_fd, &credentials) != 0) return -1;
    if (credentials.uid != 0 || credentials.pid <= 100) {
        fail(env, "Expected authenticated root daemon peer");
        return -1;
    }
    int fd = (int) syscall(SYS_pidfd_open, credentials.pid, 0);
    if (fd < 0) {
        fail_errno(env, "pidfd_open unavailable or denied");
        return -1;
    }
    struct stat identity;
    if (cloexec(env, fd) != 0
            || verified_process_image(env, fd, credentials.pid, deadline(env, IDENTITY_TIMEOUT_MS)) != 0
            || quiet_control(env, socket_fd) != 0 || fstat(fd, &identity) != 0) {
        fail(env, "Cannot retain live process descriptor");
        (void) close(fd);
        return -1;
    }
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (!processes[i].used) {
            processes[i] = (process_entry) {1, fd, credentials.pid, identity.st_dev, identity.st_ino};
            pthread_mutex_unlock(&ownership_lock);
            return fd;
        }
    }
    pthread_mutex_unlock(&ownership_lock);
    (void) close(fd);
    fail(env, "Too many retained process descriptors");
    return -1;
#else
    (void) socket_fd;
    fail(env, "Process descriptors unsupported on this platform/architecture");
    return -1;
#endif
}

JNIEXPORT void JNICALL JNI_METHOD(requireSameProcess)(JNIEnv *env, jclass type,
        jint pidfd, jint socket_fd, jint expected_pid) {
    (void) type;
#ifdef HAS_PIDFD_SYSCALLS
    pid_t retained_pid = -1;
    int retained = retained_process(env, pidfd, &retained_pid);
    if (retained < 0) return;
    peer credentials;
    if (expected_pid <= 100 || retained_pid != expected_pid
            || peer_credentials(env, socket_fd, &credentials) != 0
            || credentials.uid != 0 || credentials.pid != retained_pid) {
        fail(env, "Socket no longer identifies the retained root process");
    } else {
        (void) verified_process_image(env, retained, retained_pid, deadline(env, IDENTITY_TIMEOUT_MS));
    }
    (void) close(retained);
#else
    (void) pidfd;
    (void) socket_fd;
    (void) expected_pid;
    fail(env, "Process descriptors unsupported on this platform/architecture");
#endif
}

JNIEXPORT void JNICALL JNI_METHOD(signalAndAwaitExit)(JNIEnv *env, jclass type,
        jint pidfd, jint control, jint timeout) {
    (void) type;
#ifdef HAS_PIDFD_SYSCALLS
    int64_t end = deadline(env, timeout);
    if (end < 0) return;
    if (geteuid() != 0) {
        fail(env, "Retirement signal requires root helper");
        return;
    }
    pid_t pid = -1;
    int retained = retained_process(env, pidfd, &pid);
    if (retained < 0) return;
    peer credentials;
    if (peer_credentials(env, control, &credentials) != 0
            || verified_process_image(env, retained, pid, end) != 0
            || quiet_control(env, control) != 0
            || remaining(env, end) < 0) goto done;
    if (syscall(SYS_pidfd_send_signal, retained, SIGTERM, NULL, 0) != 0) {
        fail_errno(env, "pidfd_send_signal unavailable or denied");
        goto done;
    }
    for (;;) {
        int ms = remaining(env, end);
        if (ms < 0) goto done;
        struct pollfd items[2] = {{retained, POLLIN, 0}, {control, POLLIN, 0}};
        int result = poll(items, 2, ms);
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) {
            fail_errno(env, "poll retirement exit");
            goto done;
        }
        if (result == 0) continue;
        if (remaining(env, end) < 0) goto done;
        if (items[1].revents != 0 || quiet_control(env, control) != 0) {
            fail(env, "Control cancelled or lost before confirmed exit");
            goto done;
        }
        if ((items[0].revents & (POLLERR | POLLHUP | POLLNVAL)) != 0
                && (items[0].revents & POLLIN) == 0) {
            fail(env, "Process descriptor failed without exit");
            goto done;
        }
        if ((items[0].revents & POLLIN) != 0) goto done;
    }
done:
    (void) close(retained);
#else
    (void) pidfd;
    (void) control;
    (void) timeout;
    fail(env, "Process descriptor signalling unsupported on this platform/architecture");
#endif
}

JNIEXPORT void JNICALL JNI_METHOD(close)(JNIEnv *env, jclass type, jint fd) {
    (void) type;
    if (fd < 0) {
        fail(env, "Invalid descriptor");
        return;
    }
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (servers[i].used && servers[i].fd == fd) {
            (void) remove_server(env, &servers[i]);
            pthread_mutex_unlock(&ownership_lock);
            return;
        }
        if (processes[i].used && processes[i].fd == fd) {
            struct stat identity;
            int matches = fstat(fd, &identity) == 0
                    && identity.st_dev == processes[i].device
                    && identity.st_ino == processes[i].inode;
            processes[i].used = 0;
            if (!matches) {
                pthread_mutex_unlock(&ownership_lock);
                fail(env, "Retained process descriptor changed; refusing close");
                return;
            }
        }
    }
    int result = close(fd);
    int saved = errno;
    pthread_mutex_unlock(&ownership_lock);
    if (result != 0 && saved != EINTR) {
        errno = saved;
        fail_errno(env, "close");
    }
}

JNIEXPORT void JNICALL JNI_METHOD(cleanupServer)(JNIEnv *env, jclass type,
        jint fd, jstring value) {
    (void) type;
    char path[sizeof(((struct sockaddr_un *) 0)->sun_path)];
    if (ascii(env, value, path, sizeof(path)) < 0) return;
    pthread_mutex_lock(&ownership_lock);
    for (int i = 0; i < MAX_OWNED; ++i) {
        if (servers[i].used && servers[i].fd == fd) {
            if (strcmp(path, servers[i].path) != 0) {
                fail(env, "Cleanup path does not match owned listener");
            } else {
                (void) remove_server(env, &servers[i]);
            }
            pthread_mutex_unlock(&ownership_lock);
            return;
        }
    }
    pthread_mutex_unlock(&ownership_lock);
    fail(env, "No owned listener to clean");
}
