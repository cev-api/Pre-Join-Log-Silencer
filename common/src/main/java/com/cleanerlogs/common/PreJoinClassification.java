package com.cleanerlogs.common;

import java.util.Objects;

/**
 * Outcome of inspecting a single log event.
 *
 * @param phase        best known connection phase, or {@link ConnectionPhase#UNKNOWN} when it could not be
 *                     identified
 * @param basis        the evidence that produced the phase
 * @param suppressible true only when there is strong evidence that this event is the "lost connection" INFO
 *                     line of a connection that disconnected before reaching PLAY
 * @param reason       human readable explanation, surfaced by debug mode
 */
public record PreJoinClassification(ConnectionPhase phase, DetectionBasis basis, boolean suppressible, String reason) {

    public PreJoinClassification {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(reason, "reason");
    }

    static PreJoinClassification notSuppressible(ConnectionPhase phase, DetectionBasis basis, String reason) {
        return new PreJoinClassification(phase, basis, false, reason);
    }

    static PreJoinClassification suppressible(ConnectionPhase phase, DetectionBasis basis, String reason) {
        return new PreJoinClassification(phase, basis, true, reason);
    }
}
