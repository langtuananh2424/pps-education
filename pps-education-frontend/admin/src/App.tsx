import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppProvider } from "@/context/AppContext";
import AppShell from "@/components/layout/AppShell";
import ProtectedRoute from "@/components/layout/ProtectedRoute";
import LoginPage from "@/features/auth/pages/LoginPage";
import AccessDeniedPage from "@/features/auth/pages/AccessDeniedPage";
import DashboardPage from "@/features/dashboard/pages/DashboardPage";
import UsersPage from "@/features/system-admin/pages/UsersPage";
import RolesPage from "@/features/system-admin/pages/RolesPage";
import OverridesPage from "@/features/system-admin/pages/OverridesPage";
import AuditLogPage from "@/features/system-admin/pages/AuditLogPage";
import TaskWorkflowPage from "@/features/task-workflow/pages/TaskWorkflowPage";
import HrmProfilesPage from "@/features/hrm/pages/ProfilesPage";
import DepartmentsPositionsPage from "@/features/hrm/pages/DepartmentsPositionsPage";
import HrmAttendancePage from "@/features/hrm/pages/AttendancePage";
import LeavesPage from "@/features/hrm/pages/LeavesPage";
import PayrollPage from "@/features/hrm/pages/PayrollPage";
import CRMPage from "@/features/crm/pages/CRMPage";
import StudentProfilesPage from "@/features/student/pages/ProfilesPage";
import ParentsPage from "@/features/student/pages/ParentsPage";
import StudentAttendancePage from "@/features/student/pages/AttendancePage";
import ClassesPage from "@/features/academic/pages/ClassesPage";
import MyTeachingSchedulePage from "@/features/academic/pages/MyTeachingSchedulePage";
import SyllabusPage from "@/features/academic/pages/SyllabusPage";
import GradesPage from "@/features/academic/pages/GradesPage";
import CommentsPage from "@/features/academic/pages/CommentsPage";
import QuestionBankPage from "@/features/lms/pages/QuestionBankPage";
import ExerciseAssignPage from "@/features/lms/pages/ExerciseAssignPage";
import LecturesPage from "@/features/lms/pages/LecturesPage";
import ExamsPage from "@/features/lms/pages/ExamsPage";
import BillingPage from "@/features/finance/pages/BillingPage";
import ExpensesPage from "@/features/finance/pages/ExpensesPage";
import ReportsPage from "@/features/finance/pages/ReportsPage";
import CampusesPage from "@/features/facility/pages/CampusesPage";
import RoomsPage from "@/features/facility/pages/RoomsPage";
import FeedbackPage from "@/features/facility/pages/FeedbackPage";
import PartnerPortalPage from "@/features/partner-portal/pages/PartnerPortalPage";

export default function App() {
  return (
    <AppProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          element={
            <ProtectedRoute>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/access-denied" element={<AccessDeniedPage />} />

          <Route path="/system-admin/users" element={<UsersPage />} />
          <Route path="/system-admin/roles" element={<RolesPage />} />
          <Route path="/system-admin/overrides" element={<OverridesPage />} />
          <Route path="/system-admin/audit-log" element={<AuditLogPage />} />
          <Route path="/task-workflow" element={<TaskWorkflowPage />} />
          <Route path="/hrm/profile" element={<HrmProfilesPage />} />
          <Route path="/hrm/departments-positions" element={<DepartmentsPositionsPage />} />
          <Route path="/hrm/attendance" element={<HrmAttendancePage />} />
          <Route path="/hrm/leaves" element={<LeavesPage />} />
          <Route path="/hrm/payroll" element={<PayrollPage />} />
          <Route path="/crm/leads" element={<CRMPage />} />
          <Route path="/student/profile" element={<StudentProfilesPage />} />
          <Route path="/student/parents" element={<ParentsPage />} />
          <Route path="/student/attendance" element={<StudentAttendancePage />} />
          <Route path="/academic/classes" element={<ClassesPage />} />
          <Route path="/schedule/my-timetable" element={<MyTeachingSchedulePage />} />
          <Route path="/academic/syllabus" element={<SyllabusPage />} />
          <Route path="/academic/grades" element={<GradesPage />} />
          <Route path="/academic/comments" element={<CommentsPage />} />
          <Route path="/lms/question-banks" element={<QuestionBankPage />} />
          <Route path="/lms/exercises" element={<ExerciseAssignPage />} />
          <Route path="/lms/lectures" element={<LecturesPage />} />
          <Route path="/lms/exams" element={<ExamsPage />} />
          <Route path="/finance/billing" element={<BillingPage />} />
          <Route path="/finance/expenses" element={<ExpensesPage />} />
          <Route path="/finance/reports" element={<ReportsPage />} />
          <Route path="/facility/campuses" element={<CampusesPage />} />
          <Route path="/facility/rooms" element={<RoomsPage />} />
          <Route path="/facility/feedback" element={<FeedbackPage />} />
          <Route path="/partner/syllabus" element={<PartnerPortalPage />} />
          <Route path="/partner/portal" element={<PartnerPortalPage />} />

          <Route index element={<Navigate to="/dashboard" replace />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AppProvider>
  );
}
