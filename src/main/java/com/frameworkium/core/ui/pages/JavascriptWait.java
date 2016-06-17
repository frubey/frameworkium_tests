package com.frameworkium.core.ui.pages;

import com.frameworkium.core.ui.AwaitedConditions;
import com.paulhammant.ngwebdriver.NgWebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Wait;

/**
 * Frameworkium implementation of waiting for JS events on page-load
 */
public class JavascriptWait {

    private Integer angularVersion = 0;

    private final Wait<WebDriver> wait;
    private final JavascriptExecutor javascriptExecutor;
    private final NgWebDriver ngWebDriver;

    JavascriptWait(WebDriver driver, Wait<WebDriver> wait) {
        this.wait = wait;
        this.javascriptExecutor = (JavascriptExecutor) driver;
        this.ngWebDriver = new NgWebDriver(javascriptExecutor);
    }

    /**
     * Default entry to {@link JavascriptWait}
     *
     * Following actions are waited on:
     * <ul>
     *     <li>Document state to be ready</li>
     *     <li>If page is Angular, wait for angular to construct the page</li>
     * </ul>
     */
    void waitForJavascriptEvents() {
        waitForDocumentReady();
        if(detectAngular()) waitForAngularReady();
    }

    /**
     * Waits for angular to be ready on-page depending on the major version
     */
    void waitForAngularReady() {
        if (angularVersion == 1) waitForAngularOneReady();
        else if (angularVersion == 2) waitForAngularTwoReady();
    }

    private void waitForDocumentReady() {
        wait.until(AwaitedConditions.documentBodyReady());
    }

    private boolean detectAngular() {
        if ((Boolean) javascriptExecutor.executeScript("return typeof angular == 'object';")) {
            angularVersion = 1;
            return true;
        } else if ((Boolean) javascriptExecutor.executeScript("return typeof ng == 'object';")) {
            angularVersion = 2;
            return true;
        }
        return false;
    }

    private void waitForAngularOneReady() {
        ngWebDriver.waitForAngularRequestsToFinish();
    }

    private void waitForAngularTwoReady() {
        wait.until(AwaitedConditions.angularTwoReady());
    }

}
