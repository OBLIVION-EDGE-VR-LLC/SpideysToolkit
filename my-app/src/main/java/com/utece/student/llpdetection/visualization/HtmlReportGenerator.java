package com.utece.student.llpdetection.visualization;

import com.utece.student.llpdetection.predicate.PredicateViolation;
import com.utece.student.llpdetection.predicate.builtin.DataRacePredicate;
import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.event.EventLog;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class HtmlReportGenerator {

    public static String generate(Map<Long, EventLog> logs,
                                   List<DataRacePredicate.RaceInfo> races,
                                   List<PredicateViolation> violations,
                                   String targetName) {
        return generate(logs, races, violations, targetName, 0L);
    }

    public static String generate(Map<Long, EventLog> logs,
                                   List<DataRacePredicate.RaceInfo> races,
                                   List<PredicateViolation> violations,
                                   String targetName,
                                   long seed) {

        List<String> threadNames = logs.values().stream()
                .map(EventLog::getThreadName).collect(Collectors.toList());
        List<Long> threadIds = logs.values().stream()
                .map(EventLog::getThreadId).collect(Collectors.toList());

        List<Event> allEvents = new ArrayList<>();
        for (EventLog log : logs.values()) allEvents.addAll(log.getEvents());
        allEvents.sort(Comparator.comparingLong(Event::getSequenceNumber));

        int totalEvents = allEvents.size();
        int numThreads = threadNames.size();

        Set<Long> raceSeqs = new HashSet<>();
        for (DataRacePredicate.RaceInfo r : races) {
            raceSeqs.add(r.event1.getSequenceNumber());
            raceSeqs.add(r.event2.getSequenceNumber());
        }

        Map<String, Map<String, Integer>> heatData = new LinkedHashMap<>();
        for (EventLog log : logs.values()) {
            for (Event e : log.getEvents()) {
                if (e.isMonitorOp()) continue;
                heatData.computeIfAbsent(e.getAccessKey(), k -> new LinkedHashMap<>())
                        .merge(log.getThreadName(), 1, Integer::sum);
            }
        }

        Set<String> raceLocs = races.stream().map(r -> r.location).collect(Collectors.toSet());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>LLP Predicate Detection Report - ").append(esc(targetName)).append("</title>\n");
        html.append("<style>\n").append(css()).append("\n</style>\n");
        html.append("</head>\n<body>\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("  <h1>LLP Predicate Detection Report</h1>\n");
        html.append("  <p class=\"target\">Target: <strong>").append(esc(targetName)).append("</strong>");
        if (seed != 0) {
            html.append(" &nbsp;|&nbsp; Seed: <code>").append(seed).append("</code>");
        }
        html.append(" &nbsp;|&nbsp; Java ").append(esc(System.getProperty("java.version")));
        html.append(" &nbsp;|&nbsp; ").append(Runtime.getRuntime().availableProcessors()).append(" CPUs");
        html.append("</p>\n");
        html.append("  <div class=\"stats\">\n");
        html.append("    <div class=\"stat\"><span class=\"num\">").append(numThreads).append("</span><br>Threads</div>\n");
        html.append("    <div class=\"stat\"><span class=\"num\">").append(totalEvents).append("</span><br>Events</div>\n");
        html.append("    <div class=\"stat ").append(races.isEmpty() ? "ok" : "bad").append("\"><span class=\"num\">")
                .append(races.size()).append("</span><br>Races</div>\n");
        html.append("    <div class=\"stat ").append(violations.isEmpty() ? "ok" : "bad").append("\"><span class=\"num\">")
                .append(violations.size()).append("</span><br>Violations</div>\n");
        html.append("  </div>\n</div>\n");

        // Search & filter bar
        html.append("<div class=\"filter-bar\">\n");
        html.append("  <input type=\"text\" id=\"searchBox\" placeholder=\"Search violations by type, location, thread...\" oninput=\"filterViolations()\">\n");
        html.append("  <div class=\"filter-chips\">\n");
        html.append("    <button class=\"chip active\" data-filter=\"all\" onclick=\"setFilter('all',this)\">All</button>\n");
        html.append("    <button class=\"chip\" data-filter=\"DataRace\" onclick=\"setFilter('DataRace',this)\">Data Race</button>\n");
        html.append("    <button class=\"chip\" data-filter=\"BufferOverflow\" onclick=\"setFilter('BufferOverflow',this)\">Buffer Overflow</button>\n");
        html.append("    <button class=\"chip\" data-filter=\"StackOverflow\" onclick=\"setFilter('StackOverflow',this)\">Stack Overflow</button>\n");
        html.append("    <button class=\"chip\" data-filter=\"HeapOverflow\" onclick=\"setFilter('HeapOverflow',this)\">Heap Overflow</button>\n");
        html.append("    <button class=\"chip\" data-filter=\"AtomicityViolation\" onclick=\"setFilter('AtomicityViolation',this)\">Atomicity</button>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Focused trace panel (hidden by default, shown when a violation is clicked)
        html.append("<div id=\"tracePanel\" class=\"trace-panel\" style=\"display:none\">\n");
        html.append("  <div class=\"trace-header\">\n");
        html.append("    <h2 id=\"traceTitle\">Trace View</h2>\n");
        html.append("    <button class=\"close-btn\" onclick=\"closeTrace()\">Clear Filter &times;</button>\n");
        html.append("  </div>\n");
        html.append("  <div id=\"traceInfo\" class=\"trace-info\"></div>\n");
        html.append("  <div id=\"traceSvgContainer\" class=\"trace-svg-container\"></div>\n");
        html.append("</div>\n");

        // Timeline SVG
        html.append("<div class=\"section\" id=\"timelineSection\">\n<h2>Thread Timeline</h2>\n");
        html.append("<p class=\"hint\">Click any violation below to filter this timeline to just that trace.</p>\n");
        html.append("<div class=\"svg-scroll\">\n");
        html.append(generateTimelineSvg(logs, allEvents, threadNames, threadIds, races, raceSeqs));
        html.append("</div>\n</div>\n");

        // Heat Map
        html.append("<div class=\"section\">\n<h2>Contention Heat Map</h2>\n");
        html.append("<p class=\"hint\">Darker = more accesses. Red border = data race detected.</p>\n");
        html.append(generateHeatMapTable(heatData, threadNames, raceLocs));
        html.append("\n</div>\n");

        // Unified violation list (races + predicate violations) -- all clickable
        html.append("<div class=\"section\" id=\"violationSection\">\n");
        html.append("<h2>All Violations (").append(races.size() + violations.size()).append(")</h2>\n");
        html.append("<div class=\"violation-list\" id=\"violationList\">\n");

        // Races as clickable cards
        for (int i = 0; i < races.size(); i++) {
            DataRacePredicate.RaceInfo r = races.get(i);
            String type = (r.event1.isWrite() && r.event2.isWrite()) ? "write-write" : "read-write";
            long seq1 = r.event1.getSequenceNumber();
            long seq2 = r.event2.getSequenceNumber();
            // Compute the trace: all events on both threads up to and including the race events
            String traceSeqs = computeTraceSeqs(r.event1, r.event2, logs, allEvents);

            html.append("<div class=\"v-card\" data-type=\"DataRace\" data-location=\"").append(esc(r.location))
                    .append("\" data-seqs=\"").append(traceSeqs)
                    .append("\" data-seq1=\"").append(seq1)
                    .append("\" data-seq2=\"").append(seq2)
                    .append("\" data-thread1=\"").append(esc(r.event1.getThreadName()))
                    .append("\" data-thread2=\"").append(esc(r.event2.getThreadName()))
                    .append("\" data-detail=\"").append(esc(type + ": " + fmtEvent(r.event1) + " vs " + fmtEvent(r.event2)))
                    .append("\" onclick=\"showTrace(this)\">\n");
            html.append("  <span class=\"v-badge race-badge\">DataRace</span>\n");
            html.append("  <span class=\"v-loc\">").append(esc(r.location)).append("</span>\n");
            html.append("  <span class=\"v-type\">").append(type).append("</span>\n");
            html.append("  <div class=\"v-threads\">\n");
            html.append("    <div><b>").append(esc(r.event1.getThreadName())).append("</b> ").append(esc(fmtEvent(r.event1))).append(" <code>seq=").append(seq1).append("</code></div>\n");
            html.append("    <div><b>").append(esc(r.event2.getThreadName())).append("</b> ").append(esc(fmtEvent(r.event2))).append(" <code>seq=").append(seq2).append("</code></div>\n");
            html.append("  </div>\n");
            html.append("</div>\n");
        }

        // Predicate violations as clickable cards
        for (int i = 0; i < violations.size(); i++) {
            PredicateViolation v = violations.get(i);
            String predName = v.getPredicate().getName();
            // Compute trace from witness events
            List<Event> witnesses = v.getWitnessEvents();
            String traceSeqs = computeViolationTraceSeqs(witnesses, logs, allEvents);
            String witnessThreads = witnesses.stream()
                    .map(Event::getThreadName).distinct().collect(Collectors.joining(", "));
            String witnessDetail = witnesses.stream()
                    .map(e -> e.getThreadName() + ": " + fmtEvent(e))
                    .collect(Collectors.joining(" | "));

            // Determine location from witnesses
            String location = witnesses.isEmpty() ? v.getDescription() :
                    witnesses.stream().map(Event::getAccessKey).distinct().collect(Collectors.joining(", "));

            html.append("<div class=\"v-card\" data-type=\"").append(esc(predName))
                    .append("\" data-location=\"").append(esc(location))
                    .append("\" data-seqs=\"").append(traceSeqs)
                    .append("\" data-seq1=\"").append(witnesses.isEmpty() ? -1 : witnesses.get(0).getSequenceNumber())
                    .append("\" data-seq2=\"").append(witnesses.size() > 1 ? witnesses.get(1).getSequenceNumber() : -1)
                    .append("\" data-thread1=\"").append(esc(witnessThreads))
                    .append("\" data-thread2=\"\"")
                    .append(" data-detail=\"").append(esc(witnessDetail))
                    .append("\" onclick=\"showTrace(this)\">\n");

            String badgeClass = predName.contains("Buffer") ? "buffer-badge" :
                    predName.contains("Stack") ? "stack-badge" :
                    predName.contains("Heap") ? "heap-badge" :
                    predName.contains("Atomicity") ? "atom-badge" : "other-badge";

            html.append("  <span class=\"v-badge ").append(badgeClass).append("\">").append(esc(predName)).append("</span>\n");
            html.append("  <span class=\"v-loc\">").append(esc(location)).append("</span>\n");
            html.append("  <div class=\"v-threads\">\n");
            for (Event w : witnesses) {
                html.append("    <div><b>").append(esc(w.getThreadName())).append("</b> ")
                        .append(esc(fmtEvent(w))).append(" <code>seq=").append(w.getSequenceNumber()).append("</code></div>\n");
            }
            html.append("  </div>\n");
            html.append("</div>\n");
        }

        html.append("</div>\n</div>\n");

        // Embed event data as JSON for JS to use
        html.append("<script>\n");
        html.append("const EVENT_DATA = [\n");
        for (Event e : allEvents) {
            html.append("  {seq:").append(e.getSequenceNumber())
                    .append(",tid:").append(e.getThreadId())
                    .append(",thread:\"").append(escJs(e.getThreadName()))
                    .append("\",type:\"").append(e.getType().name())
                    .append("\",loc:\"").append(escJs(e.getAccessKey()))
                    .append("\",field:\"").append(escJs(e.getTargetField()))
                    .append("\",cls:\"").append(escJs(e.getTargetClass()))
                    .append("\",oldVal:\"").append(escJs(String.valueOf(e.getOldValue())))
                    .append("\",newVal:\"").append(escJs(String.valueOf(e.getNewValue())))
                    .append("\",idx:").append(e.getArrayIndex())
                    .append("},\n");
        }
        html.append("];\n");
        html.append("const SEED = ").append(seed).append(";\n");
        html.append("const THREAD_NAMES = [");
        for (int i = 0; i < threadNames.size(); i++) {
            if (i > 0) html.append(",");
            html.append("\"").append(escJs(threadNames.get(i))).append("\"");
        }
        html.append("];\n");
        html.append(jsCode());
        html.append("\n</script>\n");

        html.append("<div class=\"footer\">Generated by LLP Predicate Detector</div>\n");
        html.append("</body>\n</html>\n");

        return html.toString();
    }

    private static String computeTraceSeqs(Event e1, Event e2,
                                            Map<Long, EventLog> logs,
                                            List<Event> allEvents) {
        // Get all events on both threads up to the race point
        long maxSeq = Math.max(e1.getSequenceNumber(), e2.getSequenceNumber());
        Set<Long> involvedThreads = new HashSet<>();
        involvedThreads.add(e1.getThreadId());
        involvedThreads.add(e2.getThreadId());

        List<Long> seqs = new ArrayList<>();
        for (Event e : allEvents) {
            if (involvedThreads.contains(e.getThreadId()) && e.getSequenceNumber() <= maxSeq) {
                seqs.add(e.getSequenceNumber());
            }
        }
        return seqs.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String computeViolationTraceSeqs(List<Event> witnesses,
                                                      Map<Long, EventLog> logs,
                                                      List<Event> allEvents) {
        if (witnesses.isEmpty()) return "";

        Set<Long> involvedThreads = witnesses.stream()
                .map(Event::getThreadId).collect(Collectors.toSet());
        long maxSeq = witnesses.stream()
                .mapToLong(Event::getSequenceNumber).max().orElse(0);

        List<Long> seqs = new ArrayList<>();
        for (Event e : allEvents) {
            if (involvedThreads.contains(e.getThreadId()) && e.getSequenceNumber() <= maxSeq) {
                seqs.add(e.getSequenceNumber());
            }
        }
        return seqs.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static File writeReport(Map<Long, EventLog> logs,
                                    List<DataRacePredicate.RaceInfo> races,
                                    List<PredicateViolation> violations,
                                    String targetName) throws IOException {
        return writeReport(logs, races, violations, targetName, 0L);
    }

    public static File writeReport(Map<Long, EventLog> logs,
                                    List<DataRacePredicate.RaceInfo> races,
                                    List<PredicateViolation> violations,
                                    String targetName,
                                    long seed) throws IOException {
        String content = generate(logs, races, violations, targetName, seed);
        File file = new File("llp-report.html");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.print(content);
        }
        return file;
    }

    private static String generateTimelineSvg(Map<Long, EventLog> logs,
                                               List<Event> allEvents,
                                               List<String> threadNames,
                                               List<Long> threadIds,
                                               List<DataRacePredicate.RaceInfo> races,
                                               Set<Long> raceSeqs) {
        int laneWidth = 160;
        int headerH = 40;
        int rowH = 28;
        int leftMargin = 50;
        int svgW = leftMargin + laneWidth * threadNames.size() + 40;
        int svgH = headerH + rowH * allEvents.size() + 20;

        Map<Long, Integer> tidToCol = new HashMap<>();
        for (int i = 0; i < threadIds.size(); i++) tidToCol.put(threadIds.get(i), i);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg id=\"mainTimeline\" width=\"").append(svgW).append("\" height=\"").append(svgH)
                .append("\" xmlns=\"http://www.w3.org/2000/svg\" class=\"timeline\">\n");

        for (int i = 0; i < threadNames.size(); i++) {
            int cx = leftMargin + i * laneWidth + laneWidth / 2;
            svg.append("<text x=\"").append(cx).append("\" y=\"20\" text-anchor=\"middle\" class=\"thread-label\">")
                    .append(esc(threadNames.get(i))).append("</text>\n");
            svg.append("<line x1=\"").append(cx).append("\" y1=\"30\" x2=\"").append(cx)
                    .append("\" y2=\"").append(svgH - 10).append("\" class=\"lane-line\"/>\n");
        }

        for (int row = 0; row < allEvents.size(); row++) {
            Event e = allEvents.get(row);
            Integer col = tidToCol.get(e.getThreadId());
            if (col == null) continue;

            int cx = leftMargin + col * laneWidth + laneWidth / 2;
            int cy = headerH + row * rowH + rowH / 2;

            boolean isRace = raceSeqs.contains(e.getSequenceNumber());
            String color = isRace ? "#ff4444" : eventSvgColor(e);
            int radius = isRace ? 8 : 6;

            // Wrap each event row in a group with data-seq for JS filtering
            svg.append("<g class=\"event-row\" data-seq=\"").append(e.getSequenceNumber())
                    .append("\" data-tid=\"").append(e.getThreadId())
                    .append("\" data-loc=\"").append(esc(e.getAccessKey())).append("\">\n");

            svg.append("  <text x=\"10\" y=\"").append(cy + 4).append("\" class=\"seq-label\">")
                    .append(e.getSequenceNumber()).append("</text>\n");

            svg.append("  <circle cx=\"").append(cx).append("\" cy=\"").append(cy)
                    .append("\" r=\"").append(radius).append("\" fill=\"").append(color).append("\" class=\"event-dot\">\n");
            svg.append("    <title>").append(esc(e.toString())).append("</title>\n");
            svg.append("  </circle>\n");

            String label = shortLabel(e);
            svg.append("  <text x=\"").append(cx + radius + 4).append("\" y=\"").append(cy + 4)
                    .append("\" class=\"event-label").append(isRace ? " race-label" : "").append("\">")
                    .append(esc(label)).append("</text>\n");

            svg.append("</g>\n");
        }

        // Race lines with data attributes
        for (int i = 0; i < races.size(); i++) {
            DataRacePredicate.RaceInfo race = races.get(i);
            // compute positions inline
            svg.append("<line class=\"race-line\" data-race-idx=\"").append(i)
                    .append("\" data-seq1=\"").append(race.event1.getSequenceNumber())
                    .append("\" data-seq2=\"").append(race.event2.getSequenceNumber())
                    .append("\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"0\" style=\"display:none\">\n");
            svg.append("  <title>RACE: ").append(esc(race.location)).append("</title>\n");
            svg.append("</line>\n");
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String generateHeatMapTable(Map<String, Map<String, Integer>> heatData,
                                                List<String> threadNames,
                                                Set<String> raceLocs) {
        int maxCount = heatData.values().stream()
                .flatMap(m -> m.values().stream())
                .max(Integer::compareTo).orElse(1);

        StringBuilder table = new StringBuilder();
        table.append("<table class=\"heat-table\">\n<thead><tr><th>Location</th>");
        for (String name : threadNames) {
            table.append("<th>").append(esc(name)).append("</th>");
        }
        table.append("<th>Status</th></tr></thead>\n<tbody>\n");

        List<String> sorted = new ArrayList<>(heatData.keySet());
        sorted.sort((a, b) -> {
            boolean ar = raceLocs.contains(a);
            boolean br = raceLocs.contains(b);
            if (ar != br) return ar ? -1 : 1;
            int at = heatData.get(a).values().stream().mapToInt(Integer::intValue).sum();
            int bt = heatData.get(b).values().stream().mapToInt(Integer::intValue).sum();
            return Integer.compare(bt, at);
        });

        for (String loc : sorted) {
            Map<String, Integer> counts = heatData.get(loc);
            boolean isRace = raceLocs.contains(loc);

            table.append("<tr class=\"").append(isRace ? "race-row" : "").append("\">");
            table.append("<td class=\"loc\">").append(esc(loc)).append("</td>");

            for (String thread : threadNames) {
                int count = counts.getOrDefault(thread, 0);
                double intensity = maxCount > 0 ? (double) count / maxCount : 0;
                String bg = count == 0 ? "#1a1a2e" :
                        String.format("rgba(%d, %d, %d, %.2f)",
                                isRace ? 255 : 100,
                                isRace ? (int)(80 * (1 - intensity)) : (int)(200 * intensity),
                                isRace ? (int)(80 * (1 - intensity)) : 100,
                                0.3 + 0.7 * intensity);
                table.append("<td class=\"heat-cell\" style=\"background:").append(bg).append("\">")
                        .append(count > 0 ? count : "").append("</td>");
            }

            table.append("<td class=\"status ").append(isRace ? "race" : "ok").append("\">")
                    .append(isRace ? "RACE" : "OK").append("</td>");
            table.append("</tr>\n");
        }

        table.append("</tbody></table>\n");
        return table.toString();
    }

    private static String jsCode() {
        return "\nlet activeFilter = 'all';\n"
            + "let activeCard = null;\n\n"

            + "function setFilter(type, btn) {\n"
            + "  activeFilter = type;\n"
            + "  document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));\n"
            + "  btn.classList.add('active');\n"
            + "  filterViolations();\n"
            + "}\n\n"

            + "function filterViolations() {\n"
            + "  const query = document.getElementById('searchBox').value.toLowerCase();\n"
            + "  document.querySelectorAll('.v-card').forEach(card => {\n"
            + "    const type = card.dataset.type;\n"
            + "    const loc = card.dataset.location.toLowerCase();\n"
            + "    const detail = (card.dataset.detail || '').toLowerCase();\n"
            + "    const t1 = (card.dataset.thread1 || '').toLowerCase();\n"
            + "    const t2 = (card.dataset.thread2 || '').toLowerCase();\n"
            + "    const matchesType = activeFilter === 'all' || type === activeFilter;\n"
            + "    const matchesSearch = !query || loc.includes(query) || type.toLowerCase().includes(query)\n"
            + "        || detail.includes(query) || t1.includes(query) || t2.includes(query);\n"
            + "    card.style.display = (matchesType && matchesSearch) ? '' : 'none';\n"
            + "  });\n"
            + "}\n\n"

            + "function showTrace(card) {\n"
            + "  if (activeCard === card) { closeTrace(); return; }\n"
            + "  activeCard = card;\n"
            + "  document.querySelectorAll('.v-card').forEach(c => c.classList.remove('selected'));\n"
            + "  card.classList.add('selected');\n\n"

            + "  const seqStr = card.dataset.seqs;\n"
            + "  const traceSeqs = new Set(seqStr ? seqStr.split(',').map(Number) : []);\n"
            + "  const seq1 = parseInt(card.dataset.seq1);\n"
            + "  const seq2 = parseInt(card.dataset.seq2);\n"
            + "  const type = card.dataset.type;\n"
            + "  const location = card.dataset.location;\n"
            + "  const thread1 = card.dataset.thread1;\n"
            + "  const thread2 = card.dataset.thread2;\n"
            + "  const detail = card.dataset.detail;\n\n"

            // Dim non-trace events in the main timeline
            + "  document.querySelectorAll('.event-row').forEach(g => {\n"
            + "    const seq = parseInt(g.dataset.seq);\n"
            + "    if (traceSeqs.has(seq)) {\n"
            + "      g.style.opacity = '1';\n"
            + "      if (seq === seq1 || seq === seq2) {\n"
            + "        g.querySelector('.event-dot').setAttribute('r', '10');\n"
            + "        g.querySelector('.event-dot').setAttribute('stroke', '#fff');\n"
            + "        g.querySelector('.event-dot').setAttribute('stroke-width', '3');\n"
            + "      }\n"
            + "    } else {\n"
            + "      g.style.opacity = '0.1';\n"
            + "    }\n"
            + "  });\n\n"

            // Build the trace panel
            + "  const panel = document.getElementById('tracePanel');\n"
            + "  panel.style.display = 'block';\n"
            + "  document.getElementById('traceTitle').textContent = type + ': ' + location;\n\n"

            // Build trace info
            + "  let infoHtml = '<div class=\"trace-meta\">';\n"
            + "  infoHtml += '<div class=\"trace-meta-item\"><b>Type:</b> ' + type + '</div>';\n"
            + "  infoHtml += '<div class=\"trace-meta-item\"><b>Location:</b> <code>' + location + '</code></div>';\n"
            + "  if (thread1) infoHtml += '<div class=\"trace-meta-item\"><b>Threads:</b> ' + thread1 + (thread2 ? ' vs ' + thread2 : '') + '</div>';\n"
            + "  if (SEED) infoHtml += '<div class=\"trace-meta-item\"><b>Seed:</b> <code>' + SEED + '</code></div>';\n"
            + "  infoHtml += '</div>';\n\n"

            // Build filtered event table
            + "  const traceEvents = EVENT_DATA.filter(e => traceSeqs.has(e.seq));\n"
            + "  infoHtml += '<h3>Execution Path (' + traceEvents.length + ' events)</h3>';\n"
            + "  infoHtml += '<table class=\"trace-table\"><thead><tr>';\n"
            + "  infoHtml += '<th>Seq</th><th>Thread</th><th>Type</th><th>Location</th><th>Old</th><th>New</th>';\n"
            + "  infoHtml += '</tr></thead><tbody>';\n"
            + "  traceEvents.forEach(e => {\n"
            + "    const isWitness = (e.seq === seq1 || e.seq === seq2);\n"
            + "    const cls = isWitness ? 'witness-row' : '';\n"
            + "    const typeColor = e.type.includes('WRITE') ? '#f0883e' : e.type.includes('READ') ? '#3fb950' : '#58a6ff';\n"
            + "    infoHtml += '<tr class=\"' + cls + '\">';\n"
            + "    infoHtml += '<td>' + e.seq + '</td>';\n"
            + "    infoHtml += '<td>' + e.thread + '</td>';\n"
            + "    infoHtml += '<td style=\"color:' + typeColor + '\">' + e.type + '</td>';\n"
            + "    infoHtml += '<td><code>' + e.loc + '</code></td>';\n"
            + "    infoHtml += '<td>' + (e.oldVal !== 'null' ? e.oldVal : '') + '</td>';\n"
            + "    infoHtml += '<td>' + (e.newVal !== 'null' ? e.newVal : '') + '</td>';\n"
            + "    infoHtml += '</tr>';\n"
            + "  });\n"
            + "  infoHtml += '</tbody></table>';\n"
            + "  document.getElementById('traceInfo').innerHTML = infoHtml;\n\n"

            // Scroll to trace panel
            + "  panel.scrollIntoView({behavior: 'smooth', block: 'start'});\n"
            + "}\n\n"

            + "function closeTrace() {\n"
            + "  activeCard = null;\n"
            + "  document.querySelectorAll('.v-card').forEach(c => c.classList.remove('selected'));\n"
            + "  document.querySelectorAll('.event-row').forEach(g => {\n"
            + "    g.style.opacity = '1';\n"
            + "    const dot = g.querySelector('.event-dot');\n"
            + "    dot.removeAttribute('stroke');\n"
            + "    dot.removeAttribute('stroke-width');\n"
            + "    const origR = dot.getAttribute('fill') === '#ff4444' ? '8' : '6';\n"
            + "    dot.setAttribute('r', origR);\n"
            + "  });\n"
            + "  document.getElementById('tracePanel').style.display = 'none';\n"
            + "}\n";
    }

    private static String css() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "body { font-family: 'Segoe UI', system-ui, sans-serif; background: #0d1117; color: #c9d1d9; padding: 20px; }\n"
            + ".header { text-align: center; padding: 30px 0; border-bottom: 1px solid #30363d; }\n"
            + ".header h1 { color: #58a6ff; font-size: 28px; margin-bottom: 8px; }\n"
            + ".target { color: #8b949e; font-size: 14px; margin-bottom: 20px; }\n"
            + ".target code { background: #21262d; padding: 2px 6px; border-radius: 3px; color: #f0883e; }\n"
            + ".stats { display: flex; justify-content: center; gap: 30px; }\n"
            + ".stat { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px 24px; text-align: center; }\n"
            + ".stat .num { font-size: 32px; font-weight: bold; color: #58a6ff; }\n"
            + ".stat.ok .num { color: #3fb950; }\n"
            + ".stat.bad .num { color: #f85149; }\n"
            + ".section { margin: 30px 0; padding: 20px; background: #161b22; border: 1px solid #30363d; border-radius: 8px; }\n"
            + ".section h2 { color: #58a6ff; margin-bottom: 16px; font-size: 20px; }\n"
            + ".hint { color: #8b949e; font-size: 12px; margin-bottom: 12px; }\n"
            + ".svg-scroll { overflow-x: auto; overflow-y: auto; max-height: 600px; }\n"
            + ".timeline { display: block; }\n"
            + ".thread-label { font-size: 13px; font-weight: bold; fill: #58a6ff; }\n"
            + ".lane-line { stroke: #21262d; stroke-width: 1; stroke-dasharray: 4 4; }\n"
            + ".seq-label { font-size: 10px; fill: #484f58; }\n"
            + ".event-dot { cursor: pointer; transition: opacity 0.3s; }\n"
            + ".event-dot:hover { stroke: white; stroke-width: 2; }\n"
            + ".event-label { font-size: 11px; fill: #8b949e; transition: opacity 0.3s; }\n"
            + ".race-label { fill: #f85149; font-weight: bold; }\n"
            + ".race-line { stroke: #f85149; stroke-width: 2; stroke-dasharray: 6 3; opacity: 0.7; }\n"
            + ".event-row { transition: opacity 0.3s; }\n"

            // Filter bar
            + ".filter-bar { margin: 20px 0; padding: 16px 20px; background: #161b22; border: 1px solid #30363d; border-radius: 8px; display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }\n"
            + ".filter-bar input { flex: 1; min-width: 250px; padding: 8px 14px; background: #0d1117; border: 1px solid #30363d; border-radius: 6px; color: #c9d1d9; font-size: 14px; outline: none; }\n"
            + ".filter-bar input:focus { border-color: #58a6ff; }\n"
            + ".filter-chips { display: flex; gap: 6px; flex-wrap: wrap; }\n"
            + ".chip { padding: 4px 12px; border-radius: 16px; border: 1px solid #30363d; background: transparent; color: #8b949e; cursor: pointer; font-size: 12px; transition: all 0.2s; }\n"
            + ".chip:hover { border-color: #58a6ff; color: #58a6ff; }\n"
            + ".chip.active { background: #58a6ff; color: #0d1117; border-color: #58a6ff; font-weight: bold; }\n"

            // Trace panel
            + ".trace-panel { margin: 20px 0; padding: 20px; background: #0d1117; border: 2px solid #f0883e; border-radius: 8px; }\n"
            + ".trace-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }\n"
            + ".trace-header h2 { color: #f0883e; font-size: 18px; }\n"
            + ".close-btn { padding: 6px 14px; background: #21262d; border: 1px solid #30363d; color: #c9d1d9; border-radius: 6px; cursor: pointer; font-size: 13px; }\n"
            + ".close-btn:hover { background: #30363d; border-color: #58a6ff; }\n"
            + ".trace-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 16px; }\n"
            + ".trace-meta-item { font-size: 13px; }\n"
            + ".trace-meta-item code { background: #161b22; padding: 2px 6px; border-radius: 3px; color: #f0883e; }\n"
            + ".trace-info h3 { color: #58a6ff; font-size: 15px; margin: 12px 0 8px; }\n"
            + ".trace-table { width: 100%; border-collapse: collapse; font-size: 12px; font-family: 'JetBrains Mono', monospace, 'Courier New'; }\n"
            + ".trace-table th { background: #161b22; color: #58a6ff; padding: 6px 10px; text-align: left; border-bottom: 2px solid #30363d; position: sticky; top: 0; }\n"
            + ".trace-table td { padding: 4px 10px; border-bottom: 1px solid #21262d; }\n"
            + ".trace-table code { color: #c9d1d9; }\n"
            + ".witness-row { background: rgba(248, 81, 73, 0.15); border-left: 3px solid #f85149; }\n"
            + ".witness-row td { font-weight: bold; }\n"

            // Violation cards
            + ".violation-list { display: flex; flex-direction: column; gap: 8px; }\n"
            + ".v-card { background: #0d1117; border: 1px solid #30363d; border-radius: 8px; padding: 12px 16px; cursor: pointer; transition: all 0.2s; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }\n"
            + ".v-card:hover { border-color: #58a6ff; background: #161b22; }\n"
            + ".v-card.selected { border-color: #f0883e; border-width: 2px; background: rgba(240, 136, 62, 0.08); }\n"
            + ".v-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; white-space: nowrap; }\n"
            + ".race-badge { background: #f85149; color: #fff; }\n"
            + ".buffer-badge { background: #d29922; color: #0d1117; }\n"
            + ".stack-badge { background: #a371f7; color: #fff; }\n"
            + ".heap-badge { background: #f778ba; color: #0d1117; }\n"
            + ".atom-badge { background: #79c0ff; color: #0d1117; }\n"
            + ".other-badge { background: #8b949e; color: #0d1117; }\n"
            + ".v-loc { font-family: monospace; color: #f0883e; font-size: 14px; }\n"
            + ".v-type { color: #8b949e; font-size: 12px; }\n"
            + ".v-threads { width: 100%; font-size: 12px; color: #8b949e; margin-top: 4px; }\n"
            + ".v-threads code { background: #21262d; padding: 1px 4px; border-radius: 3px; font-size: 11px; }\n"

            // Heat map
            + ".heat-table { width: 100%; border-collapse: collapse; font-size: 13px; }\n"
            + ".heat-table th { background: #0d1117; color: #58a6ff; padding: 8px 12px; text-align: center; border-bottom: 2px solid #30363d; }\n"
            + ".heat-table td { padding: 6px 12px; text-align: center; border-bottom: 1px solid #21262d; }\n"
            + ".heat-table .loc { text-align: left; font-family: monospace; color: #c9d1d9; }\n"
            + ".race-row { border-left: 3px solid #f85149; }\n"
            + ".race-row .loc { color: #f85149; font-weight: bold; }\n"
            + ".status.race { color: #f85149; font-weight: bold; }\n"
            + ".status.ok { color: #3fb950; }\n"
            + ".footer { text-align: center; color: #484f58; font-size: 12px; padding: 30px 0; }\n";
    }

    private static String eventSvgColor(Event e) {
        switch (e.getType()) {
            case FIELD_WRITE:
            case ARRAY_WRITE:   return "#f0883e";
            case FIELD_READ:
            case ARRAY_READ:    return "#3fb950";
            case MONITOR_ENTER:
            case MONITOR_EXIT:  return "#58a6ff";
            default:            return "#8b949e";
        }
    }

    private static String shortLabel(Event e) {
        String prefix;
        switch (e.getType()) {
            case FIELD_READ:    prefix = "R "; break;
            case FIELD_WRITE:   prefix = "W "; break;
            case ARRAY_READ:    prefix = "R "; break;
            case ARRAY_WRITE:   prefix = "W "; break;
            case MONITOR_ENTER: prefix = "LOCK "; break;
            case MONITOR_EXIT:  prefix = "UNLOCK "; break;
            default:            prefix = ""; break;
        }
        String f = e.getTargetField();
        if (e.getArrayIndex() >= 0) f += "[" + e.getArrayIndex() + "]";
        return prefix + f;
    }

    private static String fmtEvent(Event e) {
        if (e.isWrite()) return e.getAccessKey() + " = " + e.getNewValue() + " (was " + e.getOldValue() + ")";
        return e.getAccessKey() + " -> " + e.getNewValue();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
