package com.utece.student.llpdetection.examples;

import com.utece.student.llpdetection.predicate.event.EventCollector;

public class RacyCounter {

    private static int counter = 0;
    private static int[] buckets = new int[4];

    public static void main(String[] args) {
        System.out.println("[RacyCounter] Starting with 4 threads, no synchronization...");
        Thread[] threads = new Thread[4];

        for (int t = 0; t < threads.length; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    // Unsynchronized read-modify-write on shared counter
                    int val = counter;
                    EventCollector.onFieldRead("RacyCounter", "counter", val);
                    counter = val + 1;
                    EventCollector.onFieldWrite("RacyCounter", "counter", val, val + 1);

                    // Unsynchronized writes to shared array
                    int bucket = (threadIdx + i) % buckets.length;
                    int old = buckets[bucket];
                    EventCollector.onArrayRead("RacyCounter", "buckets", bucket, old);
                    buckets[bucket] = old + 1;
                    EventCollector.onArrayWrite("RacyCounter", "buckets", bucket, old, old + 1);
                }
            }, "Counter-" + t);
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        System.out.println("[RacyCounter] Final counter: " + counter + " (expected: 40)");
        System.out.println("[RacyCounter] Lost updates: " + (40 - counter));
    }
}
