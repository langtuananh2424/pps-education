import { useEffect, useState } from "react";
import type { LeaveTypeResponse } from "../api";
import { listLeaveTypes } from "../api";

// Cache dữ liệu loại nghỉ để tránh call API nhiều lần
let leaveTypesCache: LeaveTypeResponse[] | null = null;
let leaveTypesCachePromise: Promise<LeaveTypeResponse[]> | null = null;

export function useLeaveTypes() {
  const [leaveTypes, setLeaveTypes] = useState<LeaveTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Nếu đã cache, sử dụng luôn
    if (leaveTypesCache) {
      setLeaveTypes(leaveTypesCache);
      setLoading(false);
      return;
    }

    // Nếu đang fetch, đợi promise
    if (leaveTypesCachePromise) {
      leaveTypesCachePromise
        .then((data) => {
          leaveTypesCache = data;
          setLeaveTypes(data);
          setLoading(false);
        })
        .catch((err) => {
          setError(err instanceof Error ? err.message : "Failed to fetch leave types");
          setLoading(false);
        });
      return;
    }

    // Fetch mới
    setLoading(true);
    setError(null);
    leaveTypesCachePromise = listLeaveTypes();
    leaveTypesCachePromise
      .then((data) => {
        leaveTypesCache = data;
        setLeaveTypes(data);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to fetch leave types");
      })
      .finally(() => {
        setLoading(false);
        leaveTypesCachePromise = null;
      });
  }, []);

  return { leaveTypes, loading, error };
}
