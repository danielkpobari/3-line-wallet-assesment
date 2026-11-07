package com.example.test.service;

import com.example.test.common.BusinessException;
import com.example.test.common.Emails;
import com.example.test.common.ErrorCode;
import com.example.test.dto.LoginRequest;
import com.example.test.dto.TokenResponse;
import com.example.test.model.AuditAction;
import com.example.test.model.User;
import com.example.test.repo.UserRepo;
import com.example.test.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login.
 *
 * <p>Deliberately tells the client which check failed: an unknown email gets {@code EMAIL_NOT_REGISTERED}
 * (prompting them to register), a known email with the wrong password gets {@code INVALID_CREDENTIALS}. This
 * reveals whether an email is registered, which registration already does via {@code DUPLICATE_EMAIL}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditService audit;

    /** Read-write because a successful login writes an audit event in the same transaction. */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = Emails.normalise(request.email());

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            throw loginFailed(null, email, new BusinessException(ErrorCode.EMAIL_NOT_REGISTERED,
                    "No account is registered with this email. Please register first."));
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw loginFailed(user, email, new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "Incorrect password"));
        }

        JwtTokenService.IssuedToken token = jwtTokenService.issue(user);
        audit.event(AuditAction.LOGIN_SUCCEEDED)
                .actor(user.getId(), user.getRole())
                .resource("USER", String.valueOf(user.getId()))
                .recordSuccess();
        return TokenResponse.bearer(token.value(), token.expiresAt(), user.getRole());
    }

    /** Records the failed attempt (kept even though this transaction rolls back) and returns the error to throw. */
    private BusinessException loginFailed(User user, String email, BusinessException failure) {
        audit.event(AuditAction.LOGIN_FAILED)
                .actor(user != null ? user.getId() : null, user != null ? user.getRole() : null)
                .detail("email", email)
                .recordFailureDespiteRollback(failure);
        return failure;
    }
}
