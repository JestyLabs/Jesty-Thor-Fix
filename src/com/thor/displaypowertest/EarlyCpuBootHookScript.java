package com.thor.displaypowertest;

/**
 * Deterministic generator for the stock-pservice early CPU restart prototype.
 *
 * This class only returns shell text. It has no file, property, service or
 * display authority by itself.
 */
public final class EarlyCpuBootHookScript {
    public static final String HOOK_PATH = "/data/boot_start.sh";
    public static final String PROTOTYPE_GATE =
            "/data/local/tmp/thor-pservice-early-cpu-restart-prototype";
    public static final String OPT_IN_PATH =
            "/data/user_de/0/com.thor.displaypowertest/files/jesty-thor-early-cpu-optin-v1";
    public static final String ATTEMPT_PATH =
            "/dev/jesty-thor-early-cpu-attempt-v1";
    public static final String TRACE_PATH =
            "/dev/jesty-thor-early-cpu-trace.log";
    public static final String PROPERTY =
            "vendor.display.disable_system_load_check";
    public static final String COMPOSER_SERVICE =
            "vendor.qti.hardware.display.composer";
    public static final String COMPOSER_PROCESS =
            "vendor.qti.hardware.display.composer-service";
    public static final String MAGIC = "JESTY_THOR_EARLY_CPU_HOOK_V1";

    private EarlyCpuBootHookScript() {}

