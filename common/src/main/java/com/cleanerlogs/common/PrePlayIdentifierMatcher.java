package com.cleanerlogs.common;

import java.util.regex.Pattern;

/**
 * Recognises the connection identifiers Minecraft uses for connections that have not reached PLAY.
 *
 * <p>This is the fallback for the rare case where the logger identity is unknown (for example an unmapped
 * intermediary logger name). The rule is deliberately one sided: a value only counts as a pre-join identifier
 * when it is something a PLAY-state log line can never contain, because the game listener logs the bare player
 * name.</p>
 *
 * <ul>
 *   <li>a remote socket address in parentheses, for example {@code Probe (/32.188.152.209:51894)}</li>
 *   <li>a bare remote socket address, for example {@code /192.168.1.12:51901}</li>
 *   <li>{@code GameProfile.toString()}, for example
 *       {@code com.mojang.authlib.GameProfile@1f2e3d[id=&lt;null&gt;,name=Probe,...]}</li>
 * </ul>
 *
 * <p>A Minecraft username may only contain {@code A-Z a-z 0-9 _}, so a plain username can never satisfy any of
 * these shapes.</p>
 */
public final class PrePlayIdentifierMatcher {

    /** Bracketed IPv6 literal, as produced by InetSocketAddress.toString(). */
    private static final String BRACKETED_IPV6 = "\\[[0-9A-Fa-f:.]{2,45}\\]";

    /** Unbracketed IPv6 literal, which some JDK versions produce instead. */
    private static final String BARE_IPV6 = "[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){2,7}";

    /** Hostname or IPv4 literal. */
    private static final String HOST_NAME = "[A-Za-z0-9][A-Za-z0-9._-]{0,253}";

    private static final String HOST = "(?:" + BRACKETED_IPV6 + "|" + BARE_IPV6 + "|" + HOST_NAME + ")";

    /** Host, optional virtual-host prefix and port. */
    private static final String SOCKET_ADDRESS = "(?:[A-Za-z0-9._-]{1,253}/)?" + HOST + ":[0-9]{1,5}";

    /** {@code Name (host:port)} or {@code GameProfile[...] (host:port)}. */
    private static final Pattern ADDRESS_IN_PARENTHESES = Pattern.compile("\\((?:/)?" + SOCKET_ADDRESS + "\\)$");

    /** A bare socket address, only produced while the login listener has no profile yet. */
    private static final Pattern BARE_SOCKET_ADDRESS = Pattern.compile("^/" + SOCKET_ADDRESS + "$");

    /** {@code GameProfile.toString()}, which contains characters a player name may never contain. */
    private static final Pattern GAME_PROFILE_TO_STRING =
        Pattern.compile("^(?:com\\.mojang\\.authlib\\.)?GameProfile[@\\[].*$");

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * @param value the first parameter of an {@code "{} lost connection: {}"} log event
     * @return true when the value can only have been produced for a connection without a player session
     */
    public static boolean isPrePlayIdentifier(String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        return GAME_PROFILE_TO_STRING.matcher(candidate).matches()
            || ADDRESS_IN_PARENTHESES.matcher(candidate).find()
            || BARE_SOCKET_ADDRESS.matcher(candidate).matches();
    }

    /**
     * @param value the second parameter of an {@code "{} ({}) lost connection: {}"} log event
     * @return true when the value is a game profile UUID
     */
    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value.trim()).matches();
    }

    private PrePlayIdentifierMatcher() {
    }
}
