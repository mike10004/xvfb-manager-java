package com.github.mike10004.xvfbmanager;

import com.google.common.io.ByteSource;

public class ImageioReadableScreenshot extends Screenshot.BasicScreenshot<ByteSource> {

    public ImageioReadableScreenshot(ByteSource byteSource) {
        super(byteSource);
    }

}
