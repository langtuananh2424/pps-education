import { useRef } from "react";

/**
 * Theo dõi mọi lần lưu câu trả lời đang bay + bản nháp ô gõ / ô điền từ / khối sắp xếp câu chưa lên server,
 * để lúc NỘP BÀI chờ lưu xong và lưu nốt phần còn thiếu — xem Javadoc handleSubmit ở TakeExerciseModal.
 * Các loại này chỉ lưu khi rời ô / khi hoàn tất nên có thể còn sót lại lúc bấm nộp (mạng chậm, chọn dở...).
 */
export function useTextAnswerSaves() {
  const pending = useRef<Set<Promise<unknown>>>(new Set());
  const savedText = useRef<Map<number, string>>(new Map());
  const structuredDraft = useRef<Map<number, string[]>>(new Map());
  const savedStructured = useRef<Map<number, string>>(new Map());

  /** Ghi nhớ 1 lần lưu (bất kỳ loại câu nào) đang bay để nộp bài chờ được. */
  const trackPending = <T,>(save: Promise<T>): Promise<T> => {
    pending.current.add(save);
    const done = () => pending.current.delete(save);
    save.then(done, done);
    return save;
  };

  /** Lưu chữ gõ: nhớ chữ đã lưu thành công. */
  const track = <T,>(questionId: number, text: string, save: Promise<T>): Promise<T> => {
    trackPending(save).then(() => savedText.current.set(questionId, text), () => undefined);
    return save;
  };

  /** Lưu câu có cấu trúc (WORD_BANK / SENTENCE_BUILDING): nhớ giá trị đã lưu thành công. */
  const trackStructured = <T,>(questionId: number, values: string[], save: Promise<T>): Promise<T> => {
    trackPending(save).then(() => savedStructured.current.set(questionId, JSON.stringify(values)), () => undefined);
    return save;
  };

  /** Khối điền từ / sắp xếp câu báo trạng thái hiện tại (kể cả dở dang) mỗi lần học sinh thay đổi. */
  const noteStructuredDraft = (questionId: number, values: string[]) => {
    structuredDraft.current.set(questionId, values);
  };

  /** Xoá mọi bản nháp/ghi nhớ — gọi khi mở lượt làm mới hoặc sau khi nộp xong. */
  const reset = () => {
    savedText.current.clear();
    structuredDraft.current.clear();
    savedStructured.current.clear();
  };

  /** Chờ mọi lần lưu đang bay xong (kể cả lỗi), rồi trả về các bản nháp CHƯA khớp chữ đã lưu thành công. */
  const settleAndGetUnsaved = async (
    textDraft: Record<number, string>,
    serverText: (questionId: number) => string | null | undefined,
    serverStructured: (questionId: number) => string[] | null | undefined
  ) => {
    await Promise.allSettled([...pending.current]);
    const texts = Object.entries(textDraft)
      .map(([qid, text]) => [Number(qid), text] as const)
      .filter(([qid, text]) => (savedText.current.get(qid) ?? serverText(qid) ?? "") !== text);
    const structured = [...structuredDraft.current.entries()].filter(
      ([qid, values]) => (savedStructured.current.get(qid) ?? JSON.stringify(serverStructured(qid) ?? [])) !== JSON.stringify(values)
    );
    return { texts, structured };
  };

  return { trackPending, track, trackStructured, noteStructuredDraft, reset, settleAndGetUnsaved };
}
