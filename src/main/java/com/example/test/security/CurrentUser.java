package com.example.test.security;

import com.example.test.model.Role;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The logged-in user.
 *
 * <p>Built from a verified access token by {@link #from(Jwt)}, which {@link SecurityConfig} registers as the
 * resource server's authentication converter. The object is itself the {@code Authentication} and its own
 * principal, carrying a single authority, {@code ROLE_USER} or {@code ROLE_ADMIN}.
 *
 * <p>Code that needs the logged-in user calls {@link #get()} (or {@link #find()} where nobody may be logged in,
 * e.g. system jobs). It reads Spring Security's per-request context, so it works in any layer, needs no
 * method parameters and does not depend on an HTTP request being present. Ownership decisions (whose wallet to
 * debit, whose statement to show) are made from this object, never from identifiers sent by the client.
 *
 * <p>In tests: {@code SecurityContextHolder.getContext().setAuthentication(new CurrentUser(1L, "a@b.com", USER))}.
 */
public final class CurrentUser extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final Role role;
    private final Jwt token;

    public CurrentUser(Long id, String email, Role role) {
        this(id, email, role, null);
    }

    private CurrentUser(Long id, String email, Role role, Jwt token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        this.id = id;
        this.email = email;
        this.role = role;
        this.token = token;
        setAuthenticated(true);
    }

    /** Reads {@code sub} (user id), {@code email} and {@code role} from a token whose signature is already verified. */
    public static CurrentUser from(Jwt jwt) {
        try {
            Long id = Long.valueOf(jwt.getSubject());
            Role role = Role.valueOf(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE));
            return new CurrentUser(id, jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL), role, jwt);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Valid signature but not the claims we issue: treat it as an invalid token (401), not a server error.
            throw new InvalidBearerTokenException("Token is missing required claims");
        }
    }

    /**
     * The logged-in user.
     *
     * @throws AuthenticationCredentialsNotFoundException if no user is logged in (mapped to 401)
     */
    public static CurrentUser get() {
        return find().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No logged-in user"));
    }

    /** The logged-in user, or empty when nobody is (anonymous request, startup code, scheduled job). */
    public static Optional<CurrentUser> find() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof CurrentUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public Role role() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public CurrentUser getPrincipal() {
        return this;
    }

    /** The verified access token, or null when constructed directly (e.g. in tests). */
    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    /**
     * Nothing to erase: the credential is an already-verified JWT, not a password.
     *
     * <p>Must be overridden. Spring Security calls this after authentication, and the parent implementation also
     * erases the principal when it is a {@code CredentialsContainer}. The principal is this object, so the parent
     * version would call itself until the stack overflows.
     */
    @Override
    public void eraseCredentials() {
        // intentionally empty
    }

    // Likewise the parent's equals/hashCode/toString go through getPrincipal(), so they are overridden too.

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CurrentUser that
                && Objects.equals(id, that.id) && Objects.equals(email, that.email) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email, role);
    }

    @Override
    public String toString() {
        return "CurrentUser[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
