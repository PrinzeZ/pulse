package com.pulse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AuditNotificationService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String adminEmail;

    public AuditNotificationService(JavaMailSender mailSender,
                                    @Value("${pulse.mail.from:}") String from,
                                    @Value("${PULSE_AUDIT_ADMIN_EMAIL:}") String adminEmail) {
        this.mailSender = mailSender;
        this.from = from;
        this.adminEmail = adminEmail;
    }

    public void sendDailyArchiveNotice(Long hospitalId, LocalDate day, byte[] encryptedArchive, String fileName, int entries) {
        if (adminEmail == null || adminEmail.isBlank() || encryptedArchive == null) return;
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            if (from != null && !from.isBlank()) helper.setFrom(from);
            helper.setTo(adminEmail);
            helper.setSubject("P.U.L.S.E — Daily stock audit sealed — " + day);
            helper.setText("The hospital stock audit for " + day + " has been sealed.\n\n"
                    + "Hospital ID: " + hospitalId + "\n"
                    + "Entries: " + entries + "\n"
                    + "The attached .pulse-audit file is an encrypted archive. It is not a readable spreadsheet and is intended to be opened through P.U.L.S.E after authorization.\n\n"
                    + "P.U.L.S.E");
            helper.addAttachment(fileName, new ByteArrayResource(encryptedArchive));
            mailSender.send(message);
        } catch (RuntimeException ignored) {
            // Audit storage remains authoritative even if SMTP is unavailable.
        } catch (Exception ignored) {
            // Do not make the stock system fail because notification delivery failed.
        }
    }
}
