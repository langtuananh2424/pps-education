import React from "react";
import { cn } from "@/lib/cn";

interface AvatarProps {
  name: string;
  size?: "sm" | "md" | "lg";
  className?: string;
}

const sizeClasses = {
  sm: "w-8 h-8 text-[10px]",
  md: "w-9 h-9 text-xs",
  lg: "w-14 h-14 text-lg"
};

export default function Avatar({ name, size = "md", className }: AvatarProps) {
  const initial = name.trim().split(" ").slice(-1)[0]?.[0]?.toUpperCase() || "U";
  return (
    <div
      className={cn(
        "rounded-full bg-brand-gradient flex items-center justify-center font-display font-bold text-white shadow-soft shrink-0",
        sizeClasses[size],
        className
      )}
    >
      {initial}
    </div>
  );
}
