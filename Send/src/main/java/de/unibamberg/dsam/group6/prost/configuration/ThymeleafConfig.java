package de.unibamberg.dsam.group6.prost.configuration;

import com.github.bufferings.thymeleaf.extras.nl2br.dialect.Nl2brDialect;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;

@Configuration
public class ThymeleafConfig {

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver slr = new SessionLocaleResolver();
        slr.setDefaultLocale(Locale.US);
        return slr;
    }

    @Bean
    public Nl2brDialect dialect() {
        return new Nl2brDialect();
    }

    @Bean
    public Java8TimeDialect timeDialect() {
        return new Java8TimeDialect();
    }
}
