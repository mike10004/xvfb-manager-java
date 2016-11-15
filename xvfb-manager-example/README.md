xvfb-manager-example
====================

To run the example, execute

    $ mvn compile exec:java \
          -Dexec.mainClass=com.github.mike10004.xvfbmanagerexample.XvfbManagerExample \
          -Dexec.args="chrome https://example.com/ screenshot.png" \
          -Dexec.cleanupDaemonThreads=false

from this directory. An image file named `screenshot.png` will be created in
this directory.

This is for demonstration purposes only. If you try to capture a screenshot of
any serious web site with that command line, you'll probably wind up with a 
partially-rendered or not-at-all-rendered page, because the example code does
make the webdriver wait until all javascript executes before the screenshot
is captured.
