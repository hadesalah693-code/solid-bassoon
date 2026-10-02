import type {
  HrAttendanceDayStatusDto,
  HrContractTypeDto,
  HrDeviceProtocolDto,
  HrDeviceStatusDto,
  HrEmployeeStatusDto,
  HrGenderDto,
  HrPunchTypeDto,
  HrVerifyModeDto,
} from "@/types/hr";

export type HrTone = "success" | "warning" | "info" | "neutral" | "danger";

export const HR_EMPLOYEE_STATUS_AR: Record<HrEmployeeStatusDto, string> = {
  ACTIVE: "على رأس العمل",
  ON_LEAVE: "في إجازة",
  TERMINATED: "منتهية خدمته",
};

export const HR_EMPLOYEE_STATUS_TONE: Record<HrEmployeeStatusDto, HrTone> = {
  ACTIVE: "success",
  ON_LEAVE: "warning",
  TERMINATED: "neutral",
};

export const HR_EMPLOYEE_STATUS_OPTIONS = (
  Object.entries(HR_EMPLOYEE_STATUS_AR) as [HrEmployeeStatusDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_CONTRACT_TYPE_AR: Record<HrContractTypeDto, string> = {
  PERMANENT: "دائم",
  FIXED_TERM: "مدة محددة",
  TEMPORARY: "مؤقت",
  TRAINING: "تدريب",
  OTHER: "أخرى",
};

export const HR_CONTRACT_TYPE_OPTIONS = (
  Object.entries(HR_CONTRACT_TYPE_AR) as [HrContractTypeDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_GENDER_AR: Record<HrGenderDto, string> = {
  MALE: "ذكر",
  FEMALE: "أنثى",
};

export const HR_GENDER_OPTIONS = (
  Object.entries(HR_GENDER_AR) as [HrGenderDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_DEVICE_PROTOCOL_AR: Record<HrDeviceProtocolDto, string> = {
  ZK_TECO_TCP: "سحب مباشر عبر الشبكة (TCP)",
  I_CLOCK_PUSH: "إرسال تلقائي من الجهاز (iClock)",
  MANUAL: "إدخال يدوي",
};

export const HR_DEVICE_PROTOCOL_OPTIONS = (
  Object.entries(HR_DEVICE_PROTOCOL_AR) as [HrDeviceProtocolDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_DEVICE_STATUS_AR: Record<HrDeviceStatusDto, string> = {
  ACTIVE: "يعمل",
  DISABLED: "موقوف",
};

export const HR_DEVICE_STATUS_TONE: Record<HrDeviceStatusDto, HrTone> = {
  ACTIVE: "success",
  DISABLED: "neutral",
};

export const HR_DEVICE_STATUS_OPTIONS = (
  Object.entries(HR_DEVICE_STATUS_AR) as [HrDeviceStatusDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_PUNCH_TYPE_AR: Record<HrPunchTypeDto, string> = {
  CHECK_IN: "حضور",
  CHECK_OUT: "انصراف",
  BREAK_OUT: "خروج استراحة",
  BREAK_IN: "عودة من الاستراحة",
  OVERTIME_IN: "بداية عمل إضافي",
  OVERTIME_OUT: "نهاية عمل إضافي",
  UNKNOWN: "غير محدد",
};

export const HR_PUNCH_TYPE_TONE: Record<HrPunchTypeDto, HrTone> = {
  CHECK_IN: "success",
  CHECK_OUT: "info",
  BREAK_OUT: "neutral",
  BREAK_IN: "neutral",
  OVERTIME_IN: "warning",
  OVERTIME_OUT: "warning",
  UNKNOWN: "neutral",
};

export const HR_PUNCH_TYPE_OPTIONS = (
  Object.entries(HR_PUNCH_TYPE_AR) as [HrPunchTypeDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_VERIFY_MODE_AR: Record<HrVerifyModeDto, string> = {
  PASSWORD: "كلمة مرور",
  FINGERPRINT: "بصمة إصبع",
  FINGER_VEIN: "بصمة وريد",
  FACE: "تعرف على الوجه",
  PALM: "بصمة الكف",
  CARD: "كارت",
  MANUAL: "إدخال يدوي",
  UNKNOWN: "غير محدد",
};

export const HR_VERIFY_MODE_OPTIONS = (
  Object.entries(HR_VERIFY_MODE_AR) as [HrVerifyModeDto, string][]
).map(([value, label]) => ({ value, label }));

export const HR_DAY_STATUS_AR: Record<HrAttendanceDayStatusDto, string> = {
  PRESENT: "حاضر",
  ABSENT: "بدون تسجيل",
  LATE: "متأخر",
  EARLY_LEAVE: "انصراف مبكر",
};

export const HR_DAY_STATUS_TONE: Record<HrAttendanceDayStatusDto, HrTone> = {
  PRESENT: "success",
  ABSENT: "neutral",
  LATE: "warning",
  EARLY_LEAVE: "warning",
};

/** `480` → `8 ساعات`. */
export function formatWorkedMinutes(minutes: number): string {
  if (!minutes || minutes <= 0) {
    return "0";
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) {
    return `${rest} د`;
  }
  return rest ? `${hours} س ${rest} د` : `${hours} س`;
}