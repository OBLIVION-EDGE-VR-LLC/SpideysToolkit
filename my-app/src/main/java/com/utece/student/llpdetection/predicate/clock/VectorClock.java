package com.utece.student.llpdetection.predicate.clock;

import java.util.Arrays;

public final class VectorClock {

    private VectorClock() {}

    public static int[] create(int numProcesses) {
        return new int[numProcesses];
    }

    public static int[] increment(int[] clock, int processIndex) {
        int[] result = copy(clock);
        result[processIndex]++;
        return result;
    }

    public static int[] merge(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            result[i] = Math.max(va, vb);
        }
        return result;
    }

    public static boolean happensBefore(int[] a, int[] b) {
        if (a.length == 0 && b.length == 0) return false;
        int len = Math.max(a.length, b.length);
        boolean atLeastOneStrict = false;
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            if (va > vb) return false;
            if (va < vb) atLeastOneStrict = true;
        }
        return atLeastOneStrict;
    }

    public static boolean concurrent(int[] a, int[] b) {
        return !happensBefore(a, b) && !happensBefore(b, a) && !Arrays.equals(a, b);
    }

    public static int[] copy(int[] clock) {
        return Arrays.copyOf(clock, clock.length);
    }

    public static int[] grow(int[] clock, int newSize) {
        if (clock.length >= newSize) return copy(clock);
        return Arrays.copyOf(clock, newSize);
    }
}
