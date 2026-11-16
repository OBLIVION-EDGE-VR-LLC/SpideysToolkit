package com.utece.student.llpdetection.predicate.lattice;

import com.utece.student.llpdetection.predicate.clock.VectorClock;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;

import java.util.*;

public final class Cut {

    private final Map<Long, Integer> frontier;

    public Cut(Map<Long, Integer> frontier) {
        this.frontier = Collections.unmodifiableMap(new HashMap<>(frontier));
    }

    public static Cut initial(Set<Long> threadIds) {
        Map<Long, Integer> frontier = new HashMap<>();
        for (Long tid : threadIds) {
            frontier.put(tid, -1);
        }
        return new Cut(frontier);
    }

    public Cut advance(long threadId) {
        Map<Long, Integer> newFrontier = new HashMap<>(frontier);
        newFrontier.put(threadId, frontier.getOrDefault(threadId, -1) + 1);
        return new Cut(newFrontier);
    }

    public int getPosition(long threadId) {
        return frontier.getOrDefault(threadId, -1);
    }

    public Map<Long, Integer> getFrontier() {
        return frontier;
    }

    public boolean isConsistent(Map<Long, EventLog> logs) {
        List<Event> frontierEvents = getFrontierEvents(logs);
        for (int i = 0; i < frontierEvents.size(); i++) {
            Event ei = frontierEvents.get(i);
            if (ei == null) continue;
            for (int j = i + 1; j < frontierEvents.size(); j++) {
                Event ej = frontierEvents.get(j);
                if (ej == null) continue;
                // If ei happens-before ej, but ej is at the frontier (meaning
                // events after ei on ei's thread haven't been included), that's fine.
                // Inconsistency: if an event at the frontier causally depends on
                // an event from another thread that is BEYOND that thread's frontier position.
                if (VectorClock.happensBefore(ej.getVectorClock(), ei.getVectorClock())) {
                    // ej happened before ei, but both are at frontier - check if
                    // ej's thread position in ei's clock exceeds ej's frontier position
                    EventLog ejLog = logs.get(ej.getThreadId());
                    if (ejLog != null) {
                        int ejProcessIdx = ejLog.getProcessIndex();
                        int[] eiClock = ei.getVectorClock();
                        if (ejProcessIdx < eiClock.length &&
                            eiClock[ejProcessIdx] > getPosition(ej.getThreadId()) + 1) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public List<Event> getFrontierEvents(Map<Long, EventLog> logs) {
        List<Event> events = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : frontier.entrySet()) {
            long tid = entry.getKey();
            int pos = entry.getValue();
            if (pos >= 0) {
                EventLog log = logs.get(tid);
                if (log != null && pos < log.size()) {
                    events.add(log.getEvent(pos));
                }
            }
        }
        return events;
    }

    public List<Event> getAllEventsUpToFrontier(Map<Long, EventLog> logs) {
        List<Event> events = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : frontier.entrySet()) {
            long tid = entry.getKey();
            int pos = entry.getValue();
            EventLog log = logs.get(tid);
            if (log != null) {
                for (int i = 0; i <= pos && i < log.size(); i++) {
                    events.add(log.getEvent(i));
                }
            }
        }
        events.sort(Comparator.comparingLong(Event::getSequenceNumber));
        return events;
    }

    public boolean canAdvance(long threadId, Map<Long, EventLog> logs) {
        EventLog log = logs.get(threadId);
        if (log == null) return false;
        int pos = getPosition(threadId);
        return pos + 1 < log.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cut)) return false;
        return frontier.equals(((Cut) o).frontier);
    }

    @Override
    public int hashCode() {
        return frontier.hashCode();
    }

    @Override
    public String toString() {
        return "Cut" + frontier;
    }
}
