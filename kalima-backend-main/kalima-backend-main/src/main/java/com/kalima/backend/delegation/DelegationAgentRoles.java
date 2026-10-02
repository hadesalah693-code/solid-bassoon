package com.kalima.backend.delegation;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shared definition of which {@link DomainEnums.SystemRole} values may act as
 * a delegation ({@code Delegation.agent}) or be registered as an office
 * attorney (see {@code delegation.settings}). Kept in one place so the two
 * features can never drift apart on who counts as "staff eligible to be an
 * attorney".
 *
 * <p>Deliberately narrower than the generic "staff" role set used elsewhere
 * (e.g. {@code StaffService}), which also includes {@code ACCOUNTANT}.
 */
public final class DelegationAgentRoles {

    public static final Set<DomainEnums.SystemRole> AGENT_ROLES = EnumSet.of(
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER,
            DomainEnums.SystemRole.SECRETARY,
            DomainEnums.SystemRole.ADMIN);

    private DelegationAgentRoles() {
    }
}
