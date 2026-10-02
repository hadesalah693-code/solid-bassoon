import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  HrAttendanceDayRowDto,
  HrAttendanceDeviceDto,
  HrAttendanceDeviceRequestDto,
  HrAttendanceRecordDto,
  HrAttendanceSummaryDto,
  HrDepartmentDto,
  HrDepartmentRequestDto,
  HrDeviceProtocolDto,
  HrDeviceStatusDto,
  HrDeviceTestDto,
  HrDeviceUserDto,
  HrEmployeeDto,
  HrEmployeeRequestDto,
  HrEmployeeStatusDto,
  HrManualPunchRequestDto,
  HrPositionDto,
  HrPositionRequestDto,
  HrPunchTypeDto,
  HrSyncResultDto,
  HrVerifyModeDto,
} from "@/types/hr";

const HR = "/hr";

/** Drops empty values so the backend treats them as "filter not set". */
function clean<T extends Record<string, unknown>>(query: T) {
  return Object.fromEntries(
    Object.entries(query).filter(
      ([, v]) => v !== undefined && v !== null && v !== "",
    ),
  ) as Record<string, string | number | boolean>;
}

// ---------------------------------------------------------------- departments

export type HrDepartmentsListQuery = {
  page?: number;
  size?: number;
  active?: boolean;
  search?: string;
};

export const hrDepartmentsApi = {
  list: (q: HrDepartmentsListQuery = {}) =>
    request<PagedResponse<HrDepartmentDto>>(`${HR}/departments`, {
      query: clean({
        page: q.page ?? 0,
        size: q.size ?? 20,
        active: q.active,
        search: q.search?.trim(),
      }),
    }),

  /** Flat list for the department picker on the employee form. */
  options: () => request<HrDepartmentDto[]>(`${HR}/departments/options`),

  get: (id: string) => request<HrDepartmentDto>(`${HR}/departments/${id}`),

  create: (body: HrDepartmentRequestDto) =>
    request<HrDepartmentDto>(`${HR}/departments`, { method: "POST", body }),

  update: (id: string, body: HrDepartmentRequestDto) =>
    request<HrDepartmentDto>(`${HR}/departments/${id}`, { method: "PUT", body }),

  remove: (id: string) =>
    request<void>(`${HR}/departments/${id}`, { method: "DELETE" }),
};

// ------------------------------------------------------------------ positions

export type HrPositionsListQuery = {
  page?: number;
  size?: number;
  departmentId?: string;
  active?: boolean;
  search?: string;
};

export const hrPositionsApi = {
  list: (q: HrPositionsListQuery = {}) =>
    request<PagedResponse<HrPositionDto>>(`${HR}/positions`, {
      query: clean({
        page: q.page ?? 0,
        size: q.size ?? 20,
        departmentId: q.departmentId,
        active: q.active,
        search: q.search?.trim(),
      }),
    }),

  /** Flat list for the position picker on the employee form. */
  options: () => request<HrPositionDto[]>(`${HR}/positions/options`),

  get: (id: string) => request<HrPositionDto>(`${HR}/positions/${id}`),

  create: (body: HrPositionRequestDto) =>
    request<HrPositionDto>(`${HR}/positions`, { method: "POST", body }),

  update: (id: string, body: HrPositionRequestDto) =>
    request<HrPositionDto>(`${HR}/positions/${id}`, { method: "PUT", body }),

  remove: (id: string) =>
    request<void>(`${HR}/positions/${id}`, { method: "DELETE" }),
};

// ------------------------------------------------------------------ employees

export type HrEmployeesListQuery = {
  page?: number;
  size?: number;
  departmentId?: string;
  positionId?: string;
  status?: HrEmployeeStatusDto;
  search?: string;
};

export const hrEmployeesApi = {
  list: (q: HrEmployeesListQuery = {}) =>
    request<PagedResponse<HrEmployeeDto>>(`${HR}/employees`, {
      query: clean({
        page: q.page ?? 0,
        size: q.size ?? 20,
        departmentId: q.departmentId,
        positionId: q.positionId,
        status: q.status,
        search: q.search?.trim(),
      }),
    }),

  /** Punch IDs already taken, shown as a hint so a clerk avoids duplicates. */
  deviceUserIds: () => request<string[]>(`${HR}/employees/device-user-ids`),

  get: (id: string) => request<HrEmployeeDto>(`${HR}/employees/${id}`),

  create: (body: HrEmployeeRequestDto) =>
    request<HrEmployeeDto>(`${HR}/employees`, { method: "POST", body }),

  update: (id: string, body: HrEmployeeRequestDto) =>
    request<HrEmployeeDto>(`${HR}/employees/${id}`, { method: "PUT", body }),

  remove: (id: string) =>
    request<void>(`${HR}/employees/${id}`, { method: "DELETE" }),
};

