package com.pulse.model;
public abstract class User {
    protected Long userId;
    protected String name;
    protected String username;
    protected String password;

    public User() {

    }

    public User(Long userId, String name, String username, String password){
        this.userId = userId;
        this.name = name;
        this.username = username ;
        this.password = password;


    }
    // login - verification piece 
    public boolean login(String inputPas) {
        return this.password != null && this.password.equals(inputPas);

    }
    // for logout 
    public void logout(){
        System.out.println(name + " have been logged out.");

    }
    public Long getUserId() {return userId;}
    public void setUserId(Long userId) {this.userId = userId; }
    public String getName() { return name ; }
    public void setName (String name) {this.name = name;}
    public String getUsername () { return username;} 
    public void setUsername(String username) {this.username = username ;}
    public String getPassword() {return password;}
    public void ssetPassword(String password) { this.password = password; } 

}