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

}
