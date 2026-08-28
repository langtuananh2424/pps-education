import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppProvider } from "@/context/AppContext";
import { DialogProvider } from "@/components/ui/DialogProvider";
import { StudentProfileModalProvider } from "@/features/reports/context/StudentProfileModalContext";
import AppShell from "@/components/layout/AppShell";
import ProtectedRoute from "@/components/layout/ProtectedRoute";
import LoginPage from "@/features/auth/pages/LoginPage";
import AccessDeniedPage from "@/features/auth/pages/AccessDeniedPage";
import DashboardPage from "@/features/dashboard/pages/DashboardPage";
import UsersPage from "@/features/system-admin/pages/UsersPage";
import RolesPage from "@/features/system-admin/pages/RolesPage";
import OverridesPage from "@/features/system-admin/pages/OverridesPage";
import AuditLogPage from "@/features/system-admin/pages/AuditLogPage";
import SystemSettingsPage from "@/features/system-admin/pages/SystemSettingsPage";
import SendNotificationPage from "@/features/system-admin/pages/SendNotificationPage";
import TaskWorkflowPage from "@/features/task-workflow/pages/TaskWorkflowPage";
import HrmProfilesPage from "@/features/hrm/pages/ProfilesPage";
import DepartmentsPositionsPage from "@/features/hrm/pages/DepartmentsPositionsPage";
import HrmAttendancePage from "@/features/hrm/pages/AttendancePage";
import AttendanceSitesPage from "@/features/hrm/pages/AttendanceSitesPage";
import ShiftsPage from "@/features/hrm/pages/ShiftsPage";
import WorkCalendarPage from "@/features/hrm/pages/WorkCalendarPage";
import EmployeeSchedulePage from "@/features/hrm/pages/EmployeeSchedulePage";
import LeavesPage from "@/features/hrm/pages/LeavesPage";
import PayrollPage from "@/features/hrm/pages/PayrollPage";
import CRMPage from "@/features/crm/pages/CRMPage";
import StudentProfilesPage from "@/features/student/pages/ProfilesPage";
import ParentsPage from "@/features/student/pages/ParentsPage";
import StudentAttendancePage from "@/features/student/pages/AttendancePage";
import ClassesPage from "@/features/academic/pages/ClassesPage";
import MyTeachingSchedulePage from "@/features/academic/pages/MyTeachingSchedulePage";
import SyllabusPage from "@/features/academic/pages/SyllabusPage";
import EntranceAssessmentPage from "@/features/academic/pages/EntranceAssessmentPage";
import GradesPage from "@/features/academic/pages/GradesPage";
import CommentsPage from "@/features/academic/pages/CommentsPage";
import HomeworkStatsPage from "@/features/academic/pages/HomeworkStatsPage";
import AssignmentStatsDetailPage from "@/features/academic/pages/AssignmentStatsDetailPage";
import BatchStatsDetailPage from "@/features/academic/pages/BatchStatsDetailPage";
import ReviewVideoAssignmentStatsDetailPage from "@/features/academic/pages/ReviewVideoAssignmentStatsDetailPage";
import QuestionBankPage from "@/features/lms/pages/QuestionBankPage";
import BookCatalogPage from "@/features/lms/pages/BookCatalogPage";
import ExerciseAssignPage from "@/features/lms/pages/ExerciseAssignPage";
import LecturesPage from "@/features/lms/pages/LecturesPage";
import DocumentBankPage from "@/features/lms/pages/DocumentBankPage";
import ExamsPage from "@/features/lms/pages/ExamsPage";
import BillingPage from "@/features/finance/pages/BillingPage";
import ExpensesPage from "@/features/finance/pages/ExpensesPage";
import ReportsPage from "@/features/finance/pages/ReportsPage";
import CampusesPage from "@/features/facility/pages/CampusesPage";
import RoomsPage from "@/features/facility/pages/RoomsPage";
import FeedbackPage from "@/features/facility/pages/FeedbackPage";
import PartnerPortalPage from "@/features/partner-portal/pages/PartnerPortalPage";
import ReportTemplatesPage from "@/features/reports/pages/ReportTemplatesPage";
import DailyCommentsAnalyticsPage from "@/features/reports/pages/DailyCommentsAnalyticsPage";
import GradesAnalyticsPage from "@/features/reports/pages/GradesAnalyticsPage";
import StudentProgressPage from "@/features/reports/pages/StudentProgressPage";
import EnrollmentMovementStatsPage from "@/features/reports/pages/EnrollmentMovementStatsPage";
import ActualPeriodsStatsPage from "@/features/reports/pages/ActualPeriodsStatsPage";
export default function App() {
  return (
    <AppProvider>
      <DialogProvider>
      <StudentProfileModalProvider>
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
          <Route path="/system-admin/settings" element={<SystemSettingsPage />} />
          <Route path="/system-admin/send-notification" element={<SendNotificationPage />} />
          <Route path="/task-workflow" element={<TaskWorkflowPage />} />
          <Route path="/hrm/profile" element={<HrmProfilesPage />} />
          <Route path="/hrm/departments-positions" element={<DepartmentsPositionsPage />} />
          <Route path="/hrm/attendance" element={<HrmAttendancePage />} />
          <Route path="/hrm/attendance-sites" element={<AttendanceSitesPage />} />
          <Route path="/hrm/shifts" element={<ShiftsPage />} />
          <Route path="/hrm/work-calendar" element={<WorkCalendarPage />} />
          <Route path="/hrm/employee-schedule" element={<EmployeeSchedulePage />} />
          <Route path="/hrm/leaves" element={<LeavesPage />} />
          <Route path="/hrm/payroll" element={<PayrollPage />} />
          <Route path="/crm/leads" element={<CRMPage />} />
          <Route path="/student/profile" element={<StudentProfilesPage />} />
          <Route path="/student/parents" element={<ParentsPage />} />
          <Route path="/student/attendance" element={<StudentAttendancePage />} />
          <Route path="/academic/classes" element={<ClassesPage />} />
          <Route path="/schedule/my-timetable" element={<MyTeachingSchedulePage />} />
          <Route path="/academic/syllabus" element={<SyllabusPage />} />
          <Route path="/academic/entrance-assessment" element={<EntranceAssessmentPage />} />
          <Route path="/academic/grades" element={<GradesPage />} />
          <Route path="/academic/comments" element={<CommentsPage />} />
          <Route path="/academic/homework-stats" element={<HomeworkStatsPage />} />
          <Route path="/academic/homework-stats/review-video/:assignmentId" element={<ReviewVideoAssignmentStatsDetailPage />} />
          <Route path="/academic/homework-stats/batch/:batchId" element={<BatchStatsDetailPage />} />
          <Route path="/academic/homework-stats/:assignmentId" element={<AssignmentStatsDetailPage />} />
          <Route path="/lms/question-banks" element={<QuestionBankPage />} />
          <Route path="/lms/book-catalog" element={<BookCatalogPage />} />
          <Route path="/lms/exercises" element={<ExerciseAssignPage />} />
          <Route path="/lms/lectures" element={<LecturesPage />} />
          <Route path="/lms/documents" element={<DocumentBankPage />} />
          <Route path="/lms/exams" element={<ExamsPage />} />
          <Route path="/finance/billing" element={<BillingPage />} />
          <Route path="/finance/expenses" element={<ExpensesPage />} />
          <Route path="/finance/reports" element={<ReportsPage />} />
          <Route path="/facility/campuses" element={<CampusesPage />} />
          <Route path="/facility/rooms" element={<RoomsPage />} />
          <Route path="/facility/feedback" element={<FeedbackPage />} />
          <Route path="/partner/syllabus" element={<PartnerPortalPage />} />
          <Route path="/partner/portal" element={<PartnerPortalPage />} />

          <Route path="/reports/templates" element={<ReportTemplatesPage />} />
          <Route path="/reports/daily-comments" element={<DailyCommentsAnalyticsPage />} />
          <Route path="/reports/grades" element={<GradesAnalyticsPage />} />
          <Route path="/reports/student-progress" element={<StudentProgressPage />} />
          <Route path="/reports/enrollment-movement" element={<EnrollmentMovementStatsPage />} />
          <Route path="/reports/actual-periods" element={<ActualPeriodsStatsPage />} />

          <Route index element={<Navigate to="/dashboard" replace />} />
        </Route>

        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
      </StudentProfileModalProvider>
      </DialogProvider>
    </AppProvider>
  );
}
