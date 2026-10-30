package com.example.operion.module.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.operion.common.security.ScopeContext;
import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        private final UserRepository userRepository;

        public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
                this.jwtUtil = jwtUtil;
                this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                String path = request.getServletPath();

                // BE-12: /auth/register is no longer skipped — it now needs the
                // caller's JWT parsed so @PreAuthorize can see ROLE_ADMIN
                if (path.equals("/auth/login")) {
                        filterChain.doFilter(request, response);
                        return;
                }

                final String authHeader = request.getHeader("Authorization");

                if (authHeader == null ||
                                !authHeader.startsWith("Bearer ")) {

                        filterChain.doFilter(request, response);
                        return;
                }

                try {

                        String token = authHeader.substring(7);

                        Claims claims = jwtUtil.extractAllClaims(token);

                        String userId = claims.getSubject();

                        // BE-33: a switched-off user's token stops working at once,
                        // not when it expires (one indexed read per request)
                        User user = userRepository.findById(UUID.fromString(userId)).orElse(null);

                        if (user == null || !user.isActive()) {

                                refuse(response, "This account is switched off");
                                return;
                        }

                        // BE-36: a token from before the last password change or
                        // reset is refused (both are in whole seconds)
                        if (user.getPasswordChangedAt() != null && claims.getIssuedAt() != null
                                        && claims.getIssuedAt().toInstant().isBefore(
                                                        user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toInstant())) {

                                refuse(response, "Your password was changed; sign in again");
                                return;
                        }

                        String role = claims.get("role", String.class);

                        String tenantId = claims.get("tenantId", String.class);

                        String scope = claims.get("scope", String.class);

                        TenantContext.setTenantId(tenantId);

                        ScopeContext.setScope(scope);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(
                                                        new SimpleGrantedAuthority("ROLE_" + role)));

                        SecurityContextHolder
                                        .getContext()
                                        .setAuthentication(authentication);

                } catch (Exception e) {

                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Invalid JWT token");
                        return;
                }

                try {

                        filterChain.doFilter(request, response);

                } finally {

                        TenantContext.clear();
                        ScopeContext.clear();
                }
        }

        /** 401 with the app's error shape; the app signs out and shows the message. */
        private static void refuse(HttpServletResponse response, String message) throws IOException {

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"" + message + "\"}");
        }
}
