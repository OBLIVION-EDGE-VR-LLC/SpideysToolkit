package com.utece.student.llpdetection.examples;

import com.utece.student.llpdetection.predicate.event.EventCollector;

public class SafeCounter {

    private static int counter = 0;
    private static final Object lock = new Object();

    public static void main(String[] args) {
        System.out.println("[SafeCounter] Starting with 4 threads, synchronized...");
        Thread[] threads = new Thread[4];

        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    synchronized (lock) {
                        EventCollector.onMonitorEnter("SafeCounter", "lock");
                        int val = counter;
                        EventCollector.onFieldRead("SafeCounter", "counter", val);
                        counter = val + 1;
                        EventCollector.onFieldWrite("SafeCounter", "counter", val, val + 1);
                        EventCollector.onMonitorExit("SafeCounter", "lock");
                    }
                }
            }, "SafeWorker-" + t);
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        System.out.println("[SafeCounter] Final counter: " + counter + " (expected: 40)");
    }
}
