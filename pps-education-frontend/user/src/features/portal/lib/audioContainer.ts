/**
 * Nhận diện container THẬT của bản ghi âm qua vài byte đầu, không tin `MediaRecorder.mimeType`/`Blob.type`.
 *
 * Bug thật trên iPhone/Safari (UC-23b, 2026-10-13): `recorder.mimeType` có thể rỗng nên code cũ gán nhãn
 * "audio/webm" + tên file `.webm` cho bản ghi thật sự là MP4/M4A. Hệ quả: `<audio>` của iOS báo "Lỗi" (không giải
 * mã được webm) và AI nhận sai nhãn định dạng → "Không chấm được tự động". Gắn đúng nhãn/đuôi file ngay từ phía
 * thiết bị ghi âm là cách chắc nhất.
 */
export interface AudioContainer {
  mime: string;
  extension: string;
}

const ascii = (bytes: Uint8Array, from: number, to: number) =>
  String.fromCharCode(...Array.from(bytes.slice(from, to)));

export async function sniffAudioContainer(blob: Blob): Promise<AudioContainer | null> {
  let head: Uint8Array;
  try {
    head = new Uint8Array(await blob.slice(0, 12).arrayBuffer());
  } catch {
    return null;
  }
  if (head.length < 8) return null;
  if (head[0] === 0x1a && head[1] === 0x45 && head[2] === 0xdf && head[3] === 0xa3) return { mime: "audio/webm", extension: "webm" };
  if (ascii(head, 4, 8) === "ftyp") return { mime: "audio/mp4", extension: "m4a" };
  if (ascii(head, 0, 4) === "OggS") return { mime: "audio/ogg", extension: "ogg" };
  if (ascii(head, 0, 4) === "RIFF" && ascii(head, 8, 12) === "WAVE") return { mime: "audio/wav", extension: "wav" };
  if (ascii(head, 0, 3) === "ID3" || (head[0] === 0xff && (head[1] & 0xe0) === 0xe0)) return { mime: "audio/mpeg", extension: "mp3" };
  return null;
}

/** Trả blob gắn đúng type theo nội dung thật (giữ nguyên blob nếu không nhận diện được). */
export async function relabelAudioBlob(blob: Blob): Promise<{ blob: Blob; container: AudioContainer | null }> {
  const container = await sniffAudioContainer(blob);
  if (!container || container.mime === blob.type.split(";")[0]) return { blob, container };
  return { blob: new Blob([blob], { type: container.mime }), container };
}
