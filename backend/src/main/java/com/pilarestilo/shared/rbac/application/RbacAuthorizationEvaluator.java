package com.pilarestilo.shared.rbac.application;

import com.pilarestilo.shared.rbac.domain.model.PermissionDefinition;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component("rbac")
public class RbacAuthorizationEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RbacAuthorizationEvaluator.class);

    public boolean hasPermission(Authentication authentication, PermissionDefinition permission) {
        boolean granted = hasAuthority(authentication, permission.authority());
        if (!granted && log.isDebugEnabled()) {
            log.debug("[RBAC] deny permission={} principal={}", permission.code(), principalSummary(authentication));
        }
        return granted;
    }

    public boolean hasAnyPermission(Authentication authentication, PermissionDefinition... permissions) {
        return Arrays.stream(permissions).anyMatch(permission -> hasPermission(authentication, permission));
    }

    public boolean hasAllPermissions(Authentication authentication, PermissionDefinition... permissions) {
        return Arrays.stream(permissions).allMatch(permission -> hasPermission(authentication, permission));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private String principalSummary(Authentication authentication) {
        if (authentication == null) {
            return "anonymous";
        }
        // Read once: getPrincipal() was called twice, so the null check guarded one value and the
        // use returned another. Spring never hands back null authorities, so that branch was dead.
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return "anonymous";
        }
        if (principal instanceof AuthenticatedUser user) {
            return "%s(%s) authorities=%d".formatted(
                    user.email(),
                    user.role().name(),
                    authentication.getAuthorities().size()
            );
        }
        return principal.toString();
    }
}
