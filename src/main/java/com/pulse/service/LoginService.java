package com.pulse.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.pulse.exception.InvalidLoginException;
import com.pulse.model.User;
import com.pulse.repository.UserRepository;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidLoginException("User not found: " + username));
        if (!encoder.matches(password, user.getPassword())) {
            throw new InvalidLoginException("Wrong password");
        }
        return user;
    }
}