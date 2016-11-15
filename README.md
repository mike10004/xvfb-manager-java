[![Travis build status](https://img.shields.io/travis/mike10004/xvfb-manager-java.svg)](https://travis-ci.org/mike10004/xvfb-manager-java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/xvfb-manager-java.svg)](https://repo1.maven.org/maven2/com/github/mike10004/xvfb-manager-parent/)

xvfb-manager
============

Java Xvfb manager. A library for managing an Xvfb process and a JUnit 
rule for using that library in unit or integration tests.

Quickstart - Core Library
-------------------------

### Maven

    <dependency>
        <groupId>com.github.mike10004</groupId>
        <artifactId>xvfb-manager</artifactId>
        <version>0.1</version>
    </dependency>

### Code

    XvfbManager manager = new XvfbManager();
    try (final XvfbController controller = manager.start()) {
        controller.waitUntilReady();
        // start a graphical program on a different thread
        Future<Process> processFuture = Executors.newSingleThreadExecutor().submit(new Callable<Process>(){
            @Override
            public Process call() throws Exception {
                ProcessBuilder pb = new ProcessBuilder();
                pb.environment().put("DISPLAY", controller.getDisplay());
                return pb.command("xclock").start();
            }
        });
        Screenshot screenshot = controller.getScreenshooter().capture();
        // do something with screenshot
        processFuture.cancel(true);
    }

Quickstart - JUnit Rule
-----------------------

### Maven

    <dependency>
        <groupId>com.github.mike10004</groupId>
        <artifactId>xvfb-testing</artifactId>
        <version>0.1</version>
    </dependency>

### Code

    @Rule
    public XvfbRule xvfb = new XvfbRule();
    
    @Test
    public void testSomething() throws Exception {
        XvfbController controller = xvfb.getController();
        // use controller as in the core library example above
    }

Modules
-------

The purpose of each module is as follows:

* **xvfb-manager**: core library
* **xvfb-testing**: JUnit Rule
* **xvfb-selenium**: helpful code for running Selenium WebDriver with Xvfb
* **xvfb-unittest-tools**: library only used by above projects for testing
* **xvfb-manager-example**: executable program demonstrating Selenium usage

Notes
-----

Versions of Xvfb prior to 1.13 did not support the `-displayfd` option 
that allows automatic selection of an open display number. If your 
version of Xvfb is lower than 1.13, then you must specify a display 
number. If using the core library, you would do that like this:

     XvfbManager manager = new XvfbManager();
     XvfbController controller = manager.start(99); 

If using the JUnit rule, then the display number is specified like this:

     public XvfbRule xvfb = XvfbRule.builder().onDisplay(99).build();

The display number `99` corresponds to the `DISPLAY` environment 
variable value `:99`.

Supported Operating Systems
---------------------------

Currently only Linux is supported, and the library has only been tested
on Ubuntu. In theory, it should work on Fedora-like platforms. Support
for MacOS has not been investigated. Support for Windows probably 
wouldn't make sense.

Acknowledgements
----------------

The photo used in the unit tests is by George Chernilevsky - Own work, 
CC BY-SA 4.0, https://commons.wikimedia.org/w/index.php?curid=34785750.