    public static String build(int appUid, String installToken) {
        if (appUid < 10000 || appUid > 999999) {
            throw new IllegalArgumentException("app_uid");
        }
        if (installToken == null || !installToken.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("install_token");
        }

        StringBuilder s = new StringBuilder(6000);
        line(s, "#!/system/bin/sh");
        line(s, "# " + MAGIC);
        line(s, "# One-shot prototype: no display action and no direct framework restart.");
        line(s, "GATE='" + PROTOTYPE_GATE + "'");
        line(s, "OPT='" + OPT_IN_PATH + "'");
        line(s, "ATT='" + ATTEMPT_PATH + "'");
        line(s, "TRACE='" + TRACE_PATH + "'");
        line(s, "PROP='" + PROPERTY + "'");
        line(s, "COMP_SVC='" + COMPOSER_SERVICE + "'");
        line(s, "COMP_PROC='" + COMPOSER_PROCESS + "'");
        line(s, "APP_UID='" + appUid + "'");
        line(s, "TOKEN='" + installToken + "'");
        line(s, "");
        line(s, "now_ms(){ awk '{printf \"%d\", $1*1000}' /proc/uptime 2>/dev/null; }");
        line(s, "trace(){ T=$(now_ms); printf '%s;%s\\n' \"$T\" \"$1\" >>\"$TRACE\" 2>/dev/null; chmod 600 \"$TRACE\" 2>/dev/null; }");
        line(s, "first_pid(){ pidof \"$COMP_PROC\" 2>/dev/null | awk '{print $1}'; }");
        line(s, "valid_pid(){ case \"$1\" in ''|0|*[!0-9]*) return 1;; *) return 0;; esac; }");
        line(s, "write_attempt(){");
        line(s, "  PHASE=\"$1\"; T=$(now_ms); valid_pid \"$T\" || return 1");
        line(s, "  TMP=\"$ATT.tmp.$$\"; [ ! -e \"$TMP\" ] || return 1");
        line(s, "  umask 077");
        line(s, "  printf 'v1|%s|1|%s|%s|%s|%s\\n' \"$BOOT\" \"$PREV\" \"$BASE\" \"$PHASE\" \"$T\" >\"$TMP\" || return 1");
        line(s, "  chmod 600 \"$TMP\" || { rm -f \"$TMP\"; return 1; }");
        line(s, "  mv -f \"$TMP\" \"$ATT\" || { rm -f \"$TMP\"; return 1; }");
        line(s, "}");
        line(s, "restore_previous(){");
        line(s, "  if [ \"$PREV\" = UNSET ]; then setprop \"$PROP\" ''; else setprop \"$PROP\" \"$PREV\"; fi");
        line(s, "}");
        line(s, "restored(){");
        line(s, "  R=$(getprop \"$PROP\" 2>/dev/null)");
        line(s, "  if [ \"$PREV\" = UNSET ]; then [ -z \"$R\" ]; else [ \"$R\" = \"$PREV\" ]; fi");
        line(s, "}");
        line(s, "recover_before_request(){");
        line(s, "  CUR=$(first_pid)");
        line(s, "  if [ \"$CUR\" = \"$BASE\" ]; then");
        line(s, "    restore_previous");
        line(s, "    restored && write_attempt PREPARED");
        line(s, "  fi");
        line(s, "  trace \"RECOVER_$1\"");
        line(s, "}");
        line(s, "");
        line(s, "# Prototype gate must be an exact regular file containing one byte/value: 1.");
        line(s, "[ -f \"$GATE\" ] && [ ! -L \"$GATE\" ] || exit 0");
        line(s, "[ \"$(stat -c %h \"$GATE\" 2>/dev/null)\" = 1 ] || exit 0");
        line(s, "GU=$(stat -c %u \"$GATE\" 2>/dev/null); case \"$GU\" in 0|2000) ;; *) exit 0;; esac");
        line(s, "[ \"$(stat -c %a \"$GATE\" 2>/dev/null)\" = 644 ] || exit 0");
        line(s, "[ \"$(cat \"$GATE\" 2>/dev/null)\" = 1 ] || exit 0");
        line(s, "");
        line(s, "# Per-install Direct-Boot opt-in: regular, app-owned, private and exact.");
        line(s, "[ -f \"$OPT\" ] && [ ! -L \"$OPT\" ] || exit 0");
        line(s, "[ \"$(stat -c %h \"$OPT\" 2>/dev/null)\" = 1 ] || exit 0");
        line(s, "[ \"$(stat -c %u \"$OPT\" 2>/dev/null)\" = \"$APP_UID\" ] || exit 0");
        line(s, "[ \"$(stat -c %a \"$OPT\" 2>/dev/null)\" = 600 ] || exit 0");
        line(s, "[ \"$(cat \"$OPT\" 2>/dev/null)\" = \"v1:$TOKEN\" ] || exit 0");
        line(s, "");
        line(s, "# A real kernel boot recreates /dev. Any existing object means this boot already tried.");
        line(s, "[ ! -e \"$ATT\" ] && [ ! -L \"$ATT\" ] || exit 0");
        line(s, "");
        line(s, "BOOT=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)");
        line(s, "[ \"${#BOOT}\" = 36 ] || exit 0");
        line(s, "case \"$BOOT\" in *[!0-9a-fA-F-]*) exit 0;; esac");
        line(s, "");
        line(s, "BASE=''; I=0");
        line(s, "while [ \"$I\" -lt 20 ]; do");
        line(s, "  P=$(first_pid); if valid_pid \"$P\"; then BASE=\"$P\"; break; fi");
        line(s, "  I=$((I+1)); sleep 0.05");
        line(s, "done");
        line(s, "valid_pid \"$BASE\" || exit 0");
        line(s, "");
        line(s, "RAW=$(getprop \"$PROP\" 2>/dev/null)");
        line(s, "case \"$RAW\" in '') PREV=UNSET;; 0|1) PREV=\"$RAW\";; *) exit 0;; esac");
        line(s, "");
        line(s, "# Consume the test gate before the first mutation: this candidate can fire once only.");
        line(s, "rm -f \"$GATE\" || exit 0");
        line(s, "write_attempt PREPARED || exit 0");
        line(s, "trace PREPARED");
        line(s, "");
        line(s, "setprop \"$PROP\" 1 || { recover_before_request SETPROP; exit 0; }");
        line(s, "[ \"$(getprop \"$PROP\" 2>/dev/null)\" = 1 ] || { recover_before_request READBACK; exit 0; }");
        line(s, "write_attempt PROPERTY_VERIFIED || { recover_before_request STORE_VERIFIED; exit 0; }");
        line(s, "trace PROPERTY_VERIFIED");
        line(s, "");
        line(s, "# Hard durability/order boundary for the no-repeat rule.");
        line(s, "write_attempt RESTART_REQUESTED || { recover_before_request STORE_REQUESTED; exit 0; }");
        line(s, "trace RESTART_REQUESTED");
        line(s, "");
        line(s, "setprop ctl.restart \"$COMP_SVC\"");
        line(s, "RC=$?");
        line(s, "if [ \"$RC\" -ne 0 ]; then");
        line(s, "  trace CTL_RESTART_REJECTED");
        line(s, "  exit 0");
        line(s, "fi");
        line(s, "trace CTL_RESTART_SENT");
        line(s, "exit 0");
        return s.toString();
    }

    private static void line(StringBuilder s, String value) {
        s.append(value).append('\n');
    }
}
