package com.github.mike10004.xvfbtesting;

import com.github.mike10004.nativehelper.Platforms;
import io.github.mike10004.subprocess.ProcessTracker;
import com.github.mike10004.xvfbmanager.XvfbController;
import com.github.mike10004.xvfbmanager.XvfbException;
import com.github.mike10004.xvfbmanager.XvfbManager;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

@NotThreadSafe
public class XvfbRule extends ExternalResource {

    private final TemporaryFolder temporaryFolder;
    private final @Nullable Integer initialDisplayNumber;
    private final XvfbManager xvfbManager;
    private transient volatile XvfbController xvfbController;
    private final Supplier<Boolean> disabledSupplier;
    private final StartMode startMode;

    /**
     * Creates a default rule instance that auto-selects the display number.
     * Use a {@link #builder() builder} if you want to customize the
     * {@link XvfbManager manager} instance, specify the display number, or
     * customize other aspects of operation.
     */
    public XvfbRule() {
        this(builder());
    }

    private XvfbRule(Builder builder) {
        this.xvfbManager = checkNotNull(builder.xvfbManager);
        temporaryFolder = builder.temporaryFolder;
        initialDisplayNumber = builder.displayNumber;
        if (initialDisplayNumber != null) {
            checkDisplayNumber(initialDisplayNumber);
        }
        this.disabledSupplier = conjoinDisabledSuppliers(builder.disabledSuppliers);
        this.startMode = checkNotNull(builder.startMode, "startMode");
    }

    enum StartMode {
        EAGER, LAZY
    }

    /**
     * Returns a new builder of rule instances.
     * @return a new builder instance
     * @see Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    static Supplier<Boolean> conjoinDisabledSuppliers(Iterable<Supplier<Boolean>> components) {
        ImmutableList<Supplier<Boolean>> frozenComponents = ImmutableList.copyOf(components);
        Supplier<String> frozenComponentsString = Suppliers.memoize(frozenComponents::toString);
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                for (Supplier<Boolean> component : frozenComponents) {
                    Boolean value = checkNotNull(component.get(), "returned null instead of a Boolean instance: %s", component);
                    if (value) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return "ConjoinedSupplier{" + frozenComponentsString.get() + "}";
            }
        };
    }

    private static class EmbeddedProcessTracker implements ProcessTracker {

        private final Set<Process> processes = new HashSet<>();

        @Override
        public synchronized void add(Process process) {
            processes.add(process);
        }

        @Override
        public synchronized boolean remove(Process process) {
            return processes.remove(process);
        }

        @Override
        public synchronized int activeCount() {
            return processes.size();
        }

        public void destroyAll() {
            Set<Process> processes = ImmutableSet.copyOf(this.processes);
            for (Process p : processes) {
                if (p.isAlive()) {
                    p.destroy();
                    try {
                        p.waitFor(250, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        LoggerFactory.getLogger(XvfbRule.class).info("interrupted while waiting for process to terminate");
                    }
                    if (p.isAlive()) {
                        p.destroyForcibly();
                    }
                }
            }

        }

        @Override
        public String toString() {
            return String.format("EmbeddedProcessTracker@%08x", System.identityHashCode(this));
        }
    }

    /**
     * Builder class for rule instances.
     * @see XvfbRule
     */
    public static class Builder {

        private static Supplier<Boolean> DISABLED_ON_WINDOWS = new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return Platforms.getPlatform().isWindows();
            }

