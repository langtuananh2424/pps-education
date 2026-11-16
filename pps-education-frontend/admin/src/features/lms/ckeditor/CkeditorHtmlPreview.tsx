import React, { useMemo, useState } from "react";
import parse, { Element as DomElement } from "html-react-parser";
import {
  blankAnswerMatches,
  extractBlanksInDocumentOrder,
  extractChoiceGroupsInDocumentOrder,
  extractChunkGroupsInDocumentOrder,
  extractGridsInDocumentOrder,
  extractInlineChoicesInDocumentOrder,
  getCorrectChunkOrder,
  stripAnswersForStudentView
} from "./blankParser";

/**
 * "Xem trước (học sinh)" cho tab CKEditor5 ("Ô trống"/"Hộp từ vựng"/"Nhóm lựa chọn"/"Khối sắp xếp")
 * — nhận HTML GV đang soạn (CÓ data-answer/data-correct), xoá đáp án đi (mirror backend không bao
 * giờ trả đáp án đúng cho học viên trước khi nộp bài), parse lại thành cây React: mỗi
 * `<span class="q-blank">` thành 1 `<input>` thật, mỗi `<div class="q-choice-group">` thành 1 nhóm
 * radio/checkbox thật (suy ra kiểu chấm từ số lựa chọn đúng), mỗi cụm `<span class="q-chunk">` liền
 * nhau thành 1 khu vực "ghép câu" (bấm chọn theo đúng thứ tự hiển thị, KHÔNG xáo trộn — xem Javadoc
 * CkeditorChunkParser.java). Audio (`<audio class="q-audio">`) là HTML5 chuẩn, trình duyệt tự vẽ
 * player, không cần xử lý riêng. "Nộp thử" ở đây chỉ chấm NGAY TRONG TRÌNH DUYỆT để GV xem hình
 * dạng/cách chấm — không gọi API, không liên quan ExerciseAttemptService thật (câu Nhóm lựa
 * chọn/Khối sắp xếp thật được chấm bằng choices[]/structuredContent chuẩn sau khi lưu, KHÔNG qua
 * HTML preview này).
 */
