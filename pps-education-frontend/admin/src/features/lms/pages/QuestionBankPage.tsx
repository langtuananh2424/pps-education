import React, { useEffect, useMemo, useState } from "react";
import {
  Archive,
  CheckSquare,
  Database,
  Edit3,
  Filter,
  HelpCircle,
  Mic,
  Plus,
  Search,
  Sparkles,
  Type as TypeIcon,
  UploadCloud,
  Volume2,
  X
} from "lucide-react";
import { useApp } from "@/context/AppContext";
import { CurriculumResponse, listCurriculums } from "@/features/academic/api";
import {
  QuestionBankResponse,
  QuestionDifficulty,
  QuestionResponse,
  QuestionType,
  createQuestionBank,
  listQuestionBanksByCurriculum,
  listQuestions,
  updateQuestion
} from "../api";
import QuestionEditorForm from "../components/QuestionEditorForm";
import QuestionImportPanel from "../components/QuestionImportPanel";
import Button from "@/components/ui/Button";
import { ApiError } from "@/lib/apiClient";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";
import Pagination from "@/components/ui/Pagination";
import { useDialog } from "@/components/ui/DialogProvider";

interface FlatQuestionRow {
  question: QuestionResponse;
  bank: QuestionBankResponse;
  curriculum: CurriculumResponse;
}

const typeBadge: Record<QuestionType, { label: string; icon: typeof CheckSquare; className: string }> = {
  MULTIPLE_CHOICE: { label: "Trắc nghiệm", icon: CheckSquare, className: "text-emerald-700 bg-emerald-50 border-emerald-100" },
  MULTIPLE_ANSWER: { label: "Trắc nghiệm nhiều đáp án", icon: CheckSquare, className: "text-teal-700 bg-teal-50 border-teal-100" },
  TRUE_FALSE: { label: "Đúng — Sai", icon: CheckSquare, className: "text-cyan-700 bg-cyan-50 border-cyan-100" },
  ESSAY: { label: "Tự luận", icon: Edit3, className: "text-purple-700 bg-purple-50 border-purple-100" },
  SPEAKING: { label: "Speaking", icon: Mic, className: "text-rose-700 bg-rose-50 border-rose-100" },
  FILL_IN_BLANK: { label: "Điền từ", icon: TypeIcon, className: "text-amber-700 bg-amber-50 border-amber-100" }
};

const difficultyBadge: Record<QuestionDifficulty, string> = {
  EASY: "text-emerald-600 bg-emerald-50 border-emerald-100",
  MEDIUM: "text-amber-600 bg-amber-50 border-amber-100",
  HARD: "text-rose-600 bg-rose-50 border-rose-100"
};
const difficultyLabels: Record<QuestionDifficulty, string> = { EASY: "Dễ (Easy)", MEDIUM: "Trung bình", HARD: "Khó (Hard)" };

/**
 * UC-40 bước 1: soạn ngân hàng câu hỏi TRƯỚC, độc lập với việc giao bài — thiết
 * kế theo tham chiếu Google AI Studio người dùng cung cấp, đã ánh xạ lại đúng
 * enum/field thật của backend (6 questionType + skill/difficulty thật, "cấp độ"
 * lấy từ QuestionBankResponse.level thay cho field "grade" tự do không có thật).
 */
