package fr.siamois.ui.config;

import fr.siamois.ui.config.handler.LoginSuccessHandler;
import fr.siamois.ui.config.security.ApiUnauthorizedJsonWriter;
import fr.siamois.ui.config.security.jwt.JwtAuthenticationFilter;
import fr.siamois.ui.config.security.jwt.JwtService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration class for the security of the application.
 * @author Julien Linget
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    public static final String LOGIN = "/login";

    /**
     * Le filtre JWT ne doit pas être enregistré comme filtre Servlet global (sinon il s’applique à {@code /}, JSF, etc.).
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, PersonRepository personRepository) {
        return new JwtAuthenticationFilter(jwtService, personRepository);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Swagger / OpenAPI : accès anonyme, sans redirection vers le formulaire web (SpringDoc + WebJars).
     */
    @Bean
    @Order(0)
    public SecurityFilterChain documentationSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/webjars/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    /**
     * API REST v1 : JWT Bearer (stateless), sans session ni formulaire.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiV1SecurityFilterChain(HttpSecurity http,
                                                        JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Seuls l’émission du jeton (login) et le renouvellement (refresh) sont hors JWT.
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) ->
                        ApiUnauthorizedJsonWriter.write(response, "Authentication required")));

        return http.build();
    }

    /**
     * Application web (JSF), formulaire de connexion et sessions HTTP.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/index.xhtml").permitAll()
                .requestMatchers(LOGIN, "/pages/login/login.xhtml", "/pages/login/register.xhtml").permitAll()
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/robots.txt").permitAll()
                .requestMatchers("/jakarta.faces.resource/**").permitAll()
                .requestMatchers("/error/**", "/pages/error/**").permitAll()
                .requestMatchers("/api/ping").permitAll()
                .requestMatchers("/api/context/**").permitAll()
                .requestMatchers("/api/ark:/**").permitAll()
                .requestMatchers("/api/recording-units/**").permitAll()
                .requestMatchers("/register/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                .anyRequest().authenticated()
        );
        http.formLogin(login -> login
                .loginPage(LOGIN)
                .loginProcessingUrl(LOGIN)
                .usernameParameter("email")
                .successHandler(loginSuccessHandler)
        );
        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
        );

        return http.build();
    }

    /**
     * Password encoder bean.
     * @return BCryptPasswordEncoder object to encode passwords with bcrypt.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
