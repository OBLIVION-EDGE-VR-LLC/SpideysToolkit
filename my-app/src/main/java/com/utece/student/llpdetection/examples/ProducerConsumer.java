package com.utece.student.llpdetection.examples;

import com.utece.student.llpdetection.predicate.event.EventCollector;

public class ProducerConsumer {

    private static int[] buffer = new int[5];
    private static int count = 0;
    private static int head = 0;
    private static int tail = 0;

    public static void main(String[] args) {
        System.out.println("[ProducerConsumer] Starting 2 producers + 2 consumers, no sync...");

        Thread[] producers = new Thread[2];
        Thread[] consumers = new Thread[2];

        for (int i = 0; i < 2; i++) {
            final int id = i;
            producers[i] = new Thread(() -> {
                for (int j = 0; j < 8; j++) {
                    int item = id * 100 + j;
                    // Unsynchronized produce
                    EventCollector.onFieldRead("ProducerConsumer", "count", count);
                    if (count < buffer.length) {
                        EventCollector.onFieldRead("ProducerConsumer", "tail", tail);
                        int idx = tail % buffer.length;
                        EventCollector.onArrayWrite("ProducerConsumer", "buffer", idx, buffer[idx], item);
                        buffer[idx] = item;
                        tail++;
                        EventCollector.onFieldWrite("ProducerConsumer", "tail", tail - 1, tail);
                        count++;
                        EventCollector.onFieldWrite("ProducerConsumer", "count", count - 1, count);
                    }
                }
            }, "Producer-" + i);

            consumers[i] = new Thread(() -> {
                for (int j = 0; j < 8; j++) {
                    // Unsynchronized consume
                    EventCollector.onFieldRead("ProducerConsumer", "count", count);
                    if (count > 0) {
                        EventCollector.onFieldRead("ProducerConsumer", "head", head);
                        int idx = head % buffer.length;
                        int item = buffer[idx];
                        EventCollector.onArrayRead("ProducerConsumer", "buffer", idx, item);
                        head++;
                        EventCollector.onFieldWrite("ProducerConsumer", "head", head - 1, head);
                        count--;
                        EventCollector.onFieldWrite("ProducerConsumer", "count", count + 1, count);
                    }
                }
            }, "Consumer-" + i);
        }

        for (Thread t : producers) t.start();
        for (Thread t : consumers) t.start();
        for (Thread t : producers) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        for (Thread t : consumers) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        System.out.println("[ProducerConsumer] Final count: " + count);
    }
}
