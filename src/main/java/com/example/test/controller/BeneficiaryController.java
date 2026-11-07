package com.example.test.controller;

import com.example.test.common.ApiError;
import com.example.test.dto.AddBeneficiaryRequest;
import com.example.test.dto.BeneficiaryResponse;
import com.example.test.dto.PageResponse;
import com.example.test.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Beneficiaries", description = "My saved recipients; pay one via POST /transfers/beneficiaries/{id}")
@RestController
@RequestMapping("/api/v1/me/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @Operation(summary = "List my beneficiaries (newest first)")
    @GetMapping
    public PageResponse<BeneficiaryResponse> list(
            @Parameter(description = "Partial match on nickname, holder's name, account number or phone account number")
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = ApiConstraints.DEFAULT_SIZE) @Min(1) @Max(ApiConstraints.MAX_PAGE_SIZE)
            int size) {
        return beneficiaryService.list(q, page, size);
    }

    @Operation(summary = "Save a beneficiary by account number or phone account number")
    @ApiResponse(responseCode = "201", description = "Beneficiary saved")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Already a beneficiary",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Cannot add your own account",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(@Valid @RequestBody AddBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiaryService.add(request));
    }

    @Operation(summary = "Remove a beneficiary")
    @ApiResponse(responseCode = "204", description = "Beneficiary removed")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        beneficiaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