export default function QuestionBankPage() {
  const { hasPermission } = useApp();
  // lms.exercise.manage đã bị tách nhỏ (hạt nhân hóa V62) thành lms.question-bank.create/update/view
  // — trang này thao tác cả tạo (ngân hàng/câu hỏi) lẫn sửa (trạng thái ngân hàng/câu hỏi).
  const canManage = hasPermission("lms.question-bank.create") || hasPermission("lms.question-bank.update");

  const [rows, setRows] = useState<FlatQuestionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState<QuestionType | "ALL">("ALL");
  const [difficultyFilter, setDifficultyFilter] = useState<QuestionDifficulty | "ALL">("ALL");
  const [levelFilter, setLevelFilter] = useState<string>("ALL");

  const [selectedQuestionId, setSelectedQuestionId] = useState<number | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [editingRow, setEditingRow] = useState<FlatQuestionRow | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  const loadAll = () => {
    setLoading(true);
    setError(null);
    listCurriculums()
      .then(async (curriculums) => {
        const perCurriculum = await Promise.all(
          curriculums.map(async (curriculum) => {
            const banks = await listQuestionBanksByCurriculum(curriculum.id).catch(() => [] as QuestionBankResponse[]);
            const perBank = await Promise.all(
              banks.map(async (bank) => {
                const questions = await listQuestions(bank.id).catch(() => [] as QuestionResponse[]);
                return questions.map((question) => ({ question, bank, curriculum }));
              })
            );
            return perBank.flat();
          })
        );
        setRows(perCurriculum.flat());
      })
      .catch(() => setError("Không tải được ngân hàng câu hỏi."))
      .finally(() => setLoading(false));
  };

  useEffect(loadAll, []);

  const levels = useMemo(() => Array.from(new Set(rows.map((r) => r.bank.level).filter((l): l is string => !!l))), [rows]);

  const filteredRows = rows.filter(({ question, bank }) => {
    const term = searchTerm.trim().toLowerCase();
    const matchesSearch = !term || question.content.toLowerCase().includes(term) || String(question.id).includes(term);
    const matchesType = typeFilter === "ALL" || question.questionType === typeFilter;
    const matchesDifficulty = difficultyFilter === "ALL" || question.difficulty === difficultyFilter;
    const matchesLevel = levelFilter === "ALL" || bank.level === levelFilter;
    return matchesSearch && matchesType && matchesDifficulty && matchesLevel;
  });

  // Toàn bộ câu hỏi của MỌI khung chương trình được tải hết 1 lần (loadAll flatten nested), có thể
  // lên tới hàng trăm/nghìn câu — backend chưa hỗ trợ phân trang endpoint này, phân trang phía client
  // theo đúng kết quả ĐÃ lọc. Reset về trang 1 mỗi khi bộ lọc/dữ liệu đổi để không kẹt ở trang rỗng.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [rows, searchTerm, typeFilter, difficultyFilter, levelFilter]);
  const pageRows = filteredRows.slice(page * pageSize, (page + 1) * pageSize);

  const selectedRow = rows.find((r) => r.question.id === selectedQuestionId) ?? null;

  const upsertRow = (updated: QuestionResponse, bank: QuestionBankResponse, curriculum: CurriculumResponse) => {
    setRows((prev) => {
      const exists = prev.some((r) => r.question.id === updated.id);
      if (exists) return prev.map((r) => (r.question.id === updated.id ? { ...r, question: updated } : r));
      return [{ question: updated, bank, curriculum }, ...prev];
    });
    setSelectedQuestionId(updated.id);
  };

  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — ẩn câu hỏi khỏi Ngân hàng.
   * listQuestions() ở backend chỉ trả câu hỏi ACTIVE nên câu vừa ẩn biến mất khỏi danh sách ngay —
   * trang này chưa có bộ lọc "xem câu đã ẩn" nào để khôi phục lại qua UI, nên cảnh báo rõ trước khi ẩn.
   */
  const handleArchive = async (row: FlatQuestionRow) => {
    if (
      !(await confirmDialog(
        `Ẩn câu hỏi Q-${row.question.id}? Câu hỏi sẽ biến mất khỏi danh sách này (không ảnh hưởng Bài đã dùng câu hỏi này trước đó) — hiện chưa có cách khôi phục lại qua giao diện.`,
        { danger: true }
      ))
    ) {
      return;
    }
    try {
      await updateQuestion(row.question.id, {
        content: row.question.content,
        audioUrl: row.question.audioUrl ?? undefined,
        imageUrl: row.question.imageUrl ?? undefined,
        referencePassage: row.question.referencePassage ?? undefined,
        explanation: row.question.explanation ?? undefined,
        correctAnswerText: row.question.correctAnswerText ?? undefined,
        defaultPoints: row.question.defaultPoints ?? undefined,
        tags: row.question.tags ?? undefined,
        status: "ARCHIVED"
      });
      setRows((prev) => prev.filter((r) => r.question.id !== row.question.id));
      if (selectedQuestionId === row.question.id) setSelectedQuestionId(null);
      showToast("Đã ẩn câu hỏi thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Ẩn câu hỏi thất bại.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900 flex items-center gap-2">
            <Database className="w-5 h-5 text-brand-red" />
            Ngân hàng câu hỏi
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Soạn sẵn câu hỏi đa phương thức (Trắc nghiệm, Nghe, Tự luận, Speaking) theo khung chương trình — khi giao bài ở "Soạn & giao đề" chỉ cần chọn lại từ đây.
          </p>
        </div>
        {canManage && (
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" onClick={() => setShowImportModal(true)}>
              <UploadCloud className="w-3.5 h-3.5" />
              Nhập câu hỏi hàng loạt
            </Button>
            <Button variant="primary" size="sm" onClick={() => setShowCreateModal(true)}>
              <Plus className="w-3.5 h-3.5" />
              Thêm câu hỏi mới
            </Button>
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-8 space-y-4">
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase tracking-wider">
              <Filter className="w-3.5 h-3.5 text-brand-red" />
              Bộ lọc tìm kiếm
            </div>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              <div className="relative md:col-span-2">
                <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
                <input
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Tìm theo nội dung câu hỏi, mã ID..."
                  className="w-full bg-white border border-slate-200 text-xs pl-9 pr-3 py-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-red"
                />
              </div>
              <Select
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value as QuestionType | "ALL")}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2.5 rounded-lg focus:outline-none"
              >
                <option value="ALL">Tất cả loại câu hỏi</option>
                {(Object.entries(typeBadge) as [QuestionType, (typeof typeBadge)[QuestionType]][]).map(([value, meta]) => (
                  <option key={value} value={value}>
                    {meta.label}
                  </option>
                ))}
              </Select>
              <Select
                value={difficultyFilter}
                onChange={(e) => setDifficultyFilter(e.target.value as QuestionDifficulty | "ALL")}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2.5 rounded-lg focus:outline-none"
              >
                <option value="ALL">Tất cả độ khó</option>
                {Object.entries(difficultyLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>

            {levels.length > 0 && (
              <div className="flex flex-wrap items-center gap-1.5 pt-2 border-t border-slate-200/60">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mr-1">Cấp độ:</span>
                <button
                  onClick={() => setLevelFilter("ALL")}
                  className={`text-[10px] font-bold px-2.5 py-1 rounded-md transition-all ${
                    levelFilter === "ALL" ? "bg-brand-red text-white" : "bg-white border border-slate-200 text-slate-600 hover:bg-slate-100"
                  }`}
                >
                  Tất cả cấp độ
                </button>
                {levels.map((level) => (
                  <button
                    key={level}
                    onClick={() => setLevelFilter(level)}
                    className={`text-[10px] font-bold px-2.5 py-1 rounded-md transition-all ${
                      levelFilter === level ? "bg-brand-red text-white" : "bg-white border border-slate-200 text-slate-600 hover:bg-slate-100"
                    }`}
                  >
                    {level}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200 text-slate-500 font-bold text-[10px] tracking-widest uppercase">
                    <th className="px-4 py-3 text-center w-16">Mã ID</th>
                    <th className="px-4 py-3">Nội dung câu hỏi</th>
                    <th className="px-4 py-3 w-36 text-center">Phân loại</th>
                    <th className="px-4 py-3 w-24 text-center">Độ khó</th>
                    <th className="px-4 py-3 w-28 text-center">Ngân hàng</th>
                    <th className="px-4 py-3 w-16 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {loading ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-12 text-center text-slate-400">
                        Đang tải...
                      </td>
                    </tr>
                  ) : filteredRows.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-12 text-center text-slate-400 font-medium">
                        Không tìm thấy câu hỏi nào khớp bộ lọc.
                      </td>
                    </tr>
                  ) : (
                    pageRows.map((row) => {
                      const { question, bank } = row;
                      const meta = typeBadge[question.questionType];
                      const Icon = meta.icon;
                      const isSelected = selectedQuestionId === question.id;
                      return (
                        <tr
                          key={question.id}
                          onClick={() => setSelectedQuestionId(question.id)}
                          className={`hover:bg-slate-50/60 transition-colors cursor-pointer ${isSelected ? "bg-orange-50/40" : ""}`}
                        >
                          <td className="px-4 py-3.5 text-center font-mono font-bold text-brand-red bg-slate-50/40">Q-{question.id}</td>
                          <td className="px-4 py-3.5">
                            <div className="font-bold text-slate-800 line-clamp-2 leading-relaxed">{question.content}</div>
                            {question.explanation && (
                              <p className="text-[10px] text-slate-400 font-medium italic truncate mt-1">Hướng dẫn: {question.explanation}</p>
                            )}
                          </td>
                          <td className="px-4 py-3.5 text-center">
                            <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-md border ${meta.className}`}>
                              <Icon className="w-3 h-3" />
                              {meta.label}
                            </span>
                            {question.skill === "LISTENING" && (
                              <span className="inline-flex items-center gap-0.5 text-[9px] font-bold text-blue-600 mt-1">
                                <Volume2 className="w-2.5 h-2.5" /> Nghe
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3.5 text-center">
                            {question.difficulty && (
                              <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${difficultyBadge[question.difficulty]}`}>
                                {difficultyLabels[question.difficulty]}
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3.5 text-center font-bold text-slate-700 text-[11px]">{bank.level ?? bank.name}</td>
                          <td className="px-4 py-3.5 text-center">
                            {canManage && (
                              <div className="flex items-center justify-center gap-1">
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setEditingRow(row);
                                  }}
                                  className="p-1 text-slate-400 hover:text-slate-800 hover:bg-slate-100 rounded transition-all"
                                  title="Chỉnh sửa câu hỏi"
                                >
                                  <Edit3 className="w-3.5 h-3.5" />
                                </button>
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleArchive(row);
                                  }}
                                  className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-all"
                                  title="Ẩn câu hỏi"
                                >
                                  <Archive className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            {!loading && filteredRows.length > 0 && (
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={filteredRows.length}
                itemLabel="câu hỏi"
                onPageChange={setPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setPage(0);
                }}
              />
            )}
          </div>
        </div>

        <div className="lg:col-span-4 bg-slate-50 border border-slate-200 p-5 rounded-2xl space-y-4 lg:sticky lg:top-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-2">
            <span className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
              <Sparkles className="w-4 h-4 text-brand-red" />
              Xem trước câu hỏi
            </span>
            <span className="text-[10px] bg-brand-red/10 text-brand-red px-2 py-0.5 rounded-full font-bold uppercase">Có đáp án</span>
          </div>

          {!selectedRow ? (
            <div className="py-16 text-center text-slate-400 space-y-2">
              <HelpCircle className="w-12 h-12 text-slate-300 mx-auto" />
              <p className="text-xs font-bold">Chưa chọn câu hỏi để xem trước</p>
              <p className="text-[10px] text-slate-500 max-w-[200px] mx-auto leading-relaxed">Nhấp vào 1 dòng trong bảng bên trái để xem chi tiết + đáp án đúng.</p>
            </div>
          ) : (
            <QuestionPreview row={selectedRow} onEdit={() => setEditingRow(selectedRow)} canManage={canManage} />
          )}
        </div>
      </div>

      {showCreateModal && (
        <CreateQuestionModal
          onClose={() => setShowCreateModal(false)}
          onCreated={(question, bank, curriculum) => {
            upsertRow(question, bank, curriculum);
            setShowCreateModal(false);
            showToast("Đã tạo câu hỏi thành công!");
          }}
        />
      )}

      {showImportModal && (
        <ImportQuestionsModal
          onClose={() => setShowImportModal(false)}
          onImported={(count) => {
            loadAll();
            showToast(`Đã nhập ${count} câu hỏi thành công!`);
          }}
        />
      )}

      {editingRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={() => setEditingRow(null)} />
          <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl">
            <div className="sticky top-0 bg-white px-6 py-4 border-b border-slate-200 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                <Database className="w-4 h-4 text-brand-red" />
                Sửa câu hỏi Q-{editingRow.question.id}
              </h3>
              <button onClick={() => setEditingRow(null)} className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6">
              <QuestionEditorForm
                questionBankId={editingRow.bank.id}
                existingQuestion={editingRow.question}
                onCreated={(updated) => {
                  upsertRow(updated, editingRow.bank, editingRow.curriculum);
                  setEditingRow(null);
                  showToast("Đã lưu câu hỏi thành công!");
                }}
                onCancel={() => setEditingRow(null)}
              />
            </div>
          </div>
        </div>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function QuestionPreview({ row, onEdit, canManage }: { row: FlatQuestionRow; onEdit: () => void; canManage: boolean }) {
  const { question } = row;
  const needsChoices: QuestionType[] = ["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-xs font-mono font-bold text-brand-red">Mã số: Q-{question.id}</span>
        <span className="text-[10px] text-slate-400 font-bold">{row.bank.level ?? row.bank.name}</span>
      </div>

      <div className="bg-white p-4 rounded-xl border border-slate-100 space-y-2">
        <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider">Nội dung câu hỏi:</p>
        <p className="text-xs font-bold text-slate-800 leading-relaxed">{question.content}</p>
      </div>

      {question.imageUrl && (
        <div className="bg-white p-3 rounded-xl border border-slate-100">
          <img src={question.imageUrl} alt="" className="w-full max-h-32 object-cover rounded-lg" />
        </div>
      )}

      {question.skill === "LISTENING" && question.audioUrl && (
        <div className="bg-white p-4 rounded-xl border border-slate-100 space-y-2">
          <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider">Mẫu phát âm & transcript:</p>
          <div className="p-3 bg-blue-50/50 border border-blue-100 rounded-lg space-y-2">
            <audio controls src={question.audioUrl} className="w-full h-8" />
            {question.referencePassage && <p className="text-[10px] text-blue-900 font-medium italic leading-relaxed">{question.referencePassage}</p>}
          </div>
        </div>
      )}

      {needsChoices.includes(question.questionType) && (
        <div className="bg-white p-4 rounded-xl border border-slate-100 space-y-1.5">
          <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-1">Phương án lựa chọn:</p>
          {question.choices.map((c) => (
            <div
              key={c.id}
              className={`p-2.5 rounded-lg border text-xs flex items-center justify-between ${
                c.isCorrect ? "bg-emerald-50 border-emerald-200 text-emerald-900 font-bold" : "bg-slate-50 border-slate-100 text-slate-700"
              }`}
            >
              <span>
                <strong>{c.choiceLabel}.</strong> {c.content}
              </span>
              {c.isCorrect && <CheckSquare className="w-3.5 h-3.5 text-emerald-600" />}
            </div>
          ))}
        </div>
      )}

      {question.questionType === "ESSAY" && (
        <div className="bg-white p-4 rounded-xl border border-slate-100 space-y-2">
          {question.referencePassage && <p className="text-[11px] text-slate-600 bg-slate-50 p-2 rounded-lg">{question.referencePassage}</p>}
          <p className="text-[10px] text-slate-500 italic bg-amber-50/50 p-2 border border-amber-100 rounded">
            Học viên nộp bài viết/scan để chấm tay ở Hàng chờ chấm bài.
          </p>
        </div>
      )}

      {question.questionType === "SPEAKING" && (
        <div className="bg-white p-4 rounded-xl border border-slate-100 space-y-2">
          <div className="p-3 bg-rose-50/40 border border-rose-100 rounded-lg space-y-1.5">
            <div className="flex items-center gap-1.5 text-[10px] text-rose-700 font-bold uppercase tracking-wider">
              <Mic className="w-3.5 h-3.5" />
              Từ khóa/mục tiêu chấm:
            </div>
            <p className="text-xs font-bold text-slate-800 leading-relaxed">{question.explanation ?? "Chưa khai báo tiêu chí chấm."}</p>
          </div>
        </div>
      )}

      {question.questionType === "FILL_IN_BLANK" && question.explanation && (
        <div className="bg-white p-4 rounded-xl border border-slate-100">
          <p className="text-[11px] text-slate-600">{question.explanation}</p>
        </div>
      )}

      {question.explanation && question.questionType !== "SPEAKING" && (
        <div className="p-3.5 bg-orange-50 border border-orange-100 rounded-xl space-y-1">
          <span className="text-[9px] font-bold text-brand-red uppercase tracking-wider block">Giải thích / tiêu chí chấm:</span>
          <p className="text-[11px] text-slate-700 leading-relaxed">{question.explanation}</p>
        </div>
      )}

      {canManage && (
        <button onClick={onEdit} className="w-full bg-white hover:bg-slate-100 border border-slate-200 text-slate-800 font-bold py-1.5 rounded-lg text-xs flex items-center justify-center gap-1.5">
          <Edit3 className="w-3.5 h-3.5" />
          Cập nhật
        </button>
      )}
    </div>
  );
}

function CreateQuestionModal({
  onClose,
  onCreated
}: {
  onClose: () => void;
  onCreated: (question: QuestionResponse, bank: QuestionBankResponse, curriculum: CurriculumResponse) => void;
}) {
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [curriculumId, setCurriculumId] = useState<number | null>(null);
  const [banks, setBanks] = useState<QuestionBankResponse[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [showNewBankForm, setShowNewBankForm] = useState(false);

  useEffect(() => {
    listCurriculums().then((res) => {
      setCurriculums(res);
      if (res.length > 0) setCurriculumId(res[0].id);
    });
  }, []);

  useEffect(() => {
    if (!curriculumId) return;
    listQuestionBanksByCurriculum(curriculumId).then((res) => {
      setBanks(res);
      setBankId(res.length > 0 ? res[0].id : null);
    });
  }, [curriculumId]);

  const curriculum = curriculums.find((c) => c.id === curriculumId) ?? null;
  const bank = banks.find((b) => b.id === bankId) ?? null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl">
        <div className="sticky top-0 bg-white px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
            <Database className="w-4 h-4 text-brand-red" />
            Thêm câu hỏi mới vào ngân hàng
          </h3>
          <button onClick={onClose} className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100">
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Khung chương trình</label>
              <Select
                value={curriculumId ?? ""}
                onChange={(e) => setCurriculumId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              >
                {curriculums.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Ngân hàng</label>
              <Select
                value={bankId ?? ""}
                onChange={(e) => setBankId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              >
                <option value="">-- Chọn --</option>
                {banks.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {!showNewBankForm ? (
            <button type="button" onClick={() => setShowNewBankForm(true)} className="text-[11px] font-bold text-brand-red hover:underline">
              + Chưa có ngân hàng phù hợp? Tạo mới
            </button>
          ) : (
            <QuickBankForm
              curriculumId={curriculumId}
              onCreated={(created) => {
                setBanks((prev) => [...prev, created]);
                setBankId(created.id);
                setShowNewBankForm(false);
              }}
              onCancel={() => setShowNewBankForm(false)}
            />
          )}

          {bankId && curriculum && bank ? (
            <QuestionEditorForm questionBankId={bankId} onCreated={(q) => onCreated(q, bank, curriculum)} onCancel={onClose} />
          ) : (
            <p className="text-xs text-slate-400 italic py-4 text-center">Chọn hoặc tạo 1 ngân hàng để bắt đầu soạn câu hỏi.</p>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * soạn đề nhanh — chọn 1 ngân hàng rồi nhập hàng loạt qua Excel/Word, xem
 * QuestionImportPanel.tsx. Chọn khung chương trình/ngân hàng giống hệt
 * CreateQuestionModal ở trên (không tách chung vì mỗi modal cần callback
 * khác nhau khi hoàn tất).
 */
function ImportQuestionsModal({ onClose, onImported }: { onClose: () => void; onImported: (count: number) => void }) {
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [curriculumId, setCurriculumId] = useState<number | null>(null);
  const [banks, setBanks] = useState<QuestionBankResponse[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);

  useEffect(() => {
    listCurriculums().then((res) => {
      setCurriculums(res);
      if (res.length > 0) setCurriculumId(res[0].id);
    });
  }, []);

  useEffect(() => {
    if (!curriculumId) return;
    listQuestionBanksByCurriculum(curriculumId).then((res) => {
      setBanks(res);
      setBankId(res.length > 0 ? res[0].id : null);
    });
  }, [curriculumId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl">
        <div className="sticky top-0 bg-white px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
            <Database className="w-4 h-4 text-brand-red" />
            Nhập câu hỏi hàng loạt (UC-40)
          </h3>
          <button onClick={onClose} className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100">
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Khung chương trình</label>
              <Select
                value={curriculumId ?? ""}
                onChange={(e) => setCurriculumId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              >
                {curriculums.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Ngân hàng</label>
              <Select
                value={bankId ?? ""}
                onChange={(e) => setBankId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
              >
                <option value="">-- Chọn --</option>
                {banks.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {bankId ? (
            <QuestionImportPanel bankId={bankId} onImported={(created) => onImported(created.length)} />
          ) : (
            <p className="text-xs text-slate-400 italic py-4 text-center">Chọn 1 ngân hàng để bắt đầu nhập câu hỏi.</p>
          )}
        </div>
      </div>
    </div>
  );
}

export function QuickBankForm({ curriculumId, onCreated, onCancel }: { curriculumId: number | null; onCreated: (bank: QuestionBankResponse) => void; onCancel: () => void }) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [level, setLevel] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim() || !name.trim()) {
      setError("Vui lòng nhập Mã và Tên ngân hàng.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await createQuestionBank({ code: code.trim(), name: name.trim(), curriculumId: curriculumId ?? undefined, level: level.trim() || undefined });
      onCreated(created);
    } catch {
      setError("Tạo ngân hàng thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <p className="text-xs text-rose-600">{error}</p>}
      <div className="grid grid-cols-3 gap-2">
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Mã" className="bg-white border border-slate-200 text-xs p-2 rounded-lg font-mono" />
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Tên ngân hàng" className="bg-white border border-slate-200 text-xs p-2 rounded-lg" />
        <input value={level} onChange={(e) => setLevel(e.target.value)} placeholder="Cấp độ (VD: IELTS 5.5+)" className="bg-white border border-slate-200 text-xs p-2 rounded-lg" />
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang tạo..." : "Tạo ngân hàng"}
        </Button>
      </div>
    </form>
  );
}
