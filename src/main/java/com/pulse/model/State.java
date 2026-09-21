package com.pulse.model;

import jakarta.persistence.*;

@Entity
@Table(name = "states")
public class State {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "state_id")
    private Long stateId;

    @Column(nullable = false, unique = true)
    private String name;

    public State() {}

    public Long getStateId() { return stateId; }
    public void setStateId(Long stateId) { this.stateId = stateId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