// -------------------------------------------------------------------- devices

export type HrDevicesListQuery = {
  page?: number;
  size?: number;
  protocol?: HrDeviceProtocolDto;
  status?: HrDeviceStatusDto;
  search?: string;
};

export const hrDevicesApi = {
  list: (q: HrDevicesListQuery = {}) =>
    request<PagedResponse<HrAttendanceDeviceDto>>(`${HR}/attendance-devices`, {
      query: clean({
        page: q.page ?? 0,
        size: q.size ?? 20,
        protocol: q.protocol,
        status: q.status,
        search: q.search?.trim(),
      }),
    }),

  get: (id: string) => request<HrAttendanceDeviceDto>(`${HR}/attendance-devices/${id}`),

  create: (body: HrAttendanceDeviceRequestDto) =>
    request<HrAttendanceDeviceDto>(`${HR}/attendance-devices`, {
      method: "POST",
      body,
    }),

  update: (id: string, body: HrAttendanceDeviceRequestDto) =>
    request<HrAttendanceDeviceDto>(`${HR}/attendance-devices/${id}`, {
      method: "PUT",
      body,
    }),

  remove: (id: string) =>
    request<void>(`${HR}/attendance-devices/${id}`, { method: "DELETE" }),

  /** Connectivity check for a saved device. */
  test: (id: string) =>
    request<HrDeviceTestDto>(`${HR}/attendance-devices/${id}/test`, {
      method: "POST",
    }),

  /** Connectivity check for a device being added, before it is saved. */
  testUnsaved: (ipAddress: string, port?: number) =>
    request<HrDeviceTestDto>(`${HR}/attendance-devices/test`, {
      method: "POST",
      query: clean({ ipAddress, port }),
    }),

  /** Pulls every punch the device holds and stores the new ones. */
  sync: (id: string) =>
    request<HrSyncResultDto>(`${HR}/attendance-devices/${id}/sync`, {
      method: "POST",
    }),

  /** Punch IDs enrolled on the device. */
  users: (id: string) => request<HrDeviceUserDto[]>(`${HR}/attendance-devices/${id}/users`),
};

// ----------------------------------------------------------------- attendance

export type HrAttendanceListQuery = {
  page?: number;
  size?: number;
  deviceId?: string;
  employeeId?: string;
  punchType?: HrPunchTypeDto;
  verifyMode?: HrVerifyModeDto;
  unmatchedOnly?: boolean;
  from?: string;
  to?: string;
};

export const hrAttendanceApi = {
  list: (q: HrAttendanceListQuery = {}) =>
    request<PagedResponse<HrAttendanceRecordDto>>(`${HR}/attendance`, {
      query: clean({
        page: q.page ?? 0,
        size: q.size ?? 20,
        deviceId: q.deviceId,
        employeeId: q.employeeId,
        punchType: q.punchType,
        verifyMode: q.verifyMode,
        unmatchedOnly: q.unmatchedOnly,
        from: q.from,
        to: q.to,
      }),
    }),

  get: (id: string) => request<HrAttendanceRecordDto>(`${HR}/attendance/${id}`),

  /** Daily sheet with worked times and the late/early counters. */
  daily: (q: {
    date?: string;
    departmentId?: string;
    status?: HrEmployeeStatusDto;
  }) =>
    request<HrAttendanceSummaryDto>(`${HR}/attendance/daily`, {
      query: clean({
        date: q.date,
        departmentId: q.departmentId,
        status: q.status,
      }),
    }),

  /** Punches waiting for their punch ID to be matched to an employee. */
  unmatched: (q: { from?: string; to?: string } = {}) =>
    request<HrAttendanceRecordDto[]>(`${HR}/attendance/unmatched`, {
      query: clean({ from: q.from, to: q.to }),
    }),

  /** Re-runs the punch-ID → employee match for all unmatched punches. */
  rematch: () =>
    request<{ linked: number }>(`${HR}/attendance/rematch`, { method: "POST" }),

  /** Punch typed in by hand, for terminals that cannot be reached. */
  addManual: (body: HrManualPunchRequestDto) =>
    request<HrAttendanceRecordDto>(`${HR}/attendance/manual`, {
      method: "POST",
      body,
    }),

  remove: (id: string) =>
    request<void>(`${HR}/attendance/${id}`, { method: "DELETE" }),
};

export type { HrAttendanceDayRowDto };