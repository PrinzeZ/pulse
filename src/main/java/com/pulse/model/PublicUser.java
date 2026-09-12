package com.pulse.model;

public class PublicUser extends User{

    public PublicUser() {}

    public PublicUser(Long userId, String name) {
        super (userId, name, null, null); // no login for public 


    }
}