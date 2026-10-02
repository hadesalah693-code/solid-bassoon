import type { PageAssistantContext } from "@/lib/assistant/types";

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

export function parsePageContext(pathname: string): PageAssistantContext {
  const caseMatch = pathname.match(new RegExp(`/cases/(${UUID})`, "i"));
  if (caseMatch?.[1]) {
    return {
      caseId: caseMatch[1],
      pageLabel: "صفحة القضية",
      pageKind: "case",
    };
  }

  const delegationMatch = pathname.match(
    new RegExp(`/delegations/(${UUID})`, "i"),
  );
  if (delegationMatch?.[1]) {
    return {
      delegationId: delegationMatch[1],
      pageLabel: "صفحة التوكيل",
      pageKind: "delegation",
    };
  }

  const disputeMatch = pathname.match(new RegExp(`/disputes/(${UUID})`, "i"));
  if (disputeMatch?.[1]) {
    return {
      disputeId: disputeMatch[1],
      pageLabel: "صفحة النزاع",
      pageKind: "dispute",
    };
  }

  const documentMatch = pathname.match(
    new RegExp(`/documents/(${UUID})`, "i"),
  );
  if (documentMatch?.[1]) {
    return {
      documentId: documentMatch[1],
      pageLabel: "صفحة المستند",
      pageKind: "document",
    };
  }

  return {};
}

export function describePageContext(ctx: PageAssistantContext): string {
  if (!ctx.pageLabel) return "";
  const parts = [ctx.pageLabel];
  if (ctx.caseId) parts.push("— قضية مرتبطة");
  if (ctx.delegationId) parts.push("— توكيل نشط");
  if (ctx.disputeId) parts.push("— نزاع نشط");
  if (ctx.documentId) parts.push("— مستند");
  return parts.join(" ");
}
