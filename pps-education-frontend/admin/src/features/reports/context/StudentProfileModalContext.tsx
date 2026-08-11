import React, { createContext, useCallback, useContext, useState } from "react";
import StudentProfileModal from "../components/StudentProfileModal";

interface StudentProfileModalContextValue {
  openStudentProfile: (studentId: number) => void;
}

const StudentProfileModalContext = createContext<StudentProfileModalContextValue | null>(null);

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — popup "Hồ sơ học tập"
 * dùng chung mọi nơi có tên học sinh (Quản lý lớp học, Điểm danh, Sổ điểm,
 * Nhận xét học viên...). Mount 1 lần ở gốc app (App.tsx) để bất kỳ trang nào
 * cũng gọi được qua useStudentProfileModal(), không phải tự quản lý state
 * mở/đóng modal riêng từng nơi.
 */
export function StudentProfileModalProvider({ children }: { children: React.ReactNode }) {
  const [studentId, setStudentId] = useState<number | null>(null);

  const openStudentProfile = useCallback((id: number) => setStudentId(id), []);
  const close = useCallback(() => setStudentId(null), []);

  return (
    <StudentProfileModalContext.Provider value={{ openStudentProfile }}>
      {children}
      {studentId !== null && <StudentProfileModal studentId={studentId} onClose={close} />}
    </StudentProfileModalContext.Provider>
  );
}

export function useStudentProfileModal(): StudentProfileModalContextValue {
  const ctx = useContext(StudentProfileModalContext);
  if (!ctx) {
    throw new Error("useStudentProfileModal phải được gọi bên trong StudentProfileModalProvider");
  }
  return ctx;
}
