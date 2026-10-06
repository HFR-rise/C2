package com.example.estimateserver.controller;

import com.example.estimateserver.dto.ContactSnapshot;
import com.example.estimateserver.model.Contact;
import com.example.estimateserver.model.ContactMethod;
import com.example.estimateserver.service.AccessDeniedException;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@CrossOrigin(origins = "*")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final SyncService syncService;

    public ContactController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping
    public ResponseEntity<List<Contact>> getAllContacts(@RequestHeader("X-User-Id") String userId) {
        List<Contact> contacts = syncService.getContactsForUser(userId);
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/{contactId}/methods")
    public ResponseEntity<List<ContactMethod>> getContactMethods(
            @PathVariable String contactId,
            @RequestHeader("X-User-Id") String userId) {
        if (!syncService.isContactOwner(contactId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<ContactMethod> methods = syncService.getContactMethods(contactId);
        return ResponseEntity.ok(methods);
    }

    @GetMapping("/search/by-phone")
    public ResponseEntity<List<Contact>> searchContactsByPhone(@RequestParam String phone,
                                                               @RequestHeader("X-User-Id") String userId) {
        List<Contact> contacts = syncService.searchContactsByPhoneForUser(phone, userId);
        return ResponseEntity.ok(contacts);
    }

    @PostMapping
    public ResponseEntity<Contact> createContact(@RequestBody Contact contact,
                                                 @RequestHeader("X-User-Id") String userId) {
        Contact created = syncService.addContact(contact, userId);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> updateContact(@PathVariable String id,
                                                 @RequestBody Contact contact,
                                                 @RequestHeader("X-User-Id") String userId) {
        contact.setId(id);
        Contact updated = syncService.updateContact(contact, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        syncService.deleteContact(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/methods")
    public ResponseEntity<ContactMethod> addContactMethod(@RequestBody ContactMethod method,
                                                          @RequestHeader("X-User-Id") String userId) {
        ContactMethod created = syncService.addContactMethod(method, userId);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<?> syncContact(@PathVariable String id,
                                         @RequestBody ContactSnapshot snapshot,
                                         @RequestHeader("X-User-Id") String userId) {
        try {
            Contact result = syncService.applyContactSnapshotAndSync(id, snapshot, userId);
            log.info("Contact synced: id={}, user={}", result.getId(), userId);
            return ResponseEntity.ok(result);

        } catch (AccessDeniedException e) {
            log.warn("Access denied syncing contact {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (SyncService.ContactNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Bad request syncing contact {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error syncing contact {}: {}", id, e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }
}