package com.kalima.backend.hr.device;

import com.kalima.backend.domain.entity.DomainEnums;

/** Maps the raw ZKTeco byte codes from a punch log onto the HR domain enums. */
public final class ZkPunchMapper {

    private ZkPunchMapper() {
    }

    /**
     * State byte. The 0/1 split is inverted relative to intuition: 0 is
     * check-<em>in</em>, 1 is check-<em>out</em>.
     */
    public static DomainEnums.HrPunchType toPunchType(int stateCode) {
        return switch (stateCode) {
            case 0 -> DomainEnums.HrPunchType.CHECK_IN;
            case 1 -> DomainEnums.HrPunchType.CHECK_OUT;
            case 2 -> DomainEnums.HrPunchType.BREAK_OUT;
            case 3 -> DomainEnums.HrPunchType.BREAK_IN;
            case 4 -> DomainEnums.HrPunchType.OVERTIME_IN;
            case 5 -> DomainEnums.HrPunchType.OVERTIME_OUT;
            default -> DomainEnums.HrPunchType.UNKNOWN;
        };
    }

    /**
     * Verification byte. Fingerprint terminals mostly report 1; face models use
     * 15 (face) and 20 (face + fingerprint).
     */
    public static DomainEnums.HrVerifyMode toVerifyMode(int verifyCode) {
        return switch (verifyCode) {
            case 0 -> DomainEnums.HrVerifyMode.PASSWORD;
            case 1, 11 -> DomainEnums.HrVerifyMode.FINGERPRINT;
            case 12 -> DomainEnums.HrVerifyMode.FINGER_VEIN;
            case 13 -> DomainEnums.HrVerifyMode.PALM;
            case 15, 20 -> DomainEnums.HrVerifyMode.FACE;
            case 2, 3, 4, 5, 6, 7, 8, 9, 10 -> DomainEnums.HrVerifyMode.CARD;
            default -> DomainEnums.HrVerifyMode.UNKNOWN;
        };
    }
}
