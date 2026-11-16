package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.clock.VectorClock;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.*;

public class HeapOverflowPredicate implements Predicate {

    private final int unboundedGrowthThreshold;

    public HeapOverflowPredicate() {
        this.unboundedGrowthThreshold = 5;
    }

    public HeapOverflowPredicate(int unboundedGrowthThreshold) {
        this.unboundedGrowthThreshold = unboundedGrowthThreshold;
    }

    @Override
    public String getName() {
        return "HeapOverflow";
    }

    @Override
    public String getDescription() {
        return "Detects unbounded heap growth and concurrent size-field modification races";
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> events = cut.getAllEventsUpToFrontier(logs);
        if (detectUnboundedGrowth(events)) return true;

        List<Event> frontier = cut.getFrontierEvents(logs);
        if (detectConcurrentSizeRace(frontier)) return true;

        return false;
    }

    private boolean detectUnboundedGrowth(List<Event> events) {
        Map<String, List<Number>> sizeValues = new LinkedHashMap<>();

        for (Event e : events) {
            if (e.isWrite() && isSizeField(e.getTargetField()) && e.getNewValue() instanceof Number) {
                sizeValues.computeIfAbsent(e.getAccessKey(), k -> new ArrayList<>())
                        .add((Number) e.getNewValue());
            }
        }

        for (List<Number> values : sizeValues.values()) {
            if (values.size() < unboundedGrowthThreshold) continue;

            boolean alwaysGrowing = true;
            for (int i = 1; i < values.size(); i++) {
                if (values.get(i).longValue() <= values.get(i - 1).longValue()) {
                    alwaysGrowing = false;
                    break;
                }
            }
            if (alwaysGrowing) return true;
        }
        return false;
    }

    private boolean detectConcurrentSizeRace(List<Event> frontierEvents) {
        Map<String, Set<Long>> sizeWriteThreads = new HashMap<>();
        for (Event e : frontierEvents) {
            if (e.isWrite() && isSizeField(e.getTargetField())) {
                sizeWriteThreads.computeIfAbsent(e.getAccessKey(), k -> new HashSet<>())
                        .add(e.getThreadId());
            }
        }
        for (Set<Long> threads : sizeWriteThreads.values()) {
            if (threads.size() > 1) return true;
        }
        return false;
    }

    /**
     * Fast-path: scan all events directly without lattice traversal.
     */
    public List<HeapIssue> findAllHeapIssues(Map<Long, EventLog> logs) {
        List<HeapIssue> issues = new ArrayList<>();

        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) allEvents.addAll(log.getEvents());
        allEvents.sort(Comparator.comparingLong(Event::getSequenceNumber));

        // 1) Unbounded growth: size/count fields that only increase
        Map<String, List<Event>> sizeWrites = new LinkedHashMap<>();
        for (Event e : allEvents) {
            if (e.isWrite() && isSizeField(e.getTargetField()) && e.getNewValue() instanceof Number) {
                sizeWrites.computeIfAbsent(e.getAccessKey(), k -> new ArrayList<>()).add(e);
            }
        }

        for (Map.Entry<String, List<Event>> entry : sizeWrites.entrySet()) {
            List<Event> writes = entry.getValue();
            if (writes.size() < unboundedGrowthThreshold) continue;

            boolean alwaysGrowing = true;
            for (int i = 1; i < writes.size(); i++) {
                long prev = ((Number) writes.get(i - 1).getNewValue()).longValue();
                long curr = ((Number) writes.get(i).getNewValue()).longValue();
                if (curr <= prev) { alwaysGrowing = false; break; }
            }

            if (alwaysGrowing) {
                Event first = writes.get(0);
                Event last = writes.get(writes.size() - 1);
                issues.add(new HeapIssue(
                        "UnboundedGrowth", entry.getKey(), writes,
                        entry.getKey() + " grew monotonically from "
                        + first.getNewValue() + " to " + last.getNewValue()
                        + " over " + writes.size() + " writes without ever decreasing"));
            }
        }

        // 2) Concurrent size-field races
        for (Map.Entry<String, List<Event>> entry : sizeWrites.entrySet()) {
            List<Event> writes = entry.getValue();
            for (int i = 0; i < writes.size(); i++) {
                for (int j = i + 1; j < writes.size(); j++) {
                    Event e1 = writes.get(i);
                    Event e2 = writes.get(j);
                    if (e1.getThreadId() != e2.getThreadId()
                            && VectorClock.concurrent(e1.getVectorClock(), e2.getVectorClock())) {
                        issues.add(new HeapIssue(
                                "ConcurrentSizeRace", entry.getKey(),
                                Arrays.asList(e1, e2),
                                "Concurrent modification of " + entry.getKey()
                                + ": " + e1.getThreadName() + " wrote " + e1.getNewValue()
                                + " while " + e2.getThreadName() + " wrote " + e2.getNewValue()));
                        // Only report first race per location
                        i = writes.size();
                        break;
                    }
                }
            }
        }

        return issues;
    }

    private boolean isSizeField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("size") || lower.contains("count") ||
               lower.contains("capacity") || lower.contains("length") ||
               lower.contains("alloc") || lower.contains("used");
    }

    public static class HeapIssue {
        public final String type;
        public final String location;
        public final List<Event> events;
        public final String description;

        public HeapIssue(String type, String location, List<Event> events, String description) {
            this.type = type;
            this.location = location;
            this.events = events;
            this.description = description;
        }

        @Override
        public String toString() {
            return "HEAP ISSUE [" + type + "]: " + description;
        }
    }
}
