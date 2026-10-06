package com.example.estimateserver.controller;

import com.example.estimateserver.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserLookupController {

    private static final Logger log = LoggerFactory.getLogger(UserLookupController.class);

    private final UserService userService;

    public UserLookupController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(
            @RequestParam("phone") String phone) {
        log.debug("checkUserExists: phone={}", maskPhone(phone));
        boolean exists = userService.findByPhoneNumber(phone).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
