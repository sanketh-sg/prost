package de.unibamberg.dsam.group6.prost.configuration;

import de.unibamberg.dsam.group6.prost.service.UserDetailSecurityService;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserDetailSecurityService detailsService;
    private final UserErrorManager errors;

    @Bean
    @Profile("dev")
    public SecurityFilterChain securityFilterChainProd(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(req -> {
            req.antMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.anyRequest().permitAll();
        });
        http.formLogin(form -> {
            form.loginPage("/login").failureHandler((req, res, e) -> {
                this.errors.addToast(Toast.error(e.getMessage()));
                res.sendRedirect("/login");
            });
        });
        http.headers(h -> {
            h.httpStrictTransportSecurity().disable();
            h.frameOptions().disable();
        });
        http.logout(l -> l.logoutUrl("/logout"));
        http.csrf().ignoringAntMatchers("/h2-console/**");

        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(req -> {
            req.antMatchers("/cart/**", "/orders/**", "/user/**").authenticated();
            req.antMatchers("/admin/**").hasRole("ADMIN");
            req.anyRequest().permitAll();
        });
        http.formLogin(form -> {
            form.loginPage("/login").permitAll();
            form.failureHandler((req, res, e) -> {
                this.errors.addToast(Toast.error(e.getMessage()));
                res.sendRedirect("/login");
            });
        });
        http.logout(t -> t.logoutUrl("/logout").permitAll());

        http.csrf();
        http.headers(h -> {
            h.httpStrictTransportSecurity();
            h.frameOptions().sameOrigin();
        });
        http.requiresChannel().anyRequest().requiresSecure();
        return http.build();
    }

    @Bean
    public AuthenticationProvider daoAuthenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(this.passwordEncoder());
        provider.setUserDetailsService(this.detailsService);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
