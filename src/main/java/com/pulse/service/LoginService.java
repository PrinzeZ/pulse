package com.pulse.service;
//again note all the packages and classes imported
import com.pulse.exception.InvalidLoginException;
import com.pulse.model.Admin;
import com.pulse.model.PharmacyStaff;
import com.pulse.model.User;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    // v1: hardcoded users for Monday's demo hopefully bruh
    // Sanu replaces with UserRepository lookup later ( DONT FORGET )
    private static final PharmacyStaff DEMO_STAFF = new PharmacyStaff(1L, "Staff Kozhikode", "staff", "staff123", 1L); // using random places for now
    private static final Admin DEMO_ADMIN = new Admin(2L, "District Admin", "admin", "admin123");
    // 1l and 2L basically treats the overloaded constructor to take long over int basically 1L = 000000... etc 1 64 bits
    public User authenticate(String username, String password) {
        if (DEMO_STAFF.getUsername().equals(username)) {
            if (DEMO_STAFF.login(password)) 
                return DEMO_STAFF;
            throw new InvalidLoginException("Wrong password");
        }
        if (DEMO_ADMIN.getUsername().equals(username)) {
            if (DEMO_ADMIN.login(password)) 
                return DEMO_ADMIN;
            throw new InvalidLoginException("Wrong password");
        }
        throw new InvalidLoginException("User not found: " + username);
    }
}