package com.marianhello.backgroundgeolocation;

import com.marianhello.bgloc.ResourceResolver;

public class TestResourceResolver extends ResourceResolver {

    public TestResourceResolver() {

    }

    public String getAuthority() {
        return TestConstants.Authority;
    }
}
