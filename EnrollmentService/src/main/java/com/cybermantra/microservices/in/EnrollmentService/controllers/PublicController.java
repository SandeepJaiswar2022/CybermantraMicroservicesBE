package com.cybermantra.microservices.in.EnrollmentService.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/enrollment-service")   // ← or "/public", "/api", whatever you want
public class PublicController {

    @GetMapping("/")
    @PreAuthorize("hasRole('STUDENT')")
    public String hello() {
        System.out.println("Hello World!");
        return "Hello Student!";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminController() {
//        System.out.println("Hello Admin!");
        return "Hello Admin!";
    }
}