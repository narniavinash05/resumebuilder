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
        user.setEmailVerified(false);
        user.setVerificationToken(UUID.randomUUID().toString());

        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());
        } catch (Exception e) {
            // Log but don't fail signup if mail fails
            System.err.println("Warning: Could not send verification email - " + e.getMessage());
            System.out.println(">>> VERIFY LINK: /api/auth/verify?token=" + user.getVerificationToken() + "&email=" + user.getEmail());
        }

        return new MessageResponse("Account created! Please check your email to verify your account.", true);
    }

    public MessageResponse verifyEmail(String email, String token) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            return new MessageResponse("Email already verified. You can login.", true);
        }

        if (!token.equals(user.getVerificationToken())) {
            throw new RuntimeException("Invalid verification token");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        return new MessageResponse("Email verified successfully! You can now login.", true);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email before logging in");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtUtil.generateToken(user.getEmail());

        boolean profileComplete = profileRepository.findByUser(user)
                .map(UserProfile::isProfileComplete)
                .orElse(false);

        return new AuthResponse(token, user.getEmail(), user.getFullName(), profileComplete);
    }

    public MessageResponse saveProfile(String email, String profileJson) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = profileRepository.findByUser(user)
                .orElse(new UserProfile());

        profile.setUser(user);
        profile.setProfileJson(profileJson);
        profile.setProfileComplete(true);
        profileRepository.save(profile);

        return new MessageResponse("Profile saved successfully", true);
    }

    public String getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return profileRepository.findByUser(user)
                .map(UserProfile::getProfileJson)
                .orElse(null);
    }
}
