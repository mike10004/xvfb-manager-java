[![Travis build status](https://img.shields.io/travis/mike10004/common-helper.svg)](https://travis-ci.org/mike10004/common-helper)

xvfb-manager
============

Java Xvfb manager. A library for managing an Xvfb process and a JUnit rule
to help using that library in unit or integration tests.

Quickstart
----------

This code demonstrates the basics:

    XvfbManager manager = new XvfbManager();
    try (XvfbController controller = manager.start()) {
        controller.waitUntilReady();
        final String display = controller.getDisplay();
        // start a graphical program on a different thread
        Future<Process> processFuture = Executors.newSingleThreadExecutor().submit(new Callable<Process>(){
            @Override
            public Process call() throws Exception {
                ProcessBuilder pb = new ProcessBuilder();
                pb.environment().put("DISPLAY", display);
                return pb.command("xclock").start();
            }
        });
        Screenshot screenshot = controller.captureScreenshot();
        // do something with screenshot
        processFuture.cancel(true);
    }


Modules
-------

The purpose of each module is as follows:

* **xvfb-manager**: the core library
* **xvfb-testing**: a JUnit Rule 
* **xvfb-selenium**: helpful code for running Selenium WebDriver with Xvfb
* **xvfb-unittest-tools**: library only used by above projects for testing
* **xvfb-manager-example**: executable program demonstrating Selenium usage

Acknowledgements
----------------

The photo used in the unit tests is by George Chernilevsky - Own work, 
CC BY-SA 4.0, https://commons.wikimedia.org/w/index.php?curid=34785750.
