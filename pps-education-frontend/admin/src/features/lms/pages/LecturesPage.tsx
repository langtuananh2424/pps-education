import React, { useEffect, useState } from "react";
import { FileText, Plus, Upload, Video } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ClassResponse, CurriculumResponse, CurriculumSubjectResponse, listClasses, listCurriculums, listCurriculumSubjects } from "@/features/academic/api";
import {
  AddLessonMaterialRequest,
  addLessonMaterial,
  CreateLessonRequest,
  createLesson,
  LessonMaterialResponse,
  LessonResponse,
  LessonStatus,
  LessonType,
  listLessonMaterials,
  listLessonsByClass,
  listLessonsByCurriculum,
  updateLesson,
  UpdateLessonRequest
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const lessonTypeLabels: Record<LessonType, string> = {
  VIDEO_LECTURE: "Video bài giảng",
  PDF_DOCUMENT: "Tài liệu PDF",
  MIXED: "Kết hợp",
  LIVE_RECORDING: "Ghi hình buổi dạy trực tiếp"
};

const statusLabels: Record<LessonStatus, string> = { DRAFT: "Nháp", PUBLISHED: "Đã công bố", ARCHIVED: "Đã gỡ" };
const statusVariants: Record<LessonStatus, BadgeVariant> = { DRAFT: "neutral", PUBLISHED: "success", ARCHIVED: "danger" };

const materialTypeIcon: Record<string, React.ReactNode> = {
  VIDEO: <Video className="w-4 h-4" />,
  PDF: <FileText className="w-4 h-4" />
};

/** UC-23: Kho bài giảng — chỉ Giáo viên được phân công dạy đúng lớp/khung chương trình mới tạo/sửa được (BE tự chặn, không bypass qua permission — đã xác nhận với người dùng). */
export default function LecturesPage() {
  const { selectedCampusId } = useApp();
  const [scopeType, setScopeType] = useState<"CLASS" | "CURRICULUM">("CLASS");
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [selectedCurriculumId, setSelectedCurriculumId] = useState<number | null>(null);

  const [lessons, setLessons] = useState<LessonResponse[]>([]);
  const [loadingLessons, setLoadingLessons] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingLesson, setEditingLesson] = useState<LessonResponse | null>(null);
  const [materialsLesson, setMaterialsLesson] = useState<LessonResponse | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  /** curriculumId hiệu lực để tra học phần (subjects) — theo lớp đang chọn hoặc theo khung đang chọn trực tiếp. */
  const effectiveCurriculumId = scopeType === "CLASS" ? selectedClass?.curriculumId ?? null : selectedCurriculumId;

  useEffect(() => {
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined }).then(setClasses).catch(() => undefined);
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, [selectedCampusId]);

  const loadLessons = () => {
    if (scopeType === "CLASS" && !selectedClassId) {
      setLessons([]);
      return;
    }
    if (scopeType === "CURRICULUM" && !selectedCurriculumId) {
      setLessons([]);
      return;
    }
    setLoadingLessons(true);
    setError(null);
    const request = scopeType === "CLASS" ? listLessonsByClass(selectedClassId!) : listLessonsByCurriculum(selectedCurriculumId!);
    request
      .then(setLessons)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài giảng."))
      .finally(() => setLoadingLessons(false));
  };

  useEffect(loadLessons, [scopeType, selectedClassId, selectedCurriculumId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-xs text-slate-500 mt-1">
          Kho bài giảng (UC-23) — tệp video/PDF được xem là đã upload lên CDN từ trước, ở đây chỉ đăng ký URL phân phối + metadata.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Card className="space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex bg-slate-100 rounded-lg p-1 text-xs font-semibold">
            <button
              type="button"
              onClick={() => {
                setScopeType("CLASS");
                setSelectedCurriculumId(null);
              }}
              className={`px-3 py-1.5 rounded-md transition-all ${scopeType === "CLASS" ? "bg-white shadow-sm text-slate-800" : "text-slate-500"}`}
            >
              Theo lớp cụ thể
            </button>
            <button
              type="button"
              onClick={() => {
                setScopeType("CURRICULUM");
                setSelectedClassId(null);
              }}
              className={`px-3 py-1.5 rounded-md transition-all ${scopeType === "CURRICULUM" ? "bg-white shadow-sm text-slate-800" : "text-slate-500"}`}
            >
              Theo khung chương trình (dùng chung)
            </button>
          </div>

          {scopeType === "CLASS" ? (
            <select value={selectedClassId ?? ""} onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn lớp --</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </select>
          ) : (
            <select value={selectedCurriculumId ?? ""} onChange={(e) => setSelectedCurriculumId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-64`}>
              <option value="">-- Chọn khung chương trình --</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </select>
          )}

          <Button
            variant="primary"
            disabled={scopeType === "CLASS" ? !selectedClassId : !selectedCurriculumId}
            onClick={() => setShowCreateForm(true)}
            className="ml-auto"
          >
            <Plus className="w-4 h-4" />
            <span>Soạn bài giảng mới</span>
          </Button>
        </div>
        <p className="text-[10px] text-slate-400 italic">
          Chỉ Giáo viên được phân công dạy đúng lớp/khung chương trình mới soạn được bài — tài khoản khác chọn được để xem nhưng tạo bài sẽ bị hệ
          thống chặn.
        </p>
      </Card>

      {loadingLessons ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : !selectedClassId && !selectedCurriculumId ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chọn lớp hoặc khung chương trình ở trên để xem kho bài giảng.</p>
      ) : lessons.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chưa có bài giảng nào trong phạm vi này.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {lessons.map((lesson) => (
            <Card key={lesson.id} className="flex flex-col justify-between">
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="p-2 rounded-lg bg-amber-50 text-brand-orange shrink-0">{materialTypeIcon[lesson.lessonType.split("_")[0]] ?? <FileText className="w-4 h-4" />}</span>
                  <Badge variant={statusVariants[lesson.status]}>{statusLabels[lesson.status]}</Badge>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800 leading-normal">{lesson.title}</h4>
                  <span className="text-[10px] text-slate-400 font-mono mt-0.5 block">{lesson.code}</span>
                </div>
                <p className="text-[11px] text-slate-500">{lessonTypeLabels[lesson.lessonType]}{lesson.durationMinutes ? ` · ${lesson.durationMinutes} phút` : ""}</p>
              </div>
              <div className="border-t border-slate-100 pt-3 mt-3 flex items-center gap-2">
                <Button size="sm" variant="secondary" onClick={() => setMaterialsLesson(lesson)}>
                  <Upload className="w-3.5 h-3.5" /> Học liệu
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setEditingLesson(lesson)}>
                  Sửa
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {showCreateForm && (
        <CreateLessonModal
          scopeType={scopeType}
          classId={selectedClassId}
          curriculumId={selectedCurriculumId}
          curriculumIdForSubjects={effectiveCurriculumId}
          onClose={() => setShowCreateForm(false)}
          onCreated={() => {
            setShowCreateForm(false);
            loadLessons();
          }}
        />
      )}

      {editingLesson && (
        <EditLessonModal
          lesson={editingLesson}
          curriculumIdForSubjects={editingLesson.curriculumId ?? classes.find((c) => c.id === editingLesson.classId)?.curriculumId ?? null}
          onClose={() => setEditingLesson(null)}
          onSaved={() => {
            setEditingLesson(null);
            loadLessons();
          }}
        />
      )}

      {materialsLesson && <MaterialsModal lesson={materialsLesson} onClose={() => setMaterialsLesson(null)} />}
    </div>
  );
}

function CreateLessonModal({
  scopeType,
  classId,
  curriculumId,
  curriculumIdForSubjects,
  onClose,
  onCreated
}: {
  scopeType: "CLASS" | "CURRICULUM";
  classId: number | null;
  curriculumId: number | null;
  curriculumIdForSubjects: number | null;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState({ code: "", title: "", subjectId: "", lessonOrder: "", lessonType: "VIDEO_LECTURE" as LessonType, durationMinutes: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (curriculumIdForSubjects) listCurriculumSubjects(curriculumIdForSubjects).then(setSubjects).catch(() => undefined);
  }, [curriculumIdForSubjects]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.title.trim()) {
      setError("Vui lòng điền mã và tiêu đề bài giảng.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateLessonRequest = {
        code: form.code.trim(),
        title: form.title.trim(),
        classId: scopeType === "CLASS" ? classId ?? undefined : undefined,
        curriculumId: scopeType === "CURRICULUM" ? curriculumId ?? undefined : undefined,
        subjectId: form.subjectId ? Number(form.subjectId) : undefined,
        lessonOrder: form.lessonOrder ? Number(form.lessonOrder) : undefined,
        lessonType: form.lessonType,
        durationMinutes: form.durationMinutes ? Number(form.durationMinutes) : undefined
      };
      await createLesson(request);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo bài giảng thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Soạn bài giảng mới (UC-23)" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Mã bài giảng *</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="VD: LEC-U1-01" className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>Loại bài giảng *</label>
            <select value={form.lessonType} onChange={(e) => setForm({ ...form, lessonType: e.target.value as LessonType })} className={inputClass}>
              {Object.entries(lessonTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="VD: Unit 1: Greetings and Introduction" className={inputClass} required />
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass}>Thứ tự</label>
            <input type="number" value={form.lessonOrder} onChange={(e) => setForm({ ...form, lessonOrder: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Thời lượng (phút)</label>
            <input type="number" value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} className={inputClass} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Tạo bài giảng"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EditLessonModal({
  lesson,
  curriculumIdForSubjects,
  onClose,
  onSaved
}: {
  lesson: LessonResponse;
  curriculumIdForSubjects: number | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [form, setForm] = useState({
    title: lesson.title,
    subjectId: lesson.subjectId ? String(lesson.subjectId) : "",
    lessonOrder: lesson.lessonOrder != null ? String(lesson.lessonOrder) : "",
    durationMinutes: lesson.durationMinutes != null ? String(lesson.durationMinutes) : "",
    status: lesson.status
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (curriculumIdForSubjects) listCurriculumSubjects(curriculumIdForSubjects).then(setSubjects).catch(() => undefined);
  }, [curriculumIdForSubjects]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setError("Vui lòng điền tiêu đề.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateLessonRequest = {
        title: form.title.trim(),
        subjectId: form.subjectId ? Number(form.subjectId) : undefined,
        lessonOrder: form.lessonOrder ? Number(form.lessonOrder) : undefined,
        durationMinutes: form.durationMinutes ? Number(form.durationMinutes) : undefined,
        status: form.status
      };
      await updateLesson(lesson.id, request);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật bài giảng thất bại — có thể bạn không được phân công giảng dạy lớp/khung này.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Sửa bài giảng: ${lesson.code}`} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Học phần (tùy chọn)</label>
            <select value={form.subjectId} onChange={(e) => setForm({ ...form, subjectId: e.target.value })} className={inputClass}>
              <option value="">-- Không gán --</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass}>Trạng thái</label>
            <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as LessonStatus })} className={inputClass}>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Thứ tự</label>
            <input type="number" value={form.lessonOrder} onChange={(e) => setForm({ ...form, lessonOrder: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Thời lượng (phút)</label>
            <input type="number" value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} className={inputClass} />
          </div>
        </div>
        <p className="text-[10px] text-slate-400 italic">Chuyển trạng thái sang "Đã gỡ" (ARCHIVED) để gỡ bài khỏi kho — không xoá hẳn bản ghi.</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function MaterialsModal({ lesson, onClose }: { lesson: LessonResponse; onClose: () => void }) {
  const [materials, setMaterials] = useState<LessonMaterialResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [form, setForm] = useState({
    materialType: "VIDEO" as AddLessonMaterialRequest["materialType"],
    title: "",
    fileUrl: "",
    fileSizeBytes: "",
    durationSeconds: "",
    isDownloadable: true
  });
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listLessonMaterials(lesson.id)
      .then(setMaterials)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được học liệu."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [lesson.id]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim() || !form.fileUrl.trim()) {
      setError("Vui lòng điền tiêu đề và URL tệp phân phối.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await addLessonMaterial(lesson.id, {
        materialType: form.materialType,
        title: form.title.trim(),
        fileUrl: form.fileUrl.trim(),
        fileSizeBytes: form.fileSizeBytes ? Number(form.fileSizeBytes) : undefined,
        durationSeconds: form.durationSeconds ? Number(form.durationSeconds) : undefined,
        displayOrder: materials.length,
        isDownloadable: form.isDownloadable
      });
      setForm({ materialType: "VIDEO", title: "", fileUrl: "", fileSizeBytes: "", durationSeconds: "", isDownloadable: true });
      setShowAddForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm học liệu thất bại — có thể bạn không được phân công giảng dạy lớp/khung này.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Học liệu — ${lesson.title}`} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải...</p>
        ) : materials.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Bài giảng này chưa có học liệu nào.</p>
        ) : (
          <div className="space-y-2">
            {materials.map((m) => (
              <div key={m.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-slate-800">{m.title}</span>
                  <Badge variant="info">{m.materialType}</Badge>
                </div>
                <p className="text-slate-400 break-all">{m.fileUrl}</p>
                <p className="text-slate-400">
                  {m.fileSizeBytes ? `${(m.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : "—"}
                  {m.durationSeconds ? ` · ${Math.round(m.durationSeconds / 60)} phút` : ""}
                  {" · "}
                  {m.isDownloadable ? "Cho tải xuống" : "Chỉ xem online"}
                </p>
              </div>
            ))}
          </div>
        )}

        {showAddForm ? (
          <form onSubmit={handleAdd} className="border-t border-slate-100 pt-4 space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelClass}>Loại tệp *</label>
                <select value={form.materialType} onChange={(e) => setForm({ ...form, materialType: e.target.value as AddLessonMaterialRequest["materialType"] })} className={inputClass}>
                  {["VIDEO", "PDF", "AUDIO", "SLIDE", "IMAGE", "OTHER"].map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}>Tiêu đề *</label>
                <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
              </div>
            </div>
            <div>
              <label className={labelClass}>URL tệp phân phối (CDN) *</label>
              <input
                value={form.fileUrl}
                onChange={(e) => setForm({ ...form, fileUrl: e.target.value })}
                placeholder="https://pps-cdn.vn/lectures/..."
                className={inputClass}
                required
              />
              <p className="text-[10px] text-slate-400 italic mt-1">Tệp được upload lên CDN/Object Storage từ trước — ở đây chỉ đăng ký URL đã có.</p>
            </div>
            <div className="grid grid-cols-3 gap-3 items-end">
              <div>
                <label className={labelClass}>Kích thước (bytes)</label>
                <input type="number" value={form.fileSizeBytes} onChange={(e) => setForm({ ...form, fileSizeBytes: e.target.value })} className={inputClass} />
              </div>
              <div>
                <label className={labelClass}>Thời lượng (giây)</label>
                <input type="number" value={form.durationSeconds} onChange={(e) => setForm({ ...form, durationSeconds: e.target.value })} className={inputClass} />
              </div>
              <label className="flex items-center gap-1.5 text-xs font-semibold text-slate-600 pb-2.5">
                <input type="checkbox" checked={form.isDownloadable} onChange={(e) => setForm({ ...form, isDownloadable: e.target.checked })} />
                Cho tải xuống
              </label>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={() => setShowAddForm(false)}>
                Hủy
              </Button>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? "Đang lưu..." : "Thêm học liệu"}
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="secondary" onClick={() => setShowAddForm(true)}>
            <Plus className="w-4 h-4" /> Thêm học liệu
          </Button>
        )}
      </div>
    </Modal>
  );
}
