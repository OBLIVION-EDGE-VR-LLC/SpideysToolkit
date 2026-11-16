package com.utece.student.llpdetection.predicate;

import com.utece.student.llpdetection.predicate.event.Event;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.List;

public final class PredicateViolation {

    private final Predicate predicate;
    private final Cut cut;
    private final List<Event> witnessEvents;
    private final String description;
    private final long detectedAtTimestamp;

    public PredicateViolation(Predicate predicate, Cut cut,
                              List<Event> witnessEvents, String description) {
        this.predicate = predicate;
        this.cut = cut;
        this.witnessEvents = witnessEvents;
        this.description = description;
        this.detectedAtTimestamp = System.nanoTime();
    }

    public Predicate getPredicate() { return predicate; }
    public Cut getCut() { return cut; }
    public List<Event> getWitnessEvents() { return witnessEvents; }
    public String getDescription() { return description; }
    public long getDetectedAtTimestamp() { return detectedAtTimestamp; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VIOLATION [").append(predicate.getName()).append("]: ");
        sb.append(description).append("\n");
        sb.append("  Witness events:\n");
        for (Event e : witnessEvents) {
            sb.append("    - ").append(e).append("\n");
        }
        return sb.toString();
    }
}
