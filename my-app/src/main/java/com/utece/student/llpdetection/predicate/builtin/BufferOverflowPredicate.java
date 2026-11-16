package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.clock.VectorClock;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.*;

public class BufferOverflowPredicate implements Predicate {

    private final Map<String, Integer> knownBufferSizes;

    public BufferOverflowPredicate() {
        this.knownBufferSizes = new HashMap<>();
    }

    public BufferOverflowPredicate(Map<String, Integer> bufferSizes) {
        this.knownBufferSizes = new HashMap<>(bufferSizes);
    }

    public void registerBuffer(String className, String fieldName, int size) {
        knownBufferSizes.put(className + "." + fieldName, size);
    }

    @Override
    public String getName() {
        return "BufferOverflow";
    }

    @Override
    public String getDescription() {
        return "Detects array accesses beyond declared bounds and concurrent index/array modification races";
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> events = cut.getAllEventsUpToFrontier(logs);

        for (Event e : events) {
            if (e.getArrayIndex() >= 0) {
                String bufferKey = e.getTargetClass() + "." + e.getTargetField();
                Integer knownSize = knownBufferSizes.get(bufferKey);
                if (knownSize != null && e.getArrayIndex() >= knownSize) {
                    return true;
                }
            }
        }

        // Check for concurrent array write + bounds-field write from different threads
        List<Event> frontier = cut.getFrontierEvents(logs);
        return checkConcurrentBoundaryViolation(frontier);
    }

    private boolean checkConcurrentBoundaryViolation(List<Event> frontierEvents) {
        List<Event> arrayOps = new ArrayList<>();
        List<Event> boundsOps = new ArrayList<>();

        for (Event e : frontierEvents) {
            if (e.getArrayIndex() >= 0) {
                arrayOps.add(e);
            }
            if (e.isWrite() && isBoundsField(e.getTargetField())) {
                boundsOps.add(e);
            }
        }

        for (Event arrayOp : arrayOps) {
            for (Event boundsOp : boundsOps) {
                if (arrayOp.getThreadId() != boundsOp.getThreadId()
                        && VectorClock.concurrent(arrayOp.getVectorClock(), boundsOp.getVectorClock())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fast-path: scan all events directly without lattice traversal.
     * Finds out-of-bounds accesses and concurrent array+index races.
     */
    public List<OverflowInfo> findAllOverflows(Map<Long, EventLog> logs) {
        List<OverflowInfo> results = new ArrayList<>();

        // Auto-discover buffer sizes from events if not pre-registered
        Map<String, Integer> sizes = new HashMap<>(knownBufferSizes);
        autoDiscoverSizes(logs, sizes);

        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) allEvents.addAll(log.getEvents());

        // 1) Direct out-of-bounds
        for (Event e : allEvents) {
            if (e.getArrayIndex() >= 0) {
                String key = e.getTargetClass() + "." + e.getTargetField();
                Integer size = sizes.get(key);
                if (size != null && e.getArrayIndex() >= size) {
                    results.add(new OverflowInfo(e, null, key, size, e.getArrayIndex(),
                            "Out-of-bounds: index " + e.getArrayIndex() + " >= size " + size));
                }
            }
        }

        // 2) Concurrent array access + bounds-field modification from different threads
        Map<String, List<Event>> arrayEvents = new HashMap<>();
        Map<String, List<Event>> boundsEvents = new HashMap<>();

        for (Event e : allEvents) {
            if (e.getArrayIndex() >= 0) {
                String key = e.getTargetClass() + "." + e.getTargetField();
                arrayEvents.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
            if (e.isWrite() && isBoundsField(e.getTargetField())) {
                boundsEvents.computeIfAbsent(e.getAccessKey(), k -> new ArrayList<>()).add(e);
            }
        }

        // For each array, check if any bounds field is concurrently modified
        for (Map.Entry<String, List<Event>> arrEntry : arrayEvents.entrySet()) {
            String arrClass = arrEntry.getValue().get(0).getTargetClass();
            for (Map.Entry<String, List<Event>> bndEntry : boundsEvents.entrySet()) {
                // Only match bounds fields in the same class
                if (!bndEntry.getKey().startsWith(arrClass + ".")) continue;

                for (Event arrEv : arrEntry.getValue()) {
                    for (Event bndEv : bndEntry.getValue()) {
                        if (arrEv.getThreadId() != bndEv.getThreadId()
                                && VectorClock.concurrent(arrEv.getVectorClock(), bndEv.getVectorClock())) {
                            results.add(new OverflowInfo(arrEv, bndEv,
                                    arrEntry.getKey(), sizes.getOrDefault(arrEntry.getKey(), -1),
                                    arrEv.getArrayIndex(),
                                    "Concurrent array access + bounds modification: "
                                    + arrEv.getThreadName() + " accesses " + arrEntry.getKey()
                                    + "[" + arrEv.getArrayIndex() + "] while "
                                    + bndEv.getThreadName() + " modifies " + bndEv.getAccessKey()));
                        }
                    }
                }
            }
        }

        // Deduplicate by location pair
        Set<String> seen = new HashSet<>();
        List<OverflowInfo> deduped = new ArrayList<>();
        for (OverflowInfo oi : results) {
            String key = oi.description;
            if (seen.add(key)) deduped.add(oi);
        }

        return deduped;
    }

    private void autoDiscoverSizes(Map<Long, EventLog> logs, Map<String, Integer> sizes) {
        // For arrays where we see EventCollector.onArrayWrite/Read with a "bufferSize" field,
        // infer the size as the max index + 1 seen in the first few events
        Map<String, Integer> maxIndex = new HashMap<>();
        for (EventLog log : logs.values()) {
            for (Event e : log.getEvents()) {
                if (e.getArrayIndex() >= 0) {
                    String key = e.getTargetClass() + "." + e.getTargetField();
                    maxIndex.merge(key, e.getArrayIndex(), Math::max);
                }
            }
        }
        // Only set sizes for arrays we haven't already registered
        for (Map.Entry<String, Integer> entry : maxIndex.entrySet()) {
            sizes.putIfAbsent(entry.getKey(), entry.getValue() + 1);
        }
    }

    private boolean isBoundsField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("size") || lower.contains("count") ||
               lower.contains("length") || lower.contains("capacity") ||
               lower.contains("index") || lower.contains("pos") ||
               lower.contains("offset") || lower.contains("limit") ||
               lower.equals("head") || lower.equals("tail");
    }

    public static class OverflowInfo {
        public final Event arrayEvent;
        public final Event boundsEvent; // null for direct OOB
        public final String bufferName;
        public final int bufferSize;
        public final int accessIndex;
        public final String description;

        public OverflowInfo(Event arrayEvent, Event boundsEvent, String bufferName,
                            int bufferSize, int accessIndex, String description) {
            this.arrayEvent = arrayEvent;
            this.boundsEvent = boundsEvent;
            this.bufferName = bufferName;
            this.bufferSize = bufferSize;
            this.accessIndex = accessIndex;
            this.description = description;
        }

        @Override
        public String toString() {
            return "BUFFER OVERFLOW: " + description;
        }
    }
}
