package com.utece.student.llpdetection;

import com.utece.student.llpdetection.predicate.LLPRunner;
import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.PredicateViolation;
import com.utece.student.llpdetection.predicate.builtin.AtomicityViolationPredicate;
import com.utece.student.llpdetection.predicate.builtin.DataRacePredicate;
import com.utece.student.llpdetection.predicate.event.EventCollector;

import parallelalgorithms.group9.homework3.llp.boruvka.Boruvka;
import parallelalgorithms.group9.homework3.llp.boruvka.Edge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class App {

    public static void main(String[] args) {
        System.out.println("=== LLP Predicate Detection System ===\n");

        System.out.println("--- Demo 1: Detecting data race in parallel shared array ---");
        detectSharedArrayRace();

        System.out.println("\n--- Demo 2: Detecting data race in Boruvka MST ---");
        detectBoruvkaRace();

        System.out.println("\n--- Demo 3: Safe concurrent program (no violations expected) ---");
        detectSafeProgram();
    }

    private static void detectSharedArrayRace() {
        List<Predicate> predicates = Arrays.asList(
                new DataRacePredicate(),
                new AtomicityViolationPredicate()
        );

        Runnable racyProgram = () -> {
            int[] sharedArray = new int[10];
            Thread[] threads = new Thread[4];
            for (int t = 0; t < threads.length; t++) {
                final int threadIdx = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 5; i++) {
                        int idx = (threadIdx + i) % sharedArray.length;
                        // Simulate unsynchronized read-modify-write
                        EventCollector.onFieldRead("SharedArray", "data[" + idx + "]",
                                sharedArray[idx]);
                        int val = sharedArray[idx] + 1;
                        sharedArray[idx] = val;
                        EventCollector.onFieldWrite("SharedArray", "data[" + idx + "]",
                                val - 1, val);
                    }
                }, "Worker-" + t);
            }
            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };

        List<PredicateViolation> violations = LLPRunner.runWithDetection(racyProgram, predicates);
        System.out.println("Violations found: " + violations.size());
    }

    private static void detectBoruvkaRace() {
        List<DataRacePredicate.RaceInfo> races = LLPRunner.findDataRaces(() -> {
            List<Edge> edges = new ArrayList<>();
            edges.add(new Edge(0, 1, 4));
            edges.add(new Edge(0, 2, 3));
            edges.add(new Edge(1, 2, 2));
            edges.add(new Edge(1, 3, 5));
            edges.add(new Edge(2, 3, 7));
            edges.add(new Edge(3, 4, 2));
            edges.add(new Edge(4, 5, 6));
            edges.add(new Edge(3, 5, 1));

            Boruvka boruvka = new Boruvka();

            // Instrument the parallel MST computation
            EventCollector.enable();
            int numNodes = 6;
            int[] parent = new int[numNodes];
            int[] cheapest = new int[numNodes];

            for (int i = 0; i < numNodes; i++) parent[i] = i;

            // Run the parallel portion manually to capture events
            edges.parallelStream().forEach(edge -> {
                int u = edge.source;
                int v = edge.destination;
                EventCollector.onArrayRead("Boruvka", "parent", u, parent[u]);
                EventCollector.onArrayRead("Boruvka", "parent", v, parent[v]);
                EventCollector.onArrayWrite("Boruvka", "cheapest", u,
                        cheapest[u], edges.indexOf(edge));
                cheapest[u] = edges.indexOf(edge);
                EventCollector.onArrayWrite("Boruvka", "cheapest", v,
                        cheapest[v], edges.indexOf(edge));
                cheapest[v] = edges.indexOf(edge);
            });
        });
        System.out.println("Data races found: " + races.size());
    }

    private static void detectSafeProgram() {
        List<Predicate> predicates = Arrays.asList(new DataRacePredicate());

        Runnable safeProgram = () -> {
            ConcurrentHashMap<String, Integer> safeMap = new ConcurrentHashMap<>();
            Thread[] threads = new Thread[3];
            for (int t = 0; t < threads.length; t++) {
                final String key = "key-" + t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 5; i++) {
                        safeMap.put(key, i);
                        EventCollector.onFieldWrite("SafeMap", key, null, i);
                    }
                }, "SafeWorker-" + t);
            }
            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };

        List<PredicateViolation> violations = LLPRunner.runWithDetection(safeProgram, predicates);
        System.out.println("Violations found (should be 0): " + violations.size());
    }
}
