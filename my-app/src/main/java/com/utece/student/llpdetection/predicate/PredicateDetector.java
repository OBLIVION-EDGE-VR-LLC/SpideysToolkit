package com.utece.student.llpdetection.predicate;

import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.LatticeBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PredicateDetector {

    private final List<Predicate> predicates;
    private final LatticeBuilder latticeBuilder;
    private final List<PredicateViolation> violations;

    public PredicateDetector() {
        this(new LatticeBuilder());
    }

    public PredicateDetector(LatticeBuilder latticeBuilder) {
        this.predicates = new ArrayList<>();
        this.latticeBuilder = latticeBuilder;
        this.violations = new ArrayList<>();
    }

    public void addPredicate(Predicate predicate) {
        predicates.add(predicate);
    }

    public List<PredicateViolation> detect(Map<Long, EventLog> logs) {
        violations.clear();
        if (logs.size() < 2) {
            return Collections.emptyList();
        }
        List<PredicateViolation> found = latticeBuilder.detectViolations(logs, predicates);
        violations.addAll(found);
        return Collections.unmodifiableList(violations);
    }

    public List<PredicateViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public void printViolations() {
        if (violations.isEmpty()) {
            System.out.println("[PredicateDetector] No violations detected.");
            return;
        }
        System.out.println("[PredicateDetector] Found " + violations.size() + " violation(s):");
        for (int i = 0; i < violations.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + violations.get(i));
        }
    }
}
