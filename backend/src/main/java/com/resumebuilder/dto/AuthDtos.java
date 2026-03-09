package com.resumebuilder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

public class AuthDtos {

    @Data
    public static class SignupRequest {
        @NotBlank private String fullName;
        @Email @NotBlank private String email;
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String email;
        private String fullName;
        private boolean profileComplete;

        public AuthResponse(String token, String email, String fullName, boolean profileComplete) {
            this.token = token;
            this.email = email;
            this.fullName = fullName;
            this.profileComplete = profileComplete;
        }
    }

    @Data
    public static class MessageResponse {
        private String message;
        private boolean success;

        public MessageResponse(String message, boolean success) {
            this.message = message;
            this.success = success;
        }
    }

    @Data
    public static class ProfileRequest {
        private String profileJson;
    }

    @Data
    public static class AtsScoreResponse {
        private int atsScore;
        private String scoreLabel;
        private int matchedSkills;
        private int totalSkills;
        private List<String> matchedKeywords;
        private List<String> missingKeywords;
        private ScoringBreakdown scoringBreakdown;

        @Data
        public static class ScoringBreakdown {
            private int keywordMatch;       // weighted 40%
            private int candidateFit;       // weighted 25%
            private int resumeCompleteness; // weighted 20%
            private int keywordDensity;     // weighted 15%
            private String notes;
        }
    }
}