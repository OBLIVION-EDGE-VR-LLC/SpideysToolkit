package com.utece.student.llpdetection.predicate.lattice;

import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.PredicateViolation;
import com.utece.student.llpdetection.predicate.event.EventLog;

import java.util.*;

public class LatticeBuilder {

    private final int maxCuts;

    public LatticeBuilder() {
        this(100_000);
    }

    public LatticeBuilder(int maxCuts) {
        this.maxCuts = maxCuts;
    }

    public List<PredicateViolation> detectViolations(
            Map<Long, EventLog> logs, List<Predicate> predicates) {

        if (logs.isEmpty() || predicates.isEmpty()) {
            return Collections.emptyList();
        }

        List<PredicateViolation> violations = new ArrayList<>();
        Set<Long> threadIds = logs.keySet();
        Cut initial = Cut.initial(threadIds);

        Queue<Cut> worklist = new LinkedList<>();
        Set<Cut> visited = new HashSet<>();

        worklist.add(initial);
        visited.add(initial);
        int cutsExplored = 0;

        while (!worklist.isEmpty() && cutsExplored < maxCuts) {
            Cut current = worklist.poll();
            cutsExplored++;

            for (Predicate predicate : predicates) {
                try {
                    if (predicate.test(current, logs)) {
                        List<com.utece.student.llpdetection.predicate.event.Event> witnesses =
                                current.getFrontierEvents(logs);
                        violations.add(new PredicateViolation(
                                predicate, current, witnesses,
                                predicate.getName() + " detected at " + current));
                    }
                } catch (Exception e) {
                    // Predicate evaluation failure -- skip this cut for this predicate
                }
            }

            for (Long tid : threadIds) {
                if (current.canAdvance(tid, logs)) {
                    Cut next = current.advance(tid);
                    if (!visited.contains(next) && next.isConsistent(logs)) {
                        visited.add(next);
                        worklist.add(next);
                    }
                }
            }
        }

        return violations;
    }

    public int getMaxCuts() {
        return maxCuts;
    }
}
