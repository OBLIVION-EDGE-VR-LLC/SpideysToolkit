package com.utece.student.llpdetection.predicate.event;

import com.utece.student.llpdetection.predicate.clock.VectorClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventLog {

    private final long threadId;
    private final String threadName;
    private final int processIndex;
    private int[] vectorClock;
    private final List<Event> events;

    public EventLog(long threadId, String threadName, int processIndex, int numProcesses) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.processIndex = processIndex;
        this.vectorClock = VectorClock.create(numProcesses);
        this.events = new ArrayList<>();
    }

    public void recordEvent(EventType type, String targetClass, String targetField,
                            int arrayIndex, Object oldValue, Object newValue,
                            long sequenceNumber) {
        vectorClock = VectorClock.increment(vectorClock, processIndex);
        Event event = new Event(
                threadId, threadName, type,
                targetClass, targetField, arrayIndex,
                oldValue, newValue,
                vectorClock, sequenceNumber, System.nanoTime());
        events.add(event);
    }

    public void mergeClock(int[] otherClock) {
        vectorClock = VectorClock.merge(vectorClock, otherClock);
    }

    public void growClock(int newSize) {
        vectorClock = VectorClock.grow(vectorClock, newSize);
    }

    public int[] snapshotClock() {
        return VectorClock.copy(vectorClock);
    }

    public long getThreadId() { return threadId; }
    public String getThreadName() { return threadName; }
    public int getProcessIndex() { return processIndex; }
    public List<Event> getEvents() { return Collections.unmodifiableList(events); }
    public int size() { return events.size(); }

    public Event getEvent(int index) {
        return events.get(index);
    }
}
