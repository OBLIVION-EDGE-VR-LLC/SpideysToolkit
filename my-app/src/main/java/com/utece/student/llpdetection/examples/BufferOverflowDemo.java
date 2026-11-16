package com.utece.student.llpdetection.examples;

import com.utece.student.llpdetection.predicate.event.EventCollector;

/**
 * Demonstrates buffer overflow bugs detectable by the LLP predicate detector.
 *
 * Bug 1: Out-of-bounds write -- index exceeds buffer size.
 * Bug 2: Concurrent index + array race -- one thread writes to the array while
 *         another thread modifies the index/count, causing the first thread to
 *         write past the end of the buffer.
 */
public class BufferOverflowDemo {

    static int[] buffer = new int[4];
    static int writeIndex = 0;
    static int count = 0;

    public static void main(String[] args) {
        System.out.println("[BufferOverflowDemo] Buffer size: 4, writing 6 items from 2 threads...");

        Thread writer1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                int idx = writeIndex;
                EventCollector.onFieldRead("BufferOverflowDemo", "writeIndex", idx);

                // Write to buffer -- may go out of bounds
                if (idx < buffer.length) {
                    EventCollector.onArrayWrite("BufferOverflowDemo", "buffer", idx, 0, 100 + i);
                    buffer[idx] = 100 + i;
                } else {
                    // This IS the overflow -- we're writing past the end
                    EventCollector.onArrayWrite("BufferOverflowDemo", "buffer", idx, 0, 100 + i);
                }

                writeIndex = idx + 1;
                EventCollector.onFieldWrite("BufferOverflowDemo", "writeIndex", idx, idx + 1);
                count++;
                EventCollector.onFieldWrite("BufferOverflowDemo", "count", count - 1, count);
            }
        }, "Writer-1");

        Thread writer2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                int idx = writeIndex;
                EventCollector.onFieldRead("BufferOverflowDemo", "writeIndex", idx);

                if (idx < buffer.length) {
                    EventCollector.onArrayWrite("BufferOverflowDemo", "buffer", idx, 0, 200 + i);
                    buffer[idx] = 200 + i;
                } else {
                    EventCollector.onArrayWrite("BufferOverflowDemo", "buffer", idx, 0, 200 + i);
                }

                writeIndex = idx + 1;
                EventCollector.onFieldWrite("BufferOverflowDemo", "writeIndex", idx, idx + 1);
                count++;
                EventCollector.onFieldWrite("BufferOverflowDemo", "count", count - 1, count);
            }
        }, "Writer-2");

        writer1.start();
        writer2.start();
        try { writer1.join(); writer2.join(); } catch (InterruptedException e) {}

        System.out.println("[BufferOverflowDemo] Final writeIndex: " + writeIndex + " (buffer size: 4)");
        System.out.println("[BufferOverflowDemo] Overflow occurred: " + (writeIndex > buffer.length));
    }
}
