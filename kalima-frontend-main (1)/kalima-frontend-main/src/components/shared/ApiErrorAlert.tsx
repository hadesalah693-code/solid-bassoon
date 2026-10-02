import { AlertCircle, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";

type ApiErrorAlertProps = {
  message: string;
  title?: string;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
};

export function ApiErrorAlert({
  message,
  title = "تعذر إكمال الطلب",
  onRetry,
  retryLabel = "إعادة المحاولة",
  className,
}: ApiErrorAlertProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex gap-3 rounded-xl border border-[var(--color-danger-border)] bg-[var(--color-danger-bg)] px-4 py-3.5 text-sm",
        className,
      )}
    >
      <AlertCircle
        className="mt-0.5 size-5 shrink-0 text-[var(--color-danger)]"
        aria-hidden
      />
      <div className="min-w-0 flex-1 space-y-1">
        <p className="font-bold text-[var(--color-foreground)]">{title}</p>
        <p className="text-[var(--color-danger)]">{message}</p>
        {onRetry ? (
          <div className="pt-1">
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="border-[var(--color-danger-border)] text-[var(--color-foreground)] hover:bg-[var(--color-surface)]"
              onClick={onRetry}
            >
              <RefreshCw className="size-3.5" />
              {retryLabel}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
