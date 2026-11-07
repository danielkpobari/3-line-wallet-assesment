package com.example.test.security;

import com.example.test.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    void buildsAuthenticatedCurrentUserWithRoleAuthorityFromToken() {
        Jwt token = jwt("42", "ada@example.com", "ADMIN");

        CurrentUser user = CurrentUser.from(token);

        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("ada@example.com");
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.isAuthenticated()).isTrue();
        assertThat(user.getPrincipal()).isSameAs(user);
        assertThat(user.getName()).isEqualTo("42");
        assertThat(user.getCredentials()).isSameAs(token);
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(user).isEqualTo(new CurrentUser(42L, "ada@example.com", Role.ADMIN));
        assertThat(user).hasToString("CurrentUser[id=42, email=ada@example.com, role=ADMIN]");
    }

    /** Regression: Spring Security erases credentials after authentication; this used to recurse forever. */
    @Test
    void erasingCredentialsDoesNotRecurseAndKeepsTheUserUsable() {
        CurrentUser user = CurrentUser.from(jwt("5", "tola@example.com", "USER"));

        assertThatCode(user::eraseCredentials).doesNotThrowAnyException();
        assertThat(user.id()).isEqualTo(5L);
        assertThat(user.isAuthenticated()).isTrue();
    }

    @Test
    void rejectsTokensWithMalformedClaims() {
        assertThatThrownBy(() -> CurrentUser.from(jwt("not-a-number", "a@b.com", "USER")))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> CurrentUser.from(jwt("1", "a@b.com", "SUPERUSER")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void getAndFindReadTheLoggedInUserFromTheSecurityContext() {
        CurrentUser user = new CurrentUser(7L, "kemi@example.com", Role.USER);
        try {
            assertThat(CurrentUser.find()).isEmpty();
            assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);

            SecurityContextHolder.getContext().setAuthentication(user);

            assertThat(CurrentUser.get()).isSameAs(user);
            assertThat(CurrentUser.find()).containsSame(user);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Jwt jwt(String subject, String email, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .claim(JwtTokenService.CLAIM_EMAIL, email)
                .claim(JwtTokenService.CLAIM_ROLE, role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
