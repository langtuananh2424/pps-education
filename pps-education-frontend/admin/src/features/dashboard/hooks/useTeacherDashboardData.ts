import { useEffect, useState } from "react";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import {
  ClassSessionResponse,
  ExerciseAssignmentStatsResponse,
  getMyTeachingSchedule,
  listClassEnrollments,
  listExerciseAssignmentStats
} from "@/features/academic/api";
import { listPendingGrading, listPendingListeningGrading } from "@/features/lms/api";

export interface TeacherAssignmentRow extends ExerciseAssignmentStatsResponse {
  classId: number;
  className: string;
}

export interface TeacherClassSummary {
  classId: number;
  className: string;
  studentCount: number;
  /** Trung bình completionPercent các bài ACTIVE của lớp — 0 nếu lớp chưa có bài nào đang mở. */
  completionPercent: number;
  overdueCount: number;
}

export interface TeacherWeekPoint {
  weekLabel: string;
  isCurrentWeek: boolean;
  avgCompletionPercent: number;
}

export interface TeacherDashboardData {
  totalStudents: number;
  totalClasses: number;
  pendingGradingCount: number;
  /** Tách riêng 2 nguồn gộp thành pendingGradingCount — phục vụ chú thích thật trên KPI, không hiển thị số tổng trần trụi. */
  pendingWritingGradingCount: number;
  pendingSpeakingGradingCount: number;
  overdueAssignmentCount: number;
  avgCompletionPercent: number;
  todaySessions: ClassSessionResponse[];
  recentAssignments: TeacherAssignmentRow[];
  incompleteAssignments: TeacherAssignmentRow[];
  myClasses: TeacherClassSummary[];
  weeklyCompletionTrend: TeacherWeekPoint[];
}

const EMPTY_DATA: TeacherDashboardData = {
  totalStudents: 0,
  totalClasses: 0,
  pendingGradingCount: 0,
  pendingWritingGradingCount: 0,
  pendingSpeakingGradingCount: 0,
  overdueAssignmentCount: 0,
  avgCompletionPercent: 0,
  todaySessions: [],
  recentAssignments: [],
  incompleteAssignments: [],
  myClasses: [],
  weeklyCompletionTrend: []
};

/** Đầu tuần (Thứ Hai) chứa `dateStr`, dùng làm khoá gộp nhóm theo tuần — tính thủ công, không phụ thuộc thư viện ngày tháng. */
function weekStartKey(dateStr: string): string {
  const d = new Date(dateStr);
  const dayOffset = (d.getUTCDay() + 6) % 7; // Thứ Hai = 0
  d.setUTCDate(d.getUTCDate() - dayOffset);
  return d.toISOString().slice(0, 10);
}

/** Gộp completionPercent theo tuần của availableFrom — thay cho "hoạt động học tập" chung chung vì đây là time-series thật duy nhất tự tính được từ dữ liệu đã có, không bịa thêm nguồn nào khác. */
function buildWeeklyTrend(assignments: TeacherAssignmentRow[]): TeacherWeekPoint[] {
  const buckets = new Map<string, number[]>();
  assignments.forEach((a) => {
    if (!a.availableFrom) return;
    const key = weekStartKey(a.availableFrom);
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key)!.push(a.completionPercent);
  });
  const sortedKeys = Array.from(buckets.keys()).sort().slice(-6);
  return sortedKeys.map((key, idx) => {
    const values = buckets.get(key)!;
    const d = new Date(key);
    const isCurrentWeek = idx === sortedKeys.length - 1;
    return {
      weekLabel: isCurrentWeek ? "" : `${d.getUTCDate()}/${d.getUTCMonth() + 1}`,
      isCurrentWeek,
      avgCompletionPercent: Math.round(values.reduce((s, v) => s + v, 0) / values.length)
    };
  });
}

/**
 * Gộp toàn bộ dữ liệu Dashboard Giáo viên từ các API tự-phục-vụ đã có sẵn — không có endpoint
 * tổng hợp riêng ở backend nên phải gọi theo từng lớp (useEligibleClasses, đã lọc đúng theo GV
 * đang đăng nhập + Điểm trường ở Header) rồi tự cộng dồn ở đây.
 */
export function useTeacherDashboardData() {
  const { classes, loading: classesLoading } = useEligibleClasses();
  const [data, setData] = useState<TeacherDashboardData>(EMPTY_DATA);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (classesLoading) return;

    if (classes.length === 0) {
      setData(EMPTY_DATA);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    const today = new Date().toISOString().slice(0, 10);

    Promise.all([
      Promise.all(classes.map((c) => listClassEnrollments(c.id).catch(() => []))),
      Promise.all(classes.map((c) => listExerciseAssignmentStats(c.id).catch(() => []))),
      getMyTeachingSchedule(today, today).catch(() => []),
      listPendingGrading().catch(() => []),
      listPendingListeningGrading().catch(() => [])
    ])
      .then(([enrollmentsByClass, assignmentsByClass, todaySessions, pendingGrading, pendingListening]) => {
        if (cancelled) return;

        const now = new Date();
        const totalStudents = enrollmentsByClass.reduce((sum, list) => sum + list.filter((e) => e.status === "ACTIVE").length, 0);

        const allAssignments: TeacherAssignmentRow[] = [];
        classes.forEach((c, i) => {
          assignmentsByClass[i].forEach((a) => allAssignments.push({ ...a, classId: c.id, className: c.name }));
        });

        const activeAssignments = allAssignments.filter((a) => a.status === "ACTIVE");
        const isOverdue = (a: TeacherAssignmentRow) => !!a.dueAt && new Date(a.dueAt) < now && a.completionPercent < 100;
        const overdueAssignments = activeAssignments.filter(isOverdue);

        const avgCompletionPercent = activeAssignments.length
          ? Math.round(activeAssignments.reduce((s, a) => s + a.completionPercent, 0) / activeAssignments.length)
          : 0;

        const recentAssignments = [...allAssignments]
          .sort((a, b) => new Date(b.availableFrom).getTime() - new Date(a.availableFrom).getTime())
          .slice(0, 8);

        const incompleteAssignments = [...activeAssignments]
          .filter((a) => a.completedCount < a.totalStudents)
          .sort((a, b) => (a.dueAt && b.dueAt ? new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime() : 0))
          .slice(0, 5);

        const myClasses: TeacherClassSummary[] = classes.map((c, i) => {
          const classActive = assignmentsByClass[i].filter((a) => a.status === "ACTIVE");
          const completionPercent = classActive.length
            ? Math.round(classActive.reduce((s, a) => s + a.completionPercent, 0) / classActive.length)
            : 0;
          const overdueCount = classActive.filter(
            (a) => !!a.dueAt && new Date(a.dueAt) < now && a.completionPercent < 100
          ).length;
          return {
            classId: c.id,
            className: c.name,
            studentCount: enrollmentsByClass[i].filter((e) => e.status === "ACTIVE").length,
            completionPercent,
            overdueCount
          };
        });

        setData({
          totalStudents,
          totalClasses: classes.length,
          pendingGradingCount: pendingGrading.length + pendingListening.length,
          pendingWritingGradingCount: pendingGrading.length,
          pendingSpeakingGradingCount: pendingListening.length,
          overdueAssignmentCount: overdueAssignments.length,
          avgCompletionPercent,
          todaySessions: [...todaySessions].sort((a, b) => a.startTime.localeCompare(b.startTime)),
          recentAssignments,
          incompleteAssignments,
          myClasses,
          weeklyCompletionTrend: buildWeeklyTrend(allAssignments)
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
  }, [classes, classesLoading]);

  return { data, loading: loading || classesLoading };
}
