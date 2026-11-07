package com.example.test.service;

import com.example.test.common.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"2580", "7391", "1357", "0102", "9045"})
    void acceptsReasonablePins(String pin) {
        assertThatCode(() -> PinService.requireStrong(pin)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000", "7777", "1234", "6789", "4321", "9876", "123", "12345", "12a4", ""})
    void rejectsWeakOrMalformedPins(String pin) {
        assertThatThrownBy(() -> PinService.requireStrong(pin)).isInstanceOf(BusinessException.class);
    }
}
