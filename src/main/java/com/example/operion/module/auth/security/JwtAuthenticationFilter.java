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
import com.example.operion.common.security.TenantContext;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        public JwtAuthenticationFilter(JwtUtil jwtUtil) {
                this.jwtUtil = jwtUtil;
        }

        @Override
        protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                String path = request.getServletPath();

                if (path.startsWith("/auth/")) {
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

                        String role = claims.get("role", String.class);

                        String tenantId = claims.get("tenantId", String.class);

                        TenantContext.setTenantId(tenantId);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(
                                                        new SimpleGrantedAuthority(role)));

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
                }
        }
}