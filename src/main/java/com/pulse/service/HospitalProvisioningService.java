package com.pulse.service;

import com.pulse.model.*;
import com.pulse.repository.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class HospitalProvisioningService {

    private static final int TOKEN_BYTES = 32;

    private final HospitalRegistrationRepository registrations;
    private final HospitalAdminActivationRepository activations;
    private final HospitalRepository hospitals;
    private final UserRepository users;
    private final StateRepository states;
    private final DistrictRepository districts;
    private final BCryptPasswordEncoder passwordEncoder;
    private final HospitalEmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public HospitalProvisioningService(
            HospitalRegistrationRepository registrations,
            HospitalAdminActivationRepository activations,
            HospitalRepository hospitals,
            UserRepository users,
            StateRepository states,
            DistrictRepository districts,
            BCryptPasswordEncoder passwordEncoder,
            HospitalEmailService emailService) {
        this.registrations = registrations;
        this.activations = activations;
        this.hospitals = hospitals;
        this.users = users;
        this.states = states;
        this.districts = districts;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public List<State> states() {
        return states.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    public List<District> districts(Long stateId) {
        return districts.findByStateIdOrderByName(stateId);
    }

    @Transactional
    public HospitalRegistration register(
            String hospitalName,
            String hospitalEmail,
            String adminName,
            String adminUsername,
            Long stateId,
            Long districtId) {

        State state = states.findById(stateId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid state"));

        District district = districts.findById(districtId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid district"));

        if (!district.getStateId().equals(state.getStateId())) {
            throw new IllegalArgumentException("District does not belong to selected state");
        }

        if (users.findByUsername(adminUsername).isPresent()
                || registrations.existsByAdminUsername(adminUsername)) {
            throw new IllegalArgumentException("Username is already in use");
        }

        if (registrations.existsByHospitalEmailAndStatus(
                hospitalEmail, HospitalRegistrationStatus.PENDING_EMAIL)) {
            throw new IllegalArgumentException("A pending registration already exists for this email");
        }

        HospitalRegistration registration = new HospitalRegistration();
        registration.setHospitalName(hospitalName.trim());
        registration.setHospitalEmail(hospitalEmail.trim().toLowerCase());
        registration.setAdminName(adminName.trim());
        registration.setAdminUsername(adminUsername.trim());
        registration.setStateId(state.getStateId());
        registration.setDistrictId(district.getDistrictId());
        registration.setStatus(HospitalRegistrationStatus.PENDING_EMAIL);
        registration.setVerificationToken(token());
        String verificationCode = verificationCode();
        registration.setVerificationCodeHash(passwordEncoder.encode(verificationCode));
        registration.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        registration.setCreatedAt(LocalDateTime.now());

        HospitalRegistration saved = registrations.save(registration);
        emailService.sendVerificationEmail(saved, verificationCode);

        return saved;
    }

    @Transactional
    public HospitalRegistration verifyEmail(String token) {
        HospitalRegistration registration = registrations.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link"));
        return completeEmailVerification(registration);
    }

    @Transactional
    public HospitalRegistration verifyEmailCode(String email, String code) {
        HospitalRegistration registration = registrations.findTopByHospitalEmailOrderByCreatedAtDesc(
                email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No registration was found for this email"));

        if (registration.getStatus() != HospitalRegistrationStatus.PENDING_EMAIL) {
            throw new IllegalArgumentException("This registration has already been processed");
        }

        if (registration.getVerificationCodeExpiresAt() == null
                || registration.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired");
        }

        if (code == null || !passwordEncoder.matches(code.trim(), registration.getVerificationCodeHash())) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        return completeEmailVerification(registration);
    }

    private HospitalRegistration completeEmailVerification(HospitalRegistration registration) {
        if (registration.getStatus() != HospitalRegistrationStatus.PENDING_EMAIL) {
            return registration;
        }

        registration.setStatus(HospitalRegistrationStatus.EMAIL_VERIFIED);
        registration.setVerifiedAt(LocalDateTime.now());
        registration.setVerificationCodeHash(null);
        registration.setVerificationCodeExpiresAt(null);
        return registrations.save(registration);
    }

    public List<HospitalRegistration> registrationsForDistrict(Long districtId) {
        return registrations.findByDistrictIdOrderByCreatedAtDesc(districtId);
    }

    public List<HospitalRegistration> verifiedRegistrationsForDistrict(Long districtId) {
        return registrations.findByDistrictIdAndStatusOrderByCreatedAtAsc(
                districtId, HospitalRegistrationStatus.EMAIL_VERIFIED);
    }

    @Transactional
    public String approve(Long registrationId, Long districtAdminDistrictId) {
        HospitalRegistration registration = registrations.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (!registration.getDistrictId().equals(districtAdminDistrictId)) {
            throw new IllegalArgumentException("Registration is outside your district");
        }

        if (registration.getStatus() != HospitalRegistrationStatus.EMAIL_VERIFIED) {
            throw new IllegalArgumentException("Registration is not email-verified");
        }

        District district = districts.findById(registration.getDistrictId())
                .orElseThrow(() -> new IllegalArgumentException("District not found"));

        Hospital hospital = new Hospital();
        hospital.setName(registration.getHospitalName());
        hospital.setDistrict(district.getName());
        hospital.setDistrictId(district.getDistrictId());
        hospital = hospitals.saveAndFlush(hospital);

        Admin admin = new Admin();
        admin.setName(registration.getAdminName());
        admin.setUsername(registration.getAdminUsername());
        admin.setPassword(passwordEncoder.encode(token()));
        admin.setStateId(registration.getStateId());
        admin.setDistrictId(registration.getDistrictId());
        admin.setHospitalId(hospital.getHospitalId());
        admin = users.saveAndFlush(admin);

        HospitalAdminActivation activation = new HospitalAdminActivation();
        activation.setUserId(admin.getUserId());
        activation.setToken(token());
        activation.setCreatedAt(LocalDateTime.now());
        activation.setExpiresAt(LocalDateTime.now().plusHours(24));
        activations.save(activation);

        registration.setStatus(HospitalRegistrationStatus.PROVISIONED);
        registrations.save(registration);

        emailService.sendActivationEmail(registration, activation.getToken());

        return activation.getToken();
    }

    @Transactional
    public void reject(Long registrationId, Long districtAdminDistrictId) {
        HospitalRegistration registration = registrations.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (!registration.getDistrictId().equals(districtAdminDistrictId)) {
            throw new IllegalArgumentException("Registration is outside your district");
        }

        if (registration.getStatus() != HospitalRegistrationStatus.EMAIL_VERIFIED) {
            throw new IllegalArgumentException("Registration is not email-verified");
        }

        registration.setStatus(HospitalRegistrationStatus.REJECTED);
        registrations.save(registration);
    }

    public HospitalAdminActivation activation(String token) {
        return activations.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid activation token"));
    }

    @Transactional
    public void setInitialPassword(String token, String password, String confirmation) {
        HospitalAdminActivation activation = activation(token);

        if (activation.getUsedAt() != null) {
            throw new IllegalArgumentException("Activation link has already been used");
        }

        if (activation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Activation link has expired");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }

        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = users.findById(activation.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Admin account not found"));

        user.setPassword(passwordEncoder.encode(password));
        users.saveAndFlush(user);

        activation.setUsedAt(LocalDateTime.now());
        activations.save(activation);
    }

    private String verificationCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String token() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
