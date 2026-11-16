package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.*;

public class StackOverflowPredicate implements Predicate {

    private final int maxRecursiveHits;

    public StackOverflowPredicate() {
        this.maxRecursiveHits = 20;
    }

    public StackOverflowPredicate(int maxRecursiveHits) {
        this.maxRecursiveHits = maxRecursiveHits;
    }

    @Override
    public String getName() {
        return "StackOverflow";
    }

    @Override
    public String getDescription() {
        return "Detects repeating field-access patterns indicating unbounded recursion";
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> events = cut.getAllEventsUpToFrontier(logs);
        return detectRecursivePattern(events);
    }

    private boolean detectRecursivePattern(List<Event> events) {
        // Per-thread: count how many times the same field is accessed
        Map<Long, Map<String, Integer>> threadLocCounts = new HashMap<>();
        for (Event e : events) {
            threadLocCounts
                    .computeIfAbsent(e.getThreadId(), k -> new HashMap<>())
                    .merge(e.getAccessKey(), 1, Integer::sum);
        }
        for (Map<String, Integer> locCounts : threadLocCounts.values()) {
            for (int count : locCounts.values()) {
                if (count > maxRecursiveHits) return true;
            }
        }
        return false;
    }

    /**
     * Fast-path: scan all events directly for recursive patterns.
     */
    public List<RecursionInfo> findAllRecursionIssues(Map<Long, EventLog> logs) {
        List<RecursionInfo> issues = new ArrayList<>();

        for (EventLog log : logs.values()) {
            // Count accesses per location
            Map<String, Integer> locCounts = new LinkedHashMap<>();
            for (Event e : log.getEvents()) {
                locCounts.merge(e.getAccessKey(), 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : locCounts.entrySet()) {
                if (entry.getValue() > maxRecursiveHits) {
                    // Find the events for this location
                    List<Event> matched = new ArrayList<>();
                    for (Event e : log.getEvents()) {
                        if (e.getAccessKey().equals(entry.getKey())) matched.add(e);
                    }
                    issues.add(new RecursionInfo(
                            entry.getKey(), log.getThreadName(), entry.getValue(), matched,
                            log.getThreadName() + " accessed " + entry.getKey()
                            + " " + entry.getValue() + " times (threshold: " + maxRecursiveHits
                            + ") -- possible unbounded recursion"));
                }
            }

            // Detect repeating subsequences (pattern length 2-4)
            List<String> accessSequence = new ArrayList<>();
            for (Event e : log.getEvents()) accessSequence.add(e.getAccessKey());

            for (int patLen = 2; patLen <= 4 && patLen * 3 <= accessSequence.size(); patLen++) {
                int repeats = countRepeats(accessSequence, patLen);
                if (repeats >= maxRecursiveHits / patLen) {
                    List<String> pattern = accessSequence.subList(0, patLen);
                    List<Event> patternEvents = log.getEvents().subList(0,
                            Math.min(repeats * patLen, log.size()));
                    issues.add(new RecursionInfo(
                            String.join(" -> ", pattern),
                            log.getThreadName(), repeats,
                            new ArrayList<>(patternEvents),
                            log.getThreadName() + " repeated pattern ["
                            + String.join(", ", pattern) + "] " + repeats
                            + " times -- recursive call chain"));
                }
            }
        }

        return issues;
    }

    private int countRepeats(List<String> seq, int patLen) {
        if (seq.size() < patLen * 2) return 0;
        List<String> pat = seq.subList(0, patLen);
        int count = 0;
        for (int i = 0; i + patLen <= seq.size(); i += patLen) {
            if (seq.subList(i, i + patLen).equals(pat)) count++;
            else break;
        }
        return count;
    }

    public static class RecursionInfo {
        public final String location;
        public final String threadName;
        public final int count;
        public final List<Event> events;
        public final String description;

        public RecursionInfo(String location, String threadName, int count,
                             List<Event> events, String description) {
            this.location = location;
            this.threadName = threadName;
            this.count = count;
            this.events = events;
            this.description = description;
        }

        @Override
        public String toString() {
            return "STACK OVERFLOW RISK: " + description;
        }
    }
}
