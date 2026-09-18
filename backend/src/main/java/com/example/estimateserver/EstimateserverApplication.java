package com.example.estimateserver;

import com.example.estimateserver.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.CompletableFuture;

@SpringBootApplication
@EnableScheduling
public class EstimateserverApplication {

    private static final Logger log = LoggerFactory.getLogger(EstimateserverApplication.class);

    private final WebSocketService webSocketService;

    public EstimateserverApplication(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    public static void main(String[] args) {
        SpringApplication.run(EstimateserverApplication.class, args);
        log.info("Server started");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        CompletableFuture.runAsync(this::runStartupPhase);
    }

    private void runStartupPhase() {
        log.info("=== SERVER STARTUP PHASE ===");

        try {
            webSocketService.setServerJustStarted(true);
            log.warn("Sending FORCE_LOGOUT to all active sessions...");

            webSocketService.forceLogoutAllActiveSessions();

        } catch (Exception e) {
            log.error("Startup phase failed: {}", e.getMessage(), e);
        }
    }
}