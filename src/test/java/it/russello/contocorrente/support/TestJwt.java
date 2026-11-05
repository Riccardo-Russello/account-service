package it.russello.contocorrente.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestJwt {
    private TestJwt() {
    }

    public static RequestPostProcessor admin(Long personId) {
        return jwt()
                .jwt(builder -> builder
                        .subject("test.admin")
                        .claim("personId", personId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }

    public static RequestPostProcessor user(Long personId) {
        return jwt()
                .jwt(builder -> builder
                        .subject("test.user")
                        .claim("personId", personId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }
}