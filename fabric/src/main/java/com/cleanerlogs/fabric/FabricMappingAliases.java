package com.cleanerlogs.fabric;

import com.cleanerlogs.common.ConnectionPhase;
import com.cleanerlogs.common.LoggerIdentityMatcher;
import com.cleanerlogs.common.MinecraftLoggerNames;
import com.cleanerlogs.common.RuntimeLog;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

/**
 * Registers the runtime names of the packet listener classes on the logger identity matcher.
 *
 * <p>Fabric remaps Minecraft to intermediary names in production, so the login listener is
 * {@code net.minecraft.class_XXXX} there and its logger name does not end with any simple name this project can
 * recognise. Rather than hard coding an intermediary table - which would silently rot, because a single wrong
 * entry disables suppression without any error - the names are resolved from the running loader with
 * {@link MappingResolver#mapClassName(String, String)}.</p>
 *
 * <p>Resolution is best effort. When it fails the matcher still has the Mojang names and the simple-name
 * suffixes, and the conservative message-structure fallback in the detector still handles the common shapes.</p>
 */
final class FabricMappingAliases {

    private FabricMappingAliases() {
    }

    /**
     * Adds every resolver-visible alias of the known listener classes to the matcher under construction.
     *
     * @param loader  the Fabric loader instance
     * @param builder the matcher builder to extend
     * @param log     where problems are reported
     * @return the namespace the loader is currently running in, for diagnostics
     */
    static String register(FabricLoader loader, LoggerIdentityMatcher.Builder builder, RuntimeLog log) {
        MappingResolver resolver;
        try {
            resolver = loader.getMappingResolver();
        } catch (RuntimeException | LinkageError error) {
            log.warn("Fabric's mapping resolver is unavailable, so only Mojang class names can be recognised. "
                + "Suppression still works through the message-structure fallback.", error);
            return "unknown";
        }

        String namespace = namespaceOf(resolver);
        for (Map.Entry<String, ConnectionPhase> entry : MinecraftLoggerNames.namedLoggerClasses().entrySet()) {
            String mapped = mapClass(resolver, entry.getKey());
            if (mapped != null) {
                builder.addExactName(mapped, entry.getValue());
            }
        }
        return namespace;
    }

    private static String mapClass(MappingResolver resolver, String namedClassName) {
        try {
            // Maps from the named namespace into whatever namespace the loader is running in. In a production
            // server this yields the intermediary name; in a development environment it yields the input.
            String mapped = resolver.mapClassName("named", namedClassName);
            if (mapped != null && !mapped.isEmpty() && !mapped.equals(namedClassName)) {
                return mapped;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Best effort only: the named aliases registered by the caller still apply.
        }
        return null;
    }

    private static String namespaceOf(MappingResolver resolver) {
        try {
            String namespace = resolver.getCurrentRuntimeNamespace();
            return namespace == null ? "unknown" : namespace;
        } catch (RuntimeException | LinkageError error) {
            return "unknown";
        }
    }
}
