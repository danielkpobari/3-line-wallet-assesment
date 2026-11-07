package com.example.test.config;

import com.example.test.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Adds the logged-in user's id to the logging MDC as {@code userId} once Spring Security has authenticated
 * the request, so every log line written while handling it says who it was for.
 * {@link RequestLoggingFilter} removes it when the request ends.
 */
@Component
public class CurrentUserMdcInterceptor implements HandlerInterceptor {

    public static final String MDC_USER_ID = "userId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        CurrentUser.find().ifPresent(user -> MDC.put(MDC_USER_ID, String.valueOf(user.id())));
        return true;
    }
}
