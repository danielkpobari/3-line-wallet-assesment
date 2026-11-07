package com.example.test.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nigerian mobile numbers, accepted as {@code 07068565534} or {@code +2347068565534}.
 *
 * <p>The 10 digits after the leading {@code 0} (or after {@code +234}) double as the customer's
 * <em>phone account number</em>, an alias for their generated account number. Mobile numbers always start with
 * 7, 8 or 9 after the 0, and generated account numbers start with 1-6, so the two can never collide.
 */
public final class PhoneNumbers {

    /** Regex for request validation: local (0XXXXXXXXXX) or international (+234XXXXXXXXXX) mobile format. */
    public static final String NIGERIAN_MOBILE_REGEX = "^(?:\\+234|0)[789][01]\\d{8}$";
    public static final String NIGERIAN_MOBILE_MESSAGE =
            "must be a Nigerian mobile number, e.g. 07068565534 or +2347068565534";

    private static final Pattern NIGERIAN_MOBILE = Pattern.compile("^(?:\\+234|0)([789][01]\\d{8})$");

    private PhoneNumbers() {
    }

    /**
     * @param e164          canonical form stored on the user, e.g. {@code +2347068565534}
     * @param accountNumber the 10-digit alias, e.g. {@code 7068565534}
     */
    public record NigerianMobile(String e164, String accountNumber) {
    }

    public static NigerianMobile parse(String raw) {
        Matcher matcher = raw == null ? null : NIGERIAN_MOBILE.matcher(raw.trim());
        if (matcher == null || !matcher.matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phone number " + NIGERIAN_MOBILE_MESSAGE);
        }
        String national = matcher.group(1);
        return new NigerianMobile("+234" + national, national);
    }
}
