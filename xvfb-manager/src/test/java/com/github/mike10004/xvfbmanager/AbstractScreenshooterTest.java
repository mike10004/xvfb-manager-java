package com.github.mike10004.xvfbmanager;

import com.google.common.io.Files;
import com.novetta.ibg.common.image.ImageInfo;
import com.novetta.ibg.common.image.ImageInfos;
import com.novetta.ibg.common.io.ByteSources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;

/*
 * (c) 2016 Novetta
 *
 * Created by mike
 */
public class AbstractScreenshooterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void DefaultScreenshot_convertToPnmFile() throws Exception {
        File xwdFile = tmp.newFile("example.xwd");
        ByteSources.gunzipping(getClass().getResource("/example.xwd.gz")).copyTo(Files.asByteSink(xwdFile));
        XwdScreenshooter.DefaultScreenshot screenshot = new XwdScreenshooter.DefaultScreenshot(xwdFile, tmp.newFolder().toPath());
        File pnmFile = tmp.newFile("example.ppm");
        screenshot.convertToPnmFile(pnmFile);
        ImageInfo info = ImageInfos.read(Files.asByteSource(pnmFile));
        assertEquals("format", ImageInfo.Format.PPM, info.getFormat());
    }
}