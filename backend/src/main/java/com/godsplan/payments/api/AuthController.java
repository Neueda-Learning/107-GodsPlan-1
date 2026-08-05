package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.CurrentUserResponse;
import com.godsplan.payments.config.SecurityProperties;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SecurityProperties properties;

    public AuthController(SecurityProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream().findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("STAFF");
        return new CurrentUserResponse(authentication.getName(), properties.fullName(), role);
    }
}

