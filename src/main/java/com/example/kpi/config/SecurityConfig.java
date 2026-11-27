package com.example.kpi.config;

import com.example.kpi.security.CustomUserDetailsService;
import com.example.kpi.security.JwtAuthenticationEntryPoint;
import com.example.kpi.security.JwtAuthenticationFilter;
import com.example.kpi.security.TokenDenylist;
import com.example.kpi.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security Configuration for JWT-based authentication and authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JwtTokenProvider jwtTokenProvider) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtTokenProvider = jwtTokenProvider;
    }

     /**
     * Configure password encoder (BCrypt).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    /**
     * Configure authentication manager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Configure JWT authentication filter.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenDenylist tokenDenylist) {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService, tokenDenylist);
    }

    /**
     * Configure CORS.
     *
     * <p>Origins are configurable because they were hard-coded to localhost,
     * which means a deployed frontend on any other host is refused by the
     * browser. Set {@code app.cors.allowed-origins} per environment.</p>
     *
     * <p>Credentials are allowed, so the origin list must stay explicit — the
     * CORS spec forbids pairing {@code Access-Control-Allow-Credentials} with a
     * {@code *} origin, and doing so would let any site issue authenticated
     * requests on a user's behalf.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
            List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // Lets the browser read the id back so it can be shown in an error report.
        configuration.setExposedHeaders(Arrays.asList("X-Correlation-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configure HTTP security.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        // Uploaded evidence is served back from this same
                        // origin. Without a policy, a PDF carrying JavaScript
                        // runs here — next to the token that authorises every
                        // request the viewer could then make.
                        //
                        // 'self' for scripts and styles is enough for a
                        // compiled Angular bundle. 'unsafe-inline' is left out
                        // of script-src on purpose: allowing it would readmit
                        // exactly the injection this is here to stop.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "script-src 'self'",
                                "style-src 'self' 'unsafe-inline'",
                                "img-src 'self' data: blob:",
                                "font-src 'self' data:",
                                "connect-src 'self'",
                                // Evidence is downloaded, never framed, and
                                // nothing here embeds a third party.
                                "object-src 'none'",
                                "frame-ancestors 'none'",
                                "base-uri 'self'",
                                "form-action 'self'")))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny())
                        // Six months, matching the usual preload floor. Applied
                        // by Spring only over HTTPS, so local http development
                        // is unaffected.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(15552000))
                )
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints.
                        //
                        // Deliberately enumerated rather than "/api/auth/**":
                        // that wildcard also exposed /api/auth/change-password
                        // and /api/auth/me, which have no business being
                        // reachable unauthenticated. /me in particular returned
                        // 404 rather than 401 for an anonymous caller, because
                        // it got as far as looking up a user that was not there.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh-token",
                                // Idempotent and harmless: revoking an expired
                                // or unknown token is a no-op, and a client
                                // whose token has already lapsed should still
                                // be able to complete a sign-out.
                                "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/validate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/demo-credentials").permitAll()

                        // Liveness/readiness must stay reachable by the
                        // container runtime. Metrics and the Prometheus scrape
                        // are not public: they disclose endpoint names, traffic
                        // volumes and memory profile.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Serve the Angular bundle and any client-side route
                        // without auth.
                        //
                        // Previously an enumerated list of paths, which silently
                        // fell behind the router: every /kpi/* screen and
                        // /notifications were missing, so refreshing or deep
                        // linking one of them returned the raw JSON 401 body
                        // instead of the app. Matching "any GET that is not the
                        // API" keeps new routes working without a second edit
                        // here, and gives up nothing — the bundle is public
                        // static content, and every /api/** call underneath it
                        // is still authenticated.
                        .requestMatchers(SPA_ROUTES).permitAll()

                        // Protected endpoints
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/teams/**").hasRole("ADMIN")
                        .requestMatchers("/api/hierarchy/**").authenticated()

                        // Any other endpoint requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Matches the static bundle and every client-side route: any GET that is
     * neither the API nor the actuator.
     * <p>
     * Deliberately expressed as "not the API" rather than as a list of paths.
     * The API rules above are evaluated first, so nothing under /api/** can
     * reach this, and the actuator keeps its own rules; what is left is the
     * frontend, which is public static content in any case.
     */
    private static final RequestMatcher SPA_ROUTES = request -> {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !path.startsWith("/api/") && !path.equals("/api")
                && !path.startsWith("/actuator/") && !path.equals("/actuator");
    };
}
