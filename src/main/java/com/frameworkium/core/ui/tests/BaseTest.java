package com.frameworkium.core.ui.tests;

import com.frameworkium.core.common.listeners.MethodInterceptor;
import com.frameworkium.core.common.listeners.ResultLoggerListener;
import com.frameworkium.core.common.listeners.TestListener;
import com.frameworkium.core.common.reporting.TestIdUtils;
import com.frameworkium.core.common.reporting.allure.AllureLogger;
import com.frameworkium.core.common.reporting.allure.AllureProperties;
import com.frameworkium.core.ui.capture.ScreenshotCapture;
import com.frameworkium.core.ui.driver.Driver;
import com.frameworkium.core.ui.driver.DriverSetup;
import com.frameworkium.core.ui.driver.WebDriverWrapper;
import com.frameworkium.core.ui.listeners.CaptureListener;
import com.frameworkium.core.ui.listeners.SauceLabsListener;
import com.frameworkium.core.ui.listeners.ScreenshotListener;
import com.saucelabs.common.SauceOnDemandAuthentication;
import com.saucelabs.common.SauceOnDemandSessionIdProvider;
import com.saucelabs.testng.SauceOnDemandAuthenticationProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.testng.annotations.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;

@Listeners({CaptureListener.class, ScreenshotListener.class,
        MethodInterceptor.class, SauceLabsListener.class,
        TestListener.class, ResultLoggerListener.class})
