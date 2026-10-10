package com.lifelink.dto;

import com.lifelink.model.enums.BloodGroup;
import com.lifelink.model.enums.Urgency;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Form Backing Object / DTO for creating and editing blood requests.
 *
 * <p>Enforces Design Decision #6:
 * Validation rules are strictly identical across both creation and editing,
 * requiring a valid blood group, 1-20 units, and a strictly future required-by date.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeekerRequestDto {

    private Long id;

    @NotNull(message = "Blood group is required")
    private BloodGroup bloodGroup;

    @NotNull(message = "Units needed is required")
    @Min(value = 1, message = "At least 1 unit must be requested")
    @Max(value = 20, message = "A maximum of 20 units may be requested per posting")
    private Integer unitsNeeded;

    @NotNull(message = "Urgency level is required")
    private Urgency urgency;

    @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
    private String reason;

    @NotBlank(message = "Hospital name is required")
    @Size(min = 2, max = 150, message = "Hospital name must be between 2 and 150 characters")
    private String hospitalName;

    @NotBlank(message = "Hospital address is required")
    @Size(min = 5, max = 255, message = "Hospital address must be between 5 and 255 characters")
    private String hospitalAddress;

    @NotNull(message = "Required-by date is required")
    @Future(message = "Required-by date must be a date in the future")
    private LocalDate requiredBy;
}
