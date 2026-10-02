import type { Party } from "@/types/common";

/** `hr_employees.status`. */
export type HrEmployeeStatusDto = "ACTIVE" | "ON_LEAVE" | "TERMINATED";

/** `hr_employees.contract_type`. */
export type HrContractTypeDto =
  | "PERMANENT"
  | "FIXED_TERM"
  | "TEMPORARY"
  | "TRAINING"
  | "OTHER";

/** `hr_employees.gender`. */
export type HrGenderDto = "MALE" | "FEMALE";

/** How the office reaches the device. */
export type HrDeviceProtocolDto = "ZK_TECO_TCP" | "I_CLOCK_PUSH" | "MANUAL";

/** `hr_attendance_devices.status`. */
export type HrDeviceStatusDto = "ACTIVE" | "DISABLED";

/** Direction of a punch reported by the device. */
export type HrPunchTypeDto =
  | "CHECK_IN"
  | "CHECK_OUT"
  | "BREAK_OUT"
  | "BREAK_IN"
  | "OVERTIME_IN"
  | "OVERTIME_OUT"
  | "UNKNOWN";

/** Verification method behind a punch. */
export type HrVerifyModeDto =
  | "PASSWORD"
  | "FINGERPRINT"
  | "FINGER_VEIN"
  | "FACE"
  | "PALM"
  | "CARD"
  | "MANUAL"
  | "UNKNOWN";

/** Computed status of a row on the daily sheet. */
export type HrAttendanceDayStatusDto =
  | "PRESENT"
  | "ABSENT"
  | "LATE"
  | "EARLY_LEAVE";

// ---------------------------------------------------------------- departments

export type HrDepartmentDto = {
  id: string;
  name: string;
  code: string | null;
  parentId: string | null;
  parentName: string | null;
  manager: Party | null;
  description: string | null;
  active: boolean;
};

export type HrDepartmentRequestDto = {
  name?: string;
  code?: string | null;
  parentId?: string | null;
  managerId?: string | null;
  description?: string | null;
  active?: boolean | null;
};

// ------------------------------------------------------------------ positions

export type HrPositionDto = {
  id: string;
  title: string;
  code: string | null;
  departmentId: string | null;
  departmentName: string | null;
  description: string | null;
  active: boolean;
};

export type HrPositionRequestDto = {
  title?: string;
  code?: string | null;
  departmentId?: string | null;
  description?: string | null;
  active?: boolean | null;
};

// ------------------------------------------------------------------ employees

export type HrEmployeeDto = {
  id: string;
  employeeNumber: string;
  fullName: string;
  departmentId: string | null;
  departmentName: string | null;
  positionId: string | null;
  positionTitle: string | null;
  person: Party | null;
  deviceUserId: string | null;
  deviceUserPin: string | null;
  cardNumber: string | null;
  nationalId: string | null;
  gender: HrGenderDto | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  hireDate: string | null;
  contractType: HrContractTypeDto | null;
  contractEndDate: string | null;
  basicSalary: number | null;
  status: HrEmployeeStatusDto;
  notes: string | null;
  terminatedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/**
 * `deviceUserId` is the punch ID held on the terminal — it is what attendance
 * records are matched on, so it must stay unique per office.
 */
export type HrEmployeeRequestDto = {
  employeeNumber?: string;
  fullName?: string;
  departmentId?: string | null;
  positionId?: string | null;
  personId?: string | null;
  deviceUserId?: string | null;
  deviceUserPin?: string | null;
  cardNumber?: string | null;
  nationalId?: string | null;
  gender?: HrGenderDto | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  hireDate?: string | null;
  contractType?: HrContractTypeDto | null;
  contractEndDate?: string | null;
  basicSalary?: number | null;
  status?: HrEmployeeStatusDto | null;
  notes?: string | null;
};

// -------------------------------------------------------------------- devices

export type HrAttendanceDeviceDto = {
  id: string;
  name: string;
  location: string | null;
  ipAddress: string;
  port: number;
  protocol: HrDeviceProtocolDto;
  serialNumber: string | null;
  status: HrDeviceStatusDto;
  lastSyncAt: string | null;
  lastSyncMessage: string | null;
};

export type HrAttendanceDeviceRequestDto = {
  name?: string;
  location?: string | null;
  ipAddress?: string;
  port?: number | null;
  protocol?: HrDeviceProtocolDto | null;
  serialNumber?: string | null;
  status?: HrDeviceStatusDto | null;
};

/** Result of the "test connection" button. */
export type HrDeviceTestDto = {
  reachable: boolean;
  ipAddress: string;
  port: number;
  serialNumber: string | null;
  firmware: string | null;
  deviceTime: string | null;
  message: string;
};

/** Outcome of a device sync, shown as a toast after the button is pressed. */
export type HrSyncResultDto = {
  deviceId: string;
  deviceName: string;
  fetched: number;
  inserted: number;
  skipped: number;
  unmatched: number;
  maxPunchedAt: string | null;
  message: string;
};

/** A punch ID enrolled on the device. */
export type HrDeviceUserDto = {
  uid: number;
  deviceUserId: string;
  name: string;
  role: number;
  cardNumber: number;
};

// ----------------------------------------------------------------- attendance

export type HrAttendanceRecordDto = {
  id: string;
  deviceId: string | null;
  deviceName: string | null;
  employeeId: string | null;
  employeeName: string | null;
  employeeNumber: string | null;
  deviceUserId: string;
  punchedAt: string;
  punchType: HrPunchTypeDto;
  verifyMode: HrVerifyModeDto;
  workCode: string | null;
  /** Present on the response as a derived flag; true when no employee is linked. */
  unmatched?: boolean;
};

export type HrManualPunchRequestDto = {
  deviceId?: string;
  deviceUserId?: string;
  punchedAt?: string;
  punchType?: HrPunchTypeDto | null;
  verifyMode?: HrVerifyModeDto | null;
  workCode?: string | null;
};

export type HrAttendanceDayRowDto = {
  date: string;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  departmentName: string | null;
  positionTitle: string | null;
  employeeStatus: HrEmployeeStatusDto;
  firstPunch: string;
  lastPunch: string;
  workedMinutes: number;
  status: HrAttendanceDayStatusDto;
  punches: HrAttendanceRecordDto[];
};

export type HrAttendanceSummaryDto = {
  date: string;
  totalEmployees: number;
  present: number;
  absent: number;
  late: number;
  earlyLeave: number;
  unmatchedPunches: number;
  rows: HrAttendanceDayRowDto[];
};