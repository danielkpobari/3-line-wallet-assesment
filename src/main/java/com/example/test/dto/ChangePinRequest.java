package com.example.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(

        @Schema(example = "2580")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String currentPin,

        @Schema(example = "7391", description = "No repeated digits (0000) or sequences (1234)")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String newPin) {

    @Override
    public String toString() {
        return "ChangePinRequest[currentPin=****, newPin=****]";
    }
}
