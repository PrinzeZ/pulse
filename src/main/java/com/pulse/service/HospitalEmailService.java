package com.pulse.service;

import com.pulse.model.HospitalRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class HospitalEmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public HospitalEmailService(
            JavaMailSender mailSender,
            @Value("${pulse.mail.from}") String from,
            @Value("${pulse.app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    public void sendVerificationEmail(HospitalRegistration registration, String code) {
        String link = baseUrl + "/hospital/verify?token=" + registration.getVerificationToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(registration.getHospitalEmail());
        message.setSubject("P.U.L.S.E — Verify your hospital registration");
        message.setText(
                "Hello " + registration.getAdminName() + ",\n\n" +
                "A hospital registration was submitted to P.U.L.S.E using this email address.\n\n" +
                "Hospital: " + registration.getHospitalName() + "\n" +
                "Administrator username: " + registration.getAdminUsername() + "\n\n" +
                "If you submitted this registration, verify your email using either method below:\n\n" +
                "1. Click this verification link:\n" + link + "\n\n" +
                "2. Or enter this verification code on the P.U.L.S.E verification page:\n" + code + "\n\n" +
                "The verification code expires in 15 minutes. The verification link is valid for this registration.\n\n" +
                "If you did not request this registration, you can ignore this email. Do not share this code or link with anyone.\n\n" +
                "P.U.L.S.E\nMedicine Availability & Inventory System");
        mailSender.send(message);
    }

    public void sendActivationEmail(HospitalRegistration registration, String activationToken) {
        String link = baseUrl + "/hospital/setup?token=" + activationToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(registration.getHospitalEmail());
        message.setSubject("P.U.L.S.E — Hospital administrator account approved");
        message.setText(
                "Hello " + registration.getAdminName() + ",\n\n" +
                "Your hospital registration has been approved by the district administrator.\n\n" +
                "Hospital: " + registration.getHospitalName() + "\n" +
                "Username: " + registration.getAdminUsername() + "\n\n" +
                "Your P.U.L.S.E hospital administrator account is ready.\n" +
                "For security, no password has been sent by email. Use the secure activation link below to create your own password:\n\n" +
                link + "\n\n" +
                "This activation link expires in 24 hours and can only be used once.\n\n" +
                "If you did not submit this registration, contact your P.U.L.S.E administrator immediately.\n\n" +
                "P.U.L.S.E\nMedicine Availability & Inventory System");
        mailSender.send(message);
    }
}