export default function CkeditorHtmlPreview({ authoredHtml }: { authoredHtml: string }) {
  const correctBlanks = useMemo(() => extractBlanksInDocumentOrder(authoredHtml), [authoredHtml]);
  const correctGroups = useMemo(() => extractChoiceGroupsInDocumentOrder(authoredHtml), [authoredHtml]);
  const correctChunkGroups = useMemo(() => extractChunkGroupsInDocumentOrder(authoredHtml), [authoredHtml]);
  const correctInlineChoices = useMemo(() => extractInlineChoicesInDocumentOrder(authoredHtml), [authoredHtml]);
  const correctGrids = useMemo(() => extractGridsInDocumentOrder(authoredHtml), [authoredHtml]);
  const studentHtml = useMemo(() => stripAnswersForStudentView(authoredHtml), [authoredHtml]);

  // Lựa chọn trong câu: vị trí → chỉ số đã chọn. Bảng lưới: "bảng-dòng" → chỉ số cột đã chọn.
  const [inlineSelected, setInlineSelected] = useState<Record<number, number>>({});
  const [gridSelected, setGridSelected] = useState<Record<string, number>>({});
  const [studentAnswers, setStudentAnswers] = useState<Record<number, string>>({});
  const [selectedByGroup, setSelectedByGroup] = useState<Record<number, Set<string>>>({});
  const [builtOrderByChunkGroup, setBuiltOrderByChunkGroup] = useState<Record<number, string[]>>({});
  const [checked, setChecked] = useState(false);

  function toggleOption(groupIndex: number, optionId: string, isSingleCorrect: boolean) {
    setSelectedByGroup((prev) => {
      const current = new Set(prev[groupIndex] || []);
      if (isSingleCorrect) {
        return { ...prev, [groupIndex]: current.has(optionId) ? new Set() : new Set([optionId]) };
      }
      if (current.has(optionId)) current.delete(optionId);
      else current.add(optionId);
      return { ...prev, [groupIndex]: current };
    });
  }

  function pickChunk(groupIndex: number, chunkId: string) {
    setBuiltOrderByChunkGroup((prev) => ({ ...prev, [groupIndex]: [...(prev[groupIndex] || []), chunkId] }));
  }

  function undoChunk(groupIndex: number, position: number) {
    setBuiltOrderByChunkGroup((prev) => ({
      ...prev,
      [groupIndex]: (prev[groupIndex] || []).filter((_, i) => i !== position)
    }));
  }

  // Mỗi chỗ-gõ-chữ "q-chunk" chỉ thuộc về 1 nhóm, biết trước nhóm nào là chỗ ĐẦU TIÊN của nhóm đó
  // (chỉ render widget 1 lần tại chỗ đầu, các chỗ q-chunk sau cùng nhóm bị bỏ qua).
  const chunkFlat = useMemo(
    () => correctChunkGroups.flatMap((g, gi) => g.chunks.map((_, ci) => ({ gi, ci }))),
    [correctChunkGroups]
  );
  const chunkBanks = useMemo(() => correctChunkGroups.map((g) => g.chunks.map((c) => c.chunkId)), [correctChunkGroups]);

  let blankCursor = -1;
  let groupCursor = -1;
  let chunkCursor = -1;
  let inlineCursor = -1;
  let gridCursor = -1;
  const rendered = parse(studentHtml, {
    replace: (domNode) => {
      if (!(domNode instanceof DomElement)) return undefined;

      if (domNode.name === "span" && domNode.attribs?.class?.includes("q-inline-choice")) {
        inlineCursor += 1;
        const position = inlineCursor;
        const item = correctInlineChoices[position];
        if (!item) return undefined;
        const selectedIndex = inlineSelected[position];
        const answered = selectedIndex !== undefined;
        const isCorrect = checked && answered && selectedIndex === item.correctIndex;
        return (
          <select
            key={`inline-${position}`}
            value={answered ? selectedIndex : ""}
            onChange={(e) => setInlineSelected((prev) => ({ ...prev, [position]: Number(e.target.value) }))}
            className={
              "mx-1 inline-block rounded border px-1 py-0.5 text-sm align-middle " +
              (isCorrect ? "border-emerald-500 bg-emerald-50" : checked ? "border-red-500 bg-red-50" : "border-slate-300 bg-slate-50")
            }
          >
            <option value="" disabled>
              -- chọn --
            </option>
            {item.options.map((opt, i) => (
              <option key={i} value={i}>
                {opt}
              </option>
            ))}
          </select>
        );
      }

      if (domNode.name === "div" && domNode.attribs?.class?.includes("q-grid") && !domNode.attribs.class.includes("q-grid-row")) {
        gridCursor += 1;
        const gridIndex = gridCursor;
        const grid = correctGrids[gridIndex];
        if (!grid) return undefined;
        return (
          <table key={`grid-${gridIndex}`} className="my-2 w-full border-collapse text-sm">
            <thead>
              <tr>
                <th className="border border-slate-200 bg-slate-50 px-2 py-1 text-left" />
                {grid.options.map((opt, i) => (
                  <th key={i} className="border border-slate-200 bg-slate-50 px-2 py-1 text-center font-bold">
                    {opt}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.rows.map((row, rowIndex) => {
                const key = `${gridIndex}-${rowIndex}`;
                const rowCorrect = checked && gridSelected[key] === row.correctIndex;
                return (
                  <tr key={rowIndex} className={checked ? (rowCorrect ? "bg-emerald-50" : "bg-red-50") : ""}>
                    <td className="border border-slate-200 px-2 py-1">{row.text}</td>
                    {grid.options.map((_, optIndex) => (
                      <td key={optIndex} className="border border-slate-200 px-2 py-1 text-center">
                        <input
                          type="radio"
                          name={`grid-${key}`}
                          checked={gridSelected[key] === optIndex}
                          onChange={() => setGridSelected((prev) => ({ ...prev, [key]: optIndex }))}
                        />
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        );
      }

      if (domNode.name === "span" && domNode.attribs?.class?.includes("q-blank")) {
        blankCursor += 1;
        const position = blankCursor;
        const expected = correctBlanks[position]?.answer ?? "";
        const isCorrect = checked && blankAnswerMatches(expected, studentAnswers[position] || "");
        const isWrong = checked && !isCorrect;
        return (
          <input
            key={`blank-${position}`}
            type="text"
            value={studentAnswers[position] || ""}
            onChange={(e) => setStudentAnswers((prev) => ({ ...prev, [position]: e.target.value }))}
            className={
              "mx-1 inline-block w-28 rounded border px-2 py-0.5 text-sm align-middle focus:outline-none " +
              (isCorrect ? "border-emerald-500 bg-emerald-50" : isWrong ? "border-red-500 bg-red-50" : "border-slate-300 bg-slate-50")
            }
            placeholder="Điền đáp án"
          />
        );
      }

      if (domNode.name === "div" && domNode.attribs?.class?.includes("q-choice-group")) {
        groupCursor += 1;
        const groupIndex = groupCursor;
        const group = correctGroups[groupIndex];
        if (!group) return undefined;
        const correctCount = group.options.filter((o) => o.isCorrect).length;
        const isSingleCorrect = correctCount <= 1;
        const selected = selectedByGroup[groupIndex] || new Set<string>();
        const correctSet = new Set(group.options.filter((o) => o.isCorrect).map((o) => o.optionId));
        const groupIsCorrect = checked && selected.size === correctSet.size && [...selected].every((id) => correctSet.has(id));

        return (
          <div
            key={`choice-${groupIndex}`}
            className={"my-2 space-y-1 rounded-lg border p-2 " + (checked ? (groupIsCorrect ? "border-emerald-500 bg-emerald-50" : "border-red-500 bg-red-50") : "border-slate-300")}
          >
            {group.options.map((option) => (
              <label key={option.optionId} className="flex items-center gap-2 text-sm">
                <input
                  type={isSingleCorrect ? "radio" : "checkbox"}
                  name={`choice-group-${groupIndex}`}
                  checked={selected.has(option.optionId)}
                  onChange={() => toggleOption(groupIndex, option.optionId, isSingleCorrect)}
                />
                {/* dangerouslySetInnerHTML vì option.html có thể chứa ảnh (<img>), không chỉ chữ thường —
                    option.text (textContent) làm rụng mất ảnh. Nội dung do chính GV soạn (sanitize ở
                    backend trước khi lưu, ở đây chỉ là xem trước ngay trong phiên soạn). Dùng RIÊNG class
                    "q-choice-answer-html" (không tái dùng "q-choice-option") — tránh trùng icon ☐/☑ với
                    radio/checkbox thật đã có sẵn, chỉ mượn đúng phần CSS giới hạn cỡ ảnh. */}
                <span className="q-choice-answer-html" dangerouslySetInnerHTML={{ __html: option.html }} />
              </label>
            ))}
          </div>
        );
      }

      if (domNode.name === "span" && domNode.attribs?.class?.includes("q-chunk")) {
        chunkCursor += 1;
        const loc = chunkFlat[chunkCursor];
        if (!loc) return undefined;
        if (loc.ci !== 0) return <React.Fragment key={`chunk-skip-${chunkCursor}`} />; // đã render cả nhóm ở khối đầu tiên, bỏ qua phần tử sau

        const groupIndex = loc.gi;
        const group = correctChunkGroups[groupIndex];
        const builtOrder = builtOrderByChunkGroup[groupIndex] || [];
        const bank = chunkBanks[groupIndex].filter((id) => !builtOrder.includes(id));
        const chunkById = (id: string) => group.chunks.find((c) => c.chunkId === id)?.text || "";
        const correctOrder = getCorrectChunkOrder(group);
        const isGroupCorrect = checked && builtOrder.join(",") === correctOrder.join(",");

        return (
          <span key={`chunkgroup-${groupIndex}`} className="mx-1 inline-block align-middle">
            <span
              className={
                "inline-flex flex-wrap items-center gap-1 rounded border px-2 py-1 align-middle " +
                (checked ? (isGroupCorrect ? "border-emerald-500 bg-emerald-50" : "border-red-500 bg-red-50") : "border-slate-300")
              }
            >
              {builtOrder.length === 0 && <span className="text-xs italic text-slate-400">(chưa chọn khối nào)</span>}
              {builtOrder.map((id, position) => (
                <button
                  type="button"
                  key={id}
                  onClick={() => undoChunk(groupIndex, position)}
                  className="rounded bg-indigo-100 px-2 py-0.5 text-sm hover:bg-indigo-200"
                  title="Bấm để bỏ chọn"
                >
                  {chunkById(id)}
                </button>
              ))}
            </span>
            <span className="ml-2 inline-flex flex-wrap items-center gap-1">
              {bank.map((id) => (
                <button
                  type="button"
                  key={id}
                  onClick={() => pickChunk(groupIndex, id)}
                  className="rounded border border-slate-300 bg-white px-2 py-0.5 text-sm hover:bg-slate-100"
                >
                  {chunkById(id)}
                </button>
              ))}
            </span>
          </span>
        );
      }

      return undefined;
    }
  });

  const unconfiguredCount = correctBlanks.filter((b) => !b.answered).length;
  const blankScore = correctBlanks.filter((b, i) => blankAnswerMatches(b.answer, studentAnswers[i] || "")).length;
  const inlineScore = correctInlineChoices.filter((item, i) => inlineSelected[i] === item.correctIndex).length;
  const gridRowCount = correctGrids.reduce((sum, g) => sum + g.rows.length, 0);
  const gridScore = correctGrids.reduce(
    (sum, g, gi) => sum + g.rows.filter((row, ri) => gridSelected[`${gi}-${ri}`] === row.correctIndex).length,
    0
  );
  const groupScore = correctGroups.filter((group, i) => {
    const selected = selectedByGroup[i] || new Set<string>();
    const correctSet = new Set(group.options.filter((o) => o.isCorrect).map((o) => o.optionId));
    return selected.size === correctSet.size && [...selected].every((id) => correctSet.has(id));
  }).length;
  const chunkScore = correctChunkGroups.filter((group, i) => {
    const builtOrder = builtOrderByChunkGroup[i] || [];
    return builtOrder.join(",") === getCorrectChunkOrder(group).join(",");
  }).length;
  const totalCount = correctBlanks.length + correctGroups.length + correctChunkGroups.length + correctInlineChoices.length + gridRowCount;
  const score = blankScore + groupScore + chunkScore + inlineScore + gridScore;

  return (
    <div className="space-y-3">
      {unconfiguredCount > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-[13px] font-semibold text-amber-800">
          Còn {unconfiguredCount} ô trống chưa gán đáp án — sẽ không lưu được cho đến khi gán đủ.
        </div>
      )}
      {/* `ck-content` bắt buộc — style bảng/font-size/alignment của CKEditor5 (ckeditor5-content.css)
          chỉ áp dụng cho phần tử nằm trong class này, mirror cách Portal học sinh sẽ render sau này. */}
      <div className="ck-content rounded-xl border border-slate-200 bg-white p-4">{rendered}</div>
      {totalCount > 0 && (
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setChecked(true)}
            className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-indigo-700"
          >
            Nộp thử (demo)
          </button>
          {checked && (
            <span className="text-sm font-semibold text-slate-700">
              Điểm: {score}/{totalCount}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
