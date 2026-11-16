package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.clock.VectorClock;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.event.EventType;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.*;

public class DataRacePredicate implements Predicate {

    @Override
    public String getName() {
        return "DataRace";
    }

    @Override
    public String getDescription() {
        return "Detects concurrent unsynchronized accesses to the same memory location where at least one is a write";
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> frontierEvents = cut.getFrontierEvents(logs);

        for (int i = 0; i < frontierEvents.size(); i++) {
            Event e1 = frontierEvents.get(i);
            if (e1.isMonitorOp()) continue;

            for (int j = i + 1; j < frontierEvents.size(); j++) {
                Event e2 = frontierEvents.get(j);
                if (e2.isMonitorOp()) continue;

                if (isRace(e1, e2, cut, logs)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRace(Event e1, Event e2, Cut cut, Map<Long, EventLog> logs) {
        if (e1.getThreadId() == e2.getThreadId()) return false;
        if (!e1.getAccessKey().equals(e2.getAccessKey())) return false;
        if (!e1.isWrite() && !e2.isWrite()) return false;
        if (!VectorClock.concurrent(e1.getVectorClock(), e2.getVectorClock())) return false;
        if (protectedByCommonLock(e1, e2, cut, logs)) return false;
        return true;
    }

    private boolean protectedByCommonLock(Event e1, Event e2, Cut cut, Map<Long, EventLog> logs) {
        Set<String> locksHeldByE1 = getLocksHeldAt(e1, cut, logs);
        Set<String> locksHeldByE2 = getLocksHeldAt(e2, cut, logs);
        for (String lock : locksHeldByE1) {
            if (locksHeldByE2.contains(lock)) return true;
        }
        return false;
    }

    private Set<String> getLocksHeldAt(Event target, Cut cut, Map<Long, EventLog> logs) {
        Set<String> heldLocks = new HashSet<>();
        EventLog log = logs.get(target.getThreadId());
        if (log == null) return heldLocks;

        int pos = cut.getPosition(target.getThreadId());
        for (int i = 0; i <= pos && i < log.size(); i++) {
            Event e = log.getEvent(i);
            if (e.getType() == EventType.MONITOR_ENTER) {
                heldLocks.add(e.getAccessKey());
            } else if (e.getType() == EventType.MONITOR_EXIT) {
                heldLocks.remove(e.getAccessKey());
            }
        }
        return heldLocks;
    }

    public List<RaceInfo> findAllRaces(Map<Long, EventLog> logs) {
        List<RaceInfo> races = new ArrayList<>();
        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) {
            allEvents.addAll(log.getEvents());
        }

        Map<String, List<Event>> byLocation = new HashMap<>();
        for (Event e : allEvents) {
            if (!e.isMonitorOp()) {
                byLocation.computeIfAbsent(e.getAccessKey(), k -> new ArrayList<>()).add(e);
            }
        }

        for (Map.Entry<String, List<Event>> entry : byLocation.entrySet()) {
            List<Event> events = entry.getValue();
            for (int i = 0; i < events.size(); i++) {
                for (int j = i + 1; j < events.size(); j++) {
                    Event e1 = events.get(i);
                    Event e2 = events.get(j);
                    if (e1.getThreadId() != e2.getThreadId()
                            && (e1.isWrite() || e2.isWrite())
                            && VectorClock.concurrent(e1.getVectorClock(), e2.getVectorClock())) {
                        races.add(new RaceInfo(e1, e2, entry.getKey()));
                    }
                }
            }
        }
        return races;
    }

    public static class RaceInfo {
        public final Event event1;
        public final Event event2;
        public final String location;

        public RaceInfo(Event event1, Event event2, String location) {
            this.event1 = event1;
            this.event2 = event2;
            this.location = location;
        }

        @Override
        public String toString() {
            return "DATA RACE on " + location + ":\n"
                    + "  Thread " + event1.getThreadName() + ": " + event1 + "\n"
                    + "  Thread " + event2.getThreadName() + ": " + event2;
        }
    }
}
