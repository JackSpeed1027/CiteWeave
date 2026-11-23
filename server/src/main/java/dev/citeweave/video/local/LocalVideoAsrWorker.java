package dev.citeweave.video.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/** 单机逐个执行 CPU 任务，独立调度器避免阻塞摘要 worker。 */
@Component
@ConditionalOnProperty(name = "citeweave.video.asr.enabled", havingValue = "true")
class LocalVideoAsrWorker {
    private final LocalVideoAsrService service;

    LocalVideoAsrWorker(LocalVideoAsrService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${citeweave.video.asr.poll-delay-ms}", scheduler = "localVideoAsrScheduler")
    void tick() { service.runNext(); }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "citeweave.video.asr.enabled", havingValue = "true")
class LocalVideoAsrScheduling {
    @Bean
    ThreadPoolTaskScheduler localVideoAsrScheduler() {
        return scheduler("video-asr-");
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("background-");
    }

    private static ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
