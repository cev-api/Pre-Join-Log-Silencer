package com.cleanerlogs.common;

/**
 * Diagnostic snapshot of a log event the detector considered interesting.
 *
 * <p>Only built while debug mode is on, so the normal logging path never allocates one.</p>
 *
 * @param loggerName       the Log4j2 logger name (category)
 * @param level            the log level
 * @param messageClass     the concrete {@link org.apache.logging.log4j.message.Message} implementation
 * @param messageFormat    the raw format string with placeholders not substituted, may be {@code null}
 * @param formattedMessage the rendered message, exactly as the server would print it, may be {@code null}
 * @param parameterCount   number of message parameters, or {@code -1} when the message is not parameterised
 * @param classification   the detector verdict
 */
public record DetectionReport(
    String loggerName,
    String level,
    String messageClass,
    String messageFormat,
    String formattedMessage,
    int parameterCount,
    PreJoinClassification classification) {
}
