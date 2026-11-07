package com.example.test.dto;

import com.example.test.model.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@Schema(example = "FROZEN") @NotNull AccountStatus status) {
}
