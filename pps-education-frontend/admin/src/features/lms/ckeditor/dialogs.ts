/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay `window.prompt`/`window.alert` (popup xấu
 * của trình duyệt, không đồng bộ giao diện app — người dùng báo lại khi thấy hộp thoại "localhost:
 * 3000 says") bằng modal tự vẽ, style khớp phần còn lại của app (card trắng bo góc, nút cam "Tạo câu
 * hỏi" giống CkeditorWordBankComposer.tsx). Các lệnh CKEditor5 (`Command#execute`) ở các widget
 * (BlankWidgetPlugin, WordBankWidgetPlugin...) chạy NGOÀI cây React của app nên không dùng được
 * component `Modal` sẵn có của app (cần `ReactDOM.createRoot`/portal) — dựng DOM thuần, mount thẳng
 * vào `document.body`, trả về `Promise` (thay cho giá trị trả về ĐỒNG BỘ của `window.prompt`/`alert`
 * — mọi `Command#execute()` gọi 2 hàm này đều phải chuyển thành `async`/`await`).
 */

function escapeHtml(value: string): string {
  const div = document.createElement("div");
  div.textContent = value;
  return div.innerHTML;
}

function buildOverlay(): { overlay: HTMLDivElement; card: HTMLDivElement } {
  const overlay = document.createElement("div");
  overlay.className = "ck-pps-dialog-overlay";
  const card = document.createElement("div");
  card.className = "ck-pps-dialog-card";
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  return { overlay, card };
}

/** Thay `window.alert(message)`. */
export function alertDialog(message: string, title = "Thông báo"): Promise<void> {
  return new Promise((resolve) => {
    const { overlay, card } = buildOverlay();
    card.innerHTML = `
      <div class="ck-pps-dialog-title">${escapeHtml(title)}</div>
      <div class="ck-pps-dialog-message">${escapeHtml(message)}</div>
      <div class="ck-pps-dialog-actions">
        <button type="button" class="ck-pps-dialog-btn ck-pps-dialog-btn-primary" data-action="ok">OK</button>
      </div>
    `;
    const okButton = card.querySelector('[data-action="ok"]') as HTMLButtonElement;

    const close = () => {
      document.removeEventListener("keydown", onKey);
      overlay.remove();
      resolve();
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" || event.key === "Enter") close();
    };

    okButton.addEventListener("click", close);
    overlay.addEventListener("mousedown", (event) => {
      if (event.target === overlay) close();
    });
    document.addEventListener("keydown", onKey);
    okButton.focus();
  });
}

export interface PromptDialogOptions {
  title?: string;
  label: string;
  defaultValue?: string;
  placeholder?: string;
  multiline?: boolean;
  confirmLabel?: string;
}

/** Thay `window.prompt(message, defaultValue)` — trả về `null` khi GV bấm Huỷ/Esc (khớp hành vi Cancel của prompt gốc). */
export function promptDialog(options: PromptDialogOptions): Promise<string | null> {
  const { title = "Nhập thông tin", label, defaultValue = "", placeholder = "", multiline = false, confirmLabel = "OK" } = options;

  return new Promise((resolve) => {
    const { overlay, card } = buildOverlay();
    card.innerHTML = `
      <div class="ck-pps-dialog-title">${escapeHtml(title)}</div>
      <label class="ck-pps-dialog-label">${escapeHtml(label)}</label>
      ${
        multiline
          ? `<textarea class="ck-pps-dialog-input" rows="3" placeholder="${escapeHtml(placeholder)}"></textarea>`
          : `<input type="text" class="ck-pps-dialog-input" placeholder="${escapeHtml(placeholder)}" />`
      }
      <div class="ck-pps-dialog-actions">
        <button type="button" class="ck-pps-dialog-btn" data-action="cancel">Huỷ</button>
        <button type="button" class="ck-pps-dialog-btn ck-pps-dialog-btn-primary" data-action="ok">${escapeHtml(confirmLabel)}</button>
      </div>
    `;
    const input = card.querySelector(".ck-pps-dialog-input") as HTMLInputElement | HTMLTextAreaElement;
    // Gán value qua DOM property (không chèn vào innerHTML) — tránh escaping kép cho ký tự đặc biệt.
    input.value = defaultValue;
    const okButton = card.querySelector('[data-action="ok"]') as HTMLButtonElement;
    const cancelButton = card.querySelector('[data-action="cancel"]') as HTMLButtonElement;

    const finish = (value: string | null) => {
      document.removeEventListener("keydown", onKey);
      overlay.remove();
      resolve(value);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") finish(null);
      else if (event.key === "Enter" && !multiline && document.activeElement === input) finish(input.value);
    };

    okButton.addEventListener("click", () => finish(input.value));
    cancelButton.addEventListener("click", () => finish(null));
    overlay.addEventListener("mousedown", (event) => {
      if (event.target === overlay) finish(null);
    });
    document.addEventListener("keydown", onKey);
    input.focus();
    if (input instanceof HTMLInputElement) input.select();
  });
}
