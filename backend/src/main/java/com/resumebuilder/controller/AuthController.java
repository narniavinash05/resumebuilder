package com.resumebuilder.controller;

import com.resumebuilder.dto.AuthDtos.*;
import com.resumebuilder.service.AtsScoreService;
import com.resumebuilder.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AtsScoreService atsScoreService;

    public AuthController(AuthService authService, AtsScoreService atsScoreService) {
        this.authService = authService;
        this.atsScoreService = atsScoreService;
    }

    // ── POST /api/auth/signup ────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            MessageResponse response = authService.signup(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── GET /api/auth/verify?token=...&email=... ─────────────────────────
    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String email,
                                          @RequestParam String token) {
        try {
            MessageResponse response = authService.verifyEmail(email, token);
            // Redirect to frontend login page after verification
            return ResponseEntity.status(302)
                    .header("Location", "http://localhost:3000?verified=true")
                    .build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── POST /api/auth/profile ───────────────────────────────────────────
    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestBody Map<String, String> body) {
        try {
            String profileJson = body.get("profileJson");
            authService.saveProfile(userDetails.getUsername(), profileJson);
            return ResponseEntity.ok(new MessageResponse("Profile saved successfully", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }

    // ── GET /api/auth/profile ────────────────────────────────────────────
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

    // ── POST /api/auth/ats-score ─────────────────────────────────────────
    @PostMapping("/ats-score")
    public ResponseEntity<?> calculateAtsScore(@AuthenticationPrincipal UserDetails userDetails,
                                                @RequestBody Map<String, String> body) {
        try {
            String profileJson = authService.getProfile(userDetails.getUsername());
            String jobDescription = body.get("jobDescription");
            AtsScoreResponse score = atsScoreService.calculateScore(profileJson, jobDescription);
            return ResponseEntity.ok(score);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false));
        }
    }
}
