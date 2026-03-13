package com.resumebuilder.controller;

import com.resumebuilder.dto.AuthDtos.*;
import com.resumebuilder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.base-url}")
    private String baseUrl;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── POST /api/auth/signup ─────────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            return ResponseEntity.ok(authService.signup(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── GET /api/auth/verify?token=...&email=... ──────────────────────────────
    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String email,
                                         @RequestParam String token) {
        try {
            authService.verifyEmail(email, token);

            return ResponseEntity.status(302)
                    .header("Location", baseUrl + "?verified=true")
                    .build();

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/forgot-password ───────────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(new MessageResponse("Email is required", false));
            }
            authService.processForgotPassword(email);
            // Always return 200 even if email not found (security best practice)
            return ResponseEntity.ok(new MessageResponse("If that email exists, a reset link has been sent", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/reset-password ────────────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        try {
            String email    = body.get("email");
            String token    = body.get("token");
            String password = body.get("password");

            if (email == null || token == null || password == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Email, token, and password are required", false));
            }
            authService.resetPassword(email, token, password);
            return ResponseEntity.ok(new MessageResponse("Password reset successful", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── GET /api/auth/profile ─────────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> profile = authService.getProfile(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/profile ────────────────────────────────────────────────
    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@AuthenticationPrincipal UserDetails userDetails,
                                         @RequestBody Map<String, Object> profileJson) {
        try {

            authService.saveProfile(userDetails.getUsername(), profileJson);

            return ResponseEntity.ok(new MessageResponse("Profile saved successfully", true));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/ats-score ──────────────────────────────────────────────
    // Lightweight ATS preview using profile skills
    @PostMapping("/ats-score")
    public ResponseEntity<?> previewAtsScore(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody Map<String, String> body) {

        try {

            Map<String, Object> profile = authService.getProfile(userDetails.getUsername());
            String jobDescription = body.get("jobDescription");

            List<String> profileSkills = extractSkillsFromProfile(profile);

            AtsScoreResponse preview = new AtsScoreResponse();
            preview.setAtsScore(0);
            preview.setScoreLabel("Pending");
            preview.setMatchedSkills(profileSkills.size());
            preview.setTotalSkills(profileSkills.size());
            preview.setMatchedKeywords(profileSkills);
            preview.setMissingKeywords(new ArrayList<>());

            return ResponseEntity.ok(preview);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> extractSkillsFromProfile(Map<String, Object> profile) {

        Set<String> skills = new LinkedHashSet<>();

        if (profile == null) {
            return new ArrayList<>();
        }

        // Direct skills list
        Object skillsObj = profile.get("skills");

        if (skillsObj instanceof List<?> skillList) {
            for (Object skill : skillList) {
                if (skill != null) {
                    String v = skill.toString().trim();
                    if (!v.isBlank()) {
                        skills.add(v);
                    }
                }
            }
        }

        // Skill categories
        Object categoriesObj = profile.get("skillCategories");

        if (categoriesObj instanceof List<?> categories) {

            for (Object cat : categories) {

                if (cat instanceof Map<?, ?> categoryMap) {

                    Object catSkills = categoryMap.get("skills");

                    if (catSkills instanceof List<?> skillList) {

                        for (Object skill : skillList) {
                            if (skill != null) {
                                String v = skill.toString().trim();
                                if (!v.isBlank()) {
                                    skills.add(v);
                                }
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(skills);
    }
}
