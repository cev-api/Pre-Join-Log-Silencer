package com.cleanerlogs.common.log4j;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import com.cleanerlogs.common.ConnectionPhase;
import com.cleanerlogs.common.DisconnectMessages;
import com.cleanerlogs.common.PreJoinClassification;
import com.cleanerlogs.common.PreJoinDisconnectDetector;
import com.cleanerlogs.common.SuppressedDisconnect;
import com.cleanerlogs.common.SuppressionListener;
import com.cleanerlogs.common.SuppressionPolicy;

/**
 * Log4j2 filter that denies exactly one thing: Minecraft's pre-PLAY "lost connection" INFO line.
 *
 * <p>Everything else - warnings, errors, stack traces, decoder exceptions, authentication failures, PLAY-state
 * disconnects, other plugins' output - is returned as {@link Result#NEUTRAL}, which leaves the event completely
 * untouched for the rest of the logging pipeline.</p>
 *
 * <p>The filter is safe to call from any Log4j2 thread. It holds no per-connection state, keeps no references
 * to events, players or connections, and reads the current configuration through an atomic reference so a
 * reload is a single volatile read.</p>
 */
public final class PreJoinDisconnectFilter extends AbstractFilter {

    private static final long serialVersionUID = 1L;

    private final PreJoinDisconnectDetector detector;
    private final AtomicReference<SuppressionPolicy> policy;
    private final SuppressionListener listener;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private PreJoinDisconnectFilter(PreJoinDisconnectDetector detector,
                                    AtomicReference<SuppressionPolicy> policy,
                                    SuppressionListener listener) {
        // Both results are NEUTRAL: the deprecated string based Filter overloads must never accept or deny
        // anything on their own, only the LogEvent based decision below may.
        super(Result.NEUTRAL, Result.NEUTRAL);
        this.detector = detector;
        this.policy = policy;
        this.listener = listener;
    }

    /**
     * Creates a started filter.
     *
     * @param detector   the classifier
     * @param policy     the live configuration, read on every event
     * @param listener   notified whenever something is suppressed, may be {@code null}
     * @return a new filter, ready to be handed to {@link Log4jFilterInstaller}
     */
    public static PreJoinDisconnectFilter create(PreJoinDisconnectDetector detector,
                                                 AtomicReference<SuppressionPolicy> policy,
                                                 SuppressionListener listener) {
        PreJoinDisconnectFilter filter = new PreJoinDisconnectFilter(
            Objects.requireNonNull(detector, "detector"),
            Objects.requireNonNull(policy, "policy"),
            listener == null ? SuppressionListener.NOOP : listener);
        filter.start();
        return filter;
    }

    @Override
    public Result filter(LogEvent event) {
        if (!active.get()) {
            return Result.NEUTRAL;
        }
        SuppressionPolicy currentPolicy = policy.get();
        if (!currentPolicy.enabled()) {
            return Result.NEUTRAL;
        }

        PreJoinClassification classification = detector.classify(event);
        if (!classification.suppressible() || !currentPolicy.allows(classification.phase())) {
            return Result.NEUTRAL;
        }

        notifyListener(classification.phase(), event);
        return Result.DENY;
    }

    /**
     * Makes the filter permanently inert.
     *
     * <p>Called while unloading so that logging behaves exactly as if the filter had never been installed, even
     * if the physical removal from the logger configuration fails.</p>
     */
    public void deactivate() {
        active.set(false);
    }

    /**
     * @return true while the filter is allowed to deny events
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * @return the detector this filter delegates to
     */
    public PreJoinDisconnectDetector detector() {
        return detector;
    }

    private void notifyListener(ConnectionPhase phase, LogEvent event) {
        if (listener == SuppressionListener.NOOP) {
            return;
        }
        try {
            listener.onSuppressed(SuppressedDisconnect.of(
                phase,
                event.getLoggerName(),
                DisconnectMessages.formatOf(event.getMessage()),
                DisconnectMessages.parametersOf(event.getMessage())));
        } catch (RuntimeException ignored) {
            // A broken listener must never break server logging.
        }
    }
}
