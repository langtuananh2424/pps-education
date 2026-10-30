import type { PpsConfigField } from "./ppsScoringConfigApi";

/** Giá trị đang nhập: giữ dạng chuỗi để người dùng gõ dở ("0.", "-") không bị ép thành số giữa chừng. */
export type Draft = Record<string, string>;

/** Sai số khi so hai số thực (bằng với backend: PpsConfig.changed dùng 1e-9). */
const EPS = 1e-9;

/** Sai số cộng trọng số (bằng backend: các trọng số phải cộng bằng 1 trong khoảng 0,01). */
const WEIGHT_TOLERANCE = 0.01;

export type FieldError = { kind: "notNumber" } | { kind: "range"; min: number; max: number };

export type CrossError =
  | { kind: "weightSum"; prefix: string; sum: number }
  | { kind: "topFloor" }
  | { kind: "pauseOrder" };

export interface Validation {
  fieldErrors: Record<string, FieldError>;
  crossErrors: CrossError[];
  /** Tổng từng nhóm trọng số, để hiện cho người sửa dù chưa sai. */
  weightSums: Record<string, number>;
  ok: boolean;
}

export function toDraft(fields: PpsConfigField[]): Draft {
  const d: Draft = {};
  for (const f of fields) d[f.path] = String(f.value);
  return d;
}

/** Chuỗi nhập thành số; rỗng hoặc không phải số hữu hạn thì NaN. */
export function parseValue(text: string | undefined): number {
  if (text === undefined || text.trim() === "") return Number.NaN;
  const n = Number(text);
  return Number.isFinite(n) ? n : Number.NaN;
}

export function parseDraft(draft: Draft, fields: PpsConfigField[]): Record<string, number> {
  const v: Record<string, number> = {};
  for (const f of fields) v[f.path] = parseValue(draft[f.path]);
  return v;
}

/** Kiểm tra giống backend (PpsConfig.validate) để báo lỗi ngay khi gõ; backend vẫn là nơi quyết định cuối cùng. */
export function validate(fields: PpsConfigField[], values: Record<string, number>, weightGroups: string[]): Validation {
  const fieldErrors: Record<string, FieldError> = {};
  for (const f of fields) {
    const v = values[f.path];
    if (Number.isNaN(v)) fieldErrors[f.path] = { kind: "notNumber" };
    else if (v < f.min || v > f.max) fieldErrors[f.path] = { kind: "range", min: f.min, max: f.max };
  }
  const weightSums: Record<string, number> = {};
  const crossErrors: CrossError[] = [];
  for (const prefix of weightGroups) {
    const members = fields.filter((f) => f.path.startsWith(`${prefix}.`));
    const sum = members.reduce((s, f) => s + (Number.isNaN(values[f.path]) ? 0 : values[f.path]), 0);
    weightSums[prefix] = sum;
    if (members.every((f) => !Number.isNaN(values[f.path])) && Math.abs(sum - 1) > WEIGHT_TOLERANCE) {
      crossErrors.push({ kind: "weightSum", prefix, sum });
    }
  }
  if (!Number.isNaN(values["phone.top"]) && !Number.isNaN(values["phone.floor"]) && values["phone.top"] <= values["phone.floor"]) {
    crossErrors.push({ kind: "topFloor" });
  }
  if (!Number.isNaN(values["fluency.long_pause_s"]) && !Number.isNaN(values["fluency.short_pause_s"])
    && values["fluency.long_pause_s"] <= values["fluency.short_pause_s"]) {
    crossErrors.push({ kind: "pauseOrder" });
  }
  return { fieldErrors, crossErrors, weightSums, ok: Object.keys(fieldErrors).length === 0 && crossErrors.length === 0 };
}

/** Các tham số khác mặc định (chỉ gửi phần này lên backend; vắng mặt nghĩa là mặc định). */
export function overridesOf(fields: PpsConfigField[], values: Record<string, number>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const f of fields) {
    if (Math.abs(values[f.path] - f.defaultValue) > EPS) out[f.path] = values[f.path];
  }
  return out;
}

/** Số tham số đang nhập khác với bản đã lưu (để báo "chưa lưu"). */
export function unsavedPaths(fields: PpsConfigField[], values: Record<string, number>): string[] {
  return fields.filter((f) => !(Math.abs(values[f.path] - f.value) <= EPS)).map((f) => f.path);
}

/** Làm phẳng JSON lồng nhau thành {"a.b.c": giá trị}; khoá đã phẳng giữ nguyên. */
export function flatten(node: unknown, prefix = ""): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (node !== null && typeof node === "object" && !Array.isArray(node)) {
    for (const [k, v] of Object.entries(node as Record<string, unknown>)) {
      Object.assign(out, flatten(v, prefix ? `${prefix}.${k}` : k));
    }
  } else if (prefix) {
    out[prefix] = node;
  }
  return out;
}

export interface ImportResult {
  /** Draft sau khi nhập: mặc định cộng giá trị trong file (tham số vắng mặt trong file trở về mặc định, như khi lưu). */
  draft: Draft;
  unknownKeys: string[];
  notNumberKeys: string[];
  importedCount: number;
}

/** Nhập từ file JSON: nhận cả bản lồng nhau, khoá phẳng và file xuất ra bọc trong {"config": ...}. */
export function importConfig(raw: unknown, fields: PpsConfigField[]): ImportResult {
  let source = raw;
  if (source !== null && typeof source === "object" && !Array.isArray(source)) {
    const obj = source as Record<string, unknown>;
    if (Object.keys(obj).length <= 2 && obj.config !== null && typeof obj.config === "object") source = obj.config;
  }
  const known = new Map(fields.map((f) => [f.path, f]));
  const draft: Draft = {};
  for (const f of fields) draft[f.path] = String(f.defaultValue);
  const unknownKeys: string[] = [];
  const notNumberKeys: string[] = [];
  let importedCount = 0;
  for (const [path, value] of Object.entries(flatten(source))) {
    if (!known.has(path)) {
      unknownKeys.push(path);
    } else if (typeof value !== "number" || !Number.isFinite(value)) {
      notNumberKeys.push(path);
    } else {
      draft[path] = String(value);
      importedCount++;
    }
  }
  return { draft, unknownKeys, notNumberKeys, importedCount };
}

/** Nhóm hiển thị theo thứ tự khai báo của backend. */
export function groupsOf(fields: PpsConfigField[]): { group: string; fields: PpsConfigField[] }[] {
  const out: { group: string; fields: PpsConfigField[] }[] = [];
  for (const f of fields) {
    let g = out.find((x) => x.group === f.group);
    if (!g) {
      g = { group: f.group, fields: [] };
      out.push(g);
    }
    g.fields.push(f);
  }
  return out;
}
