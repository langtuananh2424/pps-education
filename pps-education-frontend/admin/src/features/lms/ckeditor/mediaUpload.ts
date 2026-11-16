import type { Editor } from "ckeditor5";
import { uploadMedia } from "../api";

/** Tải ảnh/audio từ máy lên kho media chung (cùng API các form soạn câu hỏi cũ đang dùng), trả về URL công khai. */
export function uploadQuestionMedia(file: File): Promise<string> {
  return uploadMedia(file, "LMS_QUESTION").then((res) => res.url);
}

/** Nối nút "tải ảnh từ máy" (và dán/kéo-thả ảnh) của CKEditor5 vào API upload media của hệ thống. */
export function PpsImageUploadAdapterPlugin(editor: Editor) {
  editor.plugins.get("FileRepository").createUploadAdapter = (loader) => ({
    upload: async () => {
      const file = await loader.file;
      if (!file) throw new Error("Không đọc được file ảnh.");
      return { default: await uploadQuestionMedia(file) };
    },
    abort: () => undefined
  });
}
