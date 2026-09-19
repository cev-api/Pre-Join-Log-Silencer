package com.cleanerlogs.fabric;

import com.cleanerlogs.common.CleanerLogsRuntime;
import com.cleanerlogs.common.LoggerIdentityMatcher;
import com.cleanerlogs.common.MinecraftLoggerNames;
import com.cleanerlogs.common.RuntimeLog;
import com.cleanerlogs.common.SuppressionPolicy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric bootstrap.
 *
 * <p>Depends on Fabric Loader only. Fabric API is not used, mixins are not used and no Minecraft class is
 * touched, because the log event itself already identifies the connection phase. That is also why this mod
 * needs no remapping: with no references to Minecraft types its bytecode is namespace independent.</p>
 *
 * <p>Fabric has no mod unload, so the filter stays installed for the lifetime of the JVM. There is therefore no
 * command and no reload: change the properties file and restart the server.</p>
 */
public final class PreJoinLogSilencerMod implements ModInitializer {

    @Override
    public void onInitialize() {
        RuntimeLog log = new Log4jRuntimeLog();
        try {
            initialize(log);
        } catch (RuntimeException | LinkageError error) {
            // A logging filter must never be a reason for a server to fail to start.
            log.warn("Pre-join log silencing is unavailable in this environment. Server logging is unchanged.",
                error);
        }
    }

    private void initialize(RuntimeLog log) {
        FabricLoader loader = FabricLoader.getInstance();

        LoggerIdentityMatcher.Builder builder = MinecraftLoggerNames.defaultMatcherBuilder();
        String runtimeNamespace = FabricMappingAliases.register(loader, builder, log);

        SuppressionPolicy policy = FabricSettings.load(loader.getConfigDir(), log);

        CleanerLogsRuntime runtime = new CleanerLogsRuntime(builder.build(), policy);
        runtime.setLog(log);
        runtime.setSuppressedLogFile(loader.getConfigDir().resolve(FabricSettings.SUPPRESSED_LOG_NAME));

        if (policy.debug()) {
            log.debug("Runtime mapping namespace is '" + runtimeNamespace + "'.");
        }
        runtime.start();
    }
}
