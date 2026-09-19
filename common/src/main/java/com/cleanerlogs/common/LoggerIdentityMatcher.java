package com.cleanerlogs.common;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, thread-safe lookup from a Log4j2 logger name to a {@link ConnectionPhase}.
 *
 * <p>Names are matched in two ways. The trailing simple name is matched first, which keeps the project working
 * when only the package differs between Mojang mappings, a server implementation, a mapped runtime or a future
 * release, while still requiring the match to sit at a package or class boundary. Exact names are matched as
 * well, so platform adapters can register a resolved runtime name such as a Fabric intermediary class name. A
 * further pass normalises casing and nested-class suffixes for the rare logger name that does not already look
 * like a Java binary class name.</p>
 *
 * <p>PLAY is evaluated before LOGIN and CONFIGURATION so that a game listener can never be mistaken for a
 * pre-join listener.</p>
 */
public final class LoggerIdentityMatcher {

    private static final ConnectionPhase[] PHASE_PRIORITY = {
        ConnectionPhase.PLAY,
        ConnectionPhase.LOGIN,
        ConnectionPhase.CONFIGURATION,
    };

    private final Map<ConnectionPhase, Set<String>> exactNames;
    private final Map<ConnectionPhase, Set<String>> simpleNames;
    private final Map<ConnectionPhase, Set<String>> lowerCaseExactNames;
    private final Map<ConnectionPhase, Set<String>> lowerCaseSimpleNames;
    private final Set<String> ignoredNames;

    private LoggerIdentityMatcher(Builder builder) {
        this.exactNames = freeze(builder.exactNames, false);
        this.simpleNames = freeze(builder.simpleNames, false);
        this.lowerCaseExactNames = freeze(builder.exactNames, true);
        this.lowerCaseSimpleNames = freeze(builder.simpleNames, true);
        this.ignoredNames = Set.copyOf(builder.ignoredNames);
    }

    /**
     * @return a builder for a new matcher table
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Classifies a Log4j2 logger name.
     *
     * @param loggerName the Log4j2 category, may be {@code null}
     * @return the phase the logger belongs to, or {@link ConnectionPhase#UNKNOWN}
     */
    public ConnectionPhase classify(String loggerName) {
        if (loggerName == null || loggerName.isEmpty()) {
            return ConnectionPhase.UNKNOWN;
        }
        // Fast path: no allocation, and it is the shape Java actually produces.
        ConnectionPhase phase = match(loggerName, exactNames, simpleNames);
        if (phase != ConnectionPhase.UNKNOWN) {
            return phase;
        }
        return match(normalise(loggerName), lowerCaseExactNames, lowerCaseSimpleNames);
    }

    /**
     * @param loggerName a logger name, may be {@code null}
     * @return true when this project must never classify the logger, for example its own diagnostic logger
     */
    public boolean isIgnored(String loggerName) {
        return loggerName != null && ignoredNames.contains(loggerName);
    }

    /**
     * @param phase a connection phase
     * @return the exact logger names registered for that phase
     */
    public Set<String> exactNames(ConnectionPhase phase) {
        return exactNames.getOrDefault(phase, Set.of());
    }

    /**
     * @param phase a connection phase
     * @return the trailing simple names registered for that phase
     */
    public Set<String> simpleNames(ConnectionPhase phase) {
        return simpleNames.getOrDefault(phase, Set.of());
    }

    /**
     * @return every name that must never be classified
     */
    public Set<String> ignoredNames() {
        return ignoredNames;
    }

    private static ConnectionPhase match(String value,
                                        Map<ConnectionPhase, Set<String>> exact,
                                        Map<ConnectionPhase, Set<String>> simple) {
        for (ConnectionPhase phase : PHASE_PRIORITY) {
            for (String suffix : simple.getOrDefault(phase, Set.of())) {
                if (matchesSimpleName(value, suffix)) {
                    return phase;
                }
            }
            if (exact.getOrDefault(phase, Set.of()).contains(value)) {
                return phase;
            }
        }
        return ConnectionPhase.UNKNOWN;
    }

    /**
     * Matches a trailing class simple name at a package or nested-class boundary.
     *
     * <p>The boundary matters: {@code net.minecraft.server.LoginListener} is the legacy login handler, while
     * {@code something.RenamedLoginListener} is a different class that merely happens to end with the same
     * characters, and must not be classified.</p>
     *
     * @param value      the logger name, or its normalised form
     * @param simpleName a registered simple name in the matching case
     * @return true when the value ends with that simple name at a class name boundary
     */
    private static boolean matchesSimpleName(String value, String simpleName) {
        if (value.length() < simpleName.length() || !value.endsWith(simpleName)) {
            return false;
        }
        int boundary = value.length() - simpleName.length();
        if (boundary == 0) {
            return true;
        }
        char separator = value.charAt(boundary - 1);
        return separator == '.' || separator == '$';
    }

