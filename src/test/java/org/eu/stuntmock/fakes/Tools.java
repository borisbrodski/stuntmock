package org.eu.stuntmock.fakes;

/** Fake production class with static methods, for static mocking tests. */
public final class Tools {

    private Tools() {
    }

    public static int doubled(int value) {
        return value * 2;
    }

    public static int tripled(int value) {
        return value * 3;
    }

    public static String version() {
        return "real-version";
    }

    public static String describe(String name, int count) {
        return name + "×" + count;
    }

    public static long total(long a, long b) {
        return a + b;
    }

    public static boolean enabled() {
        return true;
    }

    public static java.util.List< String > names() {
        return java.util.List.of("real");
    }

    public static void reset() {
        counter = 0;
    }

    public static int counter;

    public static int count() {
        return ++counter;
    }

    /** Calls another static method of the same class; tests self-calls. */
    public static int sixfold(int value) {
        return doubled(tripled(value));
    }

    public static String fromHelper() {
        return Helper.help();
    }
}
