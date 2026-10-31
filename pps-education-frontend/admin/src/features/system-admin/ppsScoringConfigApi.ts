import { apiRequest } from "@/lib/apiClient";

/** Khớp PpsScoringConfigResponse.Field của backend (V225): một tham số của bộ chấm phát âm PPS Hybrid v2. */
export interface PpsConfigField {
  path: string;
  group: string;
  label: string;
  help: string;
  min: number;
  max: number;
  step: number;
  defaultValue: number;
  value: number;
  changed: boolean;
}

/** Khớp PpsScoringConfigResponse: overrides là các tham số khác mặc định (phẳng), weightGroups là tiền tố các nhóm trọng số phải cộng bằng 1. */
export interface PpsScoringConfigResponse {
  fields: PpsConfigField[];
  overrides: Record<string, number>;
  weightGroups: string[];
  updatedByName: string | null;
  updatedAt: string | null;
}

/** Khớp PpsScoringConfigHistoryResponse: previous và overrides là các tham số khác mặc định trước và sau lần lưu. */
export interface PpsScoringConfigHistoryItem {
  id: number;
  previous: Record<string, number>;
  overrides: Record<string, number>;
  changedByName: string;
  note: string | null;
  createdAt: string;
}

/** Cấu hình xuất ra: {@code config} lồng nhau, nhập lại được. */
export interface PpsScoringConfigExport {
  config: Record<string, unknown>;
  exportedAt: string;
}

const BASE = "/pps-scoring-config";

export function getPpsScoringConfig(): Promise<PpsScoringConfigResponse> {
  return apiRequest<PpsScoringConfigResponse>(BASE);
}

/** Lưu TOÀN BỘ cấu hình mong muốn (khoá phẳng "a.b.c"); tham số vắng mặt trở về mặc định. */
export function savePpsScoringConfig(config: Record<string, number>, note?: string): Promise<PpsScoringConfigResponse> {
  return apiRequest<PpsScoringConfigResponse>(BASE, { method: "PUT", body: JSON.stringify({ config, note: note?.trim() || null }) });
}

export function resetPpsScoringConfig(): Promise<PpsScoringConfigResponse> {
  return apiRequest<PpsScoringConfigResponse>(`${BASE}/reset`, { method: "POST" });
}

export function exportPpsScoringConfig(): Promise<PpsScoringConfigExport> {
  return apiRequest<PpsScoringConfigExport>(`${BASE}/export`);
}

export function getPpsScoringConfigHistory(): Promise<PpsScoringConfigHistoryItem[]> {
  return apiRequest<PpsScoringConfigHistoryItem[]>(`${BASE}/history`);
}
