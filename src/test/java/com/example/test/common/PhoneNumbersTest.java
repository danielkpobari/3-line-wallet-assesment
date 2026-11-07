package com.example.test.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @Test
    void localAndInternationalFormsGiveTheSameNumbers() {
        PhoneNumbers.NigerianMobile local = PhoneNumbers.parse("07068565534");
        PhoneNumbers.NigerianMobile international = PhoneNumbers.parse("+2347068565534");

        assertThat(local).isEqualTo(international);
        assertThat(local.e164()).isEqualTo("+2347068565534");
        assertThat(local.accountNumber()).isEqualTo("7068565534");
    }

    @ParameterizedTest
    @ValueSource(strings = {"08031234567", "08151234567", "09061234567", "09121234567", " 07011112222 "})
    void acceptsNigerianMobilePrefixes(String phone) {
        assertThat(PhoneNumbers.parse(phone).accountNumber()).hasSize(10).matches("[789][01]\\d{8}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"7068565534", "2347068565534", "0706856553", "070685655345", "06068565534",
            "07268565534", "+44706856553", "0706856553a", ""})
    void rejectsAnythingElse(String phone) {
        assertThatThrownBy(() -> PhoneNumbers.parse(phone)).isInstanceOf(BusinessException.class);
    }
}
