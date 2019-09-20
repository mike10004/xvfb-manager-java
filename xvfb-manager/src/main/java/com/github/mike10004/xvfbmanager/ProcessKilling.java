package com.github.mike10004.xvfbmanager;

import io.github.mike10004.subprocess.ProcessDestructor;

import java.util.concurrent.TimeUnit;

class ProcessKilling {

    private ProcessKilling() {}

    public static void termOrKill(ProcessDestructor destructor, long timeout, TimeUnit duration) {
        destructor.sendTermSignal()
                .await(timeout, duration)
                .kill();
    }
}
