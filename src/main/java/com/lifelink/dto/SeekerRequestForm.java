package com.lifelink.dto;

import com.lifelink.model.enums.UrgencyLevel;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class SeekerRequestForm {

    @NotBlank(message = "Blood group is required")
    private String bloodGroup;

    @NotNull(message = "Units required is required")
    @Min(value = 1, message = "Units must be at least 1")
    @Max(value = 20, message = "Units cannot exceed 20")
    private Integer units;

    @NotNull(message = "Urgency level is required")
    private UrgencyLevel urgency;

    @NotBlank(message = "Hospital name is required")
    private String hospitalName;

    @NotBlank(message = "Hospital address is required")
    private String hospitalAddress;

    @NotNull(message = "Required-by date is required")
    @Future(message = "Required-by date must be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requiredByDate;

    public SeekerRequestForm() {}

    public SeekerRequestForm(String bloodGroup, Integer units, UrgencyLevel urgency, String hospitalName, String hospitalAddress, LocalDate requiredByDate) {
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.urgency = urgency;
        this.hospitalName = hospitalName;
        this.hospitalAddress = hospitalAddress;
        this.requiredByDate = requiredByDate;
    }

    // Getters and Setters
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }

    public UrgencyLevel getUrgency() { return urgency; }
    public void setUrgency(UrgencyLevel urgency) { this.urgency = urgency; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getHospitalAddress() { return hospitalAddress; }
    public void setHospitalAddress(String hospitalAddress) { this.hospitalAddress = hospitalAddress; }

    public LocalDate getRequiredByDate() { return requiredByDate; }
    public void setRequiredByDate(LocalDate requiredByDate) { this.requiredByDate = requiredByDate; }

    // Builder
    public static SeekerRequestFormBuilder builder() { return new SeekerRequestFormBuilder(); }

    public static class SeekerRequestFormBuilder {
        private String bloodGroup;
        private Integer units;
        private UrgencyLevel urgency;
        private String hospitalName;
        private String hospitalAddress;
        private LocalDate requiredByDate;

        public SeekerRequestFormBuilder bloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; return this; }
        public SeekerRequestFormBuilder units(Integer units) { this.units = units; return this; }
        public SeekerRequestFormBuilder urgency(UrgencyLevel urgency) { this.urgency = urgency; return this; }
        public SeekerRequestFormBuilder hospitalName(String hospitalName) { this.hospitalName = hospitalName; return this; }
        public SeekerRequestFormBuilder hospitalAddress(String hospitalAddress) { this.hospitalAddress = hospitalAddress; return this; }
        public SeekerRequestFormBuilder requiredByDate(LocalDate requiredByDate) { this.requiredByDate = requiredByDate; return this; }

        public SeekerRequestForm build() {
            return new SeekerRequestForm(bloodGroup, units, urgency, hospitalName, hospitalAddress, requiredByDate);
        }
    }
}
