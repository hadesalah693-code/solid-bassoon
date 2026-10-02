"use client";

import {
  Dialog,
  DialogFooter,
  type DialogFooterProps,
  type DialogProps,
} from "@/components/shared/Dialog";

/**
 * Accounting-flavoured alias of the shared {@link Dialog}. Kept as a separate
 * name so accounting screens stay self-documenting; the implementation lives in
 * shared so HR screens reuse the same overlay instead of copying it.
 */
export type AccountingDialogProps = DialogProps;

export function AccountingDialog(props: AccountingDialogProps) {
  return <Dialog {...props} />;
}

export type AccountingDialogFooterProps = DialogFooterProps;

export function AccountingDialogFooter(props: AccountingDialogFooterProps) {
  return <DialogFooter {...props} />;
}