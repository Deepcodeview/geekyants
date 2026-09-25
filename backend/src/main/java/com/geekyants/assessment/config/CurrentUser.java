package com.geekyants.assessment.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final AppProperties props;

    public CurrentUser(AppProperties props) {
        this.props = props;
    }

    public String subject() {
        return token().getToken().getSubject();
    }

    /** External candidate ID bound to the caller's token. Candidates can only ever act on their own data. */
    public String candidateId() {
        String candidateId = token().getToken().getClaimAsString(props.security().candidateIdClaim());
        if (candidateId == null || candidateId.isBlank()) {
            throw new AccessDeniedException("Token is not bound to a candidate");
        }
        return candidateId;
    }

    private JwtAuthenticationToken token() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Not authenticated");
    }
}
