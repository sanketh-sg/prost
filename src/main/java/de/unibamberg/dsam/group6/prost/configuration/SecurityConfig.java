package de.unibamberg.dsam.group6.prost.configuration;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

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
        // Matchers are explicit rather than bare Strings because the dev profile
        // registers the H2 console servlet, and with more than one mappable servlet
        // Security refuses to guess what a String means and fails startup.
        //
        // PathPatternRequestMatcher replaces AntPathRequestMatcher, which 6.5 marked
        // for removal. `mvc` targets the DispatcherServlet; `h2` targets the console
        // servlet, which needs its own base path.
        var mvc = withDefaults();
        var h2 = withDefaults().basePath("/h2-console");

        http.authorizeHttpRequests(req -> {
            req.requestMatchers(mvc.matcher("/cart/**"), mvc.matcher("/orders/**"), mvc.matcher("/user/**"))
                    .authenticated();
            req.requestMatchers(mvc.matcher("/admin/**")).hasRole("ADMIN");
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
        http.csrf(csrf -> csrf.ignoringRequestMatchers(h2.matcher("/**")));

        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain securityFilterChainProd(HttpSecurity http) throws Exception {
        // Explicit here too. Prod registers no second servlet, so bare Strings would
        // start; writing both chains the same way keeps the real differences visible.
        //
        // The authorization matrix below is duplicated from the dev chain, which is a
        // convention rather than a mechanism — it has drifted once already. Only the
        // dev chain is exercised by tests, so a prod-only change here is invisible to
        // the suite.
        var mvc = withDefaults();

        http.authorizeHttpRequests(req -> {
            req.requestMatchers(mvc.matcher("/cart/**"), mvc.matcher("/orders/**"), mvc.matcher("/user/**"))
                    .authenticated();
            req.requestMatchers(mvc.matcher("/admin/**")).hasRole("ADMIN");
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
