package com.cleanerlogs.common.log4j;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.spi.LoggerContextFactory;

/**
 * Installs and removes the pre-join filter on the root {@link LoggerConfig} of the running Log4j2 configuration.
 *
 * <p>The root configuration is used deliberately. Looking a logger name up with
 * {@link Configuration#getLoggerConfig(String)} returns the closest <em>existing</em> configuration, which is
 * usually the root one anyway - and silently adding a filter to the root while believing it belongs to a
 * narrower logger is exactly the kind of mistake this project must avoid. Installing on the root is explicit,
 * and it is safe because the filter only ever denies the one event shape described by
 * {@link PreJoinDisconnectFilter}.</p>
 *
 * <p>Composition uses {@link LoggerConfig#addFilter(Filter)}, which wraps any pre-existing filter into a
 * {@link CompositeFilter} instead of replacing it, and {@link LoggerConfig#removeFilter(Filter)}, which unwraps
 * it again. Another plugin's filter is therefore never destroyed.</p>
 *
 * <p>Nothing here ever fails server start-up: the callers catch the exception and print one warning.</p>
 */
public final class Log4jFilterInstaller {

    /**
     * @param filter the filter to install
     * @return true when the filter is present on the root logger configuration afterwards
     * @throws IllegalStateException when Log4j2 core is not the active logging backend
     */
    public static boolean install(Filter filter) {
        LoggerContext context = requireContext();
        boolean installed = installOn(context.getConfiguration(), filter);
        if (installed) {
            // Publish the change to loggers that were created before the filter existed.
            context.updateLoggers();
        }
        return installed;
    }

    /**
     * @param filter the filter to remove
     * @return true when the filter is no longer part of the root logger configuration
     * @throws IllegalStateException when Log4j2 core is not the active logging backend
     */
    public static boolean uninstall(Filter filter) {
        LoggerContext context = requireContext();
        boolean removed = uninstallFrom(context.getConfiguration(), filter);
        if (removed) {
            context.updateLoggers();
        }
        return removed;
    }

    /**
     * Testable core of {@link #install(Filter)}: composes the filter onto the root logger configuration.
     *
     * @param configuration the live Log4j2 configuration
     * @param filter        the filter to install
     * @return true when the filter is present afterwards
     */
    public static boolean installOn(Configuration configuration, Filter filter) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(filter, "filter");

        LoggerConfig root = configuration.getRootLogger();
        if (root == null) {
            return false;
        }
        if (containsFilter(root.getFilter(), filter)) {
            // Idempotent: a second enable must not add the filter twice.
            return true;
        }
        root.addFilter(filter);
        return containsFilter(root.getFilter(), filter);
    }

    /**
     * Testable core of {@link #uninstall(Filter)}: removes only this filter and unwraps whatever was composed
     * around it.
     *
     * @param configuration the live Log4j2 configuration
     * @param filter        the filter to remove
     * @return true when the filter is gone afterwards
     */
    public static boolean uninstallFrom(Configuration configuration, Filter filter) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(filter, "filter");

        LoggerConfig root = configuration.getRootLogger();
        if (root == null) {
            return false;
        }
        if (!containsFilter(root.getFilter(), filter)) {
            // Idempotent: a second disable must not throw.
            return true;
        }
        root.removeFilter(filter);
        return !containsFilter(root.getFilter(), filter);
    }

    /**
     * @param current the filter chain currently installed, may be {@code null}
     * @param target  the filter to look for
     * @return true when the target is part of the chain
     */
    public static boolean containsFilter(Filter current, Filter target) {
        if (current == null || target == null) {
            return false;
        }
        if (current == target) {
            return true;
        }
        if (current instanceof CompositeFilter composite) {
            for (Filter child : composite.getFiltersArray()) {
                if (child == target) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return the core Log4j2 context backing the running server, never {@code null}
     * @throws IllegalStateException when the active logging backend is not Log4j2 core
     */
    public static LoggerContext requireContext() {
        LoggerContext context = currentContext();
        if (context == null) {
            throw new IllegalStateException("The active logging backend is not Log4j2 core ("
                + describeContextFactory() + "), so the filter cannot be installed.");
        }
        return context;
    }

    /**
     * @return the core Log4j2 context backing the running server, or {@code null} when Log4j2 core is absent
     */
    public static LoggerContext currentContext() {
        org.apache.logging.log4j.spi.LoggerContext context = LogManager.getContext(false);
        return context instanceof LoggerContext coreContext ? coreContext : null;
    }

    private static String describeContextFactory() {
        try {
            LoggerContextFactory factory = LogManager.getFactory();
            return factory == null ? "unknown" : factory.getClass().getName();
        } catch (RuntimeException | LinkageError error) {
            return "unknown";
        }
    }

    private Log4jFilterInstaller() {
    }
}
