import React, { useEffect, useState } from "react";
import { CheckCircle2, ClipboardList, XCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import {
  ClassEnrollmentResponse,
  GradeComponentSetupResponse,
  GradeEntryResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  listClassEnrollments,
  listGradeComponentSetups,
  listGradeEvaluationComponents,
  publishGrades
} from "../api";
import GradeSheetTable from "./GradeSheetTable";
import Select from "@/components/ui/Select";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";

interface GradePublishDetailProps {
  classId: number | null;
  classLabel: string | null;
  teacherName: string | null;
  onPublished: () => void;
}

function setupLabel(t: (key: string, options?: Record<string, unknown>) => string, s: GradeComponentSetupResponse): string {
  return t("publishDetail.setupLabel", {
    term: s.academicTermName,
    label: t(`common.evaluationType.${s.evaluationType}`)
  });
}

/** UC-20 bước 2-5 (V44): chi tiết 1 lớp đã chọn từ danh sách — cùng bảng điểm như màn Giáo viên nhập, Duyệt hoặc Từ chối tất cả bản ghi Chờ duyệt (SUBMITTED) trong 1 lần. */
export default function GradePublishDetail({ classId, classLabel, teacherName, onPublished }: GradePublishDetailProps) {
  const { t } = useTranslation("academic-grades");
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [selectedSetupId, setSelectedSetupId] = useState<number | null>(null);
  const [components, setComponents] = useState<GradeEvaluationComponentResponse[]>([]);
  const [loadedEntries, setLoadedEntries] = useState<GradeEntryResponse[]>([]);
  const [loadedResults, setLoadedResults] = useState<GradeEvaluationResultResponse[]>([]);
  const [sheetVersion, setSheetVersion] = useState(0);
  const [deciding, setDeciding] = useState(false);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setSetups([]);
    setSelectedSetupId(null);
    setComponents([]);
    setEnrollments([]);
    setError(null);
    if (!classId) return;
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
    listGradeComponentSetups(classId)
      .then((s) => {
        setSetups(s);
        if (s.length > 0) setSelectedSetupId(s[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("publishDetail.loadSetupsError")));
  }, [classId]);

  useEffect(() => {
    setComponents([]);
    if (!selectedSetupId) return;
    listGradeEvaluationComponents(selectedSetupId).then(setComponents).catch(() => undefined);
  }, [selectedSetupId]);

  const submittedEntryIds = loadedEntries.filter((e) => e.status === "SUBMITTED").map((e) => e.id);
  const submittedResultIds = loadedResults.filter((r) => r.status === "SUBMITTED").map((r) => r.id);
  const submittedCount = submittedEntryIds.length + submittedResultIds.length;

  const handleApproveAll = async () => {
    if (submittedCount === 0) return;
    setDeciding(true);
    setError(null);
    try {
      await publishGrades({ action: "APPROVE", gradeEntryIds: submittedEntryIds, gradeEvaluationResultIds: submittedResultIds });
      setSheetVersion((v) => v + 1);
      onPublished();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("publishDetail.approveError"));
    } finally {
      setDeciding(false);
    }
  };

  const handleRejectAll = async () => {
    if (submittedCount === 0) return;
    setDeciding(true);
    setError(null);
    try {
      await publishGrades({
        action: "REJECT",
        gradeEntryIds: submittedEntryIds,
        gradeEvaluationResultIds: submittedResultIds,
        rejectReason: rejectReason.trim() || undefined
      });
      setSheetVersion((v) => v + 1);
      setShowRejectModal(false);
      setRejectReason("");
      onPublished();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("publishDetail.rejectError"));
    } finally {
      setDeciding(false);
    }
  };

  if (!classId) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
        <ClipboardList className="w-12 h-12 text-slate-300" />
        <div>
          <h3 className="text-sm font-bold text-slate-700">{t("publishDetail.emptyTitle")}</h3>
          <p className="text-xs text-slate-400 mt-1">{t("publishDetail.emptyDescription")}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display">{t("publishDetail.detailTitle", { classLabel })}</span>
          <p className="text-[10px] text-slate-400 mt-0.5">{t("publishDetail.teacherPrefix", { teacherName: teacherName ?? t("common.unknownTeacher") })}</p>
        </div>
        {setups.length > 1 && (
          <Select
            value={selectedSetupId ?? ""}
            onChange={(e) => setSelectedSetupId(e.target.value ? Number(e.target.value) : null)}
            className="bg-white border border-slate-200 text-xs p-1.5 rounded-lg focus:outline-none"
          >
            {setups.map((s) => (
              <option key={s.id} value={s.id}>
                {setupLabel(t, s)}
              </option>
            ))}
          </Select>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-4 rounded-lg">{error}</div>}

      {selectedSetupId && components.length > 0 ? (
        <GradeSheetTable
          key={`${classId}-${selectedSetupId}-${sheetVersion}`}
          classId={classId}
          setupId={selectedSetupId}
          scaleType={setups.find((s) => s.id === selectedSetupId)?.scaleType ?? "POINT_10"}
          components={components}
          enrollments={enrollments}
          onLoaded={(entries, results) => {
            setLoadedEntries(entries);
            setLoadedResults(results);
          }}
        />
      ) : (
        <p className="text-xs text-slate-400 italic p-6 text-center">{t("publishDetail.noComponents")}</p>
      )}

      <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2">
        <Button variant="danger" size="sm" disabled={deciding || submittedCount === 0} onClick={() => setShowRejectModal(true)}>
          <XCircle className="w-3.5 h-3.5" />
          {t("publishDetail.rejectAll")}
        </Button>
        <Button variant="dark" size="sm" disabled={deciding || submittedCount === 0} onClick={handleApproveAll}>
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
          {deciding ? t("publishDetail.processing") : submittedCount === 0 ? t("publishDetail.noneToApprove") : t("publishDetail.approveCount", { count: submittedCount })}
        </Button>
      </div>

      <Modal
        open={showRejectModal}
        onClose={() => setShowRejectModal(false)}
        title={t("publishDetail.rejectModalTitle", { count: submittedCount })}
        footer={
          <>
            <Button variant="secondary" size="sm" onClick={() => setShowRejectModal(false)}>
              {t("publishDetail.cancel")}
            </Button>
            <Button variant="danger" size="sm" disabled={deciding} onClick={handleRejectAll}>
              {deciding ? t("publishDetail.rejecting") : t("publishDetail.confirmReject")}
            </Button>
          </>
        }
      >
        <div className="space-y-2">
          <label className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">{t("publishDetail.rejectReasonLabel")}</label>
          <textarea
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            rows={3}
            placeholder={t("publishDetail.rejectReasonPlaceholder")}
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
          />
          <p className="text-[10px] text-slate-400">{t("publishDetail.rejectReasonHint")}</p>
        </div>
      </Modal>
    </div>
  );
}
