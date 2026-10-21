import { useRef } from "react";

/**
 * Theo dõi các lần lưu ô gõ (lưu khi blur) đang bay + chữ đã lưu thành công, để lúc NỘP BÀI có thể
 * chờ lưu xong và lưu nốt bản nháp chưa lên server — xem Javadoc handleSubmit ở TakeExerciseModal.
 */
export function useTextAnswerSaves() {
  const pending = useRef<Set<Promise<unknown>>>(new Set());
  const savedText = useRef<Map<number, string>>(new Map());

  /** Bọc 1 lần lưu: ghi nhớ đang bay, và nhớ chữ đã lưu thành công. */
  const track = <T,>(questionId: number, text: string, save: Promise<T>): Promise<T> => {
    pending.current.add(save);
    const done = () => pending.current.delete(save);
    save.then(() => {
      savedText.current.set(questionId, text);
      done();
    }, done);
    return save;
  };

  /** Chờ mọi lần lưu đang bay xong (kể cả lỗi), rồi trả về các bản nháp CHƯA khớp chữ đã lưu thành công. */
  const settleAndGetUnsaved = async (draft: Record<number, string>, serverText: (questionId: number) => string | null | undefined) => {
    await Promise.allSettled([...pending.current]);
    return Object.entries(draft)
      .map(([qid, text]) => [Number(qid), text] as const)
      .filter(([qid, text]) => (savedText.current.get(qid) ?? serverText(qid) ?? "") !== text);
  };

  return { track, settleAndGetUnsaved };
}
