/* HOST-only inert syscall boundary around the actual request implementation. */
#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>

static ssize_t boundary_send(int, const void *, size_t, int);
static ssize_t boundary_recv(int, void *, size_t, int);
static int boundary_poll(struct pollfd *, nfds_t, int);
static int boundary_clock(clockid_t, struct timespec *);
static int boundary_getsockopt(int, int, int, void *, socklen_t *);
static int boundary_address(int, struct sockaddr *, socklen_t *);
#if !defined(MSG_NOSIGNAL) && defined(SO_NOSIGPIPE)
static int boundary_setsockopt(int, int, int, const void *, socklen_t);
#define setsockopt boundary_setsockopt
#endif
#define send boundary_send
#define recv boundary_recv
#define poll boundary_poll
#define clock_gettime boundary_clock
#define getsockopt boundary_getsockopt
#define getsockname boundary_address
#define getpeername boundary_address
#define RETIREMENT_HOST_IMAGE_TEST 1
#define RETIREMENT_HOST_CONNECT_TEST 1
#include "../retirement.c"
#undef send
#undef recv
#undef poll
#undef clock_gettime
#undef getsockopt
#undef getsockname
#undef getpeername
#undef setsockopt

static unsigned char input[MAX_LINE + 32];
static size_t input_size, input_index;
static unsigned char output[4];
static size_t output_size;
static int64_t virtual_ms;
static int write_delay, read_delay, read_waited;

static ssize_t boundary_send(int fd, const void *buffer, size_t length, int flags) {
    (void) flags;
    if (fd != 42 || length > sizeof(output) - output_size) {
        errno = EINVAL;
        return -1;
    }
    memcpy(output + output_size, buffer, length);
    output_size += length;
    return (ssize_t) length;
}

static ssize_t boundary_recv(int fd, void *buffer, size_t length, int flags) {
    (void) flags;
    if (fd != 42 || length != 1) {
        errno = EINVAL;
        return -1;
    }
    if (input_index == input_size) return 0;
    memcpy(buffer, input + input_index++, 1);
    return 1;
}

static int boundary_poll(struct pollfd *items, nfds_t count, int timeout) {
    if (count != 1 || items[0].fd != 42 || timeout < 0) {
        errno = EINVAL;
        return -1;
    }
    int delay = 0;
    if (items[0].events == POLLOUT) delay = write_delay;
    else if (items[0].events == POLLIN && !read_waited) delay = read_delay;
    if (delay > timeout) {
        virtual_ms += timeout;
        return 0;
    }
    virtual_ms += delay;
    if (items[0].events == POLLIN) read_waited = 1;
    items[0].revents = items[0].events;
    return 1;
}

static int boundary_clock(clockid_t clock, struct timespec *value) {
    if (clock != CLOCK_MONOTONIC) {
        errno = EINVAL;
        return -1;
    }
    value->tv_sec = (time_t) (virtual_ms / 1000);
    value->tv_nsec = (long) (virtual_ms % 1000) * 1000000L;
    return 0;
}

static int boundary_getsockopt(int fd, int level, int option, void *value, socklen_t *size) {
    if (fd != 42 || level != SOL_SOCKET || option != SO_TYPE || *size != sizeof(int)) {
        errno = EINVAL;
        return -1;
    }
    int type = SOCK_STREAM;
    memcpy(value, &type, sizeof(type));
    return 0;
}

static int boundary_address(int fd, struct sockaddr *value, socklen_t *size) {
    if (fd != 42 || *size < sizeof(struct sockaddr_un)) {
        errno = EINVAL;
        return -1;
    }
    memset(value, 0, sizeof(struct sockaddr_un));
    value->sa_family = AF_UNIX;
    *size = sizeof(struct sockaddr_un);
    return 0;
}

#if !defined(MSG_NOSIGNAL) && defined(SO_NOSIGPIPE)
static int boundary_setsockopt(int fd, int level, int option, const void *value, socklen_t size) {
    (void) value;
    if (fd == 42 && level == SOL_SOCKET && option == SO_NOSIGPIPE && size == sizeof(int)) return 0;
    errno = EINVAL;
    return -1;
}
#endif

JNIEXPORT void JNICALL Java_com_thor_displaypowertest_RetirementRequestHostTest_setup(
        JNIEnv *env, jclass type, jbyteArray data, jint send_ms, jint reply_ms) {
    (void) type;
    jsize length = (*env)->GetArrayLength(env, data);
    if (length < 0 || (size_t) length > sizeof(input) || send_ms < 0 || reply_ms < 0) {
        fail(env, "Invalid inert test input");
        return;
    }
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte *) input);
    input_size = (size_t) length;
    input_index = 0;
    output_size = 0;
    virtual_ms = 0;
    write_delay = send_ms;
    read_delay = reply_ms;
    read_waited = 0;
}

JNIEXPORT jbyteArray JNICALL Java_com_thor_displaypowertest_RetirementRequestHostTest_written(
        JNIEnv *env, jclass type) {
    (void) type;
    jbyteArray value = (*env)->NewByteArray(env, (jsize) output_size);
    if (value != NULL) (*env)->SetByteArrayRegion(env, value, 0, (jsize) output_size, (jbyte *) output);
    return value;
}

JNIEXPORT jint JNICALL Java_com_thor_displaypowertest_RetirementRequestHostTest_unread(
        JNIEnv *env, jclass type) {
    (void) env;
    (void) type;
    return (jint) (input_size - input_index);
}

JNIEXPORT jboolean JNICALL Java_com_thor_displaypowertest_RetirementImageHostTest_allowed(
        JNIEnv *env, jclass type, jbyteArray bytes) {
    (void) type;
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length <= 0 || length > MAX_CMDLINE) return JNI_FALSE;
    unsigned char buffer[MAX_CMDLINE];
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *) buffer);
    if ((*env)->ExceptionCheck(env)) return JNI_FALSE;
    return daemon_cmdline(buffer, (size_t) length) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_thor_displaypowertest_RetirementConnectHostTest_allowed(
        JNIEnv *env, jclass type, jstring value, jint real_uid, jint effective_uid,
        jint application_owner, jint expected_uid, jint peer_uid, jint peer_pid,
        jint file_uid, jint mode, jboolean same_inode) {
    (void) type;
    if (real_uid < 0 || effective_uid < 0 || application_owner < 0 || peer_uid < 0 || file_uid < 0) {
        return JNI_FALSE;
    }
    char path[sizeof(((struct sockaddr_un *) 0)->sun_path)];
    if (ascii(env, value, path, sizeof(path)) < 0) return JNI_FALSE;
    int role = connect_role(path, (uid_t) real_uid, (uid_t) effective_uid,
                            (uid_t) application_owner, expected_uid);
    struct stat before;
    memset(&before, 0, sizeof(before));
    before.st_mode = (mode_t) mode;
    before.st_uid = (uid_t) file_uid;
    before.st_dev = 1;
    before.st_ino = 2;
    struct stat after = before;
    if (!same_inode) after.st_ino = 3;
    peer credentials = {(pid_t) peer_pid, (uid_t) peer_uid};
    return connected_endpoint_allowed(role, (uid_t) application_owner, expected_uid,
            &before, &after, &credentials) ? JNI_TRUE : JNI_FALSE;
}
