package dev.citeweave.source;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 一次领取一个摘要，模型工作的同时用户仍可阅读资料。 */
@Component
class SummaryTaskWorker {
    private final SummaryTaskService tasks;

    SummaryTaskWorker(SummaryTaskService tasks) {
        this.tasks = tasks;
    }

    /** 到期时先结束旧尝试，再领取下一份资料。 */
    @Scheduled(fixedDelay = 1000)
    void tick() {
        tasks.processNext();
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "citeweave.tasks.worker-enabled", matchIfMissing = true, havingValue = "true")
class SummaryTaskScheduling {}
