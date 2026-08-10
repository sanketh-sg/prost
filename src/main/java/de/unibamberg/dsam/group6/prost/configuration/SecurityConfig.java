package de.unibamberg.dsam.group6.prost.configuration;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import de.unibamberg.dsam.group6.prost.service.UserDetailSecurityService;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
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
    public SecurityFilterChain securityFilterChainDev(HttpSecurity http) throws Exception {
        // antMatcher(...) rather than the String overload, for two reasons.
        //
        // 1. Correctness: this chain previously used antMatchers(), which is always an
        //    AntPathRequestMatcher. Security 6's String overload resolves to an
        //    MvcRequestMatcher instead, which matches differently — being explicit keeps
        //    the pre-upgrade semantics.
        // 2. It is required here anyway: the dev profile registers the H2 console
        //    servlet, and with more than one mappable servlet Security 6 refuses to
        //    guess which matcher a bare String means, failing startup.
        http.authorizeHttpRequests(req -> {
            req.requestMatchers(antMatcher("/cart/**"), antMatcher("/orders/**"), antMatcher("/user/**"))
                    .authenticated();
            req.requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN");
            req.anyRequest().permitAll();
        });
        http.formLogin(form -> {
            form.loginPage("/login").failureHandler((req, res, e) -> {
                this.errors.addToast(Toast.error(e.getMessage()));
                res.sendRedirect("/login");
            });
        });
        http.headers(h -> {
            h.httpStrictTransportSecurity(hsts -> hsts.disable());
            h.frameOptions(frame -> frame.disable());
        });
        http.logout(l -> l.logoutUrl("/logout"));
        http.csrf(csrf -> csrf.ignoringRequestMatchers(antMatcher("/h2-console/**")));

        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChainProd(HttpSecurity http) throws Exception {
        // antMatcher(...) here too. Prod registers no second servlet, so the String
        // overload would start — but it would resolve to an MvcRequestMatcher and
        // quietly change how these paths match. Keeping both chains explicit keeps
        // them identical to each other and to the pre-upgrade behaviour.
        http.authorizeHttpRequests(req -> {
            req.requestMatchers(antMatcher("/cart/**"), antMatcher("/orders/**"), antMatcher("/user/**"))
                    .authenticated();
            req.requestMatchers(antMatcher("/admin/**")).hasRole("ADMIN");
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

        // CSRF is on by default; kept explicit so the intent survives a re-read.
        http.csrf(csrf -> {});
        http.headers(h -> {
            h.httpStrictTransportSecurity(hsts -> {});
            h.frameOptions(frame -> frame.sameOrigin());
        });
        // requiresChannel() was deprecated in Security 6.5; redirectToHttps() is the
        // documented replacement. With no matcher configured it applies to every
        // request, matching the previous anyRequest().requiresSecure().
        http.redirectToHttps(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public AuthenticationProvider daoAuthenticationProvider() {
        // Security 6.5 deprecated the no-arg constructor and setUserDetailsService;
        // the UserDetailsService is now a constructor argument.
        var provider = new DaoAuthenticationProvider(this.detailsService);
        provider.setPasswordEncoder(this.passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
