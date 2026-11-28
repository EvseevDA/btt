package com.github.evseevda.domainlogicmodule.config;

import com.github.evseevda.domainlogicmodule.identity.service.AccountService;
import com.github.evseevda.domainlogicmodule.identity.service.AccountTokenService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@Profile("!mock-api")
class ApiSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AccountTokenService tokens) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                        (request, response, error) -> {
                                            response.setHeader("WWW-Authenticate", "Bearer");
                                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                                        }))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET, "/", "/index.html", "/assets/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/users",
                                                "/api/v1/auth/token")
                                        .permitAll()
                                        .requestMatchers("/api/v1/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new OncePerRequestFilter() {
                            @Override
                            protected void doFilterInternal(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
                                    throws ServletException, IOException {
                                String header = request.getHeader("Authorization");
                                if (header != null && header.startsWith("Bearer ")) {
                                    var authentication =
                                            tokens.authenticateToken(header.substring(7));
                                    if (authentication.isPresent()) {
                                        var context = SecurityContextHolder.createEmptyContext();
                                        context.setAuthentication(authentication.orElseThrow());
                                        SecurityContextHolder.setContext(context);
                                    } else {
                                        response.setHeader(
                                                "WWW-Authenticate",
                                                "Bearer error=\"invalid_token\"");
                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                                        return;
                                    }
                                }
                                chain.doFilter(request, response);
                            }
                        },
                        AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    ApplicationRunner bootstrapAdmin(
            AccountService accounts,
            @Value("${BTT_ADMIN_USERNAME:}") String username,
            @Value("${BTT_ADMIN_EMAIL:}") String email,
            @Value("${BTT_ADMIN_PASSWORD:}") String password) {
        return args -> {
            if (!username.isEmpty() || !email.isEmpty() || !password.isEmpty()) {
                accounts.createInitialAdministrator(username, email, password);
            }
        };
    }
}