public abstract class BaseTest
        implements SauceOnDemandSessionIdProvider, SauceOnDemandAuthenticationProvider {

    public static final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected static final Logger logger = LogManager.getLogger();

    private static final long DEFAULT_TIMEOUT_SECONDS = 10L;

    private static ThreadLocal<ScreenshotCapture> capture;
    private static ThreadLocal<Driver> driver;
    private static ThreadLocal<Wait<WebDriver>> wait;
    private static ThreadLocal<Boolean> resetDriver;
    private static List<Driver> activeDrivers =
            Collections.synchronizedList(new ArrayList<>());
    private static String userAgent;

    /**
     * Method which runs first upon running a test, it will do the following:
     * <ul>
     * <li>Retrieve the {@link Driver} and initialise the {@link WebDriver}</li>
     * <li>Initialise the {@link Wait}</li>
     * <li>Initialise whether the browser needs resetting</li>
     * <li>Initialise the {@link ScreenshotCapture}</li>
     * </ul>
     */
    @BeforeSuite(alwaysRun = true)
    public static void instantiateDriverObject() {
        driver = ThreadLocal.withInitial(() -> {
            Driver newDriver =
                    new DriverSetup().returnDesiredDriverType();
            newDriver.initialise();
            activeDrivers.add(newDriver);
            return newDriver;
        });
        wait = ThreadLocal.withInitial(BaseTest::newDefaultWait);
        resetDriver = ThreadLocal.withInitial(() -> false);
        capture = ThreadLocal.withInitial(() -> null);
    }

    /**
     * The methods which configure the browser once a test runs
     * <ul>
     * <li>Maximises browser based on the driver type</li>
     * <li>Initialises screenshot capture if needed</li>
     * <li>Resets the browser if another test ran prior</li>
     * <li>Sets the user agent of the browser</li>
     * </ul>
     *
     * @param testMethod The test method about to be executed
     */
    @BeforeMethod(alwaysRun = true)
    public static void configureBrowserBeforeTest(Method testMethod) {
        try {
            wait.set(newDefaultWait());
            userAgent = determineUserAgent();
            initialiseNewScreenshotCapture(testMethod);
        } catch (Exception e) {
            logger.error("Failed to configure browser.", e);
            throw new RuntimeException("Failed to configure browser.", e);
        }
    }

    /**
     * This method resets the browser after the test.
     *
     * Default implementation:
     * <ul>
     *     <li>Tear down the browser after test method</li>
     *     <li>Re-create the browser after tear down</li>
     * </ul>
     */
    @AfterMethod
    public static void resetBrowserAfterTest() {
        if (resetDriver.get()) {
            driver.get().resetBrowser();
        } else {
            resetDriver.set(true);
        }
    }

    private static String determineUserAgent() {
        try {
            return (String) getDriver().executeScript("return navigator.userAgent;");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Initialise the screenshot capture and link to issue/test case id
     *
     * @param testMethod Test method passed from the test script
     */
    private static void initialiseNewScreenshotCapture(Method testMethod) {
        if (ScreenshotCapture.isRequired()) {
            Optional<String> testID = TestIdUtils.getIssueOrTestCaseIdValue(testMethod);
            if (testID.orElse("").isEmpty()) {
                logger.warn("{} doesn't have a TestID annotation.", testMethod.getName());
                testID = Optional.of(StringUtils.abbreviate(testMethod.getName(), 20));
            }
            capture.set(new ScreenshotCapture(testID.orElse("n/a")));
        }
    }

    /**
     * @return a new {@link Wait} for the thread local driver and default timeout
     */
    public static Wait<WebDriver> newDefaultWait() {
        return newWaitWithTimeout(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * @param timeout timeout in seconds for the {@link Wait}
     * @return a new {@link Wait} for the thread local driver and given timeout
     */
    public static Wait<WebDriver> newWaitWithTimeout(long timeout) {
        return new FluentWait<>(getDriver().getWrappedDriver())
                .withTimeout(timeout, TimeUnit.SECONDS)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    /**
     * @return the {@link WebDriverWrapper} instance for the requesting thread
     */
    public static WebDriverWrapper getDriver() {
        return driver.get().getDriver();
    }

    /** Loops through all active driver types and tears them down */
    @AfterSuite(alwaysRun = true)
    public static void tearDownRemainingDrivers() {
        try {
            activeDrivers.stream().parallel()
                    .forEach(Driver::tearDown);
        } catch (Exception e) {
            logger.warn("Session quit unexpectedly.", e);
        }
    }

    /** Shuts down the {@link ExecutorService} */
    @AfterSuite(alwaysRun = true)
    public static void shutdownExecutor() {
        try {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Executor was interrupted while shutting down. " +
                    "Some tasks might not have been executed.");
        }
    }

    /** Creates the allure properties for the report */
    @AfterSuite(alwaysRun = true)
    public static void createAllureProperties() {
        AllureProperties.create();
    }

    /** @return the {@link ScreenshotCapture} object for the current test */
    public static ScreenshotCapture getCapture() {
        return capture.get();
    }

    /** @return The default {@link Wait} */
    public static Wait<WebDriver> getWait() {
        return wait.get();
    }

    /** @return Optional of the current browser user agent */
    public static Optional<String> getUserAgent() {
        return Optional.ofNullable(userAgent);
    }

    /** @return the Job id for the current thread */
    @Override
    public String getSessionId() {
        SessionId sessionId = getDriver().getWrappedRemoteWebDriver().getSessionId();
        return isNull(sessionId) ? null : sessionId.toString();
    }

    /**
     * @return the {@link SauceOnDemandAuthentication} instance containing
     * the Sauce username/access key
     */
    @Override
    public SauceOnDemandAuthentication getAuthentication() {
        return new SauceOnDemandAuthentication();
    }

    /**
     * Logs the start of a step to your allure report
     * Other steps will be sub-steps until you call stepFinish
     *
     * @param stepName the name of the step
     * @deprecated use <code>AllureLogger.__stepStart(stepName)</code>
     */
    @Deprecated
    public void __stepStart(String stepName) {
        AllureLogger.__stepStart(stepName);
    }

    /**
     * Logs the end of a step
     *
     * @deprecated use <code>AllureLogger.__stepFinish()</code>
     */
    @Deprecated
    public void __stepFinish() {
        AllureLogger.__stepFinish();
    }
}
