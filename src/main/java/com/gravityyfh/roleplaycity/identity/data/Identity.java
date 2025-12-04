package com.gravityyfh.roleplaycity.identity.data;

import java.util.UUID;

public class Identity {
    private final UUID uuid;
    private String firstName;
    private String lastName;
    private String sex; // "Homme" or "Femme"
    private int age;
    private int height; // in cm
    private final long creationDate;
    private String residenceCity; // Ville de résidence

    public Identity(UUID uuid, String firstName, String lastName, String sex, int age, int height, long creationDate) {
        this.uuid = uuid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.sex = sex;
        this.age = age;
        this.height = height;
        this.creationDate = creationDate;
        this.residenceCity = null;
    }

    public Identity(UUID uuid, String firstName, String lastName, String sex, int age, int height, long creationDate, String residenceCity) {
        this.uuid = uuid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.sex = sex;
        this.age = age;
        this.height = height;
        this.creationDate = creationDate;
        this.residenceCity = residenceCity;
    }

    public UUID getUuid() { return uuid; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getSex() { return sex; }
    public int getAge() { return age; }
    public int getHeight() { return height; }
    public long getCreationDate() { return creationDate; }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setSex(String sex) { this.sex = sex; }
    public void setAge(int age) { this.age = age; }
    public void setHeight(int height) { this.height = height; }

    public String getResidenceCity() { return residenceCity; }
    public void setResidenceCity(String residenceCity) { this.residenceCity = residenceCity; }

    public boolean hasResidenceCity() { return residenceCity != null && !residenceCity.isEmpty(); }
}
