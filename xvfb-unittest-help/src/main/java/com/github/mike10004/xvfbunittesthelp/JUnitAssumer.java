/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbunittesthelp;

import org.hamcrest.Matcher;
import org.junit.Assume;

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
