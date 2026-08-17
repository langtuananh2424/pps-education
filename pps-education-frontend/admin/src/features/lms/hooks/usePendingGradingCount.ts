import { useEffect, useState } from "react";
import { useApp } from "@/context/AppContext";
import { listPendingGradingClasses } from "../api";

/** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — số LỚP đang có bài Video phản xạ chưa chấm, hiện badge Sidebar cạnh "Hàng chờ chấm bài". */
export function usePendingGradingCount() {
  const { hasPermission } = useApp();
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!hasPermission("lms.grading.manage")) {
      setCount(0);
      return;
    }
    let cancelled = false;
    listPendingGradingClasses()
      .then((classes) => {
        if (!cancelled) setCount(classes.length);
      })
      .catch(() => {
        if (!cancelled) setCount(0);
      });
    return () => {
      cancelled = true;
    };
  }, [hasPermission]);

  return count;
}
