package com.example.estimateserver.service;

import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.repository.ProjectMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProjectAccessService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAccessService.class);

    public enum Permission {
        VIEW,
        EDIT_ESTIMATE,
        COMMENT,
        MANAGE_MEMBERS,
        MANAGE_BUILDERS,
        APPROVE,
        DELETE_PROJECT
    }

    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRole> getRole(String projectId, String userId) {
        if (projectId == null || userId == null) return Optional.empty();
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMember::getRole);
    }

    @Transactional(readOnly = true)
    public boolean can(String projectId, String userId, Permission permission) {
        return getRole(projectId, userId)
                .map(role -> roleCan(role, permission))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void require(String projectId, String userId, Permission permission) {
        if (!can(projectId, userId, permission)) {
            log.warn("Access denied: project={}, user={}, permission={}",
                    projectId, userId, permission);
            throw new AccessDeniedException(
                    "Недостаточно прав для действия: " + permission);
        }
    }

    @Transactional(readOnly = true)
    public boolean isCustomer(String projectId, String userId) {
        return getRole(projectId, userId)
                .map(r -> r == ProjectRole.CUSTOMER)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isEstimator(String projectId, String userId) {
        return getRole(projectId, userId)
                .map(r -> r == ProjectRole.ESTIMATOR)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isBuilder(String projectId, String userId) {
        return getRole(projectId, userId)
                .map(r -> r == ProjectRole.BUILDER)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<String> getCustomerUserId(String projectId) {
        return memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.CUSTOMER)
                .map(ProjectMember::getUserId);
    }

    private boolean roleCan(ProjectRole role, Permission permission) {
        return switch (role) {
            case CUSTOMER -> true;
            case ESTIMATOR -> switch (permission) {
                case VIEW, EDIT_ESTIMATE, COMMENT, MANAGE_BUILDERS -> true;
                default -> false;
            };
            case BUILDER -> switch (permission) {
                case VIEW, COMMENT -> true;
                default -> false;
            };
        };
    }
}