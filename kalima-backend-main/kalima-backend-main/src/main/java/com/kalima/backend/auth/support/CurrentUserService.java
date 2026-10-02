package com.kalima.backend.auth.support;

import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the currently-authenticated {@link Person} from the Spring
 * SecurityContext. The JWT filter populates the principal name with the
 * person's email, so a single lookup by email is enough.
 *
 * <p><b>Per-request caching:</b> the resolved {@link Person} is stored in a
 * {@link RequestScopedUserCache} for the lifetime of the HTTP request.
 * Subsequent calls within the same request return the cached value with no
 * additional DB round-trip.  Outside a request context (scheduled jobs,
 * integration-test slices without a servlet scope) the
 * {@link ObjectProvider} returns {@code null} and the method falls back to a
 * direct DB lookup — the original behaviour.
 */
@Service
public class CurrentUserService {

    private final PersonRepository personRepository;
    private final ObjectProvider<RequestScopedUserCache> cacheProvider;

    public CurrentUserService(PersonRepository personRepository,
                              ObjectProvider<RequestScopedUserCache> cacheProvider) {
        this.personRepository = personRepository;
        this.cacheProvider = cacheProvider;
    }

    public Person requireCurrentPerson() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }
        String email = EmailNormalization.normalize(auth.getName());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        RequestScopedUserCache cache = cacheProvider.getIfAvailable();
        if (cache != null && cache.get() != null) {
            return cache.get();
        }

        Person person = personRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required"));

        if (cache != null) {
            cache.set(person);
        }
        return person;
    }
}
