package com.github.mike10004.xvfbunittesthelp;

import org.hamcrest.Matcher;
import org.junit.Assume;

import javax.annotation.Nullable;

@SuppressWarnings({"unused", "BooleanParameter"})
public interface Assumer {

    void assumeTrue(boolean b);

    void assumeFalse(boolean b);

    void assumeTrue(String message, boolean b);

    void assumeFalse(String message, boolean b);

    void assumeNotNull(Object... objects);

    <T> void assumeThat(T actual, Matcher<T> matcher);

    <T> void assumeThat(String message, T actual, Matcher<T> matcher);

    void assumeNoException(Throwable e);

    void assumeNoException(String message, Throwable e);

    @SuppressWarnings("BooleanParameter")
    class FatalAssumer implements Assumer {

        static String format(String template, @Nullable Object... args) {
            template = String.valueOf(template);
            StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
            int templateStart = 0;

            int i;
            int placeholderStart;
            for(i = 0; i < args.length; templateStart = placeholderStart + 2) {
                placeholderStart = template.indexOf("%s", templateStart);
                if(placeholderStart == -1) {
                    break;
                }

                builder.append(template, templateStart, placeholderStart);
                builder.append(args[i++]);
            }

            builder.append(template, templateStart, template.length());
            if(i < args.length) {
                builder.append(" [");
                builder.append(args[i++]);

                while(i < args.length) {
                    builder.append(", ");
                    builder.append(args[i++]);
                }

                builder.append(']');
            }

            return builder.toString();
        }

        private static class AssumptionViolationError extends Error {

            public AssumptionViolationError() {
            }

            public AssumptionViolationError(String message) {
                super(message);
            }

            public AssumptionViolationError(String message, Throwable cause) {
                super(message, cause);
            }

            public AssumptionViolationError(Throwable cause) {
                super(cause);
            }
        }

        private static void checkState(boolean state) {
            if (!state) {
                throw new AssumptionViolationError();
            }
        }

        private static void checkState(boolean state, Object message) {
            if (!state) {
                throw new AssumptionViolationError(String.valueOf(message));
            }
        }

        private static void checkState(boolean state, String template, Object...args) {
            if (!state) {
                throw new AssumptionViolationError(format(template, args));
            }
        }

        public void assumeTrue(boolean b) {
            checkState(b);
        }

        public void assumeFalse(boolean b) {
            checkState(!b);
        }

        public void assumeTrue(String message, boolean b) {
            checkState(b, message);
        }

        public void assumeFalse(String message, boolean b) {
            checkState(!b, message);
        }

        public void assumeNotNull(Object... objects) {
            for (int i = 0; i < objects.length; i++) {
                checkState(objects[i] != null, objects.length == 1 ? "null" : "object at index " + i + " is null");
            }
        }

        public <T> void assumeThat(T actual, Matcher<T> matcher) {
            checkState(matcher.matches(actual));
        }

        public <T> void assumeThat(String message, T actual, Matcher<T> matcher) {
            checkState(matcher.matches(actual), message);
        }

        public void assumeNoException(Throwable e) {
            checkState(e == null);
        }

        public void assumeNoException(String message, Throwable e) {
            checkState(e == null, "%s: message", e);
        }
    }

    @SuppressWarnings("BooleanParameter")
    class JUnitAssumer implements Assumer {

        public void assumeTrue(boolean b) {
            Assume.assumeTrue(b);
        }

        public void assumeFalse(boolean b) {
            Assume.assumeFalse(b);
        }

        public void assumeTrue(String message, boolean b) {
            Assume.assumeTrue(message, b);
        }

        public void assumeFalse(String message, boolean b) {
            Assume.assumeFalse(message, b);
        }

        public void assumeNotNull(Object... objects) {
            Assume.assumeNotNull(objects);
        }

        public <T> void assumeThat(T actual, Matcher<T> matcher) {
            Assume.assumeThat(actual, matcher);
        }

        public <T> void assumeThat(String message, T actual, Matcher<T> matcher) {
            Assume.assumeThat(message, actual, matcher);
        }

        public void assumeNoException(Throwable e) {
            Assume.assumeNoException(e);
        }

        public void assumeNoException(String message, Throwable e) {
            Assume.assumeNoException(message, e);
        }
        
    }
}
