package com.pulse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

@Entity
@DiscriminatorValue("PUBLIC")
public class PublicUser extends User{

    public PublicUser() {}

    public PublicUser(Long userId, String name) {
        super (userId, name, null, null); // no login for public
    }
}