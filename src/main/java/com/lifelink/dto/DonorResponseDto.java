package com.lifelink.dto;

import com.lifelink.model.User;
import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only DTO for public donor searches.
 * Strictly excludes phone numbers and private addresses until mutual match acceptance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DonorResponseDto {

    private Long id;
    private String fullName;
    private String city;
    private String state;
    private BloodGroup bloodGroup;
    private String bloodGroupLabel;
    private Gender gender;
    private boolean availableToDonate;

    public static DonorResponseDto fromEntity(User user) {
        BloodGroup bg = null;
        String bgLabel = null;
        Gender gender = null;
        boolean available = false;

        if (user.getDonorProfile() != null) {
            bg = user.getDonorProfile().getBloodGroup();
            bgLabel = bg != null ? bg.getLabel() : null;
            gender = user.getDonorProfile().getGender();
            available = user.getDonorProfile().isAvailableToDonate();
        }

        return DonorResponseDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .city(user.getCity())
                .state(user.getState())
                .bloodGroup(bg)
                .bloodGroupLabel(bgLabel)
                .gender(gender)
                .availableToDonate(available)
                .build();
    }
}
