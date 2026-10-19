package com.thor.displaypowertest;

/**
 * Exact restrictive wire-safe fields for existing boot trace diagnostics.
 * No hardware reads, daemon writes, clocks or trace I/O.
 * Moving this policy from BootCoordinator must preserve output byte-for-byte.
 */
final class BootTraceFieldPolicy {
    private BootTraceFieldPolicy() {}

    static String token(String value) {
        return value != null && value.matches("[A-Z0-9_?.-]{1,40}") ? value : "?";
    }

    static String pid(String value) {
        return value != null && value.matches("[1-9][0-9]{0,9}") ? value : "?";
    }

    static String result(String value) {
        return value != null && value.matches("[A-Za-z0-9_=;.-]{1,160}") ? value : "?";
    }

    static String candidate(String value) {
        return value != null && value.matches("[0-2]:[01]:[01]") ? value : "-";
    }
}
