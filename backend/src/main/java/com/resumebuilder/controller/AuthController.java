package com.resumebuilder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.dto.AuthDtos.*;
import com.resumebuilder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService    authService;
    private final ObjectMapper   objectMapper;

    public AuthController(AuthService authService, ObjectMapper objectMapper) {
        this.authService  = authService;
        this.objectMapper = objectMapper;
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
                                         @RequestBody Map<String, String> body) {
        try {
            authService.saveProfile(userDetails.getUsername(), body.get("profileJson"));
            return ResponseEntity.ok(new MessageResponse("Profile saved successfully", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── GET /api/auth/profile ─────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String profileJson = authService.getProfile(userDetails.getUsername());
            if (profileJson == null) {
                return ResponseEntity.ok(Map.of("profileComplete", false, "profile", (Object) null));
            }
            return ResponseEntity.ok(Map.of("profileComplete", true, "profile", profileJson));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/ats-score ──────────────────────────────────────────────
    // NOTE: This endpoint is now a lightweight profile-preview scorer.
    // The accurate post-generation ATS score is returned by:
    //   POST /api/resume/tailor-generate-score
    //
    // This endpoint reads the saved profile's skill lists and returns an
    // estimated keyword coverage for display before the user generates a resume.
    // No hardcoded keywords — it simply returns the profile's skills as a hint.
    @PostMapping("/ats-score")
    public ResponseEntity<?> previewAtsScore(@AuthenticationPrincipal UserDetails userDetails,
                                             @RequestBody Map<String, String> body) {
        try {
            String profileJson    = authService.getProfile(userDetails.getUsername());
            String jobDescription = body.get("jobDescription");

            List<String> profileSkills = extractSkillsFromProfile(profileJson);

            // Return the profile skills so the frontend can display them as a
            // rough "skills you bring to this JD" preview — not a scored comparison.
            AtsScoreResponse preview = new AtsScoreResponse();
            preview.setAtsScore(0);           // 0 signals "not yet scored"
            preview.setScoreLabel("Pending"); // scored after generation
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

    private List<String> extractSkillsFromProfile(String profileJson) {
        List<String> skills = new ArrayList<>();
        if (profileJson == null || profileJson.isBlank()) return skills;
        try {
            JsonNode root = objectMapper.readTree(profileJson);
            if (root.has("skills")) {
                root.get("skills").forEach(s -> {
                    String v = s.asText().trim();
                    if (!v.isBlank()) skills.add(v);
                });
            }
            if (root.has("skillCategories")) {
                root.get("skillCategories").forEach(cat -> {
                    if (cat.has("skills")) {
                        cat.get("skills").forEach(s -> {
                            String v = s.asText().trim();
                            if (!v.isBlank()) skills.add(v);
                        });
                    }
                });
            }
        } catch (Exception ignored) {}
        return skills;
    }
}