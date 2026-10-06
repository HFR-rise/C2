package com.example.estimateserver.controller;

import com.example.estimateserver.dto.ChangeRoleRequest;
import com.example.estimateserver.dto.ProjectMemberDto;
import com.example.estimateserver.dto.ProjectMemberRequest;
import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.User;
import com.example.estimateserver.service.AccessDeniedException;
import com.example.estimateserver.service.MemberAlreadyExistsException;
import com.example.estimateserver.service.MemberLimitException;
import com.example.estimateserver.service.MemberNotFoundException;
import com.example.estimateserver.service.ProjectMemberService;
import com.example.estimateserver.service.SyncService;
import com.example.estimateserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemberController.class);

    private static final String HEADER_USER_ID = "X-User-Id";

    private final ProjectMemberService memberService;
    private final UserRepository userRepository;

    public ProjectMemberController(ProjectMemberService memberService,
                                   UserRepository userRepository) {
        this.memberService = memberService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProjectMemberDto>> getMembers(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        List<ProjectMember> members = memberService.getMembers(projectId, userId);
        List<ProjectMemberDto> result = new ArrayList<>(members.size());

        for (ProjectMember m : members) {
            result.add(toDto(m));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMyRole(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        return memberService.getMyRole(projectId, userId)
                .map(role -> ResponseEntity.ok(Map.of("role", role.name())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "not_a_member")));
    }

    @PostMapping("/customer")
    public ResponseEntity<ProjectMemberDto> setCustomer(
            @PathVariable String projectId,
            @RequestBody ProjectMemberRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        requirePhone(request.getPhoneNumber());

        ProjectMember member = memberService.setCustomer(
                projectId, request.getPhoneNumber(), userId, false);

        return ResponseEntity.ok(toDto(member));
    }

    @PostMapping("/estimator")
    public ResponseEntity<ProjectMemberDto> setEstimator(
            @PathVariable String projectId,
            @RequestBody ProjectMemberRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        requirePhone(request.getPhoneNumber());

        ProjectMember member = memberService.setEstimator(
                projectId, request.getPhoneNumber(), userId);

        return ResponseEntity.ok(toDto(member));
    }

    @PostMapping("/builders")
    public ResponseEntity<ProjectMemberService.AddBuildersResult> addBuilders(
            @PathVariable String projectId,
            @RequestBody ProjectMemberRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        if (request.getPhoneNumbers() == null || request.getPhoneNumbers().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ProjectMemberService.AddBuildersResult result =
                memberService.addBuilders(projectId, request.getPhoneNumbers(), userId);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{targetUserId}/role")
    public ResponseEntity<ProjectMemberDto> changeRole(
            @PathVariable String projectId,
            @PathVariable String targetUserId,
            @RequestBody ChangeRoleRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        if (request.getRole() == null) {
            return ResponseEntity.badRequest().build();
        }

        ProjectMember member = memberService.changeRole(
                projectId, targetUserId, request.getRole(), userId);

        return ResponseEntity.ok(toDto(member));
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String projectId,
            @PathVariable String targetUserId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        memberService.removeMember(projectId, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }

    private ProjectMemberDto toDto(ProjectMember m) {
        String phone = null;
        String name = null;
        Optional<User> u = userRepository.findById(m.getUserId());
        if (u.isPresent()) {
            phone = u.get().getPhoneNumber();
            name = u.get().getName();
        }
        return ProjectMemberDto.from(m, phone, name);
    }

    private void requirePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(MemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MemberLimitException.class)
    public ResponseEntity<Map<String, String>> handleLimit(MemberLimitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MemberAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleExists(MemberAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SyncService.ProjectNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProjectNotFound(
            SyncService.ProjectNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
}