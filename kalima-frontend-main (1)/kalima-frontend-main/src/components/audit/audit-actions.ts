/**
 * Maps the backend `AuditAction` constants to friendly Arabic labels.
 * Keep in sync with `com.kalima.backend.audit.AuditAction` on the server.
 */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: "تسجيل دخول ناجح",
  LOGIN_FAILED: "محاولة دخول فاشلة",
  LOGOUT: "تسجيل خروج",

  STAFF_CREATED: "إضافة موظف",
  STAFF_UPDATED: "تحديث بيانات موظف",
  STAFF_DISABLED: "إيقاف موظف",
  STAFF_ENABLED: "إعادة تفعيل موظف",
  PASSWORD_RESET: "إعادة تعيين كلمة مرور",
  PASSWORD_CHANGED: "تغيير كلمة مرور",

  PERSON_CREATED: "إضافة شخص",
  PERSON_UPDATED: "تحديث بيانات شخص",
  PERSON_DELETED: "حذف شخص",

  CASE_CREATED: "إنشاء قضية",
  CASE_UPDATED: "تحديث قضية",
  CASE_STATUS_CHANGED: "تغيير حالة قضية",
  CASE_ARCHIVED: "أرشفة قضية",
  CASE_RESTORED: "استعادة قضية",

  PARTICIPANT_ADDED: "إضافة طرف للقضية",
  PARTICIPANT_REMOVED: "إزالة طرف من قضية",
};
