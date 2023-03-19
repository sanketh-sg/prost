package de.unibamberg.dsam.group6.prost.service;

import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserDetailSecurityService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = this.userRepository.findUserByUsername(username);

        if (user.isEmpty()) {
            throw new UsernameNotFoundException(String.format("User with username %s does not exit.", username));
        }

        return user.get();
    }
}
