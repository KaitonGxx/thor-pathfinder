package com.thorpathfinder.app;

/** How TaskWatcher reports: each task is one line that TaskList.parse reads. */
oneway interface ITaskListener {

    /** The focused root task, then every root task, topmost first on each display. */
    void onTasks(String focused, in String[] tasks);
}
