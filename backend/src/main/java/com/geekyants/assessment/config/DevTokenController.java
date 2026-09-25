package com.geekyants.assessment.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mints local JWTs so the API can be exercised without an OIDC provider. Never enabled outside "dev". */
@Profile("dev")
@RestController
@RequestMapping("/dev/tokens")
class DevTokenController {

    private final NimbusJwtEncoder encoder;
    private final AppProperties props;

    DevTokenController(AppProperties props) {
        this.props = props;
        this.encoder = NimbusJwtEncoder.withSecretKey(
                SecurityConfig.devSigningKey(props.security().devJwtSecret())).build();
    }

    record TokenRequest(String subject, List<String> roles, String candidateId) {
    }

    @PostMapping
    Map<String, String> mint(@RequestBody TokenRequest request) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(request.subject())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(8)))
                .claim(props.security().rolesClaim(), request.roles());
        if (request.candidateId() != null) {
            claims.claim(props.security().candidateIdClaim(), request.candidateId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return Map.of("token", token);
    }
}
