package com.cleanerlogs.common;

/**
 * The parameterised message shapes Minecraft uses for its "lost connection" log line.
 *
 * <p>Every supported packet listener logs the disconnect through Log4j2 with {@code {}} placeholders, so the
 * original format string survives in {@link org.apache.logging.log4j.message.Message#getFormat()} and can be
 * matched without ever rendering the message. Matching the format rather than the printed text is what keeps
 * the filter narrow: a plugin that merely prints the words "lost connection" as literal text can never match,
 * because its format string has no {@code {}} placeholders.</p>
 */
public enum LostConnectionFormat {

    /**
     * {@code "{} lost connection: {}"}. Used by the login listener (connection identifier plus reason) and by
     * the game listener (player name plus reason).
     */
    IDENTIFIER_AND_REASON("{} lost connection: {}", 2),

    /**
     * {@code "{} ({}) lost connection: {}"}. Used by the configuration listener: player name, game profile
     * UUID and reason.
     */
    NAME_UUID_AND_REASON("{} ({}) lost connection: {}", 3);

    /** The part every Minecraft disconnect line contains. */
    public static final String DISCONNECT_MARKER = "lost connection: {}";

    private final String prefix;
    private final int requiredParameterCount;

    LostConnectionFormat(String prefix, int requiredParameterCount) {
        this.prefix = prefix;
        this.requiredParameterCount = requiredParameterCount;
    }

    /**
     * @return the leading part of the format string, placeholders included
     */
    public String prefix() {
        return prefix;
    }

    /**
     * @return how many message parameters this shape must carry. An event with fewer parameters can only have
     *         come from some other logging call that happens to share the prefix, so it is never suppressed.
     */
    public int requiredParameterCount() {
        return requiredParameterCount;
    }

    /**
     * Resolves the shape of a raw format string.
     *
     * <p>Text after the last placeholder is tolerated on purpose: Paper appends
     * {@code ", while in configuration phase {}"} to the configuration line when debug logging is enabled.</p>
     *
     * @param format the raw format string, may be {@code null}
     * @return the matching shape, or {@code null} when this is not a Minecraft disconnect line
     */
    public static LostConnectionFormat fromFormat(String format) {
        if (format == null || !format.contains(DISCONNECT_MARKER)) {
            return null;
        }
        if (format.startsWith(NAME_UUID_AND_REASON.prefix)) {
            return NAME_UUID_AND_REASON;
        }
        if (format.startsWith(IDENTIFIER_AND_REASON.prefix)) {
            return IDENTIFIER_AND_REASON;
        }
        return null;
    }
}
