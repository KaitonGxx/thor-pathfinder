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
}
