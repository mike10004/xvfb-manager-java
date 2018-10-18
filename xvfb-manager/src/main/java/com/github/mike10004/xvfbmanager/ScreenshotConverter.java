package com.github.mike10004.xvfbmanager;

import java.io.IOException;

public interface ScreenshotConverter<S extends Screenshot, D extends Screenshot> {

    D convert(S source) throws IOException, XvfbException;

}
