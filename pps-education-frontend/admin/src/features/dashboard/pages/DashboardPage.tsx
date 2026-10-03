import React from "react";
import { UserRole } from "@/types";
import { useApp } from "@/context/AppContext";
import { mockCampuses, mockStudents } from "@/data/mockData";
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

  if (
    currentRole === UserRole.SYS_ADMIN ||
    currentRole === UserRole.EXECUTIVE ||
    currentRole === UserRole.OPS_MANAGER ||
    currentRole === UserRole.STAFF ||
    currentRole === UserRole.HR_MANAGER
  ) {
    return (
      <ExecutiveDashboard activeStudentsCount={activeStudentsCount} campusesCount={campusesCount} />
    );
  }

  if (currentRole === UserRole.TEACHER) {
    return <TeacherDashboard />;
  }

  if (currentRole === UserRole.HEAD_ACADEMIC) {
    return <AcademicDashboard />;
  }

  return <CampusDashboard />;
}
