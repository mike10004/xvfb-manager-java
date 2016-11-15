/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbunittesthelp;

import org.hamcrest.Matcher;

import javax.annotation.Nullable;

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

    static class AssumptionViolatedError extends Error {

        public AssumptionViolatedError() {
        }

        public AssumptionViolatedError(String message) {
            super(message);
        }

        public AssumptionViolatedError(String message, Throwable cause) {
            super(message, cause);
        }

        public AssumptionViolatedError(Throwable cause) {
            super(cause);
        }
    }

    private static void checkState(boolean state) {
        if (!state) {
            throw new AssumptionViolatedError();
        }
    }

    private static void checkState(boolean state, Object message) {
        if (!state) {
            throw new AssumptionViolatedError(String.valueOf(message));
        }
    }

    private static void checkState(boolean state, String template, Object...args) {
        if (!state) {
            throw new AssumptionViolatedError(format(template, args));
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