    private static String normalise(String loggerName) {
        String lowerCase = loggerName.toLowerCase(Locale.ROOT);
        int dollar = lowerCase.indexOf('$');
        return dollar < 0 ? lowerCase : lowerCase.substring(0, dollar);
    }

    private static Map<ConnectionPhase, Set<String>> freeze(Map<ConnectionPhase, Set<String>> source,
                                                           boolean lowerCase) {
        Map<ConnectionPhase, Set<String>> result = new EnumMap<>(ConnectionPhase.class);
        for (ConnectionPhase phase : ConnectionPhase.values()) {
            Set<String> names = source.get(phase);
            if (names == null || names.isEmpty()) {
                result.put(phase, Set.of());
                continue;
            }
            Set<String> copy = new LinkedHashSet<>();
            for (String name : names) {
                copy.add(lowerCase ? name.toLowerCase(Locale.ROOT) : name);
            }
            result.put(phase, Collections.unmodifiableSet(copy));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Builder for {@link LoggerIdentityMatcher}. Not thread-safe; build once during start-up.
     */
    public static final class Builder {

        private final Map<ConnectionPhase, Set<String>> exactNames = new EnumMap<>(ConnectionPhase.class);
        private final Map<ConnectionPhase, Set<String>> simpleNames = new EnumMap<>(ConnectionPhase.class);
        private final Set<String> ignoredNames = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * Registers a Minecraft class both by the binary name its logger is named after and by its trailing
         * simple name, so the matcher keeps working when the package differs.
         *
         * @param binaryClassName fully qualified runtime class name, for example
         *                        {@code net.minecraft.server.network.ServerLoginPacketListenerImpl}
         * @param phase           the phase this class serves
         * @return this builder
         */
        public Builder addMinecraftClass(String binaryClassName, ConnectionPhase phase) {
            Objects.requireNonNull(binaryClassName, "binaryClassName");
            addExactName(binaryClassName, phase);
            int lastDot = binaryClassName.lastIndexOf('.');
            String simpleName = lastDot < 0 ? binaryClassName : binaryClassName.substring(lastDot + 1);
            return addSimpleName(simpleName, phase);
        }

        /**
         * Registers one exact logger name.
         *
         * @param loggerName the full Log4j2 category
         * @param phase      the phase this name belongs to
         * @return this builder
         */
        public Builder addExactName(String loggerName, ConnectionPhase phase) {
            Objects.requireNonNull(phase, "phase");
            if (loggerName != null && !loggerName.isEmpty()) {
                exactNames.computeIfAbsent(phase, key -> new LinkedHashSet<>()).add(loggerName);
            }
            return this;
        }

        /**
         * Registers a trailing simple name, matched with {@link String#endsWith(String)}.
         *
         * @param simpleName a class simple name such as {@code ServerLoginPacketListenerImpl}
         * @param phase      the phase this name belongs to
         * @return this builder
         */
        public Builder addSimpleName(String simpleName, ConnectionPhase phase) {
            Objects.requireNonNull(phase, "phase");
            if (simpleName != null && !simpleName.isEmpty()) {
                simpleNames.computeIfAbsent(phase, key -> new LinkedHashSet<>()).add(simpleName);
            }
            return this;
        }

        /**
         * Registers a logger name that must never be classified, for example this project's own logger.
         *
         * @param loggerName the full Log4j2 category to exclude
         * @return this builder
         */
        public Builder addIgnoredName(String loggerName) {
            if (loggerName != null && !loggerName.isEmpty()) {
                ignoredNames.add(loggerName);
            }
            return this;
        }

        /**
         * @return a complete description of the registered names, for debug output
         */
        public Map<String, Set<String>> describe() {
            Map<String, Set<String>> description = new LinkedHashMap<>();
            for (ConnectionPhase phase : ConnectionPhase.values()) {
                Set<String> names = simpleNames.get(phase);
                if (names != null && !names.isEmpty()) {
                    description.put(phase.name(), Set.copyOf(names));
                }
            }
            for (Map.Entry<ConnectionPhase, Set<String>> entry : exactNames.entrySet()) {
                description.merge(entry.getKey().name() + "_EXACT", Set.copyOf(entry.getValue()), (a, b) -> a);
            }
            return description;
        }

        /**
         * @return an immutable matcher
         */
        public LoggerIdentityMatcher build() {
            return new LoggerIdentityMatcher(this);
        }
    }
}
