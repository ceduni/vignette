package org.titiplex.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static class CsrfIfNoAuthHeader implements RequestMatcher {
        @Override
        public boolean matches(HttpServletRequest request) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) return false;

            String method = request.getMethod();
            boolean stateChanging = !(method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS"));
            return stateChanging && request.getRequestURI().startsWith("/api/");
        }
    }

    @Bean
    JwtAuthenticationConverter jwtAuthConverter() {
        var conv = new JwtGrantedAuthoritiesConverter();
        conv.setAuthoritiesClaimName("roles");
        conv.setAuthorityPrefix("");

        var jwtConv = new JwtAuthenticationConverter();
        jwtConv.setJwtGrantedAuthoritiesConverter(conv);
        return jwtConv;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtConv) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/front/**", "/css/**").permitAll()
                        .requestMatchers("/api/docs/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/languages/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/countries/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/scenarios/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/scenarios/*/thumbnails").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/thumbnails/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/thumbnails/*/audios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/thumbnails/*/content").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/audio/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/audios/*/content").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").permitAll()

                        .requestMatchers(HttpMethod.PUT, "/api/users/me/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/thumbnails").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/audio").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/community/discussions").permitAll()

                        .anyRequest().authenticated()
                )
                // session cookie OK
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // CSRF cookie -> header X-XSRF-TOKEN
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(new CsrfIfNoAuthHeader())
                        .ignoringRequestMatchers("/api/auth/**")
                )
                // JWT bearer OK
//                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConv))
                )
                // login form non necessary
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
