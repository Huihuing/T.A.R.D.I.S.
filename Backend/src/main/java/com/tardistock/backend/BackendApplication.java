package com.tardistock.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.core.metrics.StartupStep;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EnableScheduling
@SpringBootApplication(excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class BackendApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);
    private static final String STARTUP_PROFILING_ENV = "STARTUP_PROFILING";
    private static final int STARTUP_BUFFER_CAPACITY = 2_048;
    private static final int STARTUP_LOG_LIMIT = 30;
    private static final long STARTUP_SLOW_STEP_MS = 100;

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BackendApplication.class);
        BufferingApplicationStartup startup = null;

        if (Boolean.parseBoolean(System.getenv(STARTUP_PROFILING_ENV))) {
            startup = new BufferingApplicationStartup(STARTUP_BUFFER_CAPACITY);
            application.setApplicationStartup(startup);
            log.info("STARTUP_PROFILE enabled with capacity={}", STARTUP_BUFFER_CAPACITY);
        }

        application.run(args);

        if (startup != null) {
            logSlowStartupSteps(startup);
        }
    }

    private static void logSlowStartupSteps(BufferingApplicationStartup startup) {
        List<StartupTimeline.TimelineEvent> events =
                new ArrayList<>(startup.getBufferedTimeline().getEvents());
        events.sort(Comparator.comparing(StartupTimeline.TimelineEvent::getDuration).reversed());

        int logged = 0;
        for (StartupTimeline.TimelineEvent event : events) {
            long durationMs = event.getDuration().toMillis();
            if (durationMs < STARTUP_SLOW_STEP_MS) {
                break;
            }

            StartupStep step = event.getStartupStep();
            log.info(
                    "STARTUP_PROFILE durationMs={} step={}{}",
                    durationMs,
                    step.getName(),
                    formatUsefulTags(step)
            );

            logged++;
            if (logged >= STARTUP_LOG_LIMIT) {
                break;
            }
        }

        log.info(
                "STARTUP_PROFILE summary recordedEvents={} loggedSlowEvents={} thresholdMs={}",
                events.size(),
                logged,
                STARTUP_SLOW_STEP_MS
        );
    }

    private static String formatUsefulTags(StartupStep step) {
        StringBuilder tags = new StringBuilder();
        for (StartupStep.Tag tag : step.getTags()) {
            if (!"beanName".equals(tag.getKey()) && !"beanType".equals(tag.getKey())) {
                continue;
            }
            tags.append(' ')
                    .append(tag.getKey())
                    .append('=')
                    .append(tag.getValue());
        }
        return tags.toString();
    }
}
