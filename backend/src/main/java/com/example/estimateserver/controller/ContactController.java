package com.example.estimateserver.controller;

import com.example.estimateserver.model.Contact;
import com.example.estimateserver.model.ContactMethod;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@CrossOrigin(origins = "*")
public class ContactController {

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
    public ResponseEntity<List<ContactMethod>> getContactMethods(@PathVariable String contactId) {
        List<ContactMethod> methods = syncService.getContactMethods(contactId);
        return ResponseEntity.ok(methods);
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


    @GetMapping("/search/by-phone")
    public ResponseEntity<List<Contact>> searchContactsByPhone(@RequestParam String phone,
                                                               @RequestHeader("X-User-Id") String userId) {
        List<Contact> contacts = syncService.searchContactsByPhoneForUser(phone, userId);
        return ResponseEntity.ok(contacts);
    }
}
