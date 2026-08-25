import React, { useEffect, useState } from "react";
import { BookOpen, ChevronDown, ChevronRight, Layers, Library, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { CurriculumResponse, listCurriculums } from "@/features/academic/api";
import {
  BookResponse,
  CreateBookRequest,
  CreateSubTopicRequest,
  CreateUnitRequest,
  SubTopicResponse,
  UnitResponse,
  createBook,
  createSubTopic,
  createUnit,
  listBooks,
  listSubTopics,
  listUnits
} from "../api";
import Button from "@/components/ui/Button";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";

/**
 * "Danh mục sách" (V148, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — cấu hình
 * Curriculum (chương trình+khối, VD "IELTS Grade 6" — đã có sẵn) -&gt; Sách -&gt; Unit -&gt; Sub Topic
 * theo đúng mục lục sách giáo trình, TÁCH RIÊNG khỏi màn "Soạn & giao đề" (trước đây phải tạo Unit/
 * SubTopic ngay trong modal tạo Đề — người dùng phản hồi khó dùng, "vào giao đề chỉ nên chọn (vọc) từ
 * danh mục đã có sẵn"). V148: thêm cấp Sách — trước đó (V144) Unit gắn thẳng Curriculum, người dùng
 * phản hồi sai vì "Khung chương trình" chỉ là khung, không phải nơi tạo Sách/Unit trực tiếp. Màn "Soạn
 * & giao đề" (ExerciseAssignPage.tsx) giờ chỉ còn CHỌN từ danh mục đã cấu hình ở đây, không tạo mới.
 */
export default function BookCatalogPage() {
  const { t } = useTranslation("lms-question-authoring");
  const { message: toastMessage, showToast } = useToast();
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [selectedCurriculumId, setSelectedCurriculumId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listCurriculums()
      .then((res) => {
        setCurriculums(res);
        setSelectedCurriculumId((prev) => prev ?? res[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("bookCatalogPage.loadCurriculumsFailed")));
  }, [t]);

  const selectedCurriculum = curriculums.find((c) => c.id === selectedCurriculumId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("bookCatalogPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("bookCatalogPage.description")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-4 py-2.5 border-b border-slate-100 bg-slate-50">
            <p className="text-[10px] uppercase font-bold text-slate-500">{t("bookCatalogPage.curriculumColumnLabel")}</p>
          </div>
          {curriculums.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center text-slate-400 space-y-2">
              <Library className="w-10 h-10 text-slate-300" />
              <p className="text-xs text-slate-400">{t("bookCatalogPage.noCurriculums")}</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-100">
              {curriculums.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setSelectedCurriculumId(c.id)}
                  className={`w-full text-left px-4 py-3 hover:bg-slate-50/60 ${selectedCurriculumId === c.id ? "bg-brand-red/5 border-l-2 border-brand-red" : ""}`}
                >
                  <p className="text-xs font-bold text-slate-800">{c.name}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{c.code}</p>
                  {(c.track || c.gradeLevel) && (
                    <p className="text-[10px] text-slate-400 mt-0.5">
                      {[c.track, c.gradeLevel].filter(Boolean).join(" · ")}
                    </p>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="lg:col-span-9">
          {!selectedCurriculum ? (
            <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <Layers className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">{t("bookCatalogPage.selectCurriculumPrompt")}</p>
            </div>
          ) : (
            <BookListPanel curriculum={selectedCurriculum} showToast={showToast} />
          )}
        </div>
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

/** V148 — cấp Sách, con của Curriculum. Chọn 1 Sách thì hiện panel Unit/SubTopic của đúng Sách đó bên dưới. */
function BookListPanel({ curriculum, showToast }: { curriculum: CurriculumResponse; showToast: (msg: string) => void }) {
  const { t } = useTranslation("lms-question-authoring");
  const [books, setBooks] = useState<BookResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedBookId, setSelectedBookId] = useState<number | null>(null);
  const [newBookTitle, setNewBookTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadBooks = () => {
    setLoading(true);
    listBooks(curriculum.id)
      .then((res) => {
        setBooks(res);
        setSelectedBookId((prev) => (prev != null && res.some((b) => b.id === prev) ? prev : res[0]?.id ?? null));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("bookCatalogPage.loadBooksFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(loadBooks, [curriculum.id]);

  const handleCreateBook = async () => {
    if (!newBookTitle.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const request: CreateBookRequest = { title: newBookTitle.trim(), displayOrder: books.length };
      const created = await createBook(curriculum.id, request);
      setNewBookTitle("");
      loadBooks();
      setSelectedBookId(created.id);
      showToast(t("bookCatalogPage.bookCreatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("bookCatalogPage.createBookFailed"));
    } finally {
      setCreating(false);
    }
  };

  const selectedBook = books.find((b) => b.id === selectedBookId) ?? null;

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-1">
          <p className="text-sm font-bold text-slate-800">{curriculum.name}</p>
          <p className="text-[10px] text-slate-400 font-mono">{curriculum.code}</p>
        </div>

        <div className="px-5 py-3 border-b border-slate-100 flex gap-2">
          <input
            value={newBookTitle}
            onChange={(e) => setNewBookTitle(e.target.value)}
            placeholder={t("bookCatalogPage.newBookPlaceholder")}
            className={`${inputClass} flex-1`}
          />
          <Button variant="primary" size="sm" disabled={creating || !newBookTitle.trim()} onClick={handleCreateBook}>
            <Plus className="w-3.5 h-3.5" />
            {t("bookCatalogPage.addBookButton")}
          </Button>
        </div>

        {error && <p className="px-5 pt-3 text-[11px] text-rose-600">{error}</p>}

        {loading ? (
          <p className="text-xs text-slate-500 p-6 text-center">{t("common.loading")}</p>
        ) : books.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-6 text-center">{t("bookCatalogPage.noBooks")}</p>
        ) : (
          <div className="flex flex-wrap gap-1.5 p-3">
            {books
              .slice()
              .sort((a, b) => a.displayOrder - b.displayOrder)
              .map((book) => (
                <button
                  key={book.id}
                  onClick={() => setSelectedBookId(book.id)}
                  className={`flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-lg border ${
                    selectedBookId === book.id
                      ? "bg-brand-red/10 border-brand-red text-brand-red"
                      : "bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100"
                  }`}
                >
                  <BookOpen className="w-3.5 h-3.5" />
                  {book.title}
                </button>
              ))}
          </div>
        )}
      </div>

      {selectedBook && <UnitListPanel book={selectedBook} showToast={showToast} />}
    </div>
  );
}

function UnitListPanel({ book, showToast }: { book: BookResponse; showToast: (msg: string) => void }) {
  const { t } = useTranslation("lms-question-authoring");
  const [units, setUnits] = useState<UnitResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [newUnitTitle, setNewUnitTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadUnits = () => {
    setLoading(true);
    listUnits(book.id)
      .then(setUnits)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("bookCatalogPage.loadUnitsFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(loadUnits, [book.id]);

  const handleCreateUnit = async () => {
    if (!newUnitTitle.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const request: CreateUnitRequest = { title: newUnitTitle.trim(), displayOrder: units.length };
      await createUnit(book.id, request);
      setNewUnitTitle("");
      loadUnits();
      showToast(t("bookCatalogPage.unitCreatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("bookCatalogPage.createUnitFailed"));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-1">
        <p className="text-sm font-bold text-slate-800 flex items-center gap-1.5">
          <BookOpen className="w-4 h-4 text-brand-red" />
          {book.title}
        </p>
      </div>

      <div className="px-5 py-3 border-b border-slate-100 flex gap-2">
        <input
          value={newUnitTitle}
          onChange={(e) => setNewUnitTitle(e.target.value)}
          placeholder={t("bookCatalogPage.newUnitPlaceholder")}
          className={`${inputClass} flex-1`}
        />
        <Button variant="primary" size="sm" disabled={creating || !newUnitTitle.trim()} onClick={handleCreateUnit}>
          <Plus className="w-3.5 h-3.5" />
          {t("bookCatalogPage.addUnitButton")}
        </Button>
      </div>

      {error && <p className="px-5 pt-3 text-[11px] text-rose-600">{error}</p>}

      {loading ? (
        <p className="text-xs text-slate-500 p-6 text-center">{t("common.loading")}</p>
      ) : units.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-6 text-center">{t("bookCatalogPage.noUnits")}</p>
      ) : (
        <div className="divide-y divide-slate-100">
          {units
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((unit) => (
              <UnitRow key={unit.id} unit={unit} showToast={showToast} />
            ))}
        </div>
      )}
    </div>
  );
}

function UnitRow({ unit, showToast }: { unit: UnitResponse; showToast: (msg: string) => void }) {
  const { t } = useTranslation("lms-question-authoring");
  const [expanded, setExpanded] = useState(false);
  const [subTopics, setSubTopics] = useState<SubTopicResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [newSubTopicTitle, setNewSubTopicTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSubTopics = () => {
    setLoading(true);
    listSubTopics(unit.id)
      .then(setSubTopics)
      .catch(() => setSubTopics([]))
      .finally(() => setLoading(false));
  };

  const toggle = () => {
    setExpanded((v) => !v);
    if (!subTopics && !loading) loadSubTopics();
  };

  const handleCreateSubTopic = async () => {
    if (!newSubTopicTitle.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const request: CreateSubTopicRequest = { title: newSubTopicTitle.trim(), displayOrder: subTopics?.length ?? 0 };
      await createSubTopic(unit.id, request);
      setNewSubTopicTitle("");
      loadSubTopics();
      showToast(t("bookCatalogPage.subTopicCreatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("bookCatalogPage.createSubTopicFailed"));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div>
      <button onClick={toggle} className="w-full px-5 py-3 flex items-center gap-2 text-left hover:bg-slate-50/60">
        {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
        <span className="text-xs font-bold text-slate-800">{unit.title}</span>
      </button>

      {expanded && (
        <div className="px-5 pb-3.5 pl-11 space-y-2">
          {error && <p className="text-[11px] text-rose-600">{error}</p>}
          <div className="flex gap-2">
            <input
              value={newSubTopicTitle}
              onChange={(e) => setNewSubTopicTitle(e.target.value)}
              placeholder={t("bookCatalogPage.newSubTopicPlaceholder")}
              className={`${inputClass} flex-1`}
            />
            <Button variant="secondary" size="sm" disabled={creating || !newSubTopicTitle.trim()} onClick={handleCreateSubTopic}>
              <Plus className="w-3 h-3" />
              {t("bookCatalogPage.addSubTopicButton")}
            </Button>
          </div>
          {loading ? (
            <p className="text-[11px] text-slate-400">{t("common.loading")}</p>
          ) : !subTopics || subTopics.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic">{t("bookCatalogPage.noSubTopics")}</p>
          ) : (
            <div className="space-y-1">
              {subTopics
                .slice()
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((s) => (
                  <div key={s.id} className="text-[11px] text-slate-600 border-b border-slate-50 pb-1">
                    {s.title}
                  </div>
                ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
