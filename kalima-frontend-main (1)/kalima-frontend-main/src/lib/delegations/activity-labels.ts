export const DELEGATION_ACTION_AR: Record<string, string> = {
  DELEGATION_CREATED: "إنشاء توكيل",
  DELEGATION_UPDATED: "تحديث توكيل",
  DELEGATION_CASE_LINKED: "ربط قضية",
  DELEGATION_CASE_UNLINKED: "إلغاء ربط قضية",
  DOCUMENT_UPLOADED: "رفع مستند",
  DOCUMENT_DELETED: "حذف مستند",
};

export function delegationActionAr(action: string) {
  return DELEGATION_ACTION_AR[action] ?? action;
}
