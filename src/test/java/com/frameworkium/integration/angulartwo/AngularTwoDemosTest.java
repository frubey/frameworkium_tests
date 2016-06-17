package com.frameworkium.integration.angulartwo;

import com.frameworkium.core.ui.tests.BaseTest;
import com.frameworkium.integration.angulartwo.pages.SpreadsheetDemoPage;
import org.testng.annotations.Test;

import static com.google.common.truth.Truth.assert_;

public class AngularTwoDemosTest extends BaseTest {

    @Test
    public void angularTwoDemosTest() {
        SpreadsheetDemoPage page = SpreadsheetDemoPage.open();
        assert_().that(page.getTitle()).isEqualTo("Angular 2.0 sample components");
        page.clickLazyLoadedTreeView();
    }

}
