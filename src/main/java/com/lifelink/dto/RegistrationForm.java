package com.lifelink.dto;

import com.lifelink.model.enums.UserType;
import jakarta.validation.constraints.*;

public class RegistrationForm {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @NotNull(message = "User role is required")
    private UserType userType;

    // Donor Specific Fields
    private String bloodGroup;

    @Min(value = 18, message = "Donor age must be at least 18")
    @Max(value = 65, message = "Donor age cannot exceed 65")
    private Integer age;

    private String gender;

    private Boolean isAvailable = true;

    public RegistrationForm() {}

    public RegistrationForm(String email, String password, String fullName, String phone, UserType userType, String bloodGroup, Integer age, String gender, Boolean isAvailable) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.phone = phone;
        this.userType = userType;
        this.bloodGroup = bloodGroup;
        this.age = age;
        this.gender = gender;
        this.isAvailable = isAvailable != null ? isAvailable : true;
    }

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    // Builder
    public static RegistrationFormBuilder builder() { return new RegistrationFormBuilder(); }

    public static class RegistrationFormBuilder {
        private String email;
        private String password;
        private String fullName;
        private String phone;
        private UserType userType;
        private String bloodGroup;
        private Integer age;
        private String gender;
        private Boolean isAvailable = true;

        public RegistrationFormBuilder email(String email) { this.email = email; return this; }
        public RegistrationFormBuilder password(String password) { this.password = password; return this; }
        public RegistrationFormBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public RegistrationFormBuilder phone(String phone) { this.phone = phone; return this; }
        public RegistrationFormBuilder userType(UserType userType) { this.userType = userType; return this; }
        public RegistrationFormBuilder bloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; return this; }
        public RegistrationFormBuilder age(Integer age) { this.age = age; return this; }
        public RegistrationFormBuilder gender(String gender) { this.gender = gender; return this; }
        public RegistrationFormBuilder isAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; return this; }

        public RegistrationForm build() {
            return new RegistrationForm(email, password, fullName, phone, userType, bloodGroup, age, gender, isAvailable);
        }
    }
}
