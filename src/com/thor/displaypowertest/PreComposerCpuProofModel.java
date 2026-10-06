package com.thor.displaypowertest;

/**
 * Pure verifier for one cold-boot proof that the CPU-fix property was written
 * before the first vendor composer process started.
 *
 * This model deliberately does not write properties, start services or install
 * boot hooks. It only answers whether later boot code is allowed to trust a
 * matching property without scheduling the usual compositor restart.
 */
public final class PreComposerCpuProofModel {
    public enum Result {
        PROVEN,
        ABSENT,
        INVALID,
        WRONG_BOOT,
        WRONG_DESIRED,
        PROPERTY_MISMATCH,
        COMPOSER_PRESENT_DURING_WRITE,
        WRITE_NOT_BEFORE_COMPOSER
    }

    public static final class Proof {
        public final String bootId;
        public final String desired;
        public final long writeAtMs;
        public final boolean composerAbsentBefore;
        public final boolean composerAbsentAfter;
        public final boolean readbackVerified;

        public Proof(String bootId, String desired, long writeAtMs,
                boolean composerAbsentBefore, boolean composerAbsentAfter,
                boolean readbackVerified) {
            if (!CpuBootAttemptModel.validBootId(bootId)) {
                throw new IllegalArgumentException("boot_id");
            }
            if (!"1".equals(desired)) {
                throw new IllegalArgumentException("desired");
            }
            if (writeAtMs < 0L) throw new IllegalArgumentException("write_at_ms");
            this.bootId = bootId;
            this.desired = desired;
            this.writeAtMs = writeAtMs;
            this.composerAbsentBefore = composerAbsentBefore;
            this.composerAbsentAfter = composerAbsentAfter;
            this.readbackVerified = readbackVerified;
        }

        public String encode() {
            return "v1|" + bootId + "|" + desired + "|" + writeAtMs + "|"
                    + (composerAbsentBefore ? "1" : "0") + "|"
                    + (composerAbsentAfter ? "1" : "0") + "|"
                    + (readbackVerified ? "1" : "0");
        }

        public static Proof decode(String value) {
            if (value == null) throw new IllegalArgumentException("null");
            String[] p = value.trim().split("\\|", -1);
            if (p.length != 7 || !"v1".equals(p[0])) {
                throw new IllegalArgumentException("format");
            }
            final long writeAt;
            try {
                writeAt = Long.parseLong(p[3]);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("write_at_ms");
            }
            return new Proof(p[1], p[2], writeAt,
                    bit(p[4], "composer_absent_before"),
                    bit(p[5], "composer_absent_after"),
                    bit(p[6], "readback_verified"));
        }

        private static boolean bit(String value, String field) {
            if ("1".equals(value)) return true;
            if ("0".equals(value)) return false;
            throw new IllegalArgumentException(field);
        }
    }

    private PreComposerCpuProofModel() {}

    public static Result evaluate(Proof proof, String currentBootId,
            boolean desiredEnabled, String observedProperty,
            long firstComposerStartMs) {
        if (proof == null) return Result.ABSENT;
        if (!CpuBootAttemptModel.validBootId(currentBootId)
                || firstComposerStartMs < 0L) {
            return Result.INVALID;
        }
        if (!currentBootId.equalsIgnoreCase(proof.bootId)) {
            return Result.WRONG_BOOT;
        }

        // This experiment is intentionally enable-only. OFF must remain stock
        // and must never gain an early property write by inference.
        if (!desiredEnabled || !"1".equals(proof.desired)) {
            return Result.WRONG_DESIRED;
        }
        if (!"1".equals(observedProperty) || !proof.readbackVerified) {
            return Result.PROPERTY_MISMATCH;
        }
        if (!proof.composerAbsentBefore || !proof.composerAbsentAfter) {
            return Result.COMPOSER_PRESENT_DURING_WRITE;
        }
        if (proof.writeAtMs >= firstComposerStartMs) {
            return Result.WRITE_NOT_BEFORE_COMPOSER;
        }
        return Result.PROVEN;
    }
}
