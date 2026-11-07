package com.example.test.controller;

import com.example.test.dto.NameEnquiryResponse;
import com.example.test.service.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "Account lookups available to any authenticated user")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountQueryService accountQueryService;

    @Operation(summary = "Name enquiry: confirm the holder's name before transferring",
            description = "Accepts an account number or a phone account number (mobile number without the leading 0).")
    @GetMapping("/{accountNumber}/name-enquiry")
    public NameEnquiryResponse nameEnquiry(
            @PathVariable
            @Pattern(regexp = ApiConstraints.ACCOUNT_NUMBER_REGEX, message = ApiConstraints.ACCOUNT_NUMBER_MESSAGE)
            String accountNumber) {
        return accountQueryService.nameEnquiry(accountNumber);
    }
}
