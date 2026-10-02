"use client";

import { Badge } from "@/components/ui/badge";
import {
  HR_DAY_STATUS_AR,
  HR_DAY_STATUS_TONE,
  HR_DEVICE_STATUS_AR,
  HR_DEVICE_STATUS_TONE,
  HR_EMPLOYEE_STATUS_AR,
  HR_EMPLOYEE_STATUS_TONE,
  HR_PUNCH_TYPE_AR,
  HR_PUNCH_TYPE_TONE,
  HR_VERIFY_MODE_AR,
  type HrTone,
} from "@/lib/hr/labels";
import type {
  HrAttendanceDayStatusDto,
  HrDeviceStatusDto,
  HrEmployeeStatusDto,
  HrPunchTypeDto,
  HrVerifyModeDto,
} from "@/types/hr";

export function HrEmployeeStatusBadge({ value }: { value: HrEmployeeStatusDto }) {
  return <Badge tone={HR_EMPLOYEE_STATUS_TONE[value]}>{HR_EMPLOYEE_STATUS_AR[value]}</Badge>;
}

export function HrDeviceStatusBadge({ value }: { value: HrDeviceStatusDto }) {
  return <Badge tone={HR_DEVICE_STATUS_TONE[value]}>{HR_DEVICE_STATUS_AR[value]}</Badge>;
}

export function HrPunchTypeBadge({ value }: { value: HrPunchTypeDto }) {
  return <Badge tone={HR_PUNCH_TYPE_TONE[value]}>{HR_PUNCH_TYPE_AR[value]}</Badge>;
}

export function HrDayStatusBadge({ value }: { value: HrAttendanceDayStatusDto }) {
  return <Badge tone={HR_DAY_STATUS_TONE[value]}>{HR_DAY_STATUS_AR[value]}</Badge>;
}

export function HrVerifyModeBadge({ value }: { value: HrVerifyModeDto }) {
  return <Badge tone="info">{HR_VERIFY_MODE_AR[value]}</Badge>;
}

/** Generic tone badge for statuses that map straight through. */
export function HrToneBadge({ label, tone }: { label: string; tone: HrTone }) {
  return <Badge tone={tone}>{label}</Badge>;
}