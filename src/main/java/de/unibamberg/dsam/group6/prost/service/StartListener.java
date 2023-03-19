package de.unibamberg.dsam.group6.prost.service;

import de.unibamberg.dsam.group6.prost.entity.Role;
import de.unibamberg.dsam.group6.prost.entity.User;
import de.unibamberg.dsam.group6.prost.repository.RolesRepository;
import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartListener implements ApplicationListener<ContextRefreshedEvent> {
    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder encoder;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        final var adminRole = this.rolesRepository.findByName("ROLE_ADMIN");
        final var user = this.userRepository.findUserByUsername("admin");

        if (adminRole.isEmpty()) {
            final var newAdminRole = new Role();
            newAdminRole.setName("ROLE_ADMIN");
            this.rolesRepository.saveAndFlush(newAdminRole);
        }

        if (user.isEmpty()) {
            final var admin = User.builder()
                    .username("admin")
                    .password(this.encoder.encode("admin"))
                    .roles(Set.of(this.rolesRepository.findByName("ROLE_ADMIN").orElseThrow()))
                    .birthday(LocalDate.of(1998, 2, 3))
                    .build();

            this.userRepository.saveAndFlush(admin);
        }
    }
}
