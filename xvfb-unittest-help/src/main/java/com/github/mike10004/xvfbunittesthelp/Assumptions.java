/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbunittesthelp;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.hamcrest.Matcher;

/**
 * Class that provides a way to enforce assumptions selectively. Use in the
 * same way you would use {@link org.junit.Assume}, but set the system property
 * {@code Assumptions.fatal} to {@code true} to make violations of assumptions
 * throw errors.
 */
@SuppressWarnings({"unused", "BooleanParameter"})
public class Assumptions {

    public static final String SYSPROP_VIOLATIONS_ARE_FATAL = "Assumptions.fatal";
    public static final String SYSPROP_ASSUMER = "Assumptions.assumer";
    public static final String DEFAULT_ASSUMER = JUnitAssumer.class.getName();
    public static final String FATAL_ASSUMER = FatalAssumer.class.getName();

    private static final Supplier<Assumer> assumerSupplier = Suppliers.memoize(new SystemPropertiesAssumerCreator());

    static class SystemPropertiesAssumerCreator implements Supplier<Assumer> {

        @Override
        public Assumer get() {
            boolean fatal = Boolean.parseBoolean(System.getProperty(SYSPROP_VIOLATIONS_ARE_FATAL, "false"));
            String assumerClassname;
            if (fatal) {
                assumerClassname = FATAL_ASSUMER;
            } else {
                assumerClassname = DEFAULT_ASSUMER;
            }
            assumerClassname = System.getProperty(SYSPROP_ASSUMER, assumerClassname);
            try {
                return (Assumer) Class.forName(assumerClassname).newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new AssertionError("could not create assumer from system properties");
            }
        }
    }


    public static Assumer getAssumer() {
        return assumerSupplier.get();
    }

    public static void assumeTrue(boolean b) {
        getAssumer().assumeTrue(b);
    }

    public static void assumeFalse(boolean b) {
        getAssumer().assumeFalse(b);
    }

    public static void assumeTrue(String message, boolean b) {
        getAssumer().assumeTrue(message, b);
    }

    public static void assumeFalse(String message, boolean b) {
        getAssumer().assumeFalse(message, b);
    }

    public static void assumeNotNull(Object... objects) {
        getAssumer().assumeNotNull(objects);
    }

    public static <T> void assumeThat(T actual, Matcher<T> matcher) {
        getAssumer().assumeThat(actual, matcher);
    }

    public static <T> void assumeThat(String message, T actual, Matcher<T> matcher) {
        getAssumer().assumeThat(message, actual, matcher);
    }

    public static void assumeNoException(Throwable e) {
        getAssumer().assumeNoException(e);
    }

    public static void assumeNoException(String message, Throwable e) {
        getAssumer().assumeNoException(message, e);
    }
}
