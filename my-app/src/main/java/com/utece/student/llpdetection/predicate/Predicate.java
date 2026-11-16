package com.utece.student.llpdetection.predicate;

import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.predicate.lattice.Cut;

import java.util.Map;

public interface Predicate {

    boolean test(Cut cut, Map<Long, EventLog> logs);

    String getName();

    default String getDescription() {
        return getName();
    }
}
