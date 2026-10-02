/** Mirrors backend buildTitle: "{client} / {notaryNumber or delegationNumber}". */
export function buildDelegationTitle(
  clientName: string,
  notaryNumber?: string | null,
  fallbackSuffix?: string | null,
) {
  const name = clientName.trim() || "—";
  const suffix = notaryNumber?.trim() || fallbackSuffix?.trim() || "—";
  return `${name} / ${suffix}`;
}
