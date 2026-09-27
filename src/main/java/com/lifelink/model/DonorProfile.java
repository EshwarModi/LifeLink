package com.lifelink.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "donor_profiles")
public class DonorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "blood_group", nullable = false)
    private String bloodGroup;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String gender;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    public DonorProfile() {}

    public DonorProfile(Long id, User user, String bloodGroup, Integer age, String gender, Boolean isAvailable, LocalDate lastDonationDate) {
        this.id = id;
        this.user = user;
        this.bloodGroup = bloodGroup;
        this.age = age;
        this.gender = gender;
        this.isAvailable = isAvailable != null ? isAvailable : true;
        this.lastDonationDate = lastDonationDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }

    // Builder
    public static DonorProfileBuilder builder() { return new DonorProfileBuilder(); }

    public static class DonorProfileBuilder {
        private Long id;
        private User user;
        private String bloodGroup;
        private Integer age;
        private String gender;
        private Boolean isAvailable = true;
        private LocalDate lastDonationDate;

        public DonorProfileBuilder id(Long id) { this.id = id; return this; }
        public DonorProfileBuilder user(User user) { this.user = user; return this; }
        public DonorProfileBuilder bloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; return this; }
        public DonorProfileBuilder age(Integer age) { this.age = age; return this; }
        public DonorProfileBuilder gender(String gender) { this.gender = gender; return this; }
        public DonorProfileBuilder isAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; return this; }
        public DonorProfileBuilder lastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; return this; }

        public DonorProfile build() {
            return new DonorProfile(id, user, bloodGroup, age, gender, isAvailable, lastDonationDate);
        }
    }
}
