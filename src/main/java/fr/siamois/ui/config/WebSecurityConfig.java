package fr.siamois.ui.config;

import fr.siamois.ui.config.handler.LoginSuccessHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
     * Security filter chain of the application.
     * @param http HttpSecurity object to configure the security chain.
     * @return The security filter chain with the configuration.
     * @throws Exception If any filter configuration fails.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/index.xhtml").permitAll()
                .requestMatchers(LOGIN, "/pages/login/login.xhtml", "/pages/login/register.xhtml").permitAll()
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/robots.txt").permitAll()
                .requestMatchers("/jakarta.faces.resource/**").permitAll()
                .requestMatchers("/error/**", "/pages/error/**").permitAll()
                .requestMatchers("/api/**").permitAll()
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
