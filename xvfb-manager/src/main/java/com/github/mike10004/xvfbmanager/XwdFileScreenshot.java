/*
 * (c) 2016 Mike Chaberski
 *
 * Created by mike
 */
package com.github.mike10004.xvfbmanager;

import com.github.mike10004.xvfbmanager.Screenshot.FileByteSource;

import java.io.File;

public class XwdFileScreenshot extends Screenshot.BasicScreenshot<FileByteSource> {

    public XwdFileScreenshot(FileByteSource byteSource) {
        super(byteSource);
    }

    public static XwdFileScreenshot from(File file) {
        return new XwdFileScreenshot(new FileByteSource(file));
    }
}
