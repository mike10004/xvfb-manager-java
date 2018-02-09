package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platform;
import com.github.mike10004.nativehelper.Platforms;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.junit.Assume;
import org.junit.rules.ExternalResource;

import javax.annotation.Nullable;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * JUnit rule that evaluates a platform requirement to see whether a test should be ignored.
 */
public class PlatformRule extends ExternalResource {

    private final Predicate<Platform> requirement;
    @Nullable
    private final String message;

    /**
     * Constructs a rule with the default message.
     * @param requirement the requirement
     * @see #PlatformRule(Predicate, String)
     */
    public PlatformRule(Predicate<Platform> requirement) {
        this(requirement, null);
    }

    /**
     * Constructs a rule that evaluates a predicate on the platform and ignores the test if the evaluation result is false.
     * @param requirement the requirement; test is ignored if it returns false
     * @param message the message to show
     */
    public PlatformRule(Predicate<Platform> requirement, @Nullable String message) {
        this.requirement = requireNonNull(requirement);
        this.message = message;
    }

    @Override
    protected void before() {
        Platform platform = getPlatform();
        String msg = constructMessage(platform);
        Assume.assumeTrue(msg, evaluate(platform));
    }

    protected String constructMessage(Platform platform) {
        if (message == null) {
            return requirement.toString() + " on " + platform;
        } else {
            return message + "; platform = " + platform;
        }
    }

    protected boolean evaluate(Platform platform) {
        return requirement.test(platform);
    }

    @VisibleForTesting
    protected Platform getPlatform() {
        return Platforms.getPlatform();
    }

    public static PlatformRule requireWindows() {
        return new PlatformRule(Platform::isWindows, "platform must be windows");
    }

    public static PlatformRule requireNotWindows() {
        return new PlatformRule(p -> !p.isWindows(), "platform must not be windows");
    }

}
