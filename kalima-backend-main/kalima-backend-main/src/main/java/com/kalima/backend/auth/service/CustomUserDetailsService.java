package com.kalima.backend.auth.service;

import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.auth.support.PersonAuthSupport;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final String GENERIC_ERROR = "Invalid email or password";

    private final PersonRepository personRepository;

    public CustomUserDetailsService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String key = EmailNormalization.normalize(email);
        if (key == null) {
            throw new UsernameNotFoundException(GENERIC_ERROR);
        }
        Person person = personRepository.findByEmail(key)
                .orElseThrow(() -> new UsernameNotFoundException(GENERIC_ERROR));

        if (!PersonAuthSupport.isAllowedAuthenticatedSession(person)) {
            throw new UsernameNotFoundException(GENERIC_ERROR);
        }

        return User.builder()
                .username(person.getEmail())
                .password(person.getPasswordHash())
                .authorities(PersonAuthSupport.roleAuthorities(person))
                .build();
    }
}
