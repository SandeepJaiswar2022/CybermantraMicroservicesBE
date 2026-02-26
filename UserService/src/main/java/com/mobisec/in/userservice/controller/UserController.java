package com.mobisec.in.userservice.controller;

// ============================================================================
// controller/UserController.java
// ============================================================================

import com.mobisec.in.userservice.entity.UserProfile;
import com.mobisec.in.userservice.service.UserService.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getUserById(@PathVariable UUID id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<UserProfile>> getAllUsers() {
        List<UserProfile> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
}