/** Route to voice agent when the user wants an action, not legal analysis. */
const AGENT_INTENT =
  /(?:^|\s)(?:افتح|اعرض|أظهر|أنشئ|أضف|عيّن|أرسل|أغلق|جدول|سجّل|ابحث عن|جهز|حضّر|قائمة|مهمة|توكيل|توكيلات|نزاع|نزاعات|جلس|تأكيد|confirm|منته)(?:\s|$)/iu;

export function shouldRouteToAgent(
  text: string,
  options?: { isConfirm?: boolean; preferAgent?: boolean },
): boolean {
  if (options?.isConfirm || options?.preferAgent) return true;
  const trimmed = text.trim();
  if (!trimmed) return false;
  return AGENT_INTENT.test(trimmed);
}
