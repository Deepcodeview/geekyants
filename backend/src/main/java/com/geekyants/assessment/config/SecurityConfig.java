package com.geekyants.assessment.config;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    public static final String ADMIN = "ADMIN";
    public static final String CANDIDATE = "CANDIDATE";
    public static final String INTEGRATION = "INTEGRATION";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties props) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList(props.security().rolesClaim());
            Collection<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return authorities;
        });

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Authenticated by HMAC signature inside the controllers, not by JWT.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/github").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/callbacks/evaluations/*").permitAll()
                        // DevTokenController only exists under the "dev" profile.
                        .requestMatchers("/dev/**").permitAll()
                        .requestMatchers("/api/v1/intake/**").hasRole(INTEGRATION)
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN)
                        .requestMatchers("/api/v1/candidate/**").hasRole(CANDIDATE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    /**
     * Uses the OIDC issuer when configured. Falls back to an HS256 secret for local development only,
     * so a misconfigured production deployment fails at startup instead of accepting unsigned tokens.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                          AppProperties props) {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        String secret = props.security().devJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Configure OIDC_ISSUER_URI, or run with the 'dev' profile to use a local signing secret");
        }
        return NimbusJwtDecoder.withSecretKey(devSigningKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    static SecretKeySpec devSigningKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsAllowedOrigins() == null ? List.of() : props.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
