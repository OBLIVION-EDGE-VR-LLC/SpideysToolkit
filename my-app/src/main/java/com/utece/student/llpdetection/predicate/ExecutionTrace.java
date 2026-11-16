package com.utece.student.llpdetection.predicate;

import com.utece.student.llpdetection.predicate.builtin.DataRacePredicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExecutionTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String targetProgram;
    private final long timestamp;
    private final long seed;
    private final String javaVersion;
    private final int availableProcessors;
    private final long maxHeapBytes;

    // Thread scheduling order (the path)
    private final List<ThreadEvent> schedulingOrder;

    // Detected violations
    private final List<ViolationRecord> violations;

    // Input arguments
    private final String[] programArgs;

    // System properties snapshot
    private final Map<String, String> systemProperties;

    private ExecutionTrace(Builder builder) {
        this.targetProgram = builder.targetProgram;
        this.timestamp = builder.timestamp;
        this.seed = builder.seed;
        this.javaVersion = System.getProperty("java.version");
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.maxHeapBytes = Runtime.getRuntime().maxMemory();
        this.schedulingOrder = Collections.unmodifiableList(builder.schedulingOrder);
        this.violations = Collections.unmodifiableList(builder.violations);
        this.programArgs = builder.programArgs;
        this.systemProperties = Collections.unmodifiableMap(builder.systemProperties);
    }

    public String getTargetProgram() { return targetProgram; }
    public long getTimestamp() { return timestamp; }
    public long getSeed() { return seed; }
    public String getJavaVersion() { return javaVersion; }
    public int getAvailableProcessors() { return availableProcessors; }
    public long getMaxHeapBytes() { return maxHeapBytes; }
    public List<ThreadEvent> getSchedulingOrder() { return schedulingOrder; }
    public List<ViolationRecord> getViolations() { return violations; }
    public String[] getProgramArgs() { return programArgs; }
    public Map<String, String> getSystemProperties() { return systemProperties; }

    public static ExecutionTrace capture(String targetProgram,
                                          Map<Long, EventLog> logs,
                                          List<DataRacePredicate.RaceInfo> races,
                                          List<PredicateViolation> violations,
                                          String[] args,
                                          long seed) {
        Builder builder = new Builder(targetProgram)
                .seed(seed)
                .programArgs(args);

        // Record the thread scheduling order from event sequence numbers
        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) {
            allEvents.addAll(log.getEvents());
        }
        allEvents.sort(Comparator.comparingLong(Event::getSequenceNumber));

        for (Event e : allEvents) {
            builder.addThreadEvent(new ThreadEvent(
                    e.getThreadId(), e.getThreadName(),
                    e.getSequenceNumber(), e.getType().name(),
                    e.getAccessKey(),
                    Objects.toString(e.getOldValue(), ""),
                    Objects.toString(e.getNewValue(), ""),
                    e.getVectorClock()
            ));
        }

        // Record violations
        for (DataRacePredicate.RaceInfo race : races) {
            builder.addViolation(new ViolationRecord(
                    "DataRace", race.location,
                    race.event1.getThreadName(), race.event2.getThreadName(),
                    race.event1.getSequenceNumber(), race.event2.getSequenceNumber(),
                    race.toString()
            ));
        }

        for (PredicateViolation v : violations) {
            StringBuilder witnesses = new StringBuilder();
            for (Event w : v.getWitnessEvents()) {
                witnesses.append(w.toString()).append("; ");
            }
            builder.addViolation(new ViolationRecord(
                    v.getPredicate().getName(), v.getDescription(),
                    "", "", -1, -1, witnesses.toString()
            ));
        }

        // Capture relevant system properties
        builder.systemProperty("os.name", System.getProperty("os.name"));
        builder.systemProperty("os.arch", System.getProperty("os.arch"));
        builder.systemProperty("java.vm.name", System.getProperty("java.vm.name"));

        return builder.build();
    }

    public void saveToFile(File file) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }

    public static ExecutionTrace loadFromFile(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (ExecutionTrace) ois.readObject();
        }
    }

    public String toTextReport() {
        StringBuilder sb = new StringBuilder();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));

        sb.append("=== Execution Trace ===\n");
        sb.append("Target:     ").append(targetProgram).append("\n");
        sb.append("Timestamp:  ").append(dateStr).append("\n");
        sb.append("Seed:       ").append(seed).append("\n");
        sb.append("Java:       ").append(javaVersion).append("\n");
        sb.append("Processors: ").append(availableProcessors).append("\n");
        sb.append("Max Heap:   ").append(maxHeapBytes / (1024 * 1024)).append("MB\n");
        sb.append("Args:       ").append(Arrays.toString(programArgs)).append("\n");
        sb.append("Events:     ").append(schedulingOrder.size()).append("\n");
        sb.append("Violations: ").append(violations.size()).append("\n\n");

        sb.append("--- Thread Scheduling Path ---\n");
        for (ThreadEvent te : schedulingOrder) {
            sb.append(String.format("  [%4d] %-20s %-12s %s",
                    te.sequenceNumber, te.threadName, te.eventType, te.location));
            if (!te.newValue.isEmpty()) {
                sb.append(" = ").append(te.newValue);
                if (!te.oldValue.isEmpty()) sb.append(" (was ").append(te.oldValue).append(")");
            }
            sb.append("\n");
        }

        if (!violations.isEmpty()) {
            sb.append("\n--- Violations ---\n");
            for (int i = 0; i < violations.size(); i++) {
                ViolationRecord vr = violations.get(i);
                sb.append(String.format("  %d. [%s] %s\n", i + 1, vr.predicateName, vr.location));
                if (!vr.thread1.isEmpty()) {
                    sb.append("     Threads: ").append(vr.thread1)
                            .append(" vs ").append(vr.thread2).append("\n");
                }
            }
        }

        sb.append("\n--- Replay Info ---\n");
        sb.append("To reproduce, run with the same seed (").append(seed).append(")\n");
        sb.append("and the same number of processors (").append(availableProcessors).append(")\n");
        sb.append("on Java ").append(javaVersion).append("\n");

        return sb.toString();
    }

    // --- Inner classes ---

    public static class ThreadEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long threadId;
        public final String threadName;
        public final long sequenceNumber;
        public final String eventType;
        public final String location;
        public final String oldValue;
        public final String newValue;
        public final int[] vectorClock;

        public ThreadEvent(long threadId, String threadName, long seqNum,
                           String eventType, String location,
                           String oldValue, String newValue, int[] vectorClock) {
            this.threadId = threadId;
            this.threadName = threadName;
            this.sequenceNumber = seqNum;
            this.eventType = eventType;
            this.location = location;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.vectorClock = Arrays.copyOf(vectorClock, vectorClock.length);
        }
    }

    public static class ViolationRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String predicateName;
        public final String location;
        public final String thread1;
        public final String thread2;
        public final long seq1;
        public final long seq2;
        public final String detail;

        public ViolationRecord(String predicateName, String location,
                               String thread1, String thread2,
                               long seq1, long seq2, String detail) {
            this.predicateName = predicateName;
            this.location = location;
            this.thread1 = thread1;
            this.thread2 = thread2;
            this.seq1 = seq1;
            this.seq2 = seq2;
            this.detail = detail;
        }
    }

    public static class Builder {
        private final String targetProgram;
        private long timestamp = System.currentTimeMillis();
        private long seed = System.nanoTime();
        private final List<ThreadEvent> schedulingOrder = new ArrayList<>();
        private final List<ViolationRecord> violations = new ArrayList<>();
        private String[] programArgs = new String[0];
        private final Map<String, String> systemProperties = new LinkedHashMap<>();

        public Builder(String targetProgram) {
            this.targetProgram = targetProgram;
        }

        public Builder seed(long seed) { this.seed = seed; return this; }
        public Builder programArgs(String[] args) { this.programArgs = args != null ? args : new String[0]; return this; }
        public Builder addThreadEvent(ThreadEvent event) { schedulingOrder.add(event); return this; }
        public Builder addViolation(ViolationRecord violation) { violations.add(violation); return this; }
        public Builder systemProperty(String key, String value) {
            if (value != null) systemProperties.put(key, value);
            return this;
        }

        public ExecutionTrace build() { return new ExecutionTrace(this); }
    }
}
