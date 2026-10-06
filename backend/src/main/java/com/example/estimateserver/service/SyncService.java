package com.example.estimateserver.service;

import com.example.estimateserver.dto.ContactSnapshot;
import com.example.estimateserver.dto.ObjectSnapshot;
import com.example.estimateserver.dto.ProjectSnapshot;
import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.Contact;
import com.example.estimateserver.model.ContactMethod;
import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.ObjectModel;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectState;
import com.example.estimateserver.model.User;
import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.repository.ContactMethodRepository;
import com.example.estimateserver.repository.ContactRepository;
import com.example.estimateserver.repository.MaterialRepository;
import com.example.estimateserver.repository.ObjectRepository;
import com.example.estimateserver.repository.ProjectRepository;
import com.example.estimateserver.repository.UserRepository;
import com.example.estimateserver.repository.WorkItemRepository;
import com.example.estimateserver.utils.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final WebSocketService webSocketService;
    private final ProjectMemberService projectMemberService;
    private final ProjectAccessService accessService;
    private final ChangeRequestService changeRequestService;
    private final SnapshotApplier snapshotApplier;

    public SyncService(ProjectRepository projectRepository,
                       MaterialRepository materialRepository,
                       WorkItemRepository workItemRepository,
                       ContactRepository contactRepository,
                       ContactMethodRepository contactMethodRepository,
                       ObjectRepository objectRepository,
                       UserRepository userRepository,
                       WebSocketService webSocketService,
                       ProjectMemberService projectMemberService,
                       ProjectAccessService accessService,
                       @Lazy ChangeRequestService changeRequestService,
                       SnapshotApplier snapshotApplier) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.workItemRepository = workItemRepository;
        this.contactRepository = contactRepository;
        this.contactMethodRepository = contactMethodRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        this.projectMemberService = projectMemberService;
        this.accessService = accessService;
        this.changeRequestService = changeRequestService;
        this.snapshotApplier = snapshotApplier;
    }

    @Transactional
    public Project createProject(Project incoming, String userId) {
        Project project = new Project();

        String projectId = incoming.getId();
        if (projectId == null || projectId.isBlank()) {
            projectId = UUID.randomUUID().toString();
        }
        project.setId(projectId);

        project.setName(incoming.getName());
        project.setDescription(incoming.getDescription());
        project.setObjectId(incoming.getObjectId());

        project.setCreatedBy(userId);
        project.setLastModifiedBy(userId);
        project.setState(ProjectState.DRAFT);
        project.setHasPendingChanges(false);
        project.setTotalBudget(0.0);
        project.setTotalSpent(0.0);
        project.setStatus("ACTIVE");

        Project saved = projectRepository.save(project);

        projectMemberService.bootstrapCustomer(saved.getId(), userId, userId);

        sendToUser(userId, "CREATE", "PROJECT", saved.getId(), saved, saved.getVersion());

        log.info("Project created: {} by user {}", saved.getId(), userId);
        return saved;
    }

    @Transactional
    public Project updateProject(Project project, String userId) {
        Project oldProject = projectRepository.findById(project.getId()).orElse(null);
        if (oldProject == null) {
            return createProject(project, userId);
        }

        applyProjectFields(oldProject, project);
        oldProject.setUpdatedAt(new Date());
        oldProject.setLastModifiedBy(userId);

        notifyProjectParticipants(oldProject.getId(), "UPDATE", "PROJECT", oldProject, userId);

        log.info("Project updated: {}", oldProject.getId());
        return oldProject;
    }

    @Transactional
    public Project saveProject(Project project, String userId) {
        Project oldProject = projectRepository.findById(project.getId()).orElse(null);

        if (oldProject == null) {
            return createProject(project, userId);
        }

        applyProjectFields(oldProject, project);
        oldProject.setUpdatedAt(new Date());
        oldProject.setLastModifiedBy(userId);

        notifyProjectParticipants(oldProject.getId(), "UPDATE", "PROJECT", oldProject, userId);
        return oldProject;
    }

    private void applyProjectFields(Project target, Project source) {
        if (source.getName() != null) {
            target.setName(source.getName());
        }
        if (source.getDescription() != null) {
            target.setDescription(source.getDescription());
        }
        if (source.getObjectId() != null) {
            target.setObjectId(source.getObjectId().isBlank() ? null : source.getObjectId());
        }
    }

    @Transactional
    public Project applySnapshotAndSync(String projectId,
                                        ProjectSnapshot snapshot,
                                        String userId) {
        Project project = projectRepository.findById(projectId).orElse(null);

        if (project == null) {
            log.info("applySnapshotAndSync: creating project {} from snapshot, user={}",
                    projectId, userId);

            project = new Project();
            project.setId(projectId);
            project.setCreatedBy(userId);
            project.setLastModifiedBy(userId);
            project.setState(ProjectState.DRAFT);
            project.setHasPendingChanges(false);
            project.setTotalBudget(0.0);
            project.setTotalSpent(0.0);
            project.setStatus("ACTIVE");

            if (snapshot.getName() != null && !snapshot.getName().isBlank()) {
                project.setName(snapshot.getName());
            } else {
                project.setName("Смета");
            }
            if (snapshot.getDescription() != null) {
                project.setDescription(snapshot.getDescription());
            }
            if (snapshot.getObjectId() != null) {
                project.setObjectId(snapshot.getObjectId().isBlank()
                        ? null
                        : snapshot.getObjectId());
            }

            Project saved = projectRepository.save(project);

            projectMemberService.bootstrapCustomer(projectId, userId, userId);

            project = saved;
        } else {
            accessService.require(projectId, userId,
                    ProjectAccessService.Permission.EDIT_ESTIMATE);
        }

        snapshotApplier.apply(project, snapshot, userId);

        notifyProjectParticipants(projectId, "UPDATE", "PROJECT", project, userId);

        log.info("Project synced via snapshot: {} by user {}", projectId, userId);
        return project;
    }

    @Transactional
    public void deleteProject(String projectId, String userId) {
        if (!projectRepository.existsById(projectId)) return;

        boolean isCustomer = projectMemberService.getCustomerUserId(projectId)
                .map(cid -> cid.equals(userId))
                .orElse(false);

        if (!isCustomer) {
            throw new AccessDeniedException("Only customer can delete project");
        }

        deleteProjectInternal(projectId, userId, true);
    }

    private void deleteProjectInternal(String projectId, String userId, boolean notifyParticipants) {
        List<String> affectedUserIds = notifyParticipants
                ? findUsersWithAccessToProject(projectId)
                : Collections.emptyList();

        long changeRequestsDeleted = changeRequestService.deleteAllForProject(projectId);
        long membersDeleted = projectMemberService.deleteAllForProject(projectId);
        long materialsDeleted = materialRepository.deleteByProjectId(projectId);
        long worksDeleted = workItemRepository.deleteByProjectId(projectId);

        projectRepository.deleteById(projectId);

        if (notifyParticipants) {
            for (String affectedUserId : affectedUserIds) {
                sendToUser(affectedUserId, "DELETE", "PROJECT", projectId, null, null);
            }
        }

        log.info("Project {} deleted (materials: {}, works: {}, members: {}, changeRequests: {})",
                projectId, materialsDeleted, worksDeleted, membersDeleted, changeRequestsDeleted);
    }

    public List<Project> getProjects() {
        return projectRepository.findAll();
    }

    public Material getMaterialById(String id) {
        return materialRepository.findById(id).orElse(null);
    }

    public Optional<Project> getProject(String id) {
        return projectRepository.findById(id);
    }

    public List<Project> getProjectsForUser(String userId) {
        return projectRepository.findAllAccessibleForUser(userId);
    }

    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(String message) { super(message); }
    }
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) { super(message); }
    }
    public static class ObjectNotFoundException extends RuntimeException {
        public ObjectNotFoundException(String message) { super(message); }
    }
    public static class ContactNotFoundException extends RuntimeException {
        public ContactNotFoundException(String message) { super(message); }
    }

    @Transactional
    public Material addMaterial(Material material, String userId) {
        accessService.require(material.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        material.setUserId(userId);
        Material saved = materialRepository.save(material);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public Material updateMaterial(Material material, String userId) {
        accessService.require(material.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        Material existing = materialRepository.findById(material.getId()).orElse(null);
        if (existing == null) {
            material.setUserId(userId);
            Material saved = materialRepository.save(material);
            notifyProjectParticipants(saved.getProjectId(), "UPDATE", "MATERIAL", saved, userId);
            updateProjectTotal(saved.getProjectId());
            return saved;
        }

        applyMaterialFields(existing, material);

        Material saved = materialRepository.save(existing);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    private void applyMaterialFields(Material target, Material source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getQuantity() != null) target.setQuantity(source.getQuantity());
        if (source.getUnit() != null) target.setUnit(source.getUnit());
        if (source.getUnitPrice() != null) target.setUnitPrice(source.getUnitPrice());
        if (source.getCategory() != null) target.setCategory(source.getCategory());
        if (source.getNotes() != null) target.setNotes(source.getNotes());
    }

    @Transactional
    public void deleteMaterial(String materialId, String userId) {
        Material material = materialRepository.findById(materialId).orElse(null);
        if (material == null) return;

        accessService.require(material.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

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
        accessService.require(workItem.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        workItem.setUserId(userId);
        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public WorkItem updateWorkItem(WorkItem workItem, String userId) {
        accessService.require(workItem.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        WorkItem existing = workItemRepository.findById(workItem.getId()).orElse(null);
        if (existing == null) {
            workItem.setUserId(userId);
            WorkItem saved = workItemRepository.save(workItem);
            notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
            updateProjectTotal(saved.getProjectId());
            return saved;
        }

        applyWorkItemFields(existing, workItem);

        WorkItem saved = workItemRepository.save(existing);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    private void applyWorkItemFields(WorkItem target, WorkItem source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getStage() != null) target.setStage(source.getStage());
        if (source.getLaborHours() != null) target.setLaborHours(source.getLaborHours());
        if (source.getHourlyRate() != null) target.setHourlyRate(source.getHourlyRate());
        if (source.getMaterialCost() != null) target.setMaterialCost(source.getMaterialCost());
        if (source.getIsCompleted() != null) target.setIsCompleted(source.getIsCompleted());
        if (source.getNotes() != null) target.setNotes(source.getNotes());
    }

    @Transactional
    public void deleteWorkItem(String workItemId, String userId) {
        WorkItem workItem = workItemRepository.findById(workItemId).orElse(null);
        if (workItem == null) return;

        accessService.require(workItem.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

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

        accessService.require(workItem.getProjectId(), userId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        workItem.setIsCompleted(true);
        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
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
            throw new AccessDeniedException("Access denied");
        }

        if (contact.getName() != null) existing.setName(contact.getName());
        if (contact.getDescription() != null) existing.setDescription(contact.getDescription());

        sendToUser(userId, "UPDATE", "CONTACT", existing.getId(), existing, null);
        return existing;
    }

    @Transactional
    public void deleteContact(String contactId, String userId) {
        Contact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null || !contact.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
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
            throw new AccessDeniedException("Access denied");
        }

        method.setUserId(userId);
        ContactMethod saved = contactMethodRepository.save(method);

        sendToUser(userId, "CREATE", "CONTACT_METHOD", saved.getId(), saved, null);
        return saved;
    }

    public List<ContactMethod> getContactMethods(String contactId) {
        return contactMethodRepository.findByContactId(contactId);
    }

    @Transactional
    public Contact applyContactSnapshotAndSync(String contactId,
                                               ContactSnapshot snapshot,
                                               String userId) {
        Contact contact = contactRepository.findById(contactId).orElse(null);

        if (contact == null) {
            contact = new Contact();
            contact.setId(contactId);
            contact.setUserId(userId);
            contact.setDescription("");
        } else {
            if (!userId.equals(contact.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if (snapshot.getName() != null && !snapshot.getName().isBlank()) {
            contact.setName(snapshot.getName());
        }
        if (snapshot.getDescription() != null) {
            contact.setDescription(snapshot.getDescription());
        }

        Contact saved = contactRepository.save(contact);

        if (snapshot.getMethods() != null) {
            contactMethodRepository.deleteByContactId(contactId);

            if (!snapshot.getMethods().isEmpty()) {
                List<ContactMethod> methods = new ArrayList<>(snapshot.getMethods().size());
                for (ContactSnapshot.ContactMethodSnapshot s : snapshot.getMethods()) {
                    ContactMethod m = new ContactMethod();
                    m.setId(s.getId() != null ? s.getId() : UUID.randomUUID().toString());
                    m.setContactId(contactId);
                    m.setMethodType(s.getMethodType());
                    m.setValue(s.getValue());
                    m.setUserId(userId);
                    methods.add(m);
                }
                contactMethodRepository.saveAll(methods);
            }
        }

        sendToUser(userId, "UPDATE", "CONTACT", saved.getId(), saved, saved.getVersion());
        log.info("Contact synced via snapshot: {} by user {}", contactId, userId);

        return saved;
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
            throw new AccessDeniedException("Access denied");
        }

        if (object.getName() != null) existing.setName(object.getName());
        if (object.getStreet() != null) existing.setStreet(object.getStreet());
        if (object.getHouse() != null) existing.setHouse(object.getHouse());
        if (object.getBuilding() != null) existing.setBuilding(object.getBuilding());
        if (object.getDescription() != null) existing.setDescription(object.getDescription());
        if (object.getParentObjectId() != null) existing.setParentObjectId(object.getParentObjectId());

        ObjectModel saved = objectRepository.save(existing);
        sendToUser(userId, "UPDATE", "OBJECT", saved.getId(), saved, null);
        return saved;
    }

    @Transactional
    public ObjectModel applyObjectSnapshotAndSync(String objectId,
                                                  ObjectSnapshot snapshot,
                                                  String userId) {
        ObjectModel object = objectRepository.findById(objectId).orElse(null);

        if (object == null) {
            object = new ObjectModel();
            object.setId(objectId);
            object.setUserId(userId);
        } else {
            if (!userId.equals(object.getUserId())) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if (snapshot.getName() != null && !snapshot.getName().isBlank()) {
            object.setName(snapshot.getName());
        }
        if (snapshot.getStreet() != null) {
            object.setStreet(snapshot.getStreet());
        }
        if (snapshot.getHouse() != null) {
            object.setHouse(snapshot.getHouse());
        }
        if (snapshot.getBuilding() != null) {
            object.setBuilding(snapshot.getBuilding());
        }
        if (snapshot.getDescription() != null) {
            object.setDescription(snapshot.getDescription());
        }
        if (snapshot.getParentObjectId() != null) {
            object.setParentObjectId(snapshot.getParentObjectId().isBlank()
                    ? null
                    : snapshot.getParentObjectId());
        }

        ObjectModel saved = objectRepository.save(object);

        sendToUser(userId, "UPDATE", "OBJECT", saved.getId(), saved, saved.getVersion());
        log.info("Object synced via snapshot: {} by user {}", objectId, userId);

        return saved;
    }

    @Transactional
    public void deleteObject(String objectId, String userId) {
        ObjectModel root = objectRepository.findById(objectId).orElse(null);
        if (root == null || !root.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
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

    public List<Contact> searchContactsByPhoneForUser(String rawPhone, String userId) {
        String normalized = PhoneUtils.normalize(rawPhone);
        if (normalized == null) return Collections.emptyList();
        return contactRepository.findByUserIdAndPhoneNumber(userId, normalized);
    }

    public boolean isContactOwner(String contactId, String userId) {
        return contactRepository.findById(contactId)
                .map(c -> userId.equals(c.getUserId()))
                .orElse(false);
    }

    private void sendToUser(String userId, String type, String entityType,
                            String entityId, Object data, Long version) {
        SyncMessage message = new SyncMessage(
                type, entityType, entityId, data,
                userId, new Date(), version
        );

        webSocketService.sendToUserOrQueue(userId, message);
    }

    private List<String> findUsersWithAccessToProject(String projectId) {
        return projectMemberService.getMemberUserIds(projectId);
    }

    private void notifyProjectParticipants(String projectId, String type, String entityType,
                                           Object data, String senderId) {
        String entityId = resolveEntityId(data);

        SyncMessage message = new SyncMessage(
                type, entityType, entityId, data, senderId, new Date(), null);

        for (String userId : findUsersWithAccessToProject(projectId)) {
            if (userId.equals(senderId)) continue;

            webSocketService.sendToUserOrQueue(userId, message);
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