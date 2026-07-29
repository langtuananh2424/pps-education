import React, { useEffect, useState } from "react";
import { ClipboardList, Megaphone } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  ClassEnrollmentResponse,
  GradeComponentResponse,
  GradeEntryResponse,
  GradePeriodResponse,
  GradePeriodResultResponse,
  getClass,
  listClassEnrollments,
  listGradeComponents,
  listGradePeriods,
  publishGrades
} from "../api";
import GradeSheetTable from "./GradeSheetTable";
import Select from "@/components/ui/Select";

interface GradePublishDetailProps {
  classId: number | null;
  classLabel: string | null;
  teacherName: string | null;
  onPublished: () => void;
}

/** UC-20 bước 2-5: chi tiết 1 lớp đã chọn từ danh sách — cùng bảng điểm như màn Giáo viên nhập, Công bố tất cả bản ghi Nháp trong 1 lần. */
export default function GradePublishDetail({ classId, classLabel, teacherName, onPublished }: GradePublishDetailProps) {
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [periods, setPeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [components, setComponents] = useState<GradeComponentResponse[]>([]);
  const [loadedEntries, setLoadedEntries] = useState<GradeEntryResponse[]>([]);
  const [loadedResults, setLoadedResults] = useState<GradePeriodResultResponse[]>([]);
  const [sheetVersion, setSheetVersion] = useState(0);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setPeriods([]);
    setSelectedPeriodId(null);
    setComponents([]);
    setEnrollments([]);
    setError(null);
    if (!classId) return;
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
    getClass(classId)
      .then((cls) => listGradePeriods(cls.curriculumId))
      .then((p) => {
        setPeriods(p);
        if (p.length > 0) setSelectedPeriodId(p[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kỳ đánh giá của lớp."));
  }, [classId]);

  useEffect(() => {
    setComponents([]);
    if (!selectedPeriodId) return;
    listGradeComponents(selectedPeriodId).then(setComponents).catch(() => undefined);
  }, [selectedPeriodId]);

  const draftEntryIds = loadedEntries.filter((e) => e.status === "DRAFT").map((e) => e.id);
  const draftResultIds = loadedResults.filter((r) => r.status === "DRAFT").map((r) => r.id);
  const draftCount = draftEntryIds.length + draftResultIds.length;

  const handlePublishAll = async () => {
    if (draftCount === 0) return;
    setPublishing(true);
    setError(null);
    try {
      await publishGrades({ gradeEntryIds: draftEntryIds, gradePeriodResultIds: draftResultIds });
      setSheetVersion((v) => v + 1);
      onPublished();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Công bố điểm thất bại.");
    } finally {
      setPublishing(false);
    }
  };

  if (!classId) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
        <ClipboardList className="w-12 h-12 text-slate-300" />
        <div>
          <h3 className="text-sm font-bold text-slate-700">Chưa chọn lớp nào</h3>
          <p className="text-xs text-slate-400 mt-1">Chọn 1 lớp ở danh sách bên dưới để xem bảng điểm và công bố.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display">Chi tiết điểm chờ công bố (UC-20) — {classLabel}</span>
          <p className="text-[10px] text-slate-400 mt-0.5">GV: {teacherName ?? "Chưa rõ"}</p>
        </div>
        {periods.length > 1 && (
          <Select
            value={selectedPeriodId ?? ""}
            onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
            className="bg-white border border-slate-200 text-xs p-1.5 rounded-lg focus:outline-none"
          >
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-4 rounded-lg">{error}</div>}

      {selectedPeriodId && components.length > 0 ? (
        <GradeSheetTable
          key={`${classId}-${selectedPeriodId}-${sheetVersion}`}
          classId={classId}
          gradePeriodId={selectedPeriodId}
          components={components}
          enrollments={enrollments}
          onLoaded={(entries, results) => {
            setLoadedEntries(entries);
            setLoadedResults(results);
          }}
        />
      ) : (
        <p className="text-xs text-slate-400 italic p-6 text-center">Lớp này chưa có đầu điểm nào được cấu hình.</p>
      )}

      <div className="px-5 py-3 border-t border-slate-100 flex justify-end">
        <button
          onClick={handlePublishAll}
          disabled={publishing || draftCount === 0}
          className="px-4 py-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold rounded-lg flex items-center gap-1.5 disabled:opacity-50"
        >
          <Megaphone className="w-3.5 h-3.5 text-brand-yellow" />
          {publishing ? "Đang công bố..." : draftCount === 0 ? "Không còn điểm nháp" : `Công bố tất cả (${draftCount})`}
        </button>
      </div>
    </div>
  );
}
