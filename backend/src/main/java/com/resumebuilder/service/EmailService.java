package com.resumebuilder.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Value("${sendgrid.api.key}")
    private String apiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${app.backend-url}")
    private String backendUrl;

    @Value("${app.base-url}")
    private String baseUrl;

    private void sendEmail(String toEmail, String subject, String htmlContent) {

        Email from = new Email(fromEmail);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);

        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 400) {
                throw new RuntimeException("SendGrid error: " + response.getBody());
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendVerificationEmail(String toEmail, String token) {

        String verifyLink = backendUrl + "/api/auth/verify?token=" + token + "&email=" + toEmail;

        String subject = "Verify your RésuméAI account";

        String html =
                "<h2>Welcome to RésuméAI</h2>" +
                        "<p>Please verify your email by clicking the button below:</p>" +
                        "<p><a href=\"" + verifyLink + "\" style=\"padding:10px 20px;background:#4CAF50;color:white;text-decoration:none;border-radius:5px;\">Verify Email</a></p>" +
                        "<p>If you did not create this account, you can ignore this email.</p>";

        sendEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {

        // ✅ Use query param (?page=reset-password) instead of path (/reset-password)
        // so React SPA always loads index.html and can read the params correctly.
        String resetLink = baseUrl + "/?page=reset-password&token=" + token + "&email=" + toEmail;

        String subject = "Reset your RésuméAI password";

        String html =
                "<div style='font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px;'>" +
                        "<h2 style='color:#1a1a2e;'>Password Reset</h2>" +
                        "<p>You requested to reset your password. Click the button below:</p>" +
                        "<p style='margin:28px 0;'>" +
                        "<a href='" + resetLink + "' " +
                        "style='display:inline-block;padding:14px 28px;background:#e8c547;color:#0a0a0f;" +
                        "text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;'>" +
                        "Reset Password</a></p>" +
                        "<p style='color:#888;font-size:13px;'>Or copy this link into your browser:</p>" +
                        "<p style='word-break:break-all;font-size:12px;color:#555;'>" + resetLink + "</p>" +
                        "<p style='color:#aaa;font-size:12px;margin-top:24px;'>This link expires in 1 hour. " +
                        "If you did not request this, ignore this email.</p>" +
                        "</div>";

        sendEmail(toEmail, subject, html);
    }
}