"use client";

import { AlertTriangle } from "lucide-react";

import { AssistantChatUI } from "@/components/assistant/AssistantChatUI";
import { PageHeader } from "@/components/shared/PageHeader";

export default function LawyerAssistantPage() {
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <PageHeader
        title="المساعد القانوني"
        description="مساعد ذكي لدعم تحليل القضايا وصياغة الأفكار للمحامي."
        className="pb-0"
      />

      <div
        role="note"
        className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900"
      >
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-600" />
        <p className="text-sm leading-6">
          المساعد يقدم اقتراحات داعمة فقط، ولا يُغني عن مراجعة المحامي أو
          قراره المهني.
        </p>
      </div>

      <AssistantChatUI variant="page" />
    </div>
  );
}
