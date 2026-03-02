package com.resumebuilder.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.backend-url}")
    private String backendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verifyLink = backendUrl + "/api/auth/verify?token=" + token + "&email=" + toEmail;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Verify your RésuméAI account");
        message.setText(
            "Hello!\n\n" +
            "Thank you for signing up with RésuméAI.\n\n" +
            "Please verify your email by clicking the link below:\n\n" +
            verifyLink + "\n\n" +
            "This link will remain active. If you did not create an account, please ignore this email.\n\n" +
            "Best regards,\nThe RésuméAI Team"
        );

        mailSender.send(message);
    }
}