            @Override
            public String toString() {
                return "DisabledOnWindows";
            }
        };

        private XvfbManager xvfbManager;
        private final Set<Supplier<Boolean>> disabledSuppliers;
        @Nullable
        private Integer displayNumber;
        private TemporaryFolder temporaryFolder = new TemporaryFolder();
        private StartMode startMode = StartMode.LAZY;

        private Builder() {
            this(new XvfbManager(new EmbeddedProcessTracker()), Collections.singleton(DISABLED_ON_WINDOWS));
        }

        private Builder(XvfbManager xvfbManager, Collection<? extends Supplier<Boolean>> disabledSuppliers) {
            this.xvfbManager = requireNonNull(xvfbManager);
            this.disabledSuppliers = new LinkedHashSet<>();
            this.disabledSuppliers.addAll(disabledSuppliers);
        }

        /**
         * Builds a rule instance.
         * @return a new rule instance
         */
        public XvfbRule build() {
            return new XvfbRule(this);
        }

        /**
         * Sets the manager instance to be used in building a rule.
         * @param xvfbManager the manager to build the rule with
         * @return this builder instance
         */
        public Builder manager(XvfbManager xvfbManager) {
            this.xvfbManager = checkNotNull(xvfbManager);
            return this;
        }

        /**
         * Sets the disabled flag to true.
         * @return this builder instance
         */
        public Builder disabled() {
            return disabled(true);
        }

        /**
         * Sets the disabled flag.
         * @return this builder instance
         */
        @SuppressWarnings("BooleanParameter")
        public Builder disabled(boolean disabled) {
            return disabled(Suppliers.ofInstance(disabled));
        }

        /**
         * Sets the rule to be disabled depending on the result of
         * evaluating the given supplier.
         * @param disabledSupplier the supplier
         * @return this builder instance
         */
        public Builder disabled(Supplier<Boolean> disabledSupplier) {
            disabledSuppliers.add(disabledSupplier);
            return this;
        }

        /**
         * Disables the rule if the platform is Windows. This is added by default.
         * @return this builder instance
         * @deprecated added by default; no need to invoke this again
         */
        @Deprecated
        public Builder disabledOnWindows() {
            disabled(DISABLED_ON_WINDOWS);
            return this;
        }

        /**
         * Enables the rule even if the platform is Windows. You probably wouldn't do this
         * unless you were testing error conditions.
         * @return this builder instance
         */
        public Builder notDisabledOnWindows() {
            disabledSuppliers.remove(DISABLED_ON_WINDOWS);
            return this;
        }

        /**
         * Sets the display number to be automatically selected. This is the
         * default. Uses the {@code -displayfd} option to {@code Xvfb}.
         * @return this builder instance
         */
        @SuppressWarnings("unused")
        public Builder autoDisplay() {
            displayNumber = null;
            return this;
        }

        /**
         * Sets the display number of the rule instance being built.
         * Use {@link #autoDisplay()} to automatically select an unused
         * display number.
         * @param displayNumber the display number
         * @return this builder instance
         */
        public Builder onDisplay(int displayNumber) {
            this.displayNumber = checkDisplayNumber(displayNumber);
            return this;
        }

        /**
         * Delays starting the framebuffer daemon until the controller is requested.
         * This sets a flag that causes the rule to delay {@link XvfbManager#start()}
         * invocation until {@link #getController()} is invoked. Otherwise, {@code start()}
         * will be invoked, and the controller created, in the "before" phase of the
         * test lifecycle. This is the default.
         * @return this builder instance
         */
        public Builder lazy() {
            return startMode(StartMode.LAZY);
        }

        private Builder startMode(StartMode startMode) {
            this.startMode = requireNonNull(startMode);
            return this;
        }

        /**
         * Starts the framebuffer daemon as soon in the "before" stage of the rule
         * lifecycle. This executes {@link XvfbManager#start()} in the
         * {@link ExternalResource#before()} method.
         * @return this builder instance
         */
        public Builder eager() {
            return startMode(StartMode.EAGER);
        }

    }

    protected boolean isDisabled() {
        boolean disabled = checkNotNull(disabledSupplier.get(), "disabledness Boolean supplier returned null");
        return disabled;
    }

    protected static int checkDisplayNumber(Integer displayNum) {
        checkNotNull(displayNum, "displayNum must be non-null");
        checkArgument(displayNum >= 0, "displayNum >= 0 is required");
        return displayNum;
    }

    @Override
    protected void before() throws Throwable {
        prepare();
    }

    protected void prepare() throws IOException {
        checkState(xvfbController == null, "xvfbController already created");
        if (!isDisabled()) {
            if (startMode == StartMode.EAGER) {
                reallyPrepare();
            }
        }
    }

    private void reallyPrepare() throws IOException {
        temporaryFolder.create();
        if (initialDisplayNumber != null) {
            xvfbController = xvfbManager.start(initialDisplayNumber);
        } else {
            xvfbController = xvfbManager.start();
        }
    }

    @Override
    protected void after() {
        cleanUp();
    }

    protected void cleanUp() {
        XvfbController xvfbController_ = xvfbController;
        xvfbController = null;
        if (xvfbController_ != null) {
            xvfbController_.stop();
        }
        temporaryFolder.delete();
        ProcessTracker tracker = xvfbManager.getProcessTracker();
        if (tracker instanceof EmbeddedProcessTracker) {
            ((EmbeddedProcessTracker) tracker).destroyAll();
        }
    }

    /**
     * Gets the controller instance.
     * @return the controller; never null
     * @throws IllegalStateException if controller has not been created yet
     * @throws XvfbException if start mode is lazy but {@link XvfbManager#start()} threw an exception
     */
    public XvfbController getController() throws XvfbException {
        if (isDisabled()) {
            return DisabledXvfbController.getInstance();
        } else {
            XvfbController xvfbController_ = xvfbController;
            if (xvfbController_ == null) {
                checkState(startMode == StartMode.LAZY, "must invoke before()/prepare() before getController() unless start mode is lazy");
                try {
                    reallyPrepare();
                    xvfbController_ = xvfbController;
                    assert xvfbController_ != null : "controller should be initialized here";
                } catch (IOException e) {
                    throw new LazyPreparationException(e);
                }
            }
            return xvfbController_;
        }
    }

    private static class LazyPreparationException extends XvfbException {
        public LazyPreparationException(Throwable cause) {
            super("lazy preparation failed", cause);
        }
    }

    @Override
    public String toString() {
        return "XvfbRule{" +
                "temporaryFolder=" + toString(temporaryFolder) +
                ", initialDisplayNumber=" + initialDisplayNumber +
                ", xvfbManager=" + xvfbManager +
                ", disabledSupplier=" + disabledSupplier +
                ", startMode=" + startMode +
                '}';
    }

    private static String toString(TemporaryFolder temporaryFolder) {
        File root = null;
        try {
            root = temporaryFolder.getRoot();
        } catch (IllegalStateException ignore) {
        }
        return "TemporaryFolder{root=" + root + "}";
    }
}
