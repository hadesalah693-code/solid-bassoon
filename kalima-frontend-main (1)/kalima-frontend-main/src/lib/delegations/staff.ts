import type { ClientPerson } from "@/types/person";
import type { MentionStaff } from "@/components/delegations/StaffMentionTextarea";

const STAFF_ROLES = ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] as const;

export function clientDisplayName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

export function toMentionStaff(people: ClientPerson[]): MentionStaff[] {
  return people.map((p) => ({
    id: p.id,
    name: clientDisplayName(p),
  }));
}

export { STAFF_ROLES };

export function formatAgentsLabel(agents: { fullName?: string | null }[], fallback?: { fullName?: string | null } | null) {
  const names = (agents.length > 0 ? agents : fallback ? [fallback] : [])
    .map((a) => a.fullName?.trim())
    .filter(Boolean);
  return names.length > 0 ? names.join("، ") : "—";
}

