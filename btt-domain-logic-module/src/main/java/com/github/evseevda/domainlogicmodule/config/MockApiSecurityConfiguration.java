package com.github.evseevda.domainlogicmodule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("mock-api")
class MockApiSecurityConfiguration {

    @Bean
    SecurityFilterChain mockApiSecurityFilterChain(HttpSecurity http) {
        return http.authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers("/api/v1/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
                .build();
    }
}
