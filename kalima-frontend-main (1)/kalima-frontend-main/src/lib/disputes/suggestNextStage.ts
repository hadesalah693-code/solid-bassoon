import { disputeStageTypeAr } from "@/lib/disputes/labels";

/** Suggested next step after closing a dispute stage (UI helper). */
export type SuggestedNextStep =
  | {
      kind: "NEW_STAGE";
      stageType: string;
      titleAr: string;
      footnoteAr?: string;
    }
  | { kind: "DISPOSE"; messageAr: string };

function normOutcome(outcome: string) {
  return outcome.trim().toUpperCase();
}

export function suggestNextStageAfterClose(
  stageType: string,
  outcome: string,
): SuggestedNextStep | null {
  const st = stageType.trim().toUpperCase();
  const oc = normOutcome(outcome);

  const pick = (type: string, footnoteAr?: string): SuggestedNextStep => ({
    kind: "NEW_STAGE",
    stageType: type,
    titleAr: disputeStageTypeAr(type),
    footnoteAr,
  });

  const dispose = (messageAr: string): SuggestedNextStep => ({
    kind: "DISPOSE",
    messageAr,
  });

  switch (st) {
    case "NEGOTIATION": {
      if (oc === "SETTLED" || oc === "SUCCESS") {
        return pick(
          "FRIENDLY_SETTLEMENT",
          "إن انتهى النزاع نهائياً، يمكنك تحديث حالة النزاع إلى «مغلق» من صفحة النزاع.",
        );
      }
      if (oc === "FAILED" || oc === "NO_RESPONSE") return pick("LEGAL_NOTICE");
      if (oc === "NEEDS_FOLLOW_UP") return pick("NEGOTIATION");
      break;
    }
    case "LEGAL_NOTICE": {
      if (oc === "NO_RESPONSE" || oc === "FAILED") return pick("COURT_FILING");
      if (oc === "SETTLED" || oc === "SUCCESS") return pick("FRIENDLY_SETTLEMENT");
      if (oc === "NEEDS_FOLLOW_UP") return pick("LEGAL_NOTICE");
      break;
    }
    case "FRIENDLY_SETTLEMENT": {
      if (oc === "SETTLED" || oc === "SUCCESS") {
        return dispose(
          "بناءً على نتيجة التسوية الودية، يُنصح بإغلاق النزاع أو تحديث حالته إلى «مغلق» عند اكتمال الإجراءات.",
        );
      }
      if (oc === "FAILED") return pick("LEGAL_NOTICE");
      break;
    }
    case "MEDIATION": {
      if (oc === "SUCCESS" || oc === "SETTLED") return pick("FRIENDLY_SETTLEMENT");
      if (oc === "FAILED" || oc === "NO_RESPONSE") return pick("COURT_FILING");
      if (oc === "NEEDS_FOLLOW_UP") return pick("MEDIATION");
      break;
    }
    case "COURT_FILING": {
      if (oc === "SUCCESS") return pick("COURT_CASE");
      if (oc === "FAILED") return pick("COURT_FILING");
      if (oc === "NEEDS_FOLLOW_UP") return pick("COURT_FILING");
      break;
    }
    case "COURT_CASE": {
      if (oc === "SUCCESS") return pick("HEARINGS");
      if (oc === "NEEDS_FOLLOW_UP") return pick("COURT_CASE");
      break;
    }
    case "HEARINGS": {
      if (oc === "SUCCESS") return pick("JUDGMENT");
      if (oc === "NEEDS_FOLLOW_UP") return pick("HEARINGS");
      break;
    }
    case "JUDGMENT": {
      if (oc === "SUCCESS") return pick("EXECUTION");
      if (oc === "FAILED" || oc === "ESCALATED") return pick("COURT_CASE");
      if (oc === "NEEDS_FOLLOW_UP") return pick("JUDGMENT");
      break;
    }
    case "EXECUTION": {
      if (oc === "SUCCESS") {
        return dispose(
          "اكتمل التنفيذ — يمكنك إغلاق النزاع أو تحديث حالته عند انتهاء المتابعة.",
        );
      }
      if (oc === "NEEDS_FOLLOW_UP") return pick("EXECUTION");
      break;
    }
    default:
      break;
  }
  return null;
}
