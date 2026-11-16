package com.utece.student.llpdetection.predicate;

import com.utece.student.llpdetection.predicate.builtin.DataRacePredicate;
import com.utece.student.llpdetection.predicate.event.EventCollector;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.LatticeBuilder;

import java.util.*;

public class LLPRunner {

    public static List<PredicateViolation> runWithDetection(
            Runnable targetProgram, List<Predicate> predicates) {
        return runWithDetection(targetProgram, predicates, 100_000);
    }

    public static List<PredicateViolation> runWithDetection(
            Runnable targetProgram, List<Predicate> predicates, int maxCuts) {

        EventCollector.reset();
        EventCollector.enable();

        System.out.println("[LLPRunner] Starting monitored execution...");
        long startTime = System.currentTimeMillis();

        targetProgram.run();

        long elapsed = System.currentTimeMillis() - startTime;
        EventCollector.disable();

        Map<Long, EventLog> logs = EventCollector.getAllLogs();
        System.out.println("[LLPRunner] Execution completed in " + elapsed + "ms");
        System.out.println("[LLPRunner] Captured " + EventCollector.getTotalEvents()
                + " events across " + EventCollector.getThreadCount() + " threads");

        if (logs.size() < 2) {
            System.out.println("[LLPRunner] Only " + logs.size()
                    + " thread(s) observed -- need at least 2 for predicate detection");
            return Collections.emptyList();
        }

        System.out.println("[LLPRunner] Running predicate detection...");
        PredicateDetector detector = new PredicateDetector(new LatticeBuilder(maxCuts));
        for (Predicate p : predicates) {
            detector.addPredicate(p);
        }

        List<PredicateViolation> violations = detector.detect(logs);
        detector.printViolations();

        return violations;
    }

    public static List<DataRacePredicate.RaceInfo> findDataRaces(Runnable targetProgram) {
        EventCollector.reset();
        EventCollector.enable();

        targetProgram.run();

        EventCollector.disable();

        Map<Long, EventLog> logs = EventCollector.getAllLogs();
        System.out.println("[LLPRunner] Captured " + EventCollector.getTotalEvents()
                + " events across " + EventCollector.getThreadCount() + " threads");

        DataRacePredicate racePredicate = new DataRacePredicate();
        List<DataRacePredicate.RaceInfo> races = racePredicate.findAllRaces(logs);

        if (races.isEmpty()) {
            System.out.println("[LLPRunner] No data races detected.");
        } else {
            System.out.println("[LLPRunner] Found " + races.size() + " potential data race(s):");
            for (DataRacePredicate.RaceInfo race : races) {
                System.out.println("  " + race);
            }
        }

        return races;
    }
}
