package com.utece.student.llpdetection.predicate.event;

import com.utece.student.llpdetection.predicate.clock.VectorClock;

public final class Event {

    private final long threadId;
    private final String threadName;
    private final EventType type;
    private final String targetClass;
    private final String targetField;
    private final int arrayIndex;
    private final Object oldValue;
    private final Object newValue;
    private final int[] vectorClock;
    private final long sequenceNumber;
    private final long timestamp;

    public Event(long threadId, String threadName, EventType type,
                 String targetClass, String targetField, int arrayIndex,
                 Object oldValue, Object newValue,
                 int[] vectorClock, long sequenceNumber, long timestamp) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.type = type;
        this.targetClass = targetClass;
        this.targetField = targetField;
        this.arrayIndex = arrayIndex;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.vectorClock = VectorClock.copy(vectorClock);
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public long getThreadId() { return threadId; }
    public String getThreadName() { return threadName; }
    public EventType getType() { return type; }
    public String getTargetClass() { return targetClass; }
    public String getTargetField() { return targetField; }
    public int getArrayIndex() { return arrayIndex; }
    public Object getOldValue() { return oldValue; }
    public Object getNewValue() { return newValue; }
    public int[] getVectorClock() { return VectorClock.copy(vectorClock); }
    public long getSequenceNumber() { return sequenceNumber; }
    public long getTimestamp() { return timestamp; }

    public boolean isWrite() {
        return type == EventType.FIELD_WRITE || type == EventType.ARRAY_WRITE;
    }

    public boolean isRead() {
        return type == EventType.FIELD_READ || type == EventType.ARRAY_READ;
    }

    public boolean isMonitorOp() {
        return type == EventType.MONITOR_ENTER || type == EventType.MONITOR_EXIT;
    }

    public String getAccessKey() {
        if (arrayIndex >= 0) {
            return targetClass + "." + targetField + "[" + arrayIndex + "]";
        }
        return targetClass + "." + targetField;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Event{thread=").append(threadName)
          .append(", type=").append(type)
          .append(", target=").append(getAccessKey());
        if (isWrite()) {
            sb.append(", old=").append(oldValue).append(", new=").append(newValue);
        } else if (isRead()) {
            sb.append(", value=").append(newValue);
        }
        sb.append(", seq=").append(sequenceNumber).append("}");
        return sb.toString();
    }
}
