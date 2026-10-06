package com.example.estimateserver.service;

import com.example.estimateserver.model.User;
import com.example.estimateserver.repository.UserRepository;
import com.example.estimateserver.utils.PhoneUtils;
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

    public static class InvalidPhoneException extends RuntimeException {
        public InvalidPhoneException(String message) { super(message); }
    }

    public static class InvalidCodeException extends RuntimeException {
        public InvalidCodeException(String message) { super(message); }
    }

    @Deprecated
    public static class AccountInUseException extends RuntimeException {
        public AccountInUseException(String message) { super(message); }
    }

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
    public void sendVerificationCode(String rawPhoneNumber) {
        String phoneNumber = PhoneUtils.normalize(rawPhoneNumber);
        if (phoneNumber == null) {
            throw new InvalidPhoneException("Неверный формат номера телефона");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        User user = findOrCreateUser(phoneNumber);

        user.setVerificationCode(code);
        user.setCodeExpiresAt(new Date(System.currentTimeMillis() + CODE_TTL_MS));
        user.setVerified(false);

        userRepository.save(user);
        smsService.sendCode(phoneNumber, code);

        log.info("Verification code sent to {}", PhoneUtils.mask(phoneNumber));
    }

    private User findOrCreateUser(String normalizedPhoneNumber) {
        Optional<User> existing = userRepository.findByPhoneNumber(normalizedPhoneNumber);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User(normalizedPhoneNumber);
        user.setId(UUID.randomUUID().toString());
        user.setUserId(user.getId());
        user.setCreatedAt(new Date());
        user.setLastActiveAt(new Date());

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent user creation for {}, re-reading",
                    PhoneUtils.mask(normalizedPhoneNumber));
            return userRepository.findByPhoneNumber(normalizedPhoneNumber)
                    .orElseThrow(() -> new IllegalStateException(
                            "User disappeared after constraint violation", e));
        }
    }

    @Transactional
    public User verifyCode(String rawPhoneNumber, String code) {
        return verifyCode(rawPhoneNumber, code, null);
    }

    @Transactional
    public User verifyCode(String rawPhoneNumber, String code, String deviceId) {
        String phoneNumber = PhoneUtils.normalize(rawPhoneNumber);
        if (phoneNumber == null) {
            throw new InvalidPhoneException("Неверный формат номера телефона");
        }

        User user = userRepository.findByPhoneNumberForUpdate(phoneNumber)
                .orElseThrow(() -> new InvalidCodeException("Пользователь не найден"));

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            throw new InvalidCodeException("Неверный код");
        }
        if (user.getCodeExpiresAt() != null && user.getCodeExpiresAt().before(new Date())) {
            throw new InvalidCodeException("Код истёк");
        }

        String finalDeviceId = (deviceId != null && !deviceId.isBlank())
                ? deviceId
                : UUID.randomUUID().toString();

        handleDeviceChange(user, finalDeviceId);

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
        log.info("User {} logged in on device {}",
                PhoneUtils.mask(phoneNumber), finalDeviceId);
        return saved;
    }

    private void handleDeviceChange(User user, String newDeviceId) {
        String oldDeviceId = user.getActiveSessionId();

        if (newDeviceId == null || newDeviceId.equals(oldDeviceId)) {
            return;
        }

        log.info("Device change: user={}, old={}, new={}",
                user.getId(), oldDeviceId, newDeviceId);

        boolean oldOnline = webSocketService.hasSession(user.getId());

        if (oldOnline) {
            log.warn("Old device online, forcing logout: user={}, old={}",
                    user.getId(), oldDeviceId);
            webSocketService.sendForceLogout(user.getId());
            webSocketService.removeSession(user.getId(), false);
        } else if (webSocketService.hasAnySession(user.getId())) {
            webSocketService.removeSession(user.getId(), false);
        }

        if (oldDeviceId != null && !oldDeviceId.isEmpty()) {
            webSocketService.queueForceLogoutForOfflineUser(user.getId(), oldDeviceId);
        }
    }

    @Transactional
    public void logout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            log.info("Logout: user={}, oldDevice={}",
                    userId, user.getActiveSessionId());

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
            log.warn("isSessionValid FAIL: userId={}, expected={}, got={}",
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

    public Optional<User> findByPhoneNumber(String rawPhoneNumber) {
        String normalized = PhoneUtils.normalize(rawPhoneNumber);
        if (normalized == null) return Optional.empty();
        return userRepository.findByPhoneNumber(normalized);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }
}