package com.kalima.backend.auth.support;

import com.kalima.backend.domain.entity.Person;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Per-request cache for the authenticated {@link Person}.
 *
 * <p>Spring creates a fresh instance for every HTTP request and destroys it
 * when the request completes, so there is no risk of stale data leaking
 * across requests. The first call within a request hits the database;
 * every subsequent call within the same request returns the cached value
 * with zero DB overhead.
 *
 * <p>This bean must only be obtained via {@link CurrentUserService} which
 * guards against non-request contexts (scheduled jobs, async threads) by
 * using an {@link org.springframework.beans.factory.ObjectProvider} that
 * returns {@code null} when no request scope is active.
 */
@Component
@RequestScope
public class RequestScopedUserCache {

    private Person cachedPerson;

    /** Returns the cached person, or {@code null} if not yet populated. */
    public Person get() {
        return cachedPerson;
    }

    /** Stores the resolved person for subsequent calls within this request. */
    public void set(Person person) {
        this.cachedPerson = person;
    }
}
