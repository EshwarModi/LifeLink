package com.lifelink.dto;

import com.lifelink.model.SeekerRequest;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.RequestStatus;
import com.lifelink.model.enums.Urgency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only DTO for SeekerRequest API responses.
 * Excludes private phone numbers to preserve contact privacy until match acceptance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeekerRequestResponseDto {

    private Long id;
    private BloodGroup bloodGroup;
    private String bloodGroupLabel;
    private Integer unitsNeeded;
    private Urgency urgency;
    private String urgencyLabel;
    private String reason;
    private String hospitalName;
    private String hospitalAddress;
    private String city;
    private String state;
    private LocalDate requiredBy;
    private RequestStatus status;
    private LocalDateTime createdAt;

    public static SeekerRequestResponseDto fromEntity(SeekerRequest req) {
        return SeekerRequestResponseDto.builder()
                .id(req.getId())
                .bloodGroup(req.getBloodGroup())
                .bloodGroupLabel(req.getBloodGroup().getLabel())
                .unitsNeeded(req.getUnitsNeeded())
                .urgency(req.getUrgency())
                .urgencyLabel(req.getUrgency().getLabel())
                .reason(req.getReason())
                .hospitalName(req.getHospitalName())
                .hospitalAddress(req.getHospitalAddress())
                .city(req.getSeeker().getCity())
                .state(req.getSeeker().getState())
                .requiredBy(req.getRequiredBy())
                .status(req.getStatus())
                .createdAt(req.getCreatedAt())
                .build();
    }
}
