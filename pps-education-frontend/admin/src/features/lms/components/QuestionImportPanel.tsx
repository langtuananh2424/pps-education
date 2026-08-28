import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import {
  ExamTeacherType,
  ExerciseSkillCategory,
  QuestionImportResponse,
  QuestionImportedRow,
  downloadExamQuestionImportWordTemplate,
  downloadQuestionImportWordTemplate,
  importExamQuestions,
  importQuestions
} from "../api";

type FileFormat = "xlsx" | "docx";

interface QuestionImportPanelProps {
  bankId?: number;
  examId?: number;
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — khi truyền kèm (luồng "Soạn Bài
   * mới" theo Đề), lọc mẫu Excel + tải mẫu Word chỉ còn loại khớp Nhóm kỹ năng đã chọn ở bước 1
   * (skillCategoryKinds.ts phía FE, SKILL_CATEGORY_KIND_TOKENS phía backend). Bỏ trống (dùng ở trang
   * Ngân hàng câu hỏi độc lập, không có ngữ cảnh Bài) = hiện đủ tất cả như trước.
   */
  skillCategory?: ExerciseSkillCategory;
  teacherType?: ExamTeacherType;
  onImported: (createdQuestions: QuestionImportedRow[]) => void;
}

/** Mirror SKILL_CATEGORY_KIND_TOKENS ở QuestionImportService.java (backend) — nguồn chân lý DUY NHẤT
 * cho lọc file mẫu Excel phía FE, KHÔNG tự thêm/bớt token khác backend. */
const SKILL_CATEGORY_KIND_TOKENS: Record<string, string[]> = {
  VOCAB_GRAMMAR: ["TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "DIEN_TU_NHOM", "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI"],
  WRITING: ["TU_LUAN"],
  LISTENING: ["TRAC_NGHIEM_VOICE", "NGHE_NOP_AUDIO", "NGHE_DIEN_TU"]
};

/** Mirror VALID_KINDS ở QuestionImportService.java (backend) — nguồn cho dropdown "Loại câu hỏi mặc định". */
const ALL_KIND_TOKENS = [
  "TRAC_NGHIEM", "TRAC_NGHIEM_VOICE", "DIEN_TU", "DIEN_TU_NHOM", "TU_LUAN", "SPEAKING",
  "DIEN_TU_HOP_TU_VUNG", "DIEN_TU_HOP_TU_VUNG_ANH", "SAP_XEP_CAU", "SAP_XEP_CHU_CAI",
  "NGHE_NOP_AUDIO", "NGHE_DIEN_TU"
];

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * soạn đề nhanh — panel dùng chung cho cả trang "Ngân hàng câu hỏi" (import
 * độc lập vào 1 bank) lẫn bước "Soạn đề" trong "Soạn & giao đề" (import rồi
 * tự gắn luôn vào đề đang soạn — xem CreateAndAssignExerciseModal.tsx).
 * KHÔNG dùng chung components/ui/ImportExcelButton.tsx vì cái đó cứng 1
 * định dạng .xlsx và đang dùng ở 7 nơi khác — hỗ trợ thêm .docx ở đây sẽ
 * phải sửa hành vi chung không cần thiết cho các nơi kia.
 */
export default function QuestionImportPanel({ bankId, examId, skillCategory, teacherType, onImported }: QuestionImportPanelProps) {
  const { t } = useTranslation("lms-question-authoring");
  const [format, setFormat] = useState<FileFormat>("xlsx");
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<QuestionImportResponse | null>(null);
  // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — "Loại câu hỏi mặc định": khớp thói quen thật
  // "1 Ex chỉ 1 loại câu hỏi" nên cả file thường cùng 1 giá trị — chọn 1 lần ở đây thay vì gõ lại cột
  // "Loại câu hỏi" ở MỌI dòng trong file. Rỗng ("") = giữ hành vi cũ (bắt buộc mỗi dòng tự ghi loại).
  const [defaultKind, setDefaultKind] = useState("");
  const allowedKindTokens = teacherType === "FOREIGN" ? SKILL_CATEGORY_KIND_TOKENS.LISTENING : skillCategory ? SKILL_CATEGORY_KIND_TOKENS[skillCategory] : undefined;
  const defaultKindOptions = allowedKindTokens ?? ALL_KIND_TOKENS;

  const handleDownloadTemplate = async () => {
    setError(null);
    if (format === "xlsx") {
      // Cột cố định của mẫu Excel — ExcelQuestionRowParser.java đọc theo TÊN header (dòng 1), không
      // theo vị trí cột, nên thứ tự/ngôn ngữ header đổi được miễn còn khớp alias (xem
      // QuestionImportFieldAliases.java) — mảng dưới đây chỉ cần khớp ĐÚNG THỨ TỰ với mảng `headers`.
      // 12 dòng ví dụ demo đủ 12 loại UI hỗ trợ (bổ sung 2026-08-26: NGHE_NOP_AUDIO/NGHE_DIEN_TU cho
      // GV nước ngoài; bổ sung 2026-08-28: DIEN_TU_NHOM — "Cách B", 1 dòng tạo N Question riêng cùng
      // groupKey, mirror FillInBlankGroupBuilder.tsx), y hệt nội dung buildTemplateBlocks() bên backend
      // (QuestionImportService.java) để 2 định dạng nhất quán.
      const headers = t("questionImportPanel.excelHeaders", { returnObjects: true }) as string[];
      const sampleRows: string[][] = [
        ["TRAC_NGHIEM", "EASY", "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
          "", "", "", "1", t("questionImportPanel.excelSampleExplanations.geo"), "geo,easy"],
        ["TRAC_NGHIEM_VOICE", "MEDIUM", "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "B",
          "https://example-r2.dev/lms/questions/audio/mau.mp3", "", "sheep", "1", "", ""],
        ["DIEN_TU", "", "She ___ (go) to school every day.", "", "", "", "", "goes",
          "", "", "", "1", t("questionImportPanel.excelSampleExplanations.presentSimple"), ""],
        ["DIEN_TU_NHOM", "", "Tom is very ___.|English is my ___ subject.|Our football ___ helps us win the game.", "", "", "", "", "smart|favourite|coach",
          "", "", "activity, advanced, beginner, classmate, smart, coach, competition, course, favourite, geography, history, practice", "1",
          t("questionImportPanel.excelSampleExplanations.fillInBlankGroup"), ""],
        ["TU_LUAN", "HARD", "Write a 150-word essay about your favorite hobby.", "", "", "", "", "",
          "", "https://example-r2.dev/lms/questions/images/mau.png", "", "2", t("questionImportPanel.excelSampleExplanations.essayRubric"), ""],
        ["SPEAKING", "", "Read the following sentence aloud.", "", "", "", "", "",
          "", "", "enthusiasm, literature, variety", "1", "", ""],
        ["DIEN_TU_HOP_TU_VUNG", "", "She ___ to school every day. He ___ football on Sundays.", "", "", "", "", "goes|plays",
          "", "", "", "1", t("questionImportPanel.excelSampleExplanations.wordBank"), ""],
        ["DIEN_TU_HOP_TU_VUNG_ANH", "", "1. The cat is ___ the bed. 2. The ball is ___ the box.", "", "", "", "", "under|next to",
          "", "https://example-r2.dev/lms/questions/images/mau-phong.png", "under, next to, behind, in front of, on", "1",
          t("questionImportPanel.excelSampleExplanations.wordBankPicture"), ""],
        ["SAP_XEP_CAU", "", "Sắp xếp thành câu hoàn chỉnh.", "", "", "", "", "This|is|a|pen",
          "", "", "", "1", t("questionImportPanel.excelSampleExplanations.sentenceBuilding"), ""],
        ["SAP_XEP_CHU_CAI", "", "Sắp xếp chữ cái thành từ đúng (nghĩa: nụ cười).", "", "", "", "", "s|m|i|l|e",
          "", "https://example-r2.dev/lms/questions/images/mau-smile.png", "", "1",
          t("questionImportPanel.excelSampleExplanations.letterScramble"), ""],
        ["NGHE_NOP_AUDIO", "", "Listen to the audio and record your answer.", "", "", "", "", "",
          "https://example-r2.dev/lms/questions/audio/mau-nghe.mp3", "", "", "1",
          t("questionImportPanel.excelSampleExplanations.listeningAudioSubmission"), ""],
        ["NGHE_DIEN_TU", "", "Listen and fill in the blank: She usually ___ to work.", "", "", "", "", "drives",
          "https://example-r2.dev/lms/questions/audio/mau-nghe-dien-tu.mp3", "", "", "1",
          t("questionImportPanel.excelSampleExplanations.listeningFillInBlank"), ""]
      ];
      // Bổ sung 2026-08-28 (đã xác nhận với người dùng) — defaultKind ưu tiên CAO HƠN lọc theo Nhóm kỹ
      // năng: GV đã chọn cụ thể 1 loại thì chỉ cần đúng 1 dòng ví dụ loại đó để copy xuống nhiều dòng,
      // không cần cả bảng tra cứu nhiều loại nữa.
      const filteredRows = defaultKind
        ? sampleRows.filter((row) => row[0] === defaultKind)
        : allowedKindTokens
          ? sampleRows.filter((row) => allowedKindTokens.includes(row[0]))
          : sampleRows;
      const blob = buildXlsxTemplateBlob(headers, filteredRows);
      downloadBlob(blob, "mau-soan-cau-hoi.xlsx");
      return;
    }
    setDownloadingTemplate(true);
    try {
      const blob = await (examId
        ? downloadExamQuestionImportWordTemplate(skillCategory, teacherType, defaultKind || undefined)
        : downloadQuestionImportWordTemplate(defaultKind || undefined));
      downloadBlob(blob, "mau-soan-cau-hoi.docx");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("questionImportPanel.downloadTemplateFailed"));
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".xlsx") && !lower.endsWith(".docx")) {
      setError(t("questionImportPanel.invalidFileType"));
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = examId
        ? await importExamQuestions(examId, file, defaultKind || undefined)
        : bankId
          ? await importQuestions(bankId, file, defaultKind || undefined)
          : (() => { throw new Error(t("common.missingExamOrBankContext")); })();
      setResult(res);
      if (res.successRows > 0) onImported(res.createdQuestions);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("questionImportPanel.importFailed"));
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
        {(["xlsx", "docx"] as FileFormat[]).map((f) => (
          <button
            key={f}
            type="button"
            onClick={() => setFormat(f)}
            className={`text-[11px] font-bold px-3 py-1.5 rounded-md transition-all ${
              format === f ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {f === "xlsx" ? t("questionImportPanel.formatExcel") : t("questionImportPanel.formatWord")}
          </button>
        ))}
      </div>

      <div>
        <label className="block font-bold text-slate-600 mb-1 text-[10px] uppercase tracking-wider">{t("questionImportPanel.defaultKindLabel")}</label>
        <select
          value={defaultKind}
          onChange={(e) => setDefaultKind(e.target.value)}
          className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red"
        >
          <option value="">{t("questionImportPanel.defaultKindNone")}</option>
          {defaultKindOptions.map((token) => (
            <option key={token} value={token}>
              {t(`questionImportPanel.kindLabels.${token}`)}
            </option>
          ))}
        </select>
        <p className="text-[9px] text-slate-400 mt-1">{t("questionImportPanel.defaultKindHint")}</p>
      </div>

      <button
        type="button"
        onClick={handleDownloadTemplate}
        disabled={downloadingTemplate}
        className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
      >
        <Download className="w-4 h-4" />
        {downloadingTemplate ? t("common.loading") : t("questionImportPanel.downloadTemplate", { format })}
      </button>

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={submitting}
        className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50"
      >
        <UploadCloud className="w-6 h-6 text-brand-orange" />
        <span className="text-xs font-bold text-slate-700">
          {submitting ? t("common.creating") : t("questionImportPanel.clickToChooseFile", { format })}
        </span>
      </button>
      <input ref={inputRef} type="file" accept=".xlsx,.docx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {result && (
        <div className="space-y-3">
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
              <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
              <div className="text-[10px] text-slate-400">{t("questionImportPanel.totalRows")}</div>
            </div>
            <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
              <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
              <div className="text-[10px] text-emerald-500">{t("questionImportPanel.successRows")}</div>
            </div>
            <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
              <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
              <div className="text-[10px] text-rose-500">{t("questionImportPanel.failedRows")}</div>
            </div>
          </div>

          {result.errorSummary.length > 0 && (
            <div className="border border-rose-100 rounded-lg overflow-hidden">
              <div className="bg-rose-50 px-3 py-1.5 text-[10px] font-bold text-rose-600 uppercase">{t("questionImportPanel.errorDetailTitle")}</div>
              <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                {result.errorSummary.map((e, i) => (
                  <div key={i} className="px-3 py-1.5 text-xs flex gap-2">
                    <span className="font-mono font-bold text-slate-400 shrink-0">#{e.row}</span>
                    <span className="text-slate-600">{e.reason}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
