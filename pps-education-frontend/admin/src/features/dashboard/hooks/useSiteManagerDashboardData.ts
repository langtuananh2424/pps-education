import { useEffect, useState } from "react";
import { useApp } from "@/context/AppContext";
import {
  ExpiringPartnerContractResponse,
  PartnerFeedbackResponse,
  SiteResponse,
  listExpiringContracts,
  listPartnerContractsBySite,
  listPartnerFeedbacksForMySites,
  listSites
} from "@/features/facility/api";
import {
  ClassResponse,
  listClassEnrollments,
  listClasses,
  listExerciseAssignmentStats,
  listPendingComments,
  listUnpublishedGrades
} from "@/features/academic/api";
import { LeaveRequestResponse, listPendingLeaveRequestsForApprover } from "@/features/hrm/api";

export interface SiteManagerFeedbackRow extends PartnerFeedbackResponse {
  siteName: string;
}

export interface SiteManagerClassSummary {
  classId: number;
  className: string;
  siteName: string;
  studentCount: number;
  completionPercent: number;
}

export interface SiteManagerDashboardData {
  mySites: SiteResponse[];
  totalFeedbackCount: number;
  newFeedbackCount: number;
  feedbackInbox: SiteManagerFeedbackRow[];
  activeContractsCount: number;
  expiringContracts: ExpiringPartnerContractResponse[];
  pendingWarningCommentCount: number;
  pendingGradeCount: number;
  pendingLeaveCount: number;
  pendingLeaveRequests: LeaveRequestResponse[];
  totalActiveClasses: number;
  totalStudents: number;
  avgCompletionPercent: number;
  myClasses: SiteManagerClassSummary[];
}

const EMPTY_DATA: SiteManagerDashboardData = {
  mySites: [],
  totalFeedbackCount: 0,
  newFeedbackCount: 0,
  feedbackInbox: [],
  activeContractsCount: 0,
  expiringContracts: [],
  pendingWarningCommentCount: 0,
  pendingGradeCount: 0,
  pendingLeaveCount: 0,
  pendingLeaveRequests: [],
  totalActiveClasses: 0,
  totalStudents: 0,
  avgCompletionPercent: 0,
  myClasses: []
};

/**
 * Gộp dữ liệu Dashboard Quản lý điểm trường từ các API tự-phục-vụ đã có sẵn (chưa có endpoint
 * tổng hợp riêng ở backend). Phạm vi = (các) điểm trường mà `currentUser` đứng tên
 * `currentManagerUserId` (site_managers), lọc thêm theo Điểm trường đang chọn ở Header (đúng quy
 * ước lọc theo Header đã áp dụng xuyên suốt). 2 khối "công nợ/doanh thu theo site" và "tỷ lệ chuyên
 * cần theo site" (FR-FIN-04, FR-STU-03/UC-15b) chưa có API tổng hợp ở backend nên không đưa vào —
 * không tự suy diễn số liệu (business-fidelity.md), hiển thị rõ "chưa có dữ liệu" ở UI.
 */
export function useSiteManagerDashboardData() {
  const { currentUser, selectedCampusId } = useApp();
  const [data, setData] = useState<SiteManagerDashboardData>(EMPTY_DATA);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!currentUser) {
      setData(EMPTY_DATA);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);

    listSites()
      .then(async (allSites) => {
        if (cancelled) return;

        const managedSites = allSites.filter((s) => s.currentManagerUserId === currentUser.id);
        const mySites = managedSites.filter((s) => selectedCampusId === "ALL" || String(s.id) === selectedCampusId);

        if (mySites.length === 0) {
          setData(EMPTY_DATA);
          setLoading(false);
          return;
        }

        const siteIds = new Set(mySites.map((s) => s.id));
        const siteNameById = new Map(mySites.map((s) => [s.id, s.name]));

        const [feedbacks, contractsBySite, expiring, pendingComments, pendingGrades, pendingLeave, classesBySite] = await Promise.all([
          listPartnerFeedbacksForMySites().catch(() => []),
          Promise.all(mySites.map((s) => listPartnerContractsBySite(s.id).catch(() => []))),
          listExpiringContracts(30).catch(() => []),
          listPendingComments().catch(() => []),
          listUnpublishedGrades().catch(() => []),
          listPendingLeaveRequestsForApprover().catch(() => []),
          Promise.all(mySites.map((s) => listClasses({ siteId: s.id }).catch(() => [])))
        ]);

        if (cancelled) return;

        const myFeedbacks = feedbacks.filter((f) => siteIds.has(f.siteId));
        const feedbackInbox: SiteManagerFeedbackRow[] = [...myFeedbacks]
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
          .slice(0, 6)
          .map((f) => ({ ...f, siteName: siteNameById.get(f.siteId) ?? "—" }));

        const activeContractsCount = contractsBySite.flat().filter((c) => c.status === "ACTIVE").length;
        const myExpiringContracts = expiring.filter((c) => siteIds.has(c.siteId));

        const allClasses: ClassResponse[] = classesBySite.flat();
        const activeClasses = allClasses.filter((c) => c.status === "IN_PROGRESS" || c.status === "OPEN_ENROLLMENT");
        const myClassIds = new Set(allClasses.map((c) => c.id));

        const pendingWarningCommentCount = pendingComments.filter((c) => myClassIds.has(c.classId) && c.severity === "WARNING").length;
        const pendingGradeCount = pendingGrades.filter((g) => myClassIds.has(g.classId)).length;

        const [enrollmentsByClass, assignmentsByClass] = await Promise.all([
          Promise.all(activeClasses.map((c) => listClassEnrollments(c.id).catch(() => []))),
          Promise.all(activeClasses.map((c) => listExerciseAssignmentStats(c.id).catch(() => [])))
        ]);

        if (cancelled) return;

        const totalStudents = enrollmentsByClass.reduce((sum, list) => sum + list.filter((e) => e.status === "ACTIVE").length, 0);

        const myClasses: SiteManagerClassSummary[] = activeClasses
          .map((c, i) => {
            const activeAssignments = assignmentsByClass[i].filter((a) => a.status === "ACTIVE");
            const completionPercent = activeAssignments.length
              ? Math.round(activeAssignments.reduce((s, a) => s + a.completionPercent, 0) / activeAssignments.length)
              : 0;
            return {
              classId: c.id,
              className: c.name,
              siteName: c.siteName,
              studentCount: enrollmentsByClass[i].filter((e) => e.status === "ACTIVE").length,
              completionPercent
            };
          })
          .sort((a, b) => a.className.localeCompare(b.className));

        const classesWithActivity = myClasses.filter((c) => c.completionPercent > 0);
        const avgCompletionPercent = classesWithActivity.length
          ? Math.round(classesWithActivity.reduce((s, c) => s + c.completionPercent, 0) / classesWithActivity.length)
          : 0;

        setData({
          mySites,
          totalFeedbackCount: myFeedbacks.length,
          newFeedbackCount: myFeedbacks.filter((f) => f.status === "NEW").length,
          feedbackInbox,
          activeContractsCount,
          expiringContracts: myExpiringContracts,
          pendingWarningCommentCount,
          pendingGradeCount,
          pendingLeaveCount: pendingLeave.length,
          pendingLeaveRequests: pendingLeave.slice(0, 5),
          totalActiveClasses: activeClasses.length,
          totalStudents,
          avgCompletionPercent,
          myClasses
        });
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setData(EMPTY_DATA);
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [currentUser, selectedCampusId]);

  return { data, loading };
}
