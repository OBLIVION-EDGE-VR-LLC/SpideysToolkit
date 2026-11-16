package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.*;

public class AtomicityViolationPredicate implements Predicate {

    @Override
    public String getName() {
        return "AtomicityViolation";
    }

    @Override
    public String getDescription() {
        return "Detects read-write-write interleaving across threads (stale-read pattern)";
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> events = cut.getAllEventsUpToFrontier(logs);
        Map<String, List<Event>> byLocation = new HashMap<>();

        for (Event e : events) {
            if (!e.isMonitorOp()) {
                byLocation.computeIfAbsent(e.getAccessKey(), k -> new ArrayList<>()).add(e);
            }
        }

        for (List<Event> locationEvents : byLocation.values()) {
            if (hasAtomicityViolation(locationEvents)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAtomicityViolation(List<Event> events) {
        // Look for pattern: Thread A reads X, Thread B writes X, Thread A writes X
        // This means Thread A's write was based on a stale read
        for (int i = 0; i < events.size(); i++) {
            Event readA = events.get(i);
            if (!readA.isRead()) continue;

            for (int j = i + 1; j < events.size(); j++) {
                Event writeB = events.get(j);
                if (!writeB.isWrite()) continue;
                if (writeB.getThreadId() == readA.getThreadId()) continue;

                for (int k = j + 1; k < events.size(); k++) {
                    Event writeA = events.get(k);
                    if (!writeA.isWrite()) continue;
                    if (writeA.getThreadId() != readA.getThreadId()) continue;

                    // Found: A reads, B writes, A writes (based on stale read)
                    return true;
                }
            }
        }
        return false;
    }
}
