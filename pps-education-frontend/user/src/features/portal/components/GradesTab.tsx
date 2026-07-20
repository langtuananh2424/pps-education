import React, { useEffect, useState } from "react";
import { Award, Star } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { GradeEntryResponse, GradePeriodResponse, GradePeriodResultResponse, getPeriodResult, getPortalClass, listGradePeriods, listGrades } from "../api";
import ComingSoon from "./ComingSoon";

interface GradesTabProps {
  studentId: number;
  classId: number;
}

const scaleLabels: Record<GradePeriodResultResponse["scaleType"], string> = {
  NUMERIC: "Số (0–10)",
  PERCENTAGE: "Phần trăm (%)",
  BAND: "Thang chữ (Band)"
};

export default function GradesTab({ studentId, classId }: GradesTabProps) {
  const [grades, setGrades] = useState<GradeEntryResponse[]>([]);
  const [periodResults, setPeriodResults] = useState<{ period: GradePeriodResponse; result: GradePeriodResultResponse }[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      listGrades(studentId, classId),
      getPortalClass(classId)
        .then((cls) => listGradePeriods(cls.curriculumId))
        .then((periods) =>
          Promise.all(
            periods.map((period) =>
              getPeriodResult(studentId, classId, period.id)
                .then((result) => ({ period, result }))
                .catch((err) => {
                  if (err instanceof ApiError && err.status === 404) return null; // chưa có/chưa công bố cho kỳ này — bỏ qua, không phải lỗi
                  throw err;
                })
            )
          )
        )
        .then((rows) => rows.filter((r): r is { period: GradePeriodResponse; result: GradePeriodResultResponse } => r !== null))
    ])
      .then(([entries, results]) => {
        setGrades(entries);
        setPeriodResults(results);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được điểm số."))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {periodResults.length > 0 && (
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
          <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
            <Star className="text-gold" /> Điểm tổng kết (Overall)
          </h2>
          <div className="space-y-3">
            {periodResults.map(({ period, result }) => (
              <div key={period.id} className="border border-line/60 p-4 rounded-[16px] flex justify-between items-center bg-sky-2">
                <div>
                  <p className="text-xs font-extrabold text-ink">{period.name}</p>
                  <p className="text-[10px] text-muted font-bold">
                    {scaleLabels[result.scaleType]}
                    {result.level ? ` · ${result.level}` : ""}
                  </p>
                </div>
                <span className="text-lg font-extrabold text-teal-deep">{result.overallScore ?? "—"}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Award className="text-teal" /> Điểm số đã công bố
        </h2>
        {grades.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Chưa có điểm nào được công bố cho lớp này.</p>
        ) : (
          <div className="space-y-3">
            {grades.map((g) => (
              <div key={g.id} className="border border-line/60 p-4 rounded-[16px] flex justify-between items-center bg-sky-2">
                <div>
                  <p className="text-xs text-muted font-bold">{g.teacherNote || "Đầu điểm"}</p>
                  {g.absenceFlag && <span className="text-[10px] text-coral font-extrabold uppercase">Vắng — không có điểm</span>}
                </div>
                <span className="text-lg font-extrabold text-teal-deep">{g.absenceFlag ? "—" : g.score}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <ComingSoon
        title="Làm bài kiểm tra trực tuyến"
        description="Học sinh tự đăng nhập để làm quiz — đang chờ backend bổ sung API tự tra cứu hồ sơ học sinh (studentId) từ tài khoản đăng nhập."
      />
    </div>
  );
}
