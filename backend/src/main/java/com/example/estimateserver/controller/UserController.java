package com.example.estimateserver.controller;

import com.example.estimateserver.model.User;
import com.example.estimateserver.service.UserService;
import com.example.estimateserver.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static final String KEY_ERROR = "error";
    private static final String KEY_IS_VALID = "isValid";

    private final UserService userService;
    private final WebSocketService webSocketService;

    public UserController(UserService userService, WebSocketService webSocketService) {
        this.userService = userService;
        this.webSocketService = webSocketService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, String>> sendCode(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (isBlank(phoneNumber)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Phone number is required"));
        }

        try {
            userService.sendVerificationCode(phoneNumber);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to send code to {}", maskPhone(phoneNumber), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Failed to send verification code"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        String code = request.get("code");
        String deviceId = request.get("deviceId");

        if (isBlank(phoneNumber)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Phone number is required"));
        }
        if (isBlank(code)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Code is required"));
        }

        try {
            User user = userService.verifyCode(phoneNumber, code, deviceId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return handleVerifyException(e);
        } catch (Exception e) {
            log.error("Unexpected error in verify for {}", maskPhone(phoneNumber), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Internal server error"));
        }
    }

    private ResponseEntity<Map<String, String>> handleVerifyException(RuntimeException e) {
        String message = e.getMessage();

        if ("Account already in use on another device".equals(message)) {
            log.warn("Login rejected: {}", message);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_ERROR, message));
        }

        log.debug("Verify failed: {}", message);
        return ResponseEntity.badRequest()
                .body(Map.of(KEY_ERROR, message != null ? message : "Verification failed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-User-Id") String userId) {
        userService.logout(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/session/check")
    public ResponseEntity<Map<String, Boolean>> checkSession(@RequestHeader("X-User-Id") String userId) {
        boolean isValid = userService.findById(userId)
                .map(u -> u.getActiveSessionId() != null)
                .orElse(false);
        return ResponseEntity.ok(Map.of(KEY_IS_VALID, isValid));
    }

    @GetMapping("/session/check-with-device")
    public ResponseEntity<Map<String, Boolean>> checkSessionWithDevice(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("deviceId") String deviceId) {

        log.debug("Session check with device: userId={}, deviceId={}", userId, deviceId);

        Optional<User> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            log.debug("Session check: user not found: {}", userId);
            return ResponseEntity.ok(Map.of(KEY_IS_VALID, false));
        }

        String activeSessionId = userOpt.get().getActiveSessionId();
        boolean hasWebSocket = webSocketService.hasSession(userId);
        boolean isValid = activeSessionId != null
                && activeSessionId.equals(deviceId)
                && hasWebSocket;

        log.debug("Session check result: isValid={}, deviceMatches={}, hasWebSocket={}",
                isValid,
                activeSessionId != null && activeSessionId.equals(deviceId),
                hasWebSocket);

        return ResponseEntity.ok(Map.of(KEY_IS_VALID, isValid));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Server is working!");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}