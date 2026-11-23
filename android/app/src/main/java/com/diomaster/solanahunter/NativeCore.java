package com.diomaster.solanahunter;

/** Starts the bundled Rust ScreenerBot engine in this app process. */
public final class NativeCore {
    static {
        System.loadLibrary("screenerbot");
    }

    private NativeCore() {}

    public static native boolean start(String privateDataDirectory);
}
