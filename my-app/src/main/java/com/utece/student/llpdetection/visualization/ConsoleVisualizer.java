package com.utece.student.llpdetection.visualization;

import com.utece.student.llpdetection.predicate.PredicateViolation;
import com.utece.student.llpdetection.predicate.builtin.DataRacePredicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;

import java.util.*;
import java.util.stream.Collectors;

public class ConsoleVisualizer {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";
    private static final String BG_RED = "\u001B[41m";
    private static final String BG_YELLOW = "\u001B[43m";
    private static final String BG_GREEN = "\u001B[42m";

    public static void visualize(Map<Long, EventLog> logs,
                                  List<DataRacePredicate.RaceInfo> races,
                                  List<PredicateViolation> violations) {
        printHeader(logs, races, violations);
        printThreadTimeline(logs, races);
        printHeatMap(logs, races);
        printRaceDetails(races);
        printViolationSummary(violations);
    }

    private static void printHeader(Map<Long, EventLog> logs,
                                     List<DataRacePredicate.RaceInfo> races,
                                     List<PredicateViolation> violations) {
        int totalEvents = logs.values().stream().mapToInt(EventLog::size).sum();

        System.out.println();
        System.out.println(BOLD + CYAN + "=========================================" + RESET);
        System.out.println(BOLD + CYAN + "  LLP Predicate Detection Report" + RESET);
        System.out.println(BOLD + CYAN + "=========================================" + RESET);
        System.out.println();
        System.out.printf("  Threads: %s%d%s  |  Events: %s%d%s  |  Races: %s%s%d%s  |  Violations: %s%s%d%s%n",
                BOLD, logs.size(), RESET,
                BOLD, totalEvents, RESET,
                BOLD, races.isEmpty() ? GREEN : RED, races.size(), RESET,
                BOLD, violations.isEmpty() ? GREEN : RED, violations.size(), RESET);
        System.out.println();
    }

    private static void printThreadTimeline(Map<Long, EventLog> logs,
                                             List<DataRacePredicate.RaceInfo> races) {
        System.out.println(BOLD + "--- Thread Timeline ---" + RESET);
        System.out.println();

        // Collect all events sorted by sequence number
        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) {
            allEvents.addAll(log.getEvents());
        }
        allEvents.sort(Comparator.comparingLong(Event::getSequenceNumber));

        // Build thread name list
        List<String> threadNames = logs.values().stream()
                .map(EventLog::getThreadName)
                .collect(Collectors.toList());

        Map<Long, String> tidToName = new HashMap<>();
        for (EventLog log : logs.values()) {
            tidToName.put(log.getThreadId(), log.getThreadName());
        }

        // Build race location set for highlighting
        Set<String> raceLocations = races.stream()
                .map(r -> r.location)
                .collect(Collectors.toSet());

        Set<Long> raceEventSeqs = new HashSet<>();
        for (DataRacePredicate.RaceInfo race : races) {
            raceEventSeqs.add(race.event1.getSequenceNumber());
            raceEventSeqs.add(race.event2.getSequenceNumber());
        }

        int colWidth = 20;

        // Header
        System.out.print("  " + pad("seq", 5));
        for (String name : threadNames) {
            System.out.print(pad(truncate(name, colWidth - 2), colWidth));
        }
        System.out.println();

        System.out.print("  " + pad("---", 5));
        for (String ignored : threadNames) {
            System.out.print(pad(repeat("-", colWidth - 2), colWidth));
        }
        System.out.println();

