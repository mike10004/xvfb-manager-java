[![Travis build status](https://img.shields.io/travis/mike10004/xvfb-manager-java.svg)](https://travis-ci.org/mike10004/xvfb-manager-java)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/xvfb-manager-parent.svg)](https://repo1.maven.org/maven2/com/github/mike10004/xvfb-manager-parent/)

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
        <version>0.14</version>
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
        <version>0.4</version>
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
* **xvfb-unittest-tools**: library only used by above projects for testing
* **xvfb-manager-example**: executable program demonstrating Selenium usage

Using with Selenium
-------------------

To use an `XvfbManager` instance with Selenium, start the virtual framebuffer 
and then configure the webdriver process's environment with the appropriate 
display. (There used to be an **xvfb-selenium** artifact that had a convenience
method for this, but I didn't like keeping up with the Selenium API changes.)

### Chrome

    ChromeDriverService service = new ChromeDriverService.Builder()
            .usingAnyFreePort()
            .withEnvironment(ImmutableMap.of("DISPLAY", display))
            .build();
    return new ChromeDriver(service);

### Firefox

    GeckoDriverService service = new GeckoDriverService.Builder()
            .usingAnyFreePort()
            .withEnvironment(ImmutableMap.of("DISPLAY", display))
            .build();
    FirefoxDriver driver = new FirefoxDriver(service);

Notes
-----

### Automatic Display Selection

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

### Unit tests with Selenium

The unit tests that involve Selenium make use of [WebDriverManager]
to manage webdrivers for Chrome and Firefox. This library queries the 
GitHub API and occasionally gets a 403 response due to [rate-limiting]
of anonymous requests. To make your requests non-anonymous and prevent
the rate-limiting from breaking the build:

1. Create a pair token/secret in your [GitHub account]
2. Set system properties `wdm.gitHubTokenName` and `wdm.gitHubTokenSecret`
   for the build

Supported Operating Systems
---------------------------

Currently only Linux is supported, and the library has only been tested
on Ubuntu. In theory, it should work on Fedora-like platforms. Support
for MacOS has not been investigated. Support for Windows probably 
wouldn't make sense.

Runtime and Unit Test Requirements
----------------------------------

Some programs are required to be installed at runtime, and many tests 
will be skipped if certain programs are not available in the build 
environment. These prerequisites are listed in the table below by 
distribution.

<table>
  <tr>
    <td><b>Ubuntu 16.04</b></td>
    <td><b>Fedora 24</b></td>
    <td><b>Scope</b></td>
  </tr>
  <tr>
    <td>xvfb</td>
    <td>xorg-x11-server-Xvfb</td>
    <td>runtime</td>
  </tr>
  <tr>
    <td>netpbm</td>
    <td>netpbm-progs</td>
    <td>runtime</td>
  </tr>
  <tr>
    <td>x11-utils</td>
    <td>xorg-x11-utils</td>
    <td>runtime</td>
  </tr>
  <tr>
    <td>x11-apps</td>
    <td>xorg-x11-apps</td>
    <td>test</td>
  </tr>
  <tr>
    <td>xdotool</td>
    <td>xdotool</td>
    <td>test</td>
  </tr>
  <tr>
    <td>imagemagick</td>
    <td>ImageMagick</td>
    <td>test</td>
  </tr>
  <tr>
    <td>firefox</td>
    <td>firefox</td>
    <td>test</td>
  </tr>
  <tr>
    <td>google-chrome or chromium</td>
    <td>google-chrome or chromium</td>
    <td>test</td>
  </tr>
</table>

FAQ
---

### Does it work on MacOS?

Not really, but you might be able to shoehorn it. You can install [XQuartz] 
(see [Homebrew instructions]), but that version of Xvfb requires being
run as root (for the creation of secure directories in /tmp), and you should
be justifiably skeptical of anyone suggesting you run your unit tests as root.
You might be able to get around this be manually creating `/tmp/.X11-unix` as
root and setting its permissions to 1777 *before* you run your tests.

Acknowledgements
----------------

The photo used in the unit tests is by George Chernilevsky - Own work, 
CC BY-SA 4.0, https://commons.wikimedia.org/w/index.php?curid=34785750.

[WebDriverManager]: https://github.com/bonigarcia/webdrivermanager
[rate-limiting]: https://developer.github.com/v3/#rate-limiting
[GitHub account]: https://github.com/settings/tokens
[Homebrew instructions]: http://macappstore.org/xquartz/
