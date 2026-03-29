package com.resumebuilder.service;

import com.resumebuilder.dto.AuthDtos.*;
import com.resumebuilder.model.User;
import com.resumebuilder.model.UserProfile;
import com.resumebuilder.repository.UserProfileRepository;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.security.JwtUtil;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
    }

    public MessageResponse signup(SignupRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        user.setVerificationToken(null);

        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());
        } catch (Exception e) {
            System.err.println("Warning: Could not send verification email - " + e.getMessage());
            System.out.println(">>> VERIFY LINK: /api/auth/verify?token=" + user.getVerificationToken() + "&email=" + user.getEmail());
        }

        return new MessageResponse("Account created! Please check your email to verify your account.", true);
    }

    public void verifyEmail(String email, String token) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            return;
        }

        if (!token.equals(user.getVerificationToken())) {
            throw new RuntimeException("Invalid verification token");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

//        if (!user.isEmailVerified()) {
//            throw new RuntimeException("Please verify your email before logging in");
//        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtUtil.generateToken(user.getEmail());

        boolean profileComplete = profileRepository.findByUser(user)
                .map(UserProfile::isProfileComplete)
                .orElse(false);

        return new AuthResponse(token, user.getEmail(), user.getFullName(), profileComplete);
    }

    public MessageResponse saveProfile(String email, Map<String, Object> profileJson) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = profileRepository.findByUser(user)
                .orElseGet(UserProfile::new);

        profile.setUser(user);
        profile.setProfileJson(profileJson);
        profile.setProfileComplete(true);

        profileRepository.save(profile);

        return new MessageResponse("Profile saved successfully", true);
    }

    public Map<String, Object> getProfile(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return profileRepository.findByUser(user)
                .map(UserProfile::getProfileJson)
                .orElse(null);
    }

    public void processForgotPassword(String email) {
        // Silently return if user not found — don't reveal whether email exists
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();

        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(email, token);
    }

    public void resetPassword(String email, String token, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid reset request"));

        if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        if (user.getResetTokenExpiry() == null ||
                LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }
}