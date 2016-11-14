/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.DefaultXvfbController.Screenshooter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Partial implementation of a screenshooter. Nested classes implementing {@link XvfbManager.Screenshot} and
 * providing a screenshot-related exception class are contained in this source file.
 */
public abstract class AbstractScreenshooter implements Screenshooter {

    private static final Logger log = LoggerFactory.getLogger(AbstractScreenshooter.class);

    protected final String display;
    protected final File outputDir;

    public AbstractScreenshooter(String display, File outputDir) {
        this.display = checkNotNull(display);
        this.outputDir = checkNotNull(outputDir);
    }

}
