package edu.lms.service;

import jakarta.persistence.*;

@Embeddable
public class Preferences {

    @Column(name = "pref_timezone", nullable = false)
    private String timezone;
    @Column(name = "pref_notifications", nullable = false)
    private String notifications;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getNotifications() {
        return notifications;
    }

    public void setNotifications(String notifications) {
        this.notifications = notifications;
    }
}

