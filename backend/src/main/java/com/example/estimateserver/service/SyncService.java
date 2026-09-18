package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.*;
import com.example.estimateserver.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final ProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final WorkItemRepository workItemRepository;
    private final ContactRepository contactRepository;
    private final ContactMethodRepository contactMethodRepository;
    private final ObjectRepository objectRepository;
    private final UserRepository userRepository;
    private final SharedProjectRepository sharedProjectRepository;
    private final WebSocketService webSocketService;

    public SyncService(ProjectRepository projectRepository,
                       MaterialRepository materialRepository,
                       WorkItemRepository workItemRepository,
                       ContactRepository contactRepository,
                       ContactMethodRepository contactMethodRepository,
                       ObjectRepository objectRepository,
                       UserRepository userRepository,
                       SharedProjectRepository sharedProjectRepository,
                       WebSocketService webSocketService) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.workItemRepository = workItemRepository;
        this.contactRepository = contactRepository;
        this.contactMethodRepository = contactMethodRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.sharedProjectRepository = sharedProjectRepository;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public Project createProject(Project project, String userId) {
        project.setUserId(userId);
        project.setCreatedBy(userId);
        project.setCreatedAt(new Date());
        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(userId);

        Project saved = projectRepository.save(project);

        sendToUser(userId, "CREATE", "PROJECT", saved.getId(), saved, saved.getVersion());
        shareProjectWithContacts(saved, userId);

        log.info("Project created: {} by user {}", saved.getId(), userId);
        return saved;
    }

    @Transactional
    public Project updateProject(Project project, String userId) {
        Project oldProject = projectRepository.findById(project.getId()).orElse(null);
        if (oldProject == null) {
            return createProject(project, userId);
        }

        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(userId);
        project.setVersion(oldProject.getVersion());
        project.setUserId(oldProject.getUserId());
        project.setCreatedAt(oldProject.getCreatedAt());
        project.setCreatedBy(oldProject.getCreatedBy());

        Project saved = projectRepository.save(project);

        notifyProjectParticipants(saved.getId(), "UPDATE", "PROJECT", saved, userId);
        notifyNewContacts(oldProject, saved, userId);

        log.info("Project updated: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Project saveProject(Project project, String userId) {
        Project oldProject = projectRepository.findById(project.getId()).orElse(null);

        if (oldProject == null) {
            return createProject(project, userId);
        }

        project.setUserId(oldProject.getUserId());
        project.setCreatedAt(oldProject.getCreatedAt());
        project.setCreatedBy(oldProject.getCreatedBy());
        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(userId);
        project.setVersion(oldProject.getVersion());

        Project saved = projectRepository.save(project);
        notifyProjectParticipants(saved.getId(), "UPDATE", "PROJECT", saved, userId);
        return saved;
    }

    @Transactional
    public void deleteProject(String projectId, String userId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        if (!project.getUserId().equals(userId)) {
            throw new RuntimeException("Only owner can delete project");
        }
        deleteProjectInternal(projectId, userId, true);
    }

    private void deleteProjectInternal(String projectId, String userId, boolean notifyParticipants) {
        List<String> affectedUserIds = notifyParticipants
                ? findUsersWithAccessToProject(projectId)
                : Collections.emptyList();

        long materialsDeleted = materialRepository.deleteByProjectId(projectId);
        long worksDeleted = workItemRepository.deleteByProjectId(projectId);
        long sharesDeleted = sharedProjectRepository.deleteByProjectId(projectId);

        projectRepository.deleteById(projectId);

        if (notifyParticipants) {
            for (String affectedUserId : affectedUserIds) {
                sendToUser(affectedUserId, "DELETE", "PROJECT", projectId, null, null);
            }
        }

        log.info("Project {} deleted (materials: {}, works: {}, shares: {})",
                projectId, materialsDeleted, worksDeleted, sharesDeleted);
    }

    public List<Project> getProjects() {
        return projectRepository.findAll();
    }

    public Optional<Project> getProject(String id) {
        return projectRepository.findById(id);
    }

    public List<Project> getProjectsForUser(String userId) {
        return projectRepository.findAllAccessibleForUser(userId);
    }

    public List<Project> getProjectsSharedWithUser(String userId) {
        return projectRepository.findSharedWithUser(userId);
    }

    @Transactional
    public void shareProjectWithUser(String projectId, String targetUserPhone, String sharedByUserId) {
        Project originalProject = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!originalProject.getUserId().equals(sharedByUserId)) {
            throw new RuntimeException("You can only share your own projects");
        }

        User targetUser = userRepository.findByPhoneNumber(targetUserPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Project savedCopy = createSharedProjectCopy(originalProject, targetUser, sharedByUserId);
        copyMaterialsAndWorks(projectId, savedCopy.getId(), targetUser.getId());

        SharedProject shared = new SharedProject(projectId, targetUser.getId(), sharedByUserId, "READ");
        shared.setStatus(ShareStatus.ACCEPTED);
        shared.setRespondedAt(new Date());
        sharedProjectRepository.save(shared);

        sendToUser(targetUser.getId(), "CREATE", "PROJECT", savedCopy.getId(), savedCopy, 0L);

        log.info("Project {} shared with user {} (copy id: {})",
                projectId, targetUser.getId(), savedCopy.getId());
    }

    private Project createSharedProjectCopy(Project original, User targetUser, String sharedByUserId) {
        Project copy = new Project();
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setObjectId(null);
        copy.setUserId(targetUser.getId());
        copy.setCreatedBy(sharedByUserId);
        copy.setCreatedAt(new Date());
        copy.setUpdatedAt(new Date());
        copy.setStatus("ACTIVE");
        copy.setTotalBudget(original.getTotalBudget());
        copy.setTotalSpent(0.0);
        copy.setCustomerContactId(original.getCustomerContactId());
        copy.setForemanContactId(original.getForemanContactId());
        copy.setManagerContactId(original.getManagerContactId());
        copy.setIncludeForeman(original.isIncludeForeman());
        copy.setIncludeManager(original.isIncludeManager());
        return projectRepository.save(copy);
    }

    private void copyMaterialsAndWorks(String sourceProjectId, String targetProjectId, String targetUserId) {
        List<Material> materials = materialRepository.findByProjectId(sourceProjectId);
        if (!materials.isEmpty()) {
            List<Material> copies = materials.stream()
                    .map(m -> copyMaterial(m, targetProjectId, targetUserId))
                    .collect(Collectors.toList());
            materialRepository.saveAll(copies);
        }

        List<WorkItem> workItems = workItemRepository.findByProjectId(sourceProjectId);
        if (!workItems.isEmpty()) {
            List<WorkItem> copies = workItems.stream()
                    .map(w -> copyWorkItem(w, targetProjectId, targetUserId))
                    .collect(Collectors.toList());
            workItemRepository.saveAll(copies);
        }

        log.debug("Copied {} materials and {} works to project {}",
                materials.size(), workItems.size(), targetProjectId);
    }

    private Material copyMaterial(Material src, String targetProjectId, String targetUserId) {
        Material copy = new Material();
        copy.setProjectId(targetProjectId);
        copy.setName(src.getName());
        copy.setQuantity(src.getQuantity());
        copy.setUnit(src.getUnit());
        copy.setUnitPrice(src.getUnitPrice());
        copy.setCategory(src.getCategory());
        copy.setNotes(src.getNotes());
        copy.setUserId(targetUserId);
        return copy;
    }

    private WorkItem copyWorkItem(WorkItem src, String targetProjectId, String targetUserId) {
        WorkItem copy = new WorkItem();
        copy.setProjectId(targetProjectId);
        copy.setName(src.getName());
        copy.setStage(src.getStage());
        copy.setLaborHours(src.getLaborHours());
        copy.setHourlyRate(src.getHourlyRate());
        copy.setMaterialCost(src.getMaterialCost());
        copy.setNotes(src.getNotes());
        copy.setUserId(targetUserId);
        return copy;
    }

    public List<SharedProject> getPendingSharesForUser(String userId) {
        return sharedProjectRepository.findBySharedWithUserIdAndStatus(userId, ShareStatus.PENDING);
    }

    public List<Project> getPendingProjectsForUser(String userId) {
        List<String> ids = sharedProjectRepository
                .findSharedProjectIdsByUserAndStatus(userId, ShareStatus.PENDING);
        if (ids.isEmpty()) return Collections.emptyList();
        return projectRepository.findAllById(ids);
    }

    @Transactional
    public void acceptShare(String projectId, String userId) {
        sharedProjectRepository.findByProjectIdAndSharedWithUserId(projectId, userId)
                .ifPresent(share -> {
                    share.setStatus(ShareStatus.ACCEPTED);
                    share.setRespondedAt(new Date());
                    sharedProjectRepository.save(share);
                    sendToUser(share.getSharedByUserId(), "SHARE_ACCEPTED", "PROJECT", projectId, null, null);
                });
    }

    @Transactional
    public void declineShare(String projectId, String userId) {
        sharedProjectRepository.findByProjectIdAndSharedWithUserId(projectId, userId)
                .ifPresent(share -> {
                    share.setStatus(ShareStatus.DECLINED);
                    share.setRespondedAt(new Date());
                    sharedProjectRepository.save(share);
                    sendToUser(share.getSharedByUserId(), "SHARE_DECLINED", "PROJECT", projectId, null, null);
                });
    }

    @Transactional
    public Material addMaterial(Material material, String userId) {
        requireProjectAccess(material.getProjectId(), userId);

        material.setUserId(userId);
        Material saved = materialRepository.save(material);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public Material updateMaterial(Material material, String userId) {
        requireProjectAccess(material.getProjectId(), userId);

        Material saved = materialRepository.save(material);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteMaterial(String materialId, String userId) {
        Material material = materialRepository.findById(materialId).orElse(null);
        if (material == null) return;

        requireProjectAccess(material.getProjectId(), userId);

        String projectId = material.getProjectId();
        materialRepository.deleteById(materialId);

        notifyProjectParticipants(projectId, "DELETE", "MATERIAL", materialId, userId);
        updateProjectTotal(projectId);
    }

    public List<Material> getMaterials(String projectId) {
        return materialRepository.findByProjectId(projectId);
    }

    public WorkItem getWorkItemById(String id) {
        return workItemRepository.findById(id).orElse(null);
    }

    @Transactional
    public WorkItem addWorkItem(WorkItem workItem, String userId) {
        requireProjectAccess(workItem.getProjectId(), userId);

        workItem.setUserId(userId);
        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public WorkItem updateWorkItem(WorkItem workItem, String userId) {
        requireProjectAccess(workItem.getProjectId(), userId);

        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteWorkItem(String workItemId, String userId) {
        WorkItem workItem = workItemRepository.findById(workItemId).orElse(null);
        if (workItem == null) return;

        requireProjectAccess(workItem.getProjectId(), userId);

        String projectId = workItem.getProjectId();
        workItemRepository.deleteById(workItemId);

        notifyProjectParticipants(projectId, "DELETE", "WORK_ITEM", workItemId, userId);
        updateProjectTotal(projectId);
    }

    public List<WorkItem> getWorkItems(String projectId) {
        return workItemRepository.findByProjectId(projectId);
    }

    @Transactional
    public WorkItem markWorkItemAsCompleted(String workItemId, String userId) {
        WorkItem workItem = workItemRepository.findById(workItemId).orElse(null);
        if (workItem == null) return null;

        requireProjectAccess(workItem.getProjectId(), userId);

        workItem.setIsCompleted(true);
        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    public List<Contact> searchContactsByPhoneForUser(String phone, String userId) {
        String normalizedPhone = normalizePhoneNumber(phone);
        if (normalizedPhone == null || normalizedPhone.isEmpty()) {
            return Collections.emptyList();
        }
        return contactRepository.findByUserIdAndPhoneNumber(userId, normalizedPhone);
    }

    @Transactional
    public Contact addContact(Contact contact, String userId) {
        contact.setUserId(userId);
        Contact saved = contactRepository.save(contact);

        sendToUser(userId, "CREATE", "CONTACT", saved.getId(), saved, null);
        return saved;
    }

    @Transactional
    public Contact updateContact(Contact contact, String userId) {
        Contact existing = contactRepository.findById(contact.getId()).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        contact.setUserId(userId);
        Contact saved = contactRepository.save(contact);

        sendToUser(userId, "UPDATE", "CONTACT", saved.getId(), saved, null);
        return saved;
    }

    @Transactional
    public void deleteContact(String contactId, String userId) {
        Contact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null || !contact.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        long methodsDeleted = contactMethodRepository.deleteByContactId(contactId);
        contactRepository.deleteById(contactId);

        sendToUser(userId, "DELETE", "CONTACT", contactId, null, null);

        log.info("Contact {} deleted (methods: {})", contactId, methodsDeleted);
    }

    public List<Contact> getContactsForUser(String userId) {
        return contactRepository.findByUserId(userId);
    }

    @Transactional
    public ContactMethod addContactMethod(ContactMethod method, String userId) {
        Contact contact = contactRepository.findById(method.getContactId()).orElse(null);
        if (contact == null || !contact.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        method.setUserId(userId);
        ContactMethod saved = contactMethodRepository.save(method);

        sendToUser(userId, "CREATE", "CONTACT_METHOD", saved.getId(), saved, null);
        return saved;
    }

    public List<ContactMethod> getContactMethods(String contactId) {
        return contactMethodRepository.findByContactId(contactId);
    }

    public List<ObjectModel> getRootObjectsForUser(String userId) {
        return objectRepository.findByParentObjectIdIsNullAndUserId(userId);
    }

    public List<ObjectModel> getChildObjectsForUser(String parentId, String userId) {
        return objectRepository.findByParentObjectIdAndUserId(parentId, userId);
    }

    public List<ObjectModel> getRootObjects() {
        return objectRepository.findByParentObjectIdIsNull();
    }

    public List<ObjectModel> getChildObjects(String parentId) {
        return objectRepository.findByParentObjectId(parentId);
    }

    public ObjectModel getObjectById(String id) {
        return objectRepository.findById(id).orElse(null);
    }

    @Transactional
    public ObjectModel createObject(ObjectModel object, String userId) {
        object.setUserId(userId);
        ObjectModel saved = objectRepository.save(object);

        log.info("Object created: {} (parent: {})", saved.getId(), saved.getParentObjectId());
        return saved;
    }

    @Transactional
    public ObjectModel updateObject(ObjectModel object, String userId) {
        ObjectModel existing = objectRepository.findById(object.getId()).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        object.setUserId(userId);
        ObjectModel saved = objectRepository.save(object);

        sendToUser(userId, "UPDATE", "OBJECT", saved.getId(), saved, null);
        return saved;
    }

    @Transactional
    public void deleteObject(String objectId, String userId) {
        ObjectModel root = objectRepository.findById(objectId).orElse(null);
        if (root == null || !root.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        List<ObjectModel> allObjects = new ArrayList<>();
        allObjects.add(root);
        collectChildObjectsRecursive(objectId, userId, allObjects);

        List<String> objectIds = allObjects.stream()
                .map(ObjectModel::getId)
                .collect(Collectors.toList());

        List<Project> projectsToDelete = projectRepository.findAllByObjectIdIn(objectIds);

        for (Project p : projectsToDelete) {
            deleteProjectInternal(p.getId(), userId, false);
        }

        objectRepository.deleteAllInBatch(allObjects);

        sendToUser(userId, "DELETE", "OBJECT", objectId, null, null);

        log.info("Object cascade deleted: {} (sub-objects: {}, projects: {})",
                objectId, allObjects.size() - 1, projectsToDelete.size());
    }

    private void collectChildObjectsRecursive(String parentId, String userId, List<ObjectModel> acc) {
        List<ObjectModel> children = objectRepository.findByParentObjectIdAndUserId(parentId, userId);
        for (ObjectModel child : children) {
            acc.add(child);
            collectChildObjectsRecursive(child.getId(), userId, acc);
        }
    }

    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> getUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    public boolean hasAccessToProject(String projectId, String userId) {
        return projectRepository.hasUserAccessToProject(projectId, userId);
    }

    private void requireProjectAccess(String projectId, String userId) {
        if (!hasAccessToProject(projectId, userId)) {
            throw new RuntimeException("Access denied");
        }
    }

    private void sendToUser(String userId, String type, String entityType,
                            String entityId, Object data, Long version) {
        SyncMessage message = new SyncMessage(
                type, entityType, entityId, data,
                userId, new Date(), version
        );
        webSocketService.sendToUser(userId, message);
    }

    private void shareProjectWithContacts(Project project, String senderId) {
        for (String phoneNumber : getContactPhones(project)) {
            userRepository.findByPhoneNumber(phoneNumber)
                    .filter(user -> !user.getId().equals(senderId))
                    .ifPresent(user -> {
                        if (!sharedProjectRepository.existsByProjectIdAndSharedWithUserId(
                                project.getId(), user.getId())) {
                            sharedProjectRepository.save(new SharedProject(
                                    project.getId(), user.getId(), senderId, "READ"));
                        }
                        sendToUser(user.getId(), "SHARE", "PROJECT", project.getId(), project,
                                project.getVersion() != null ? project.getVersion() : 0L);
                    });
        }
    }

    private void notifyNewContacts(Project oldProject, Project newProject, String senderId) {
        Set<String> oldPhones = getContactPhones(oldProject);
        Set<String> newPhones = getContactPhones(newProject);
        newPhones.removeAll(oldPhones);

        for (String phoneNumber : newPhones) {
            userRepository.findByPhoneNumber(phoneNumber)
                    .filter(user -> !user.getId().equals(senderId))
                    .ifPresent(user -> {
                        if (!sharedProjectRepository.existsByProjectIdAndSharedWithUserId(
                                newProject.getId(), user.getId())) {
                            sharedProjectRepository.save(new SharedProject(
                                    newProject.getId(), user.getId(), senderId, "READ"));
                        }
                        sendToUser(user.getId(), "SHARE", "PROJECT", newProject.getId(),
                                newProject, newProject.getVersion());
                    });
        }
    }

    private Set<String> getContactPhones(Project project) {
        Set<String> phones = new HashSet<>();
        addPhoneFromContact(project.getCustomerContactId(), phones);
        addPhoneFromContact(project.getForemanContactId(), phones);
        addPhoneFromContact(project.getManagerContactId(), phones);
        return phones;
    }

    private void addPhoneFromContact(String contactId, Set<String> phones) {
        if (contactId == null) return;

        for (ContactMethod method : contactMethodRepository.findByContactId(contactId)) {
            String type = method.getMethodType().toLowerCase();
            if (type.contains("???????") || type.contains("phone")) {
                String phone = normalizePhoneNumber(method.getValue());
                if (phone != null && !phone.isEmpty()) {
                    phones.add(phone);
                }
            }
        }
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return null;

        if (digits.startsWith("8") && digits.length() == 11) {
            return "7" + digits.substring(1);
        }
        if (digits.startsWith("7") && digits.length() == 11) {
            return digits;
        }
        if (digits.length() == 10) {
            return "7" + digits;
        }
        if (digits.length() > 11) {
            String last11 = digits.substring(digits.length() - 11);
            if (last11.startsWith("7") || last11.startsWith("8")) {
                return normalizePhoneNumber(last11);
            }
            return "7" + last11;
        }
        return digits;
    }

    private List<String> findUsersWithAccessToProject(String projectId) {
        Set<String> userIds = new HashSet<>();
        projectRepository.findById(projectId)
                .map(Project::getUserId)
                .ifPresent(userIds::add);

        sharedProjectRepository.findByProjectId(projectId)
                .forEach(share -> userIds.add(share.getSharedWithUserId()));

        return new ArrayList<>(userIds);
    }

    private void notifyProjectParticipants(String projectId, String type, String entityType,
                                           Object data, String senderId) {
        String entityId = resolveEntityId(data);

        SyncMessage message = new SyncMessage(
                type, entityType, entityId, data, senderId, new Date(), null);

        for (String userId : findUsersWithAccessToProject(projectId)) {
            if (!userId.equals(senderId)) {
                webSocketService.sendToUser(userId, message);
            }
        }
    }

    private String resolveEntityId(Object data) {
        if (data == null) return null;
        if (data instanceof String s) return s;
        if (data instanceof Project p) return p.getId();
        if (data instanceof Material m) return m.getId();
        if (data instanceof WorkItem w) return w.getId();
        if (data instanceof Contact c) return c.getId();
        if (data instanceof ObjectModel o) return o.getId();
        return null;
    }

    private void updateProjectTotal(String projectId) {
        Double materialCost = materialRepository.sumCostByProject(projectId);
        Double workCost = workItemRepository.sumCostByProject(projectId);

        double total = (materialCost != null ? materialCost : 0.0)
                + (workCost != null ? workCost : 0.0);

        projectRepository.updateTotalBudget(projectId, total);
    }
}