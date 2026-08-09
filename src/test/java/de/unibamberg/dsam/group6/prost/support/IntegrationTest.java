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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.session.store-type=none")
public @interface IntegrationTest {}
