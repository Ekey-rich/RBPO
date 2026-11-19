package ru.example.exams;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserDetailsService users;

    JwtFilter(JwtService jwt, UserDetailsService users) { this.jwt = jwt; this.users = users; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String username = jwt.checkAccess(header.substring(7)).getSubject();
                UserDetails user = users.loadUserByUsername(username);
                if (!user.isEnabled()) { response.setStatus(401); return; }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } catch (JWTVerificationException | IllegalArgumentException | UsernameNotFoundException e) {
                response.setStatus(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
