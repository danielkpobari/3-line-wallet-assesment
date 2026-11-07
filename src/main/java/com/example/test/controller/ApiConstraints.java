package com.example.test.controller;

/** Shared validation constraints and paging defaults for request parameters. */
final class ApiConstraints {

    static final String ACCOUNT_NUMBER_REGEX = "\\d{10}";
    static final String ACCOUNT_NUMBER_MESSAGE = "must be exactly 10 digits";
    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_SIZE = "20";
    static final int MAX_PAGE_SIZE = 100;

    private ApiConstraints() {
    }
}
