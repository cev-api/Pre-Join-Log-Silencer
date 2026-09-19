package com.cleanerlogs.common;

/**
 * The Minecraft connection phase a log event came from.
 *
 * <p>{@link #LOGIN} and {@link #CONFIGURATION} connections have not joined the server yet and therefore never
 * became a player session. {@link #PLAY} connections are real player sessions.</p>
 */
public enum ConnectionPhase {

    /** Handshake and login phase, before the server knows a player profile. */
    LOGIN,

    /** Configuration phase (Minecraft 1.20.2 and later), after login but before play. */
    CONFIGURATION,

    /** The connection reached a real in-game player session. */
    PLAY,

    /** The source of the event could not be identified. */
    UNKNOWN;

    /**
     * @return true when a disconnect in this phase happens before the connection ever reached PLAY
     */
    public boolean isBeforePlay() {
        return this == LOGIN || this == CONFIGURATION;
    }
}
