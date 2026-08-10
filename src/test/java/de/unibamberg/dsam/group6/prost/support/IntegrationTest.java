package de.unibamberg.dsam.group6.prost.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the full application context with MockMvc against the dev profile
 * (H2 in-memory). Applied to every integration test so the setup lives in
 * one place.
 *
 * <p>Spring Session is disabled here. In production the app uses
 * spring-session-jdbc, which stores the session in a database table keyed by
 * its own SESSION cookie rather than in the servlet session — so MockMvc's
 * {@code .session(mockSession)} would set a session the application never
 * reads, and every cart or login assertion would fail for reasons unrelated to
 * the behavior under test.
 *
 * <p>Disabling it restores the plain servlet session so these tests exercise
 * application logic. Spring Session's own behavior is covered separately by
 * {@code SessionPersistenceTest}, which drives it through real cookies.
 *
 * <p>Set as a property rather than a {@code src/test/resources/application-dev.yml}
 * file: that filename would shadow the real dev profile config instead of
 * merging with it.
 *
 * <p>Boot 3 removed {@code spring.session.store-type}, which is what previously
 * disabled Spring Session here. Unknown properties are ignored silently rather
 * than failing, so the upgrade re-enabled Spring Session without any warning and
 * every cart assertion broke. Excluding the auto-configuration is the Boot 3
 * equivalent and cannot fail quietly — a wrong class name throws at startup.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(
        properties =
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
public @interface IntegrationTest {}
