import React from "react";
import { UserRole } from "@/types";
import { useApp } from "@/context/AppContext";
import { mockCampuses, mockClassrooms, mockExpenses, mockInvoices, mockStudents } from "@/data/mockData";
import ExecutiveDashboard from "../components/ExecutiveDashboard";
import AcademicDashboard from "../components/AcademicDashboard";
import CampusDashboard from "../components/CampusDashboard";
import TeacherDashboard from "../components/TeacherDashboard";

function filterByCampus<T extends { campusId?: string; campusIds?: string[] }>(list: T[], selectedCampusId: string): T[] {
  if (selectedCampusId === "ALL") return list;
  return list.filter((item) => {
    if (item.campusId) return item.campusId === selectedCampusId;
    if (item.campusIds) return item.campusIds.includes(selectedCampusId);
    return true;
  });
}

export default function DashboardPage() {
  const { currentRole, selectedCampusId } = useApp();

  const students = filterByCampus(mockStudents, selectedCampusId);
  const activeStudentsCount = students.filter((s) => s.status === "STUDYING").length;
  const campusesCount = selectedCampusId === "ALL" ? mockCampuses.length : 1;
  const classrooms = mockClassrooms.filter((c) => selectedCampusId === "ALL" || c.campusId === selectedCampusId);

  const billingList = filterByCampus(mockInvoices, selectedCampusId);
  const totalPaid = billingList.filter((inv) => inv.status === "PAID").reduce((sum, item) => sum + item.finalAmount, 0);

  const totalExpenses = mockExpenses.reduce((sum, exp) => sum + exp.amount, 0);
  const profit = totalPaid - totalExpenses;

  if (
    currentRole === UserRole.SYS_ADMIN ||
    currentRole === UserRole.EXECUTIVE ||
    currentRole === UserRole.OPS_MANAGER ||
    currentRole === UserRole.STAFF ||
    currentRole === UserRole.HR_MANAGER
  ) {
    return (
      <ExecutiveDashboard
        totalPaid={totalPaid}
        totalExpenses={totalExpenses}
        profit={profit}
        activeStudentsCount={activeStudentsCount}
        campusesCount={campusesCount}
      />
    );
  }

  if (currentRole === UserRole.TEACHER) {
    return <TeacherDashboard />;
  }

  if (currentRole === UserRole.HEAD_ACADEMIC) {
    return <AcademicDashboard classrooms={classrooms} />;
  }

  return <CampusDashboard />;
}
