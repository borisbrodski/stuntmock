package org.eu.de.stuntmock.unit;

/** Stands in for Hamcrest/AssertJ: a static {@code startsWith} and {@code any} a test might import. */
final class OtherLibrary {

    private OtherLibrary() {
    }

    static String startsWith(String prefix) {
        return "other:" + prefix;
    }

    static String any() {
        return "other:any";
    }
}
