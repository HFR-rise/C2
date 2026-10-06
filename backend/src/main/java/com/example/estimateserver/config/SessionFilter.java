package com.example.estimateserver.config;

import com.example.estimateserver.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class SessionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/send-code",
            "/api/auth/verify",
            "/api/auth/test",
            "/api/auth/session/check-with-device",
            "/ws"
    );

    private final UserService userService;

    public SessionFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String userId = request.getHeader("X-User-Id");
        String deviceId = request.getHeader("X-Device-Id");

        log.debug("SessionFilter: IN method={}, path={}, userId={}, deviceId={}",
                method, path, userId, deviceId);

        if (isPublicPath(path)) {
            log.debug("SessionFilter: PUBLIC path={}, method={}", path, method);
            chain.doFilter(request, response);
            return;
        }

        boolean missingUserId = userId == null || userId.isBlank();
        boolean missingDeviceId = deviceId == null || deviceId.isBlank();

        if (missingUserId || missingDeviceId) {
            log.warn("SessionFilter: MISSING HEADERS path={}, method={}, " +
                            "missingUserId={}, missingDeviceId={}, userId={}, deviceId={}",
                    path, method, missingUserId, missingDeviceId, userId, deviceId);
            reject(response, "missing_session_headers");
            return;
        }

        if (!userService.isSessionValid(userId, deviceId)) {
            log.warn("SessionFilter: SESSION INVALID path={}, method={}, " +
                            "userId={}, deviceId={}",
                    path, method, userId, deviceId);
            reject(response, "session_invalid");
            return;
        }

        log.debug("SessionFilter: OK path={}, method={}, userId={}",
                path, method, userId);
        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        if (path == null) return false;
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        log.warn("SessionFilter: REJECT status=401, reason={}", reason);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }
}