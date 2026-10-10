package com.lifelink.dto;

import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for viewing and editing a donor's profile and donation availability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DonorProfileDto {

    @NotNull(message = "Blood group is required")
    private BloodGroup bloodGroup;

    @NotNull(message = "Age is required")
    @Min(value = 18, message = "Eligible donors must be at least 18 years old")
    @Max(value = 65, message = "Eligible donors must be 65 years old or younger")
    private Integer age;

    @NotNull(message = "Gender is required")
    private Gender gender;

    private boolean availableToDonate;

    @PastOrPresent(message = "Last donation date cannot be in the future")
    private LocalDate lastDonationDate;

    @Size(max = 1000, message = "Medical conditions notes must not exceed 1000 characters")
    private String medicalConditions;
}
