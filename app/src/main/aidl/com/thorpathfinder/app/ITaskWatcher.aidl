package com.thorpathfinder.app;

import com.thorpathfinder.app.ITaskListener;

/** The helper Shizuku runs for app profiles: see TaskWatcher. */
interface ITaskWatcher {

    /** Shizuku's own call to stop a user service; its number is Shizuku's. */
    void destroy() = 16777114;

    /** Starts reporting to listener, with the tasks as they are right now. */
    void watch(ITaskListener listener) = 1;

    /**
     * Moves root tasks and swaps the screens' volumes, in this process: a few
     * milliseconds where the same as shell commands took about 50 each. moves
     * is pairs of root task id and display. topLevel and bottomLevel are the
     * volumes as they are now (a negative one leaves volumes alone); each
     * screen takes the other's, set just before the app headed there moves,
     * so that app never plays at the wrong volume. False if a move failed.
     */
    boolean swap(in int[] moves, int topLevel, int bottomLevel) = 2;

    /** A screen's brightness as Android holds it, 0..1; negative if it can't be read. */
    float getBrightness(int display) = 3;

    /** Sets a screen's brightness, 0..1 (what AYN's dual-screen panel does for the bottom one). */
    void setBrightness(int display, float brightness) = 4;

    /** Sets the bottom screen's volume, 0..15 (AYN's secondary_screen_volume_level). */
    void setBottomVolume(int level) = 5;
}
