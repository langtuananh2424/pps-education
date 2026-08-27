import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import Select from "@/components/ui/Select";
import { BookResponse, SubTopicResponse, UnitResponse, listBooks, listSubTopics, listUnits } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";

/**
 * V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — CHỌN Sách -&gt; Unit -&gt; Sub
 * Topic theo đúng mục lục sách giáo trình (Curriculum — chương trình+khối — đã có sẵn qua
 * curriculumId). Thuần chọn từ danh mục đã cấu hình sẵn ở màn "Danh mục sách" (BookCatalogPage.tsx,
 * route /lms/book-catalog) — KHÔNG tạo mới tại chỗ (đã xác nhận với người dùng: tạo Sách/Unit/SubTopic
 * ngay trong modal tạo Đề "khó dùng", tách hẳn ra 1 màn cấu hình riêng, ở đây chỉ "vọc" chọn lại).
 * Cascading 3 cấp, mirror pattern Select đơn giản đã dùng cho curriculum/teacherType.
 *
 * Bổ sung 2026-08-24 (đã xác nhận với người dùng, fix UX thật đã gặp qua test tay) — trước đây edit
 * mode chỉ hiện gợi ý chữ "Hiện tại: ..." mà KHÔNG tự chọn sẵn Sách/Unit trong 2 Select đầu, khiến ô
 * đóng luôn hiện placeholder "-- Chọn Sách --" trông như trống dù bấm mở ra vẫn có đúng lựa chọn — dễ
 * hiểu nhầm là bug/mất dữ liệu. Giờ tự dò ngược 1 lần duy nhất lúc mount (chưa có API tra cứu ngược
 * true nên dò bằng cách duyệt books -&gt; units -&gt; subTopics tìm đúng subTopic id = value, danh mục
 * nhỏ nên chấp nhận được — KHÔNG dò lại mỗi lần value đổi do chính người dùng chọn ở đây, chỉ 1 lần cho
 * giá trị ban đầu truyền vào).
 *
 * Bổ sung 2026-08-26 — tách ra thành component dùng chung (trước đây private trong
 * ExerciseAssignPage.tsx) để tái dùng cho "Kho Video Ôn tập" (LecturesPage.tsx), mirror đúng cách Kho đề
 * gắn Lesson (Exam) vào Sub Topic cho ReviewVideoSet ("Bộ"). Tự load namespace i18n riêng
 * ("lms-question-authoring", nơi các key này đã có sẵn) — độc lập namespace của trang cha.
 */
export default function UnitSubTopicPicker({
  curriculumId,
  value,
  currentLabel,
  onChange
}: {
  curriculumId: number | null;
  value: number | null;
  currentLabel?: string | null;
  onChange: (subTopicId: number | null) => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [books, setBooks] = useState<BookResponse[]>([]);
  const [selectedBookId, setSelectedBookId] = useState<number | null>(null);
  const [units, setUnits] = useState<UnitResponse[]>([]);
  const [selectedUnitId, setSelectedUnitId] = useState<number | null>(null);
  const [subTopics, setSubTopics] = useState<SubTopicResponse[]>([]);
  const resolvedInitialValue = React.useRef(false);

  useEffect(() => {
    if (!curriculumId) {
      setBooks([]);
      return;
    }
    listBooks(curriculumId).then(setBooks).catch(() => setBooks([]));
  }, [curriculumId]);

  useEffect(() => {
    if (resolvedInitialValue.current || !value || books.length === 0) return;
    resolvedInitialValue.current = true;
    (async () => {
      for (const book of books) {
        const bookUnits = await listUnits(book.id).catch(() => [] as UnitResponse[]);
        for (const unit of bookUnits) {
          const unitSubTopics = await listSubTopics(unit.id).catch(() => [] as SubTopicResponse[]);
          if (unitSubTopics.some((s) => s.id === value)) {
            setSelectedBookId(book.id);
            setUnits(bookUnits);
            setSelectedUnitId(unit.id);
            setSubTopics(unitSubTopics);
            return;
          }
        }
      }
    })();
  }, [books, value]);

  useEffect(() => {
    if (!selectedBookId) {
      setUnits([]);
      return;
    }
    listUnits(selectedBookId).then(setUnits).catch(() => setUnits([]));
  }, [selectedBookId]);

  useEffect(() => {
    if (!selectedUnitId) {
      setSubTopics([]);
      return;
    }
    listSubTopics(selectedUnitId).then(setSubTopics).catch(() => setSubTopics([]));
  }, [selectedUnitId]);

  return (
    <div className="space-y-2 border border-slate-200 rounded-lg p-2.5 bg-slate-50/50">
      <p className="text-[10px] uppercase font-bold text-slate-500">{t("assignPage.unitSubTopicPicker.label")}</p>
      {currentLabel !== undefined && (
        <p className="text-[10px] text-slate-400">
          {t("assignPage.unitSubTopicPicker.currentLabel", { label: currentLabel ?? t("assignPage.unitSubTopicPicker.unclassified") })}
        </p>
      )}
      {!curriculumId ? (
        <p className="text-[10px] text-slate-400 italic">{t("assignPage.unitSubTopicPicker.selectCurriculumFirst")}</p>
      ) : books.length === 0 ? (
        <p className="text-[10px] text-slate-400 italic">{t("assignPage.unitSubTopicPicker.noBooksHint")}</p>
      ) : (
        <>
          <Select
            value={selectedBookId ?? ""}
            onChange={(e) => {
              setSelectedBookId(e.target.value ? Number(e.target.value) : null);
              setSelectedUnitId(null);
              onChange(null);
            }}
            className={inputClass}
          >
            <option value="">{t("assignPage.unitSubTopicPicker.bookPlaceholder")}</option>
            {books.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title}
              </option>
            ))}
          </Select>

          {selectedBookId && units.length === 0 ? (
            <p className="text-[10px] text-slate-400 italic">{t("assignPage.unitSubTopicPicker.noUnitsHint")}</p>
          ) : (
            selectedBookId && (
              <Select
                value={selectedUnitId ?? ""}
                onChange={(e) => {
                  setSelectedUnitId(e.target.value ? Number(e.target.value) : null);
                  onChange(null);
                }}
                className={inputClass}
              >
                <option value="">{t("assignPage.unitSubTopicPicker.unitPlaceholder")}</option>
                {units.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.title}
                  </option>
                ))}
              </Select>
            )
          )}

          {selectedUnitId && (
            <Select value={value ?? ""} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
              <option value="">{t("assignPage.unitSubTopicPicker.subTopicPlaceholder")}</option>
              {subTopics.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.title}
                </option>
              ))}
            </Select>
          )}
        </>
      )}
    </div>
  );
}
