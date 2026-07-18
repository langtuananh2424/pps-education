import React, { useEffect, useState } from "react";
import { Award } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { GradeEntryResponse, listGrades } from "../api";
import ComingSoon from "./ComingSoon";

interface GradesTabProps {
  studentId: number;
  classId: number;
}

export default function GradesTab({ studentId, classId }: GradesTabProps) {
  const [grades, setGrades] = useState<GradeEntryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    listGrades(studentId, classId)
      .then(setGrades)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được điểm số."))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <Award className="text-teal" /> Điểm số đã được duyệt
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