        // Events
        for (Event event : allEvents) {
            boolean isRaceEvent = raceEventSeqs.contains(event.getSequenceNumber());
            String marker = formatEventShort(event);

            System.out.print("  " + pad(String.valueOf(event.getSequenceNumber()), 5));
            for (EventLog log : logs.values()) {
                if (log.getThreadId() == event.getThreadId()) {
                    String color = isRaceEvent ? RED + BOLD : eventColor(event);
                    String prefix = isRaceEvent ? "!" : " ";
                    System.out.print(color + prefix + pad(marker, colWidth - 1) + RESET);
                } else {
                    System.out.print(pad("|", colWidth));
                }
            }

            if (isRaceEvent) {
                System.out.print(RED + " << RACE" + RESET);
            }
            System.out.println();
        }
        System.out.println();
    }

    private static void printHeatMap(Map<Long, EventLog> logs,
                                      List<DataRacePredicate.RaceInfo> races) {
        System.out.println(BOLD + "--- Contention Heat Map ---" + RESET);
        System.out.println();

        // Count accesses per location per thread
        Map<String, Map<String, Integer>> locationThreadCounts = new LinkedHashMap<>();
        Map<String, Integer> locationTotalWrites = new LinkedHashMap<>();

        for (EventLog log : logs.values()) {
            for (Event e : log.getEvents()) {
                if (e.isMonitorOp()) continue;
                String loc = e.getAccessKey();
                locationThreadCounts
                        .computeIfAbsent(loc, k -> new LinkedHashMap<>())
                        .merge(log.getThreadName(), 1, Integer::sum);
                if (e.isWrite()) {
                    locationTotalWrites.merge(loc, 1, Integer::sum);
                }
            }
        }

        // Build race location set
        Set<String> raceLocations = races.stream()
                .map(r -> r.location)
                .collect(Collectors.toSet());

        // Collect thread names
        List<String> threadNames = logs.values().stream()
                .map(EventLog::getThreadName)
                .collect(Collectors.toList());

        // Find max count for heat scaling
        int maxCount = locationThreadCounts.values().stream()
                .flatMap(m -> m.values().stream())
                .max(Integer::compareTo)
                .orElse(1);

        int locColWidth = 28;
        int threadColWidth = 14;

        // Header
        System.out.print("  " + pad("Location", locColWidth));
        for (String name : threadNames) {
            System.out.print(pad(truncate(name, threadColWidth - 2), threadColWidth));
        }
        System.out.print(pad("Writes", 8));
        System.out.print(pad("Status", 12));
        System.out.println();

        System.out.print("  " + pad(repeat("-", locColWidth - 2), locColWidth));
        for (String ignored : threadNames) {
            System.out.print(pad(repeat("-", threadColWidth - 2), threadColWidth));
        }
        System.out.print(pad("------", 8));
        System.out.print(pad("------", 12));
        System.out.println();

        // Sort by race status, then by total accesses
        List<String> sortedLocations = new ArrayList<>(locationThreadCounts.keySet());
        sortedLocations.sort((a, b) -> {
            boolean aRace = raceLocations.contains(a);
            boolean bRace = raceLocations.contains(b);
            if (aRace != bRace) return aRace ? -1 : 1;
            int aTotal = locationThreadCounts.get(a).values().stream().mapToInt(Integer::intValue).sum();
            int bTotal = locationThreadCounts.get(b).values().stream().mapToInt(Integer::intValue).sum();
            return Integer.compare(bTotal, aTotal);
        });

        for (String loc : sortedLocations) {
            Map<String, Integer> threadCounts = locationThreadCounts.get(loc);
            boolean isRace = raceLocations.contains(loc);
            int writes = locationTotalWrites.getOrDefault(loc, 0);

            String locColor = isRace ? RED + BOLD : "";
            System.out.print("  " + locColor + pad(truncate(loc, locColWidth - 2), locColWidth) + RESET);

            for (String threadName : threadNames) {
                int count = threadCounts.getOrDefault(threadName, 0);
                String heatColor = heatColor(count, maxCount);
                String block = count > 0 ? heatBlock(count, maxCount) + " " + count : ".";
                System.out.print(heatColor + pad(block, threadColWidth) + RESET);
            }

            System.out.print(pad(String.valueOf(writes), 8));

            if (isRace) {
                int numThreads = (int) threadCounts.values().stream().filter(c -> c > 0).count();
                System.out.print(BG_RED + WHITE + BOLD + " RACE(" + numThreads + "t) " + RESET);
            } else {
                int numThreads = (int) threadCounts.values().stream().filter(c -> c > 0).count();
                if (numThreads > 1 && writes > 0) {
                    System.out.print(YELLOW + " SHARED " + RESET);
                } else {
                    System.out.print(GREEN + " OK " + RESET);
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    private static void printRaceDetails(List<DataRacePredicate.RaceInfo> races) {
        if (races.isEmpty()) {
            System.out.println(GREEN + BOLD + "  No data races detected!" + RESET);
            System.out.println();
            return;
        }

        System.out.println(BOLD + "--- Data Races Found: " + RED + races.size() + RESET + BOLD + " ---" + RESET);
        System.out.println();

        for (int i = 0; i < races.size(); i++) {
            DataRacePredicate.RaceInfo race = races.get(i);
            String type = raceType(race);

            System.out.printf("  %s%d.%s %s%s%s%n", BOLD, i + 1, RESET, RED, race.location, RESET);
            System.out.printf("     %sThread %s%s: %s (%s)%n",
                    YELLOW, race.event1.getThreadName(), RESET,
                    formatEventDetail(race.event1), race.event1.getType());
            System.out.printf("     %sThread %s%s: %s (%s)%n",
                    YELLOW, race.event2.getThreadName(), RESET,
                    formatEventDetail(race.event2), race.event2.getType());
            System.out.printf("     Type: %s%s%s%n", MAGENTA, type, RESET);
            System.out.println();
        }
    }

    private static void printViolationSummary(List<PredicateViolation> violations) {
        if (violations.isEmpty()) return;

        System.out.println(BOLD + "--- Predicate Violations: " + RED + violations.size() + RESET + BOLD + " ---" + RESET);
        System.out.println();
        for (int i = 0; i < violations.size(); i++) {
            PredicateViolation v = violations.get(i);
            System.out.printf("  %s%d.%s [%s%s%s] %s%n",
                    BOLD, i + 1, RESET,
                    CYAN, v.getPredicate().getName(), RESET,
                    v.getDescription());
        }
        System.out.println();
    }

    // --- Helpers ---

    private static String formatEventShort(Event e) {
        String prefix;
        switch (e.getType()) {
            case FIELD_READ:    prefix = "R "; break;
            case FIELD_WRITE:   prefix = "W "; break;
            case ARRAY_READ:    prefix = "R "; break;
            case ARRAY_WRITE:   prefix = "W "; break;
            case MONITOR_ENTER: prefix = "L "; break;
            case MONITOR_EXIT:  prefix = "U "; break;
            default:            prefix = "? "; break;
        }
        String field = e.getTargetField();
        if (e.getArrayIndex() >= 0) {
            field += "[" + e.getArrayIndex() + "]";
        }
        return prefix + truncate(field, 15);
    }

    private static String formatEventDetail(Event e) {
        if (e.isWrite()) {
            return e.getAccessKey() + " = " + e.getNewValue() + " (was " + e.getOldValue() + ")";
        }
        return e.getAccessKey() + " -> " + e.getNewValue();
    }

    private static String raceType(DataRacePredicate.RaceInfo race) {
        if (race.event1.isWrite() && race.event2.isWrite()) return "write-write";
        return "read-write";
    }

    private static String eventColor(Event e) {
        switch (e.getType()) {
            case FIELD_WRITE:
            case ARRAY_WRITE:   return YELLOW;
            case FIELD_READ:
            case ARRAY_READ:    return GREEN;
            case MONITOR_ENTER:
            case MONITOR_EXIT:  return BLUE;
            default:            return WHITE;
        }
    }

    private static String heatColor(int count, int max) {
        if (count == 0) return "";
        double ratio = (double) count / max;
        if (ratio > 0.7) return RED;
        if (ratio > 0.4) return YELLOW;
        return GREEN;
    }

    private static String heatBlock(int count, int max) {
        double ratio = (double) count / max;
        if (ratio > 0.8) return "\u2588\u2588\u2588";
        if (ratio > 0.6) return "\u2588\u2588\u2591";
        if (ratio > 0.4) return "\u2588\u2591\u2591";
        if (ratio > 0.2) return "\u2591\u2591\u2591";
        return "\u2591";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + repeat(" ", width - s.length());
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 2) + "..";
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
