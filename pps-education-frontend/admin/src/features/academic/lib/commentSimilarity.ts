/**
 * Mirror CommentSimilarity.java (UC-74 bước 7) — tỷ lệ Jaccard trên cụm 3 từ liên tiếp, sau khi chữ thường
 * + bỏ dấu câu (GIỮ dấu thanh tiếng Việt). Dùng để báo "giống buổi trước X%" ngay trên thẻ nhận xét, cùng
 * thang đo với cảnh báo trợ lý AI trả về.
 */
function words(text: string): string[] {
  return text
    .normalize("NFC")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
}

function shingles(list: string[], size: number): Set<string> {
  const result = new Set<string>();
  for (let i = 0; i + size <= list.length; i++) result.add(list.slice(i, i + size).join(" "));
  return result;
}

export function commentSimilarity(a: string, b: string): number {
  const wordsA = words(a ?? "");
  const wordsB = words(b ?? "");
  if (wordsA.length === 0 || wordsB.length === 0) return 0;
  const size = Math.min(3, wordsA.length, wordsB.length);
  const setA = shingles(wordsA, size);
  const setB = shingles(wordsB, size);
  let intersection = 0;
  setA.forEach((s) => {
    if (setB.has(s)) intersection++;
  });
  return intersection / (setA.size + setB.size - intersection);
}
