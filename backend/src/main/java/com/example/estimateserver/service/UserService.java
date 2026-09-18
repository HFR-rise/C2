package com.example.estimateserver.service;

import com.example.estimateserver.model.User;
import com.example.estimateserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    private final UserRepository userRepository;
    private final SmsService smsService;
    private final WebSocketService webSocketService;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository,
                       SmsService smsService,
                       WebSocketService webSocketService) {
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public void sendVerificationCode(String phoneNumber) {
        String code = String.format("%06d", random.nextInt(1_000_000));

        User user = findOrCreateUser(phoneNumber);

        user.setVerificationCode(code);
        user.setCodeExpiresAt(new Date(System.currentTimeMillis() + CODE_TTL_MS));
        user.setVerified(false);

        userRepository.save(user);
        smsService.sendCode(phoneNumber, code);

        log.info("Verification code sent to {}", maskPhone(phoneNumber));
    }

    private User findOrCreateUser(String phoneNumber) {
        Optional<User> existing = userRepository.findByPhoneNumber(phoneNumber);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User(phoneNumber);
        user.setId(UUID.randomUUID().toString());
        user.setUserId(user.getId());
        user.setCreatedAt(new Date());
        user.setLastActiveAt(new Date());

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent user creation for {}, re-reading", maskPhone(phoneNumber));
            return userRepository.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new IllegalStateException(
                            "User disappeared after constraint violation", e));
        }
    }

    @Transactional
    public User verifyCode(String phoneNumber, String code) {
        return verifyCode(phoneNumber, code, null);
    }

    @Transactional
    public User verifyCode(String phoneNumber, String code, String deviceId) {
        User user = userRepository.findByPhoneNumberForUpdate(phoneNumber)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            throw new RuntimeException("Invalid code");
        }
        if (user.getCodeExpiresAt() != null && user.getCodeExpiresAt().before(new Date())) {
            throw new RuntimeException("Code expired");
        }

        String finalDeviceId = (deviceId != null) ? deviceId : UUID.randomUUID().toString();
        String currentSession = user.getActiveSessionId();

        log.debug("Verify code: user={}, currentSession={}, newDevice={}",
                user.getId(), currentSession, finalDeviceId);

        if (currentSession != null && !currentSession.equals(finalDeviceId)) {
            handleDeviceChange(user, finalDeviceId);
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setCodeExpiresAt(null);
        user.setLastActiveAt(new Date());
        user.setActiveSessionId(finalDeviceId);
        user.setCurrentDeviceInfo(finalDeviceId);

        if (user.getUserId() == null) {
            user.setUserId(user.getId());
        }

        User saved = userRepository.save(user);
        log.info("User {} logged in on device {}", maskPhone(phoneNumber), finalDeviceId);
        return saved;
    }

    private void handleDeviceChange(User user, String newDeviceId) {
        String oldDeviceId = user.getActiveSessionId();
        boolean oldOnline = webSocketService.hasSession(user.getId());

        if (oldOnline) {
            log.warn("Login rejected: user={} already online on device={}",
                    user.getId(), oldDeviceId);
            throw new RuntimeException("Account already in use on another device");
        }

        log.info("Old device offline, replacing session: user={}, old={}, new={}",
                user.getId(), oldDeviceId, newDeviceId);

        if (webSocketService.hasAnySession(user.getId())) {
            webSocketService.removeSession(user.getId());
        }

        if (oldDeviceId != null && !oldDeviceId.isEmpty()) {
            webSocketService.queueForceLogoutForOfflineUser(user.getId(), oldDeviceId);
        }
    }

    @Transactional
    public void logout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            log.info("Logout: user={}", userId);

            webSocketService.removeSession(userId);

            user.setActiveSessionId(null);
            user.setCurrentDeviceInfo(null);
            userRepository.save(user);
        });
    }

    @Transactional
    public void forceLogout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            log.warn("Force logout: user={}", userId);

            String deviceId = user.getActiveSessionId();

            if (webSocketService.hasSession(userId)) {
                webSocketService.sendForceLogout(userId);
            } else if (deviceId != null && !deviceId.isEmpty()) {
                webSocketService.queueForceLogoutForOfflineUser(userId, deviceId);
            }

            user.setActiveSessionId(null);
            user.setCurrentDeviceInfo(null);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(String userId, String deviceId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.debug("isSessionValid: user not found: {}", userId);
            return false;
        }

        String activeSessionId = userOpt.get().getActiveSessionId();
        boolean matches = activeSessionId != null && activeSessionId.equals(deviceId);

        if (!matches) {
            log.debug("isSessionValid: device mismatch for user={} (expected={}, got={})",
                    userId, activeSessionId, deviceId);
        }
        return matches;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSession(String userId) {
        return userRepository.findById(userId)
                .map(u -> u.getActiveSessionId() != null && webSocketService.hasSession(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAccountOccupied(String userId) {
        return webSocketService.hasAnySession(userId);
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}