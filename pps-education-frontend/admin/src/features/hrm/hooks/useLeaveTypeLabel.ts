import { useMemo } from "react";
import { useLeaveTypes } from "./useLeaveTypes";

export function useLeaveTypeLabel() {
  const { leaveTypes } = useLeaveTypes();

  const labelMap = useMemo(() => {
    const map: Record<string, string> = {};
    for (const type of leaveTypes) {
      map[type.code] = type.label;
    }
    return map;
  }, [leaveTypes]);

  return (code: string): string => {
    return labelMap[code] || code;
  };
}
