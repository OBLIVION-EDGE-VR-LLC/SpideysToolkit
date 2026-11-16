package com.utece.student.llpdetection.predicate.builtin;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class InvariantViolationPredicate implements Predicate {

    private final String name;
    private final Function<List<Event>, Boolean> invariantCheck;

    public InvariantViolationPredicate(String name,
                                       Function<List<Event>, Boolean> invariantCheck) {
        this.name = name;
        this.invariantCheck = invariantCheck;
    }

    @Override
    public String getName() {
        return "InvariantViolation:" + name;
    }

    @Override
    public String getDescription() {
        return "Checks user-defined invariant: " + name;
    }

    @Override
    public boolean test(Cut cut, Map<Long, EventLog> logs) {
        List<Event> events = cut.getAllEventsUpToFrontier(logs);
        // Returns true when the invariant is VIOLATED (predicate detected)
        return invariantCheck.apply(events);
    }
}
