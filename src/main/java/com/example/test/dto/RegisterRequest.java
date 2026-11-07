package com.example.test.dto;

import com.example.test.common.PhoneNumbers;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(example = "Ada")
        @NotBlank
        @Size(max = 50)
        String firstName,

        @Schema(example = "Obi")
        @NotBlank
        @Size(max = 50)
        String lastName,

        @Schema(example = "ada.obi@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "07068565534", description = "Nigerian mobile number (07068565534 or +2347068565534). "
                + "Without the leading 0 it becomes a second account number that receives into your wallet.")
        @NotBlank
        @Pattern(regexp = PhoneNumbers.NIGERIAN_MOBILE_REGEX, message = PhoneNumbers.NIGERIAN_MOBILE_MESSAGE)
        String phoneNumber,

        @Schema(example = "Str0ngPass!", description = "8-72 characters with at least one letter and one digit")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,

        @Schema(example = "2580", description = "4-digit transaction PIN required for every transfer; "
                + "no repeated digits (0000) or sequences (1234)")
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits")
        String pin) {

    @Override
    public String toString() {
        return "RegisterRequest[firstName=" + firstName + ", lastName=" + lastName + ", email=" + email
                + ", phoneNumber=" + phoneNumber + ", password=***, pin=****]";
    }
}
