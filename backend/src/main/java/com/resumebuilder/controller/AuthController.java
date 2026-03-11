package com.resumebuilder.controller;

import com.resumebuilder.dto.AuthDtos.*;
import com.resumebuilder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

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
                    .header("Location", "http://localhost:3000?verified=true")
                    .build();

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
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
