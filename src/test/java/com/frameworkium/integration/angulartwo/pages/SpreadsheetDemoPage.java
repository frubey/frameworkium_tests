package com.frameworkium.integration.angulartwo.pages;

import com.frameworkium.core.ui.annotations.Visible;
import com.frameworkium.core.ui.pages.BasePage;
import com.frameworkium.core.ui.pages.PageFactory;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class SpreadsheetDemoPage extends BasePage<SpreadsheetDemoPage> {

    @FindBy(css = ".jumbotron h1")
    @Visible
    private WebElement title;

    @FindBy(linkText = "Lazy Loaded Tree View")
    @Visible
    private WebElement lazyLoadedTreeViewButton;

    @FindBy(css = ".iconButton:not(.tree-node-no-children)")
    private WebElement usaExpander;

    public static SpreadsheetDemoPage open() {
        return PageFactory.newInstance(SpreadsheetDemoPage.class, "http://www.syntaxsuccess.com/angular-2-samples/#/demo/spreadsheet");
    }

    public String getTitle() {
        return title.getText();
    }

    public void clickLazyLoadedTreeView() {
        lazyLoadedTreeViewButton.click();
        waitForAngularRequestsToFinish();
        usaExpander.click();
        waitForAngularRequestsToFinish();
    }

}
