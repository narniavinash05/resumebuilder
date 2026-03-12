package com.resumebuilder.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String home() {
        return "Resume Builder API is running";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}