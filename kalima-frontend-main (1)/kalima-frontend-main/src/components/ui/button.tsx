import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils/cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-[0.8125rem] font-semibold transition-[background,color,box-shadow,transform,opacity] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98] [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default:
          "bg-[var(--color-navy-800)] text-white shadow-[var(--shadow-sm)] hover:bg-[var(--color-navy-700)] hover:shadow-[var(--shadow-medium)]",
        gold: "bg-[var(--color-gold-400)] text-[var(--color-navy-900)] shadow-[var(--shadow-sm)] hover:bg-[var(--color-gold-300)] hover:shadow-[var(--shadow-medium)]",
        outline:
          "border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:bg-[var(--color-surface-muted)] hover:border-[var(--color-border-strong)]",
        ghost:
          "text-[var(--color-foreground-secondary)] hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-foreground)]",
        destructive:
          "bg-[var(--color-danger)] text-white shadow-[var(--shadow-sm)] hover:bg-red-700 hover:shadow-[var(--shadow-medium)]",
        link: "text-[var(--color-navy-700)] underline-offset-4 hover:underline",
      },
      size: {
        sm: "h-8 px-3 text-xs rounded-lg",
        md: "h-10 px-4",
        lg: "h-11 px-5 text-[0.875rem]",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: { variant: "default", size: "md" },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        ref={ref}
        className={cn(buttonVariants({ variant, size, className }))}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";

export { buttonVariants };
