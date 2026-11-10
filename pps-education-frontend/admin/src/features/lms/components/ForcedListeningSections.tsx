import { useCallback, useEffect, useState } from "react";
import { Download, Trash2, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExerciseResponse,
  ExerciseSectionResponse,
  createExerciseSection,
  deleteExerciseSection,
  importForcedListening,
  listExerciseSections
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2 rounded-lg focus:outline-none";

/** Tiêu đề cột PHẢI khớp ForcedListeningImportService (backend) — file mẫu riêng cho Nghe ép. */
const IMPORT_HEADERS = ["Bài tập con", "Mức đạt %", "Loại câu hỏi", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D", "Đáp án đúng", "URL Audio", "Tapescript", "Điểm", "Giải thích"];
const IMPORT_SAMPLES = [
  ["Part 1", "70", "TRAC_NGHIEM", "What does the speaker say?", "a library", "a hospital", "a school", "a shop", "B", "https://.../part1.mp3", "Lời thoại của đoạn nghe ứng với câu này", "1", ""],
  ["Part 1", "", "NHIEU_DAP_AN", "Which TWO reasons are given?", "to meet a friend", "to learn more", "to buy something", "to have fun", "B|D", "https://.../part1.mp3", "", "2", ""],
  ["Part 2", "50", "DIEN_TU", "The meeting starts at ___.", "", "", "", "", "9 am/9:00", "https://.../part2.mp3", "", "1", ""]
];

/** Khớp ForcedListeningService.MAX_SECTIONS (backend) — mô tả nghiệp vụ: thường 4, tối đa 12 bài tập con/Bài. */
const MAX_SECTIONS = 12;

/**
 * V226 (Nghe ép, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-09) — quản lý các bài tập con
 * của 1 Bài "Nghe ép" khi soạn đề: tạo/xoá bài tập con và chọn bài tập con đang gắn câu hỏi vào. Mỗi câu
 * hỏi thêm vào Bài phải thuộc đúng 1 bài tập con (backend tự chặn nếu thiếu).
 */
export default function ForcedListeningSections({
  exercise,
  selectedSectionId,
  onSelect,
  onError,
  onImported
}: {
  exercise: ExerciseResponse;
  selectedSectionId: number | null;
  onSelect: (sectionId: number | null) => void;
  onError: (message: string | null) => void;
  /** Gọi sau khi import thành công để cha tải lại danh sách câu hỏi đã gắn. */
  onImported: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [sections, setSections] = useState<ExerciseSectionResponse[]>([]);
  const [title, setTitle] = useState("");
  const [threshold, setThreshold] = useState("");
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importMessage, setImportMessage] = useState<string | null>(null);
  const [importErrors, setImportErrors] = useState<string[]>([]);

  const reload = useCallback(() => {
    listExerciseSections(exercise.id)
      .then((res) => {
        setSections(res);
        if (selectedSectionId == null && res.length > 0) onSelect(res[res.length - 1].id);
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exercise.id]);

  useEffect(() => {
    reload();
  }, [reload]);

  const handleAdd = async () => {
    onError(null);
    if (!title.trim()) {
      onError(t("assignModal.forcedListening.errors.titleRequired"));
      return;
    }
    setSaving(true);
    try {
      const created = await createExerciseSection(exercise.id, {
        title: title.trim(),
        passThresholdPercent: threshold ? Number(threshold) : undefined
      });
      setSections((prev) => [...prev, created]);
      onSelect(created.id);
      setTitle("");
      setThreshold("");
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.forcedListening.errors.createFailed"));
    } finally {
      setSaving(false);
    }
  };

  const handleImportFile = async (file: File | undefined) => {
    if (!file) return;
    onError(null);
    setImportMessage(null);
    setImportErrors([]);
    setImporting(true);
    try {
      const res = await importForcedListening(exercise.id, file);
      if (res.errors.length > 0) {
        setImportErrors(res.errors);
        return;
      }
      setImportMessage(t("assignModal.forcedListening.importDone", { sections: res.createdSections, questions: res.createdQuestions }));
      reload();
      onImported();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.forcedListening.errors.importFailed"));
    } finally {
      setImporting(false);
    }
  };

  const handleDelete = async (section: ExerciseSectionResponse) => {
    onError(null);
    try {
      await deleteExerciseSection(exercise.id, section.id);
      setSections((prev) => prev.filter((s) => s.id !== section.id));
      if (selectedSectionId === section.id) onSelect(null);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.forcedListening.errors.deleteFailed"));
    }
  };

  return (
    <div className="border border-sky-200 bg-sky-50/50 rounded-lg p-3 space-y-2">
      <div>
        <p className="text-[13px] font-bold text-sky-900">{t("assignModal.forcedListening.title")}</p>
        <p className="text-[12px] text-slate-500">{t("assignModal.forcedListening.hint", { max: MAX_SECTIONS })}</p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <label className="inline-flex items-center gap-1.5 text-[13px] font-bold text-slate-700 bg-white border border-slate-200 rounded-lg px-3 py-1.5 cursor-pointer hover:bg-slate-50">
          <Upload size={14} />
          {importing ? t("assignModal.forcedListening.importing") : t("assignModal.forcedListening.importButton")}
          <input
            type="file"
            accept=".xlsx"
            className="hidden"
            disabled={importing}
            onChange={(e) => {
              handleImportFile(e.target.files?.[0]);
              e.target.value = "";
            }}
          />
        </label>
        <button
          type="button"
          onClick={() => downloadBlob(buildXlsxTemplateBlob(IMPORT_HEADERS, IMPORT_SAMPLES), "mau-nghe-ep.xlsx")}
          className="inline-flex items-center gap-1.5 text-[13px] font-bold text-sky-700 hover:underline"
        >
          <Download size={14} /> {t("assignModal.forcedListening.templateButton")}
        </button>
        <span className="text-[12px] text-slate-500">{t("assignModal.forcedListening.importHint")}</span>
      </div>
      {importMessage && <p className="text-[13px] font-bold text-emerald-700">{importMessage}</p>}
      {importErrors.length > 0 && (
        <ul className="text-[13px] text-red-600 list-disc pl-5 max-h-32 overflow-y-auto">
          {importErrors.map((msg) => (
            <li key={msg}>{msg}</li>
          ))}
        </ul>
      )}

      {sections.length > 0 && (
        <ul className="space-y-1">
          {sections.map((s) => (
            <li key={s.id} className="flex items-center gap-2 text-sm">
              <label className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer">
                <input type="radio" name="forced-section" checked={selectedSectionId === s.id} onChange={() => onSelect(s.id)} />
                <span className="truncate font-bold">{s.title}</span>
                <span className="text-[12px] text-slate-500 shrink-0">
                  {t("assignModal.forcedListening.sectionMeta", { percent: s.effectivePassThresholdPercent, count: s.questionCount })}
                </span>
              </label>
              {s.questionCount === 0 && (
                <button
                  type="button"
                  onClick={() => handleDelete(s)}
                  title={t("assignModal.forcedListening.deleteTitle")}
                  className="text-slate-400 hover:text-red-600 shrink-0"
                >
                  <Trash2 size={14} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {sections.length < MAX_SECTIONS && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t("assignModal.forcedListening.titlePlaceholder")}
            className={`${inputClass} flex-1 min-w-[10rem]`}
          />
          <input
            type="number"
            min={1}
            max={100}
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            placeholder={t("assignModal.forcedListening.thresholdPlaceholder")}
            className={`${inputClass} w-28`}
          />
          <Button type="button" variant="secondary" size="sm" onClick={handleAdd} disabled={saving}>
            {t("assignModal.forcedListening.add")}
          </Button>
        </div>
      )}

      {selectedSectionId == null && <p className="text-[12px] font-bold text-amber-700">{t("assignModal.forcedListening.pickSectionHint")}</p>}
    </div>
  );
}
