package com.lang.sekiross.netty.task;


import com.lang.sekiross.netty.task.TaskRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TimeoutTaskCleaner {

    private static final long taskCleanDuration = 30000;

    @Scheduled(fixedRate = taskCleanDuration)
    public void clean() {

        try {
            doClean();
        } catch (Exception e) {
            log.error("clean timeout task failed", e);
        }
    }

    private void doClean() {
        //60s之前的任务，都给remove调
        TaskRegistry.getInstance().cleanBefore(System.currentTimeMillis() - taskCleanDuration * 2);
    }
}
