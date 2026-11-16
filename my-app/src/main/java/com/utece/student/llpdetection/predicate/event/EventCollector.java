package com.utece.student.llpdetection.predicate.event;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class EventCollector {

    private static final ConcurrentHashMap<Long, EventLog> threadLogs = new ConcurrentHashMap<>();
    private static final AtomicLong globalSequence = new AtomicLong(0);
    private static final AtomicInteger processCounter = new AtomicInteger(0);
    private static volatile boolean enabled = true;
    private static volatile int initialClockSize = 16;

    private EventCollector() {}

    public static void enable() { enabled = true; }
    public static void disable() { enabled = false; }

    public static void setInitialClockSize(int size) { initialClockSize = size; }

    public static void reset() {
        threadLogs.clear();
        globalSequence.set(0);
        processCounter.set(0);
    }

    private static EventLog getOrCreateLog() {
        if (!enabled) return null;
        long tid = Thread.currentThread().getId();
        return threadLogs.computeIfAbsent(tid, id -> {
            int idx = processCounter.getAndIncrement();
            Thread t = Thread.currentThread();
            EventLog log = new EventLog(id, t.getName(), idx, initialClockSize);
            // Grow all existing logs to accommodate new process index
            int requiredSize = idx + 1;
            for (EventLog existing : threadLogs.values()) {
                existing.growClock(requiredSize);
            }
            return log;
        });
    }

    public static void onFieldRead(String className, String fieldName, Object value) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.FIELD_READ, className, fieldName,
                -1, null, value, globalSequence.getAndIncrement());
    }

    public static void onFieldWrite(String className, String fieldName,
                                     Object oldValue, Object newValue) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.FIELD_WRITE, className, fieldName,
                -1, oldValue, newValue, globalSequence.getAndIncrement());
    }

    public static void onArrayRead(String className, String fieldName,
                                    int index, Object value) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.ARRAY_READ, className, fieldName,
                index, null, value, globalSequence.getAndIncrement());
    }

    public static void onArrayWrite(String className, String fieldName,
                                     int index, Object oldValue, Object newValue) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.ARRAY_WRITE, className, fieldName,
                index, oldValue, newValue, globalSequence.getAndIncrement());
    }

    public static void onMonitorEnter(String className, String lockId) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.MONITOR_ENTER, className, lockId,
                -1, null, null, globalSequence.getAndIncrement());
    }

    public static void onMonitorExit(String className, String lockId) {
        EventLog log = getOrCreateLog();
        if (log == null) return;
        log.recordEvent(EventType.MONITOR_EXIT, className, lockId,
                -1, null, null, globalSequence.getAndIncrement());
    }

    public static void onThreadSync(long otherThreadId) {
        EventLog myLog = getOrCreateLog();
        if (myLog == null) return;
        EventLog otherLog = threadLogs.get(otherThreadId);
        if (otherLog != null) {
            myLog.mergeClock(otherLog.snapshotClock());
        }
    }

    public static Map<Long, EventLog> getAllLogs() {
        return Collections.unmodifiableMap(threadLogs);
    }

    public static int getThreadCount() {
        return threadLogs.size();
    }

    public static long getTotalEvents() {
        return globalSequence.get();
    }
}
