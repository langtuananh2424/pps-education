import { Employee, LeaveRequest } from "@/types";
import { mockEmployees, mockLeaveRequests } from "@/data/mockData";

function readJson<T>(key: string, fallback: T): T {
  const saved = localStorage.getItem(key);
  if (saved) {
    try {
      return JSON.parse(saved) as T;
    } catch {
      /* fall through to fallback */
    }
  }
  localStorage.setItem(key, JSON.stringify(fallback));
  return fallback;
}

export const readStoredEmployees = (): Employee[] => readJson("pps_employees", mockEmployees);
export const writeStoredEmployees = (employees: Employee[]) => localStorage.setItem("pps_employees", JSON.stringify(employees));

export const readStoredLeaveRequests = (): LeaveRequest[] => readJson("pps_leave_requests", mockLeaveRequests);
export const writeStoredLeaveRequests = (requests: LeaveRequest[]) => localStorage.setItem("pps_leave_requests", JSON.stringify(requests));
