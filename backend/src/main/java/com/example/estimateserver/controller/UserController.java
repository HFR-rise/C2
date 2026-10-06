package com.example.estimateserver.controller;

import com.example.estimateserver.dto.UserDto;
import com.example.estimateserver.model.User;
import com.example.estimateserver.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static final String KEY_ERROR = "error";
    private static final String KEY_IS_VALID = "isValid";
    private static final String HEADER_USER_ID = "X-User-Id";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, String>> sendCode(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        log.info("sendCode: request, phone={}", maskPhone(phoneNumber));

        if (isBlank(phoneNumber)) {
            log.warn("sendCode: blank phone number");
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Phone number is required"));
        }

        try {
            userService.sendVerificationCode(phoneNumber);
            log.info("sendCode: OK, phone={}", maskPhone(phoneNumber));
            return ResponseEntity.ok().build();
        } catch (UserService.InvalidPhoneException e) {
            log.warn("sendCode: invalid phone, phone={}, reason={}",
                    maskPhone(phoneNumber), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("sendCode: FAILED, phone={}", maskPhone(phoneNumber), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Failed to send verification code"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        String code = request.get("code");
        String deviceId = request.get("deviceId");

        log.info("verify: request, phone={}, deviceId={}, codeLength={}",
                maskPhone(phoneNumber), deviceId, code != null ? code.length() : 0);

        if (isBlank(phoneNumber)) {
            log.warn("verify: blank phone");
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Phone number is required"));
        }
        if (isBlank(code)) {
            log.warn("verify: blank code, phone={}", maskPhone(phoneNumber));
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Code is required"));
        }

        try {
            User user = userService.verifyCode(phoneNumber, code, deviceId);

            log.info("verify: SUCCESS, userId={}, phone={}, activeSessionId={}",
                    user.getId(), maskPhone(phoneNumber), user.getActiveSessionId());

            return ResponseEntity.ok(UserDto.from(user));

        } catch (UserService.InvalidPhoneException e) {
            log.warn("verify: invalid phone, phone={}, reason={}",
                    maskPhone(phoneNumber), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, e.getMessage()));

        } catch (UserService.InvalidCodeException e) {
            log.warn("verify: invalid code, phone={}, reason={}",
                    maskPhone(phoneNumber), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, e.getMessage()));

        } catch (UserService.AccountInUseException e) {
            log.warn("verify: account in use, phone={}, reason={}",
                    maskPhone(phoneNumber), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_ERROR, e.getMessage()));

        } catch (Exception e) {
            log.error("verify: UNEXPECTED ERROR, phone={}", maskPhone(phoneNumber), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_ERROR, "Internal server error"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HEADER_USER_ID) String userId) {
        log.info("logout: userId={}", userId);
        userService.logout(userId);
        log.info("logout: OK, userId={}", userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/session/check-with-device", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> checkSessionWithDevice(
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestParam("deviceId") String deviceId) {

        log.info(">>> checkSessionWithDevice CALLED: userId={}, deviceId={}", userId, deviceId);

        try {
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn(">>> checkSessionWithDevice: user not found, userId={}", userId);
                return ResponseEntity.ok(Map.of(KEY_IS_VALID, false));
            }

            String activeSessionId = userOpt.get().getActiveSessionId();
            boolean isValid = activeSessionId != null && activeSessionId.equals(deviceId);

            if (isValid) {
                log.info(">>> checkSessionWithDevice: VALID, userId={}, deviceId={}",
                        userId, deviceId);
            } else {
                log.warn(">>> checkSessionWithDevice: INVALID, userId={}, expected={}, got={}",
                        userId, activeSessionId, deviceId);
            }

            return ResponseEntity.ok(Map.of(KEY_IS_VALID, isValid));

        } catch (Exception e) {
            log.error(">>> checkSessionWithDevice: EXCEPTION, userId={}, deviceId={}",
                    userId, deviceId, e);
            throw e;
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUser(
            @PathVariable String userId,
            @RequestHeader(HEADER_USER_ID) String requesterId) {

        log.info("getUser: requesterId={}, targetUserId={}", requesterId, userId);

        if (!requesterId.equals(userId)) {
            log.warn("getUser: ACCESS DENIED, requester={}, target={}",
                    requesterId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(KEY_ERROR, "Access denied"));
        }

        return userService.findById(userId)
                .map(u -> {
                    log.info("getUser: OK, userId={}", userId);
                    return ResponseEntity.ok(UserDto.from(u));
                })
                .orElseGet(() -> {
                    log.warn("getUser: NOT FOUND, userId={}", userId);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.debug("test: ping");
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