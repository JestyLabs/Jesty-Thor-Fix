#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/pidfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

/* Disposable native mechanism fixture, not a daemon or legacy protocol.
 * No externally supplied PID, app path, vendor service or product code.
 */
#define CLIENT_UID 10042
#define WRONG_UID 10043
#define MAX_CHILDREN 24
struct observed { volatile int commands; volatile int denied; };
struct child { pid_t pid; int reaped; struct observed *seen; };
struct message { uint64_t nonce; int command; };
struct reply { struct message request; pid_t server; uid_t client; };
static struct child children[MAX_CHILDREN];
static int child_count, ignore_peer, suppress_signal, signals_sent;
static unsigned long sequence;

enum result { RETIRED, UID_DENIED, PEER_CHANGED, ORIGINAL_GONE,
              NATIVE_DENIED, NO_EXIT, PROTOCOL_FAILED };

static void cleanup(void);
static void fail(const char *code) {
    cleanup();
    fprintf(stderr, "PROCESS_BINDING_FAIL:%s\n", code);
    exit(1);
}
static void require(int value, const char *code) { if (!value) fail(code); }
static void deadline(int signal_number) { (void)signal_number; _exit(90); }

static int transfer(int fd, void *data, size_t length, int sending) {
    size_t done = 0;
    while (done < length) {
        ssize_t count = sending ? send(fd, (char *)data + done, length - done, MSG_NOSIGNAL)
                                : recv(fd, (char *)data + done, length - done, 0);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return 0;
        done += (size_t)count;
    }
    return 1;
}
static void socket_timeout(int fd) {
    struct timeval limit = {1, 0};
    if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &limit, sizeof(limit)) != 0
            || setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &limit, sizeof(limit)) != 0) _exit(91);
}
static void child_server(const char *path, uid_t server_uid, int ready, struct observed *seen) {
    alarm(20);
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (listener < 0) _exit(92);
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    if (strlen(path) >= sizeof(address.sun_path)) _exit(93);
    strcpy(address.sun_path, path);
    if (bind(listener, (struct sockaddr *)&address, sizeof(address)) != 0
            || chown(path, CLIENT_UID, CLIENT_UID) != 0 || chmod(path, 0600) != 0) _exit(94);
    /* Credentials for a listening Unix socket are established at listen(). */
    if (server_uid != 0 && (setgid(server_uid) != 0 || setuid(server_uid) != 0)) _exit(95);
    if (listen(listener, 4) != 0 || write(ready, "R", 1) != 1) _exit(96);
    close(ready);
    for (;;) {
        int peer = accept4(listener, NULL, NULL, SOCK_CLOEXEC);
        if (peer < 0) { if (errno == EINTR) continue; _exit(97); }
        socket_timeout(peer);
        struct ucred credential;
        socklen_t size = sizeof(credential);
        if (getsockopt(peer, SOL_SOCKET, SO_PEERCRED, &credential, &size) != 0) _exit(98);
        /* The old server's app-UID admission is preserved, not broadened. */
        if (credential.uid != CLIENT_UID) {
            seen->denied++;
            close(peer);
            continue;
        }
        struct message request;
        if (transfer(peer, &request, sizeof(request), 0)) {
            seen->commands++;
            struct reply response = {.request = request, .server = getpid(), .client = credential.uid};
            (void)transfer(peer, &response, sizeof(response), 1);
        }
        close(peer);
    }
}
static struct child *start_server(const char *path, uid_t server_uid) {
    require(child_count < MAX_CHILDREN, "CHILD_LIMIT");
    require(unlink(path) == 0 || errno == ENOENT, "FIXTURE_UNLINK");
    struct observed *seen = mmap(NULL, sizeof(*seen), PROT_READ | PROT_WRITE,
                                MAP_SHARED | MAP_ANONYMOUS, -1, 0);
    require(seen != MAP_FAILED, "FIXTURE_SHARED_STATE");
    int ready[2];
    require(pipe2(ready, O_CLOEXEC) == 0, "FIXTURE_PIPE");
    pid_t pid = fork();
    require(pid >= 0, "FIXTURE_FORK");
    if (pid == 0) { close(ready[0]); child_server(path, server_uid, ready[1], seen); _exit(99); }
    close(ready[1]);
    struct child *child = &children[child_count++];
    *child = (struct child){.pid = pid, .seen = seen};
    struct pollfd waiting = {.fd = ready[0], .events = POLLIN};
    char value = 0;
    int ok = poll(&waiting, 1, 3000) > 0 && read(ready[0], &value, 1) == 1 && value == 'R';
    close(ready[0]);
    require(ok, "FIXTURE_SERVER_READY");
    return child;
}
static int alive(struct child *child) {
    if (child->reaped) return 0;
    int status;
    pid_t result = waitpid(child->pid, &status, WNOHANG);
    if (result == child->pid) { child->reaped = 1; return 0; }
    return result == 0;
}
static void stop_child(struct child *child) {
    if (!alive(child)) return;
    /* Only PIDs returned by this fixture's fork(), never peer/input PIDs. */
    (void)kill(child->pid, SIGKILL);
    int status;
    while (waitpid(child->pid, &status, 0) < 0 && errno == EINTR) { }
    child->reaped = 1;
}
static void cleanup(void) {
    if (geteuid() != 0 && seteuid(0) != 0) _exit(89);
    for (int i = 0; i < child_count; i++) stop_child(&children[i]);
}
static int exited(int descriptor, int timeout) {
    struct pollfd state = {.fd = descriptor, .events = POLLIN};
    int result;
    do { result = poll(&state, 1, timeout); } while (result < 0 && errno == EINTR);
    return result > 0 && (state.revents & (POLLIN | POLLHUP)) != 0;
}
static int connect_as(const char *path, uid_t uid, struct ucred *credential) {
    require(seteuid(uid) == 0, "EFFECTIVE_UID_DROP");
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    strcpy(address.sun_path, path);
    int ok = fd >= 0 && connect(fd, (struct sockaddr *)&address, sizeof(address)) == 0;
    if (ok) {
        socklen_t size = sizeof(*credential);
        ok = getsockopt(fd, SOL_SOCKET, SO_PEERCRED, credential, &size) == 0;
    }
    require(seteuid(0) == 0, "EFFECTIVE_UID_RESTORE");
    if (!ok) { if (fd >= 0) close(fd); return -1; }
    socket_timeout(fd);
    return fd;
}
static int challenge(int fd, pid_t pid, int command) {
    struct message request = {.nonce = ++sequence, .command = command};
    struct reply response;
    return transfer(fd, &request, sizeof(request), 1)
        && transfer(fd, &response, sizeof(response), 0)
        && response.request.nonce == request.nonce && response.request.command == command
        && response.server == pid && response.client == CLIENT_UID;
}
enum interference { NONE, REPLACE_BETWEEN, EXIT_BEFORE_OPEN, EXIT_BEFORE_SIGNAL, UNSUPPORTED, NO_EFFECT };
static struct child *replacement;
static enum result attempt(const char *path, struct child *original, enum interference change) {
    enum result result = NATIVE_DENIED;
    struct ucred first_peer, second_peer;
    int first = connect_as(path, CLIENT_UID, &first_peer), second = -1, handle = -1;
    if (first < 0) goto finish;
    if (!ignore_peer && first_peer.uid != 0) { result = UID_DENIED; goto finish; }
    if (change == EXIT_BEFORE_OPEN) stop_child(original);
    if (change == UNSUPPORTED) { errno = ENOSYS; goto finish; }
    /* A handle is opened before the first live challenge, not after a cached reply. */
    handle = pidfd_open(first_peer.pid, 0);
    if (handle < 0) goto finish;
    if (!challenge(first, first_peer.pid, 'I')) { result = PROTOCOL_FAILED; goto finish; }
    if (change == REPLACE_BETWEEN) { stop_child(original); replacement = start_server(path, 0); }
    second = connect_as(path, CLIENT_UID, &second_peer);
    if (second < 0) goto finish;
    if (!ignore_peer && (second_peer.uid != 0 || second_peer.pid != first_peer.pid)) {
        result = PEER_CHANGED; goto finish;
    }
    if (!challenge(second, first_peer.pid, 'Q')) { result = PROTOCOL_FAILED; goto finish; }
    if (change == EXIT_BEFORE_SIGNAL) { stop_child(original); replacement = start_server(path, 0); }
    if (exited(handle, 0)) { result = ORIGINAL_GONE; goto finish; }
    if (!suppress_signal && change != NO_EFFECT) {
        if (pidfd_send_signal(handle, SIGTERM, NULL, 0) != 0) goto finish;
        signals_sent++;
    }
    result = exited(handle, 600) ? RETIRED : NO_EXIT;
finish:
    if (first >= 0) close(first);
    if (second >= 0) close(second);
    if (handle >= 0) close(handle);
    return result;
}
static void passed(const char *name) { printf("PROCESS_BINDING_PASS:%s\n", name); }
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    if (strcmp(argv[1], "ignore-peer") == 0) ignore_peer = 1;
    else if (strcmp(argv[1], "no-signal") == 0) suppress_signal = 1;
    else if (strcmp(argv[1], "plain") != 0) return 2;
    require(getuid() == 0 && geteuid() == 0, "FIXTURE_REQUIRES_ROOT_EMULATOR");
    struct sigaction action = {.sa_handler = deadline};
    sigemptyset(&action.sa_mask);
    require(sigaction(SIGALRM, &action, NULL) == 0, "FIXTURE_DEADLINE");
    alarm(60);
    /* Only this disposable fixture's cwd is used; app directories are never opened. */
    require(chmod(".", 0711) == 0, "FIXTURE_DIRECTORY");
    const char *path = "endpoint.sock";
    struct child *sibling = start_server("sibling.sock", 0);
    struct child *original = start_server(path, 0);
    require(attempt(path, original, NONE) == RETIRED, "EXPECTED_EXIT");
    int sibling_handle = pidfd_open(sibling->pid, 0);
    require(sibling_handle >= 0, "SIBLING_HANDLE");
    require(exited(sibling_handle, 0) == 0 && alive(sibling), "SIBLING_CHANGED");
    close(sibling_handle);
    passed("descriptor_targets_endpoint_not_sibling");
    original = start_server(path, WRONG_UID);
    int before = signals_sent;
    require(attempt(path, original, NONE) == UID_DENIED, "EXPECTED_UID_DENIAL");
    require(original->seen->commands == 0 && signals_sent == before && alive(original), "UID_DENIAL_SIDE_EFFECT");
    passed("wrong_server_uid_rejected_before_command");
    stop_child(original);
    original = start_server(path, 0);
    require(attempt(path, original, REPLACE_BETWEEN) == PEER_CHANGED, "EXPECTED_CHANGED_PEER");
    require(alive(replacement) && replacement->seen->commands == 0 && signals_sent == before, "REPLACEMENT_SIDE_EFFECT");
    passed("replacement_between_reads_rejected");
    stop_child(replacement);
    original = start_server(path, 0);
    require(attempt(path, original, EXIT_BEFORE_OPEN) == NATIVE_DENIED, "EXPECTED_EARLY_EXIT_DENIAL");
    require(signals_sent == before, "EARLY_EXIT_SIGNALLED");
    passed("exit_before_handle_rejected");
    original = start_server(path, 0);
    require(attempt(path, original, EXIT_BEFORE_SIGNAL) == ORIGINAL_GONE, "EXPECTED_ORIGINAL_EXIT");
    require(alive(replacement) && signals_sent == before, "FINAL_REPLACEMENT_SIGNALLED");
    passed("exit_before_signal_preserves_replacement");
    stop_child(replacement);
    original = start_server(path, 0);
    require(attempt(path, original, UNSUPPORTED) == NATIVE_DENIED, "EXPECTED_UNSUPPORTED_DENIAL");
    require(original->seen->commands == 0 && alive(original) && signals_sent == before, "UNSUPPORTED_SIDE_EFFECT");
    passed("unsupported_primitive_rejected");
    require(attempt(path, original, NO_EFFECT) == NO_EXIT, "EXPECTED_NO_EFFECT_DENIAL");
    require(alive(original) && signals_sent == before, "NO_EFFECT_SIDE_EFFECT");
    passed("submission_without_exit_is_not_success");
    struct ucred root_server;
    int root_client = connect_as(path, 0, &root_server);
    require(root_client >= 0 && !challenge(root_client, original->pid, 'I'), "ROOT_CLIENT_MUST_NOT_BE_ADMITTED");
    close(root_client);
    require(original->seen->denied == 1 && alive(original), "APP_UID_ADMISSION_CHANGED");
    passed("server_preserves_app_uid_admission");
    cleanup();
    puts("PROCESS_BINDING_COMPLETE:8");
    return 0;
}
