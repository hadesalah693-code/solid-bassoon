import { toast } from "sonner";

import { getApiErrorMessage } from "@/lib/get-api-error-message";

export function notifySuccess(message: string, description?: string) {
  toast.success(message, { description });
}

export function notifyError(message: string, description?: string) {
  toast.error(message, { description });
}

export function notifyInfo(message: string, description?: string) {
  toast.message(message, { description });
}

export function notifyApiError(error: unknown, fallback: string) {
  notifyError(getApiErrorMessage(error, fallback));
}
