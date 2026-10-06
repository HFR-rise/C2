package com.example.estimateserver.controller;

import com.example.estimateserver.dto.ChangeRequestDto;
import com.example.estimateserver.dto.CommentRequest;
import com.example.estimateserver.dto.ProjectSnapshot;
import com.example.estimateserver.dto.RejectChangeRequest;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectChangeRequest;
import com.example.estimateserver.model.User;
import com.example.estimateserver.repository.ProjectRepository;
import com.example.estimateserver.repository.UserRepository;
import com.example.estimateserver.service.AccessDeniedException;
import com.example.estimateserver.service.ChangeRequestService;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChangeRequestController {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestController.class);

    private static final String HEADER_USER_ID = "X-User-Id";

    private final ChangeRequestService changeService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ChangeRequestController(ChangeRequestService changeService,
                                   ProjectRepository projectRepository,
                                   UserRepository userRepository) {
        this.changeService = changeService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/changes/inbox")
    public ResponseEntity<List<ChangeRequestDto>> getInbox(
            @RequestHeader(HEADER_USER_ID) String userId) {

        List<ProjectChangeRequest> changes = changeService.getInboxForUser(userId);
        return ResponseEntity.ok(toDtoList(changes));
    }

    @GetMapping("/projects/{projectId}/changes")
    public ResponseEntity<List<ChangeRequestDto>> getForProject(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        List<ProjectChangeRequest> changes = changeService.getAllForProject(projectId, userId);
        return ResponseEntity.ok(toDtoList(changes));
    }

    @GetMapping("/projects/{projectId}/changes/pending")
    public ResponseEntity<List<ChangeRequestDto>> getPending(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        List<ProjectChangeRequest> changes =
                changeService.getPendingForProject(projectId, userId);
        return ResponseEntity.ok(toDtoList(changes));
    }

    @GetMapping("/projects/{projectId}/changes/pending-estimate")
    public ResponseEntity<ChangeRequestDto> getPendingEstimate(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        return changeService.getPendingEstimateChange(projectId, userId)
                .map(c -> ResponseEntity.ok(toDtoList(List.of(c)).get(0)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/projects/{projectId}/changes/estimate")
    public ResponseEntity<ChangeRequestDto> submitEstimate(
            @PathVariable String projectId,
            @RequestBody ProjectSnapshot snapshot,
            @RequestHeader(HEADER_USER_ID) String userId) {

        ProjectChangeRequest saved = changeService.submitEstimateEdit(
                projectId, snapshot, userId);
        return ResponseEntity.ok(toDtoList(List.of(saved)).get(0));
    }

    @PostMapping("/projects/{projectId}/comments")
    public ResponseEntity<ChangeRequestDto> addComment(
            @PathVariable String projectId,
            @RequestBody CommentRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ProjectChangeRequest saved = changeService.submitComment(
                projectId,
                request.getText(),
                request.getEntityType(),
                request.getEntityId(),
                userId);

        return ResponseEntity.ok(toDtoList(List.of(saved)).get(0));
    }

    @PostMapping("/changes/{changeId}/approve")
    public ResponseEntity<ChangeRequestDto> approve(
            @PathVariable String changeId,
            @RequestBody(required = false) RejectChangeRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        String comment = request != null ? request.getComment() : null;
        ProjectChangeRequest saved = changeService.approveChange(
                changeId, userId, comment);
        return ResponseEntity.ok(toDtoList(List.of(saved)).get(0));
    }

    @PostMapping("/changes/{changeId}/reject")
    public ResponseEntity<ChangeRequestDto> reject(
            @PathVariable String changeId,
            @RequestBody RejectChangeRequest request,
            @RequestHeader(HEADER_USER_ID) String userId) {

        if (request == null || request.getComment() == null
                || request.getComment().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ProjectChangeRequest saved = changeService.rejectChange(
                changeId, request.getComment(), userId);
        return ResponseEntity.ok(toDtoList(List.of(saved)).get(0));
    }

    @DeleteMapping("/changes/{changeId}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String changeId,
            @RequestHeader(HEADER_USER_ID) String userId) {

        changeService.deleteRejectedDraft(changeId, userId);
        return ResponseEntity.noContent().build();
    }

    private List<ChangeRequestDto> toDtoList(List<ProjectChangeRequest> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }

        Set<String> projectIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();

        for (ProjectChangeRequest c : changes) {
            if (c.getProjectId() != null) projectIds.add(c.getProjectId());
            if (c.getAuthorId() != null) userIds.add(c.getAuthorId());
            if (c.getReviewerId() != null) userIds.add(c.getReviewerId());
        }

        Map<String, String> projectNamesById = projectIds.isEmpty()
                ? Map.of()
                : projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        Map<String, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ChangeRequestDto> result = new ArrayList<>(changes.size());
        for (ProjectChangeRequest c : changes) {
            String projectName = projectNamesById.get(c.getProjectId());

            User author = usersById.get(c.getAuthorId());
            String authorName = author != null ? author.getName() : null;
            String authorPhone = author != null ? author.getPhoneNumber() : null;

            String reviewerName = null;
            String reviewerPhone = null;
            if (c.getReviewerId() != null) {
                User reviewer = usersById.get(c.getReviewerId());
                if (reviewer != null) {
                    reviewerName = reviewer.getName();
                    reviewerPhone = reviewer.getPhoneNumber();
                }
            }

            result.add(ChangeRequestDto.from(
                    c, projectName, authorName, authorPhone, reviewerName, reviewerPhone));
        }
        return result;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ChangeRequestService.ChangeRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            ChangeRequestService.ChangeRequestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SyncService.ProjectNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProjectNotFound(
            SyncService.ProjectNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
}