package com.oms.common.security;

import com.oms.common.constant.OmsConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/** Issues and verifies HS256 access tokens. Shared by every service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties properties;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .setSubject(email)
                .claim(OmsConstants.CLAIM_USER_ID, userId)
                .claim(OmsConstants.CLAIM_ROLE, role)
                .setIssuer(properties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationMs() {
        return properties.getExpirationMs();
    }

    /** @return the parsed principal, or null when the token is absent, malformed, tampered with or expired. */
    public UserPrincipal parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(token);
            Claims claims = jws.getBody();
            Number userId = claims.get(OmsConstants.CLAIM_USER_ID, Number.class);
            String role = claims.get(OmsConstants.CLAIM_ROLE, String.class);
            if (userId == null || role == null) {
                log.warn("JWT is missing required claims");
                return null;
            }
            return new UserPrincipal(userId.longValue(), claims.getSubject(), role);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired at {}", ex.getClaims().getExpiration());
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }
}
