package edu.lms.service;

import jakarta.persistence.*;

@Embeddable
public class Guardian {

    @Column(name = "guardian_name")
    private String name;
    @Column(name = "guardian_relation")
    private String relation;
    @Column(name = "guardian_email")
    private String email;
    @Column(name = "guardian_phone")
    private String phone;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
