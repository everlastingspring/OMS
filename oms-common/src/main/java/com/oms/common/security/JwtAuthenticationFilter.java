package com.oms.common.security;

import com.oms.common.constant.OmsConstants;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Populates the SecurityContext from a Bearer token when one is present and valid.
 * An absent or invalid token is not an error here; the authorisation rules decide
 * whether an anonymous request is allowed to proceed.
 *
 * Deliberately NOT a Spring bean: Boot auto-registers any Filter bean into the
 * servlet chain, which would run it twice. Each service constructs it inside
 * its own SecurityConfig instead.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserPrincipal principal = tokenProvider.parse(token);
            if (principal != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(OmsConstants.AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(OmsConstants.BEARER_PREFIX)) {
            return header.substring(OmsConstants.BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
