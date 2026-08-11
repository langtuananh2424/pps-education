import { useEffect, useState } from "react";
import {
  StudentProfileAttendanceEntry,
  StudentProfileComment,
  StudentProfileEnrollment,
  StudentProfileGradeResult,
  StudentProfileStudent,
  getStudentProfile,
} from "@/features/student/api";

export interface SkillTrendPoint {
  academicTermId: number;
  termLabel: string;
  academicYear: string | null;
  evaluationType: "MID_TERM" | "END_TERM";
  score: number;
  maxScore: number | null;
}

export interface SkillTrendSeries {
  skillCode: string;
  skillName: string;
  points: SkillTrendPoint[];
}

export interface StudentProfileData {
  student: StudentProfileStudent | null;
  enrollments: StudentProfileEnrollment[];
  allGrades: StudentProfileGradeResult[];
  skillTrends: SkillTrendSeries[];
  allComments: StudentProfileComment[];
  attendance: StudentProfileAttendanceEntry[];
  loading: boolean;
  error: string | null;
}

const EMPTY: StudentProfileData = {
  student: null,
  enrollments: [],
  allGrades: [],
  skillTrends: [],
  allComments: [],
  attendance: [],
  loading: false,
  error: null,
};

/**
 * Tải toàn bộ hồ sơ học tập của 1 học sinh (lớp đã học, điểm tổng kết + điểm
 * từng kỹ năng theo kỳ, nhận xét, điểm danh) — dùng chung cho popup Hồ sơ
 * học sinh (StudentProfileModal) và trang Hồ sơ học tập (StudentProgressPage).
 * <p>
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — trước đây hook này tự
 * "fan-out" ~200-300 request nhỏ ra BE (list lớp → với mỗi lớp list ghi
 * danh/sổ điểm/nhận xét/buổi học...), chậm không chấp nhận được khi deploy
 * thật (mỗi request cộng thêm round-trip mạng thật, khác hẳn localhost gần
 * như 0 độ trễ). Giờ gọi đúng 1 API tổng hợp (StudentProfileService, FR-REP-04)
 * — chỉ còn việc nhóm skillScores phẳng từ BE thành skillTrends theo từng
 * kỹ năng ở đây (BE đã trả đúng thứ tự thời gian sẵn, không cần tự sort lại).
 */
export function useStudentProfileData(studentId: number | null): StudentProfileData {
  const [data, setData] = useState<StudentProfileData>(EMPTY);

  useEffect(() => {
    if (!studentId) {
      setData(EMPTY);
      return;
    }
    let cancelled = false;
    setData({ ...EMPTY, loading: true });

    getStudentProfile(studentId)
      .then((profile) => {
        if (cancelled) return;

        const bySkill = new Map<string, SkillTrendSeries>();
        profile.skillScores.forEach((s) => {
          if (!bySkill.has(s.skillCode)) {
            bySkill.set(s.skillCode, { skillCode: s.skillCode, skillName: s.skillName, points: [] });
          }
          bySkill.get(s.skillCode)!.points.push({
            academicTermId: s.academicTermId,
            termLabel: `${s.academicTermName} ${s.evaluationType === "MID_TERM" ? "GK" : "CK"}`,
            academicYear: s.academicYear,
            evaluationType: s.evaluationType,
            score: s.score,
            maxScore: s.maxScore,
          });
        });

        setData({
          student: profile.student,
          enrollments: profile.enrollments,
          allGrades: profile.gradeResults,
          skillTrends: Array.from(bySkill.values()),
          allComments: profile.comments,
          attendance: profile.attendance,
          loading: false,
          error: null,
        });
      })
      .catch(() => {
        if (!cancelled) {
          setData({ ...EMPTY, loading: false, error: "Không tải được hồ sơ học tập." });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [studentId]);

  return data;
}
