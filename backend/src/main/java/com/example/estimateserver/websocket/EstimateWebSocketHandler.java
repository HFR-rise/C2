package com.example.estimateserver.websocket;

import com.example.estimateserver.service.UserService;
import com.example.estimateserver.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class EstimateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EstimateWebSocketHandler.class);

    private final WebSocketService webSocketService;
    private final UserService userService;

    public EstimateWebSocketHandler(WebSocketService webSocketService, UserService userService) {
        this.webSocketService = webSocketService;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractParameter(session, "userId");
        String deviceId = extractParameter(session, "deviceId");

        log.debug("WebSocket connection attempt: userId={}, deviceId={}", userId, deviceId);

        if (isBlank(userId) || isBlank(deviceId)) {
            log.warn("Connection rejected: missing userId or deviceId");
            closeSilently(session);
            return;
        }

        if (webSocketService.isServerJustStarted()) {
            log.warn("Connection rejected: server in startup phase, userId={}", userId);
            rejectSession(session, "server_startup");
            return;
        }

        if (!userService.isSessionValid(userId, deviceId)) {
            log.warn("Connection rejected: invalid session for userId={}", userId);
            rejectSession(session, "session_invalid");
            return;
        }

        webSocketService.addSession(userId, session);
        log.info("WebSocket connected: userId={}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractParameter(session, "userId");
        log.info("WebSocket closed: userId={}, code={}, reason={}",
                userId, status.getCode(), status.getReason());

        if (!isBlank(userId)) {
            boolean isServerShutdown = (status.getCode() == 1001)
                    || "Service shutdown".equals(status.getReason());
            webSocketService.removeSession(userId, isServerShutdown);
        }

        webSocketService.dumpSessionsState();
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = extractParameter(session, "userId");
        log.error("Transport error: userId={}, message={}", userId, exception.getMessage(), exception);

        closeSilently(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String userId = extractParameter(session, "userId");
        String deviceId = extractParameter(session, "deviceId");

        if (!isBlank(userId) && !isBlank(deviceId) && !userService.isSessionValid(userId, deviceId)) {
            log.warn("Invalid session, closing: userId={}", userId);
            rejectSession(session, "session_expired");
            return;
        }

        if (payload == null) return;

        if ("ping".equalsIgnoreCase(payload)) {
            sendTextSilently(session, "pong");
            return;
        }

        if ("pong".equalsIgnoreCase(payload)) {
            if (!isBlank(userId)) {
                webSocketService.updateSessionLastPong(userId);
            }
            return;
        }
    }

    private String extractParameter(WebSocketSession session, String paramName) {
        try {
            if (session.getUri() == null) return null;

            return UriComponentsBuilder.fromUri(session.getUri())
                    .build()
                    .getQueryParams()
                    .getFirst(paramName);
        } catch (Exception e) {
            log.warn("Failed to extract '{}' from session URI: {}", paramName, e.getMessage());
            return null;
        }
    }

    private void rejectSession(WebSocketSession session, String reason) {
        String message = String.format("{\"type\":\"FORCE_LOGOUT\",\"reason\":\"%s\"}", reason);
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send rejection message: {}", e.getMessage());
        } finally {
            closeSilently(session);
        }
    }

    private void sendTextSilently(WebSocketSession session, String text) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(text));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send text to session={}: {}", session.getId(), e.getMessage());
        }
    }

    private void closeSilently(WebSocketSession session) {
        try {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException e) {
            log.debug("Failed to close session={}: {}", session.getId(), e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}