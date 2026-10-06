package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.UserSession;
import com.example.estimateserver.repository.UserSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class WebSocketService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private static final int MAX_OFFLINE_MESSAGES_PER_USER = 50;
    private static final long STARTUP_CLOSE_DELAY_MS = 3_000L;
    private static final long STARTUP_FINALIZE_DELAY_MS = 5_000L;
    private static final long UNRESPONSIVE_CLOSE_DELAY_MS = 5_000L;

    private final Map<String, List<SyncMessage>> offlineMessagesByUser = new ConcurrentHashMap<>();

    private final Map<String, List<SyncMessage>> offlineMessages = new ConcurrentHashMap<>();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final UserSessionRepository sessionRepository;

    private final AtomicBoolean serverJustStarted = new AtomicBoolean(true);
    private volatile boolean acceptingNewConnections = false;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "ws-scheduler");
                t.setDaemon(true);
                return t;
            });

    public WebSocketService(UserSessionRepository sessionRepository,
                            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    private static String offlineKey(String userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    public void addSession(String userId, WebSocketSession session) {
        if (userId == null || session == null) {
            log.warn("Cannot add session: userId or session is null");
            return;
        }

        if (serverJustStarted.get() && !acceptingNewConnections) {
            log.info("Rejecting connection during startup: user={}", userId);
            rejectConnection(userId, session);
            return;
        }

        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            WebSocketSession existing = sessions.get(userId);
            if (existing != null && existing.isOpen()) {
                log.info("Replacing existing session for user={} (old={}, new={})",
                        userId, existing.getId(), session.getId());
                sendForceLogoutToSession(userId, existing);
                closeQuietly(existing, CloseStatus.POLICY_VIOLATION);
                sessions.remove(userId);
                sessionToUser.remove(existing.getId());
            }

            sessions.put(userId, session);
            sessionToUser.put(session.getId(), userId);

            String deviceId = getDeviceIdFromSession(session);
            saveSessionToDatabase(userId, session.getId(), deviceId);

            List<SyncMessage> pendingByUser = offlineMessagesByUser.remove(userId);
            if (pendingByUser != null && !pendingByUser.isEmpty()) {
                log.debug("Sending {} user-scoped pending messages to user={}",
                        pendingByUser.size(), userId);
                for (SyncMessage msg : pendingByUser) {
                    trySend(session, userId, msg);
                }
            }

            List<SyncMessage> pending = offlineMessages.remove(offlineKey(userId, deviceId));
            if (pending != null && !pending.isEmpty()) {
                log.debug("Sending {} device-scoped pending messages to user={} device={}",
                        pending.size(), userId, deviceId);
                for (SyncMessage msg : pending) {
                    trySend(session, userId, msg);
                }
            }

            log.info("User {} connected (total sessions: {})", userId, sessions.size());
        } finally {
            lock.unlock();
        }
    }

    private void trySend(WebSocketSession session, String userId, SyncMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send pending message to user={}: {}", userId, e.getMessage());
        }
    }

    public void removeSession(String userId, boolean isServerShutdown) {
        if (userId == null) return;

        ReentrantLock lock = userLocks.get(userId);
        if (lock != null) {
            lock.lock();
            try {
                doRemoveSession(userId, isServerShutdown);
            } finally {
                lock.unlock();
            }
        } else {
            doRemoveSession(userId, isServerShutdown);
        }

        userLocks.computeIfPresent(userId,
                (k, l) -> (!sessions.containsKey(k) && !l.isLocked()) ? null : l);
    }

    public void removeSession(String userId) {
        removeSession(userId, false);
    }

    private void doRemoveSession(String userId, boolean isServerShutdown) {
        WebSocketSession session = sessions.remove(userId);
        if (session != null) {
            sessionToUser.remove(session.getId());
            log.debug("Removed session for user={}", userId);
        }

        if (!isServerShutdown) {
            safeDeactivateSession(userId);
        }
    }

    public void sendToUser(String userId, String deviceId, SyncMessage message) {
        if (message == null || userId == null) return;

        if (serverJustStarted.get() && !"FORCE_LOGOUT".equals(message.getType())) {
            saveOfflineMessage(userId, deviceId, message);
            return;
        }

        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            saveOfflineMessage(userId, deviceId, message);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Send failed to user={}: {}", userId, e.getMessage());
            saveOfflineMessage(userId, deviceId, message);
        }
    }

    public void sendToUserOrQueue(String userId, SyncMessage message) {
        if (message == null || userId == null) return;

        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            sendToUser(userId, null, message);
            return;
        }

        String deviceId = getDeviceIdForUser(userId);
        saveOfflineMessage(userId, deviceId, message);
    }

    public void queueOfflineMessageForUser(String userId, SyncMessage message) {
        if (userId == null || message == null) return;

        List<SyncMessage> list = offlineMessagesByUser.computeIfAbsent(
                userId, k -> new CopyOnWriteArrayList<>());
        list.add(message);
        while (list.size() > MAX_OFFLINE_MESSAGES_PER_USER) {
            list.remove(0);
        }
        log.debug("Queued offline message for user={}, type={}", userId, message.getType());
    }

    public boolean hasSession(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public boolean hasAnySession(String userId) {
        return sessions.containsKey(userId);
    }

    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    public Set<String> getOnlineUsers() {
        return new HashSet<>(sessions.keySet());
    }

    public int getActiveSessionsCount() {
        return sessions.size();
    }

    public String getDeviceIdForUser(String userId) {
        try {
            return sessionRepository.findByUserId(userId)
                    .map(UserSession::getDeviceId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to get deviceId for user={}: {}", userId, e.getMessage());
            return null;
        }
    }

    public void setServerJustStarted(boolean started) {
        log.info("serverJustStarted = {}", started);
        serverJustStarted.set(started);
        acceptingNewConnections = !started;
    }

    public boolean isServerJustStarted() {
        return serverJustStarted.get();
    }

    public void forceLogoutAllActiveSessions() {
        log.warn("FORCE LOGOUT ALL ACTIVE SESSIONS");
        acceptingNewConnections = false;

        int sent = 0;
        for (String userId : new HashSet<>(sessions.keySet())) {
            ReentrantLock lock = userLocks.get(userId);
            if (lock == null) continue;

            lock.lock();
            try {
                WebSocketSession session = sessions.get(userId);
                if (session != null && session.isOpen() && sendForceLogoutToSession(userId, session)) {
                    sent++;
                }
            } finally {
                lock.unlock();
            }
        }
        log.info("Sent FORCE_LOGOUT to {} users", sent);

        scheduler.schedule(this::closeAllSessionsAfterStartup,
                STARTUP_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void closeAllSessionsAfterStartup() {
        log.info("Closing all sessions after force logout");

        for (String userId : new HashSet<>(sessions.keySet())) {
            ReentrantLock lock = userLocks.get(userId);
            if (lock == null) continue;

            lock.lock();
            try {
                WebSocketSession session = sessions.get(userId);
                if (session != null && session.isOpen()) {
                    closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                }
            } finally {
                lock.unlock();
            }
        }

        sessions.clear();
        sessionToUser.clear();
        acceptingNewConnections = true;

        scheduler.schedule(() -> {
            serverJustStarted.set(false);
            log.info("Server startup phase completed");
        }, STARTUP_FINALIZE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void gracefulShutdown() {
        log.info("WebSocket graceful shutdown");
        scheduler.shutdownNow();

        for (String userId : new HashSet<>(sessions.keySet())) {
            ReentrantLock lock = userLocks.get(userId);
            if (lock == null) continue;

            lock.lock();
            try {
                WebSocketSession session = sessions.get(userId);
                if (session != null && session.isOpen()) {
                    closeQuietly(session, CloseStatus.NORMAL);
                }
            } finally {
                lock.unlock();
            }
        }

        sessions.clear();
        sessionToUser.clear();
        offlineMessages.clear();
        offlineMessagesByUser.clear();
        userLocks.clear();
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupStaleLocks() {
        userLocks.entrySet().removeIf(entry ->
                !sessions.containsKey(entry.getKey()) && !entry.getValue().isLocked());

        long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        offlineMessagesByUser.entrySet().removeIf(entry -> {
            List<SyncMessage> list = entry.getValue();
            return list.isEmpty()
                    || (list.get(list.size() - 1).getTimestamp() != null
                    && list.get(list.size() - 1).getTimestamp().getTime() < cutoff);
        });
    }

    @Scheduled(fixedDelay = 30_000)
    public void sendPingToAllClients() {
        if (serverJustStarted.get()) {
            log.debug("Skipping ping during startup phase");
            return;
        }

        for (String userId : new HashSet<>(sessions.keySet())) {
            WebSocketSession session = sessions.get(userId);
            if (session == null || !session.isOpen()) continue;

            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage("ping"));
                }
            } catch (IOException e) {
                log.warn("Ping failed to user={}", userId);
                scheduleSessionClose(userId, session);
            }
        }
    }

    public void sendForceLogout(String userId) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock != null) {
            lock.lock();
            try {
                WebSocketSession session = sessions.get(userId);
                if (session != null && session.isOpen()) {
                    sendForceLogoutToSession(userId, session);
                    closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                }
            } finally {
                lock.unlock();
            }
        }
        removeSession(userId, false);
    }

    public void queueForceLogoutForOfflineUser(String userId, String deviceId) {
        SyncMessage msg = new SyncMessage(
                "FORCE_LOGOUT", "SESSION", null, null,
                userId, new Date(), null);

        if (deviceId == null || deviceId.isEmpty()) {
            queueOfflineMessageForUser(userId, msg);
            return;
        }

        saveOfflineMessage(userId, deviceId, msg);
    }

    public void updateSessionLastPong(String userId) {
        try {
            sessionRepository.findByUserId(userId).ifPresent(session -> {
                session.setLastPongAt(new Date());
                sessionRepository.save(session);
            });
        } catch (Exception e) {
            log.warn("Error updating last pong for user={}: {}", userId, e.getMessage());
        }
    }

    public void dumpSessionsState() {
        log.info("Sessions: total={}, serverJustStarted={}, accepting={}",
                sessions.size(), serverJustStarted.get(), acceptingNewConnections);
    }

    private void rejectConnection(String userId, WebSocketSession session) {
        try {
            sendForceLogoutToSession(userId, session);
        } catch (Exception ignored) {
        } finally {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
        }
    }

    private boolean sendForceLogoutToSession(String userId, WebSocketSession session) {
        try {
            SyncMessage message = new SyncMessage(
                    "FORCE_LOGOUT", "SESSION", null, null,
                    userId, new Date(), null);
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException e) {
            log.warn("Failed to send FORCE_LOGOUT to user={}: {}", userId, e.getMessage());
            return false;
        }
    }

    private void saveOfflineMessage(String userId, String deviceId, SyncMessage message) {
        if (deviceId == null || deviceId.isEmpty()) {
            queueOfflineMessageForUser(userId, message);
            return;
        }

        String key = offlineKey(userId, deviceId);
        List<SyncMessage> list = offlineMessages.computeIfAbsent(
                key, k -> new CopyOnWriteArrayList<>());
        list.add(message);
        while (list.size() > MAX_OFFLINE_MESSAGES_PER_USER) {
            list.remove(0);
        }
        log.debug("Queued offline message for user={} device={}, type={}",
                userId, deviceId, message.getType());
    }

    private void saveSessionToDatabase(String userId, String sessionId, String deviceId) {
        try {
            sessionRepository.findByUserId(userId).ifPresentOrElse(
                    existing -> {
                        existing.setSessionId(sessionId);
                        existing.setDeviceId(deviceId);
                        existing.setActive(true);
                        existing.setConnectedAt(new Date());
                        existing.setLastPongAt(new Date());
                        sessionRepository.save(existing);
                    },
                    () -> sessionRepository.save(new UserSession(userId, sessionId, deviceId))
            );
        } catch (Exception e) {
            log.error("Failed to save session for user={}: {}", userId, e.getMessage());
        }
    }

    private void safeDeactivateSession(String userId) {
        try {
            sessionRepository.deactivateUserSession(userId);
        } catch (Exception e) {
            log.warn("Failed to deactivate session for user={}: {}", userId, e.getMessage());
        }
    }

    private String getDeviceIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("deviceId=")) {
                        return param.substring(9);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting deviceId: {}", e.getMessage());
        }
        return UUID.randomUUID().toString();
    }

    private void scheduleSessionClose(String userId, WebSocketSession session) {
        scheduler.schedule(() -> {
            WebSocketSession current = sessions.get(userId);
            if (current == session && session.isOpen()) {
                closeQuietly(session, CloseStatus.SERVER_ERROR);
                sessions.remove(userId);
                sessionToUser.remove(session.getId());
                log.info("Closed unresponsive session for user={}", userId);
            }
        }, UNRESPONSIVE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session != null && session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.debug("Close session failed: {}", e.getMessage());
        }
    }
}