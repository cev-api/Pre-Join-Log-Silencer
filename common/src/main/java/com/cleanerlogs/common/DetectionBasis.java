package com.cleanerlogs.common;

/**
 * How a {@link PreJoinClassification} was reached. Only used for diagnostics and tests; the suppression
 * decision itself never depends on it.
 */
public enum DetectionBasis {

    /** The Log4j2 logger identity alone proved which kind of connection produced the event. */
    LOGGER_IDENTITY,

    /** The logger identity was unknown, so the structured shape of the message had to be used instead. */
    MESSAGE_STRUCTURE,

    /** No evidence was found. */
    NONE
}
