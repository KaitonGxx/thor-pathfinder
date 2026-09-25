package com.thorpathfinder.app;

import com.thorpathfinder.app.ITaskListener;

/** The helper Shizuku runs for app profiles: see TaskWatcher. */
interface ITaskWatcher {

    /** Shizuku's own call to stop a user service; its number is Shizuku's. */
    void destroy() = 16777114;

    /** Starts reporting to listener, with the tasks as they are right now. */
    void watch(ITaskListener listener) = 1;
}
