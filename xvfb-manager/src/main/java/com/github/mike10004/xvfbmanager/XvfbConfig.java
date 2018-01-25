/*
 * (c) 2016 Mike Chaberski
 */
package com.github.mike10004.xvfbmanager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configuration for Xvfb execution.
 */
public class XvfbConfig {

    /**
     * Geometry of the screen. This value is specified in the
     * {@code -screen 0 <geometry>} option. The syntax is
     * <i>W</i>{@code x}<i>H</i>{@code x}<i>D</i>, where <i>W</i> and <i>H</i>
     * are positive integer width and height values, respectively,
     * and <i>D</i> is a depth value. Supported values for <i>D</i> include
     * "8", "24", and "24+32". A fine choice for this field is
     * {@code 1280x1024x8} or {@code 1280x1024x24+32}.
     */
    public final String geometry;

    public XvfbConfig(String geometry) {
        this.geometry = checkNotNull(geometry);
        checkArgument(geometry.matches("\\d+x\\d+x\\d+(?:\\+32)?"), "argument must have form WxHxD where W=width, H=height, and D=depth; default is 1280x1024x24+32");
    }

    @Override
    public String toString() {
        return "XvfbConfig{" +
                "geometry='" + geometry + '\'' +
                '}';
    }

    private static final XvfbConfig DEFAULT = new XvfbConfig("1280x1024x24+32");

    /**
     * Default configuration instance. This differs from the {@code 1280x1024x8} configuration that {@code Xvfb}
     * uses by default, stated in the manual.
     */
    public static XvfbConfig getDefault() {
        return DEFAULT;
    }
}
