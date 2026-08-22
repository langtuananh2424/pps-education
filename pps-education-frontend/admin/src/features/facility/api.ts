import { apiRequest } from "@/lib/apiClient";

/** Khớp SiteResponse thật của backend — xem UC-36 (Quản lý điểm trường). */
export interface SiteResponse {
  id: number;
  code: string;
  name: string;
  siteType: "OWNED" | "PARTNER";
  address: string | null;
  district: string | null;
  phone: string | null;
  status: "ACTIVE" | "INACTIVE" | "PENDING";
  partnerInfo: PartnerSchoolInfo | null;
  currentManagerUserId: number | null;
  currentManagerFullName: string | null;
  /** sites.geo_location (PostGIS) — dùng validate bán kính GPS chấm công UC-09 A2. null nếu chưa cấu hình. */
  latitude: number | null;
  longitude: number | null;
}

export interface PartnerSchoolInfo {
  contactPersonName: string | null;
  contactPersonTitle: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  additionalInfo: string | null;
}

/** Khớp CreateSiteRequest thật — partnerInfo chỉ hợp lệ khi siteType=PARTNER (BE từ chối nếu gửi kèm OWNED). */
export interface CreateSiteRequest {
  code: string;
  name: string;
  siteType: "OWNED" | "PARTNER";
  address?: string;
  district?: string;
  phone?: string;
  partnerInfo?: PartnerSchoolInfo;
  managerUserId?: number;
  latitude?: number;
  longitude?: number;
}

/** Khớp UpdateSiteRequest thật — đổi siteType sang OWNED sẽ xóa cứng partnerInfo phía BE. */
export interface UpdateSiteRequest {
  name: string;
  siteType: "OWNED" | "PARTNER";
  address?: string;
  district?: string;
  phone?: string;
  status?: "ACTIVE" | "INACTIVE" | "PENDING";
  partnerInfo?: PartnerSchoolInfo;
  latitude?: number;
  longitude?: number;
}

export interface PartnerContractResponse {
  id: number;
  siteId: number;
  contractNumber: string;
  contractType: "INITIAL" | "RENEWAL" | "AMENDMENT";
  parentContractId: number | null;
  startDate: string;
  endDate: string;
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
  termsSummary: string | null;
  fileUrl: string | null;
  signedAt: string | null;
  signedByCenter: string | null;
  signedByPartner: string | null;
  revenueShareNotes: string | null;
}

export interface CreatePartnerContractRequest {
  siteId: number;
  contractType: "INITIAL" | "RENEWAL" | "AMENDMENT";
  parentContractId?: number;
  startDate: string;
  endDate: string;
  termsSummary?: string;
  fileUrl?: string;
  signedAt?: string;
  signedByCenter?: string;
  signedByPartner?: string;
  revenueShareNotes?: string;
}

/** Khớp UpdatePartnerContractRequest thật — không có contractType/siteId (bất biến sau khi tạo). */
export interface UpdatePartnerContractRequest {
  endDate: string;
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";
  termsSummary?: string;
  fileUrl?: string;
  signedAt?: string;
  signedByCenter?: string;
  signedByPartner?: string;
  revenueShareNotes?: string;
}

export interface ExpiringPartnerContractResponse {
  contractId: number;
  siteId: number;
  siteName: string;
  contractNumber: string;
  endDate: string;
}

/** UC-36 Main Flow: danh sách điểm trường (không phân trang). */
export function listSites(): Promise<SiteResponse[]> {
  return apiRequest<SiteResponse[]>("/sites");
}

export interface SiteTeacherResponse {
  id: number;
  siteId: number;
  teacherUserId: number;
  teacherFullName: string;
  assignedFrom: string | null;
  assignedTo: string | null;
  notes: string | null;
}

/** Giáo viên đang được gán vào 1 điểm trường (site_teachers, khác site_managers) — dùng để tự dò điểm trường của Giáo viên đang đăng nhập. */
export function listSiteTeachers(siteId: number): Promise<SiteTeacherResponse[]> {
  return apiRequest<SiteTeacherResponse[]>(`/sites/${siteId}/teachers`);
}

export interface AssignSiteTeacherRequest {
  teacherUserId: number;
  assignedFrom: string;
  notes?: string;
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — gán 1 giáo viên vào điểm trường (UC-36). */
export function assignSiteTeacher(siteId: number, request: AssignSiteTeacherRequest): Promise<SiteTeacherResponse> {
  return apiRequest<SiteTeacherResponse>(`/sites/${siteId}/teachers`, { method: "POST", body: JSON.stringify(request) });
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — gỡ 1 giáo viên khỏi điểm trường (UC-36). */
export function removeSiteTeacher(siteId: number, siteTeacherId: number): Promise<void> {
  return apiRequest<void>(`/sites/${siteId}/teachers/${siteTeacherId}`, { method: "DELETE" });
}

export type RoomType = "THEORY" | "COMPUTER" | "LAB" | "OTHER";
export type RoomStatus = "AVAILABLE" | "MAINTENANCE" | "DISABLED";

export interface RoomResponse {
  id: number;
  siteId: number;
  code: string;
  name: string;
  roomType: string;
  capacity: number;
  flexible: boolean;
  managedByCenter: boolean;
  status: string;
  notes: string | null;
}

/** UC-37: phòng học của 1 điểm trường — dùng khi xếp/sinh lịch buổi học (UC-48/56). */
export function listRoomsBySite(siteId: number): Promise<RoomResponse[]> {
  return apiRequest<RoomResponse[]>(`/sites/${siteId}/rooms`);
}

export interface CreateRoomRequest {
  siteId: number;
  code: string;
  name?: string;
  roomType: RoomType;
  capacity: number;
  flexible: boolean;
  managedByCenter: boolean;
}

/** UC-37 Main Flow bước 1-2: khai báo phòng học mới tại 1 điểm trường. */
export function createRoom(request: CreateRoomRequest): Promise<RoomResponse> {
  return apiRequest<RoomResponse>("/rooms", { method: "POST", body: JSON.stringify(request) });
}

/** Không có roomType/siteId — bất biến sau khi tạo, giống nhiều pattern khác trong dự án. */
export interface UpdateRoomRequest {
  name?: string;
  capacity: number;
  flexible: boolean;
  managedByCenter: boolean;
  status: RoomStatus;
  notes?: string;
}

export function updateRoom(id: number, request: UpdateRoomRequest): Promise<RoomResponse> {
  return apiRequest<RoomResponse>(`/rooms/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

// ===================== UC-37: Thiết bị dạy học (FR-FAC-02) =====================

export type EquipmentType = "PROJECTOR" | "SPEAKER" | "MIC" | "COMPUTER" | "OTHER";
export type EquipmentStatus = "AVAILABLE" | "IN_USE" | "MAINTENANCE" | "BROKEN";

export interface EquipmentResponse {
  id: number;
  roomId: number | null;
  code: string;
  name: string;
  equipmentType: EquipmentType;
  status: EquipmentStatus;
  notes: string | null;
}

export interface CreateEquipmentRequest {
  roomId?: number;
  code: string;
  name: string;
  equipmentType: EquipmentType;
}

export function createEquipment(request: CreateEquipmentRequest): Promise<EquipmentResponse> {
  return apiRequest<EquipmentResponse>("/equipment", { method: "POST", body: JSON.stringify(request) });
}

/** UC-37 A1: thiết bị hỏng/bảo trì — chuyển status để loại khỏi danh sách khả dụng. */
export function updateEquipmentStatus(id: number, status: EquipmentStatus, notes?: string): Promise<EquipmentResponse> {
  return apiRequest<EquipmentResponse>(`/equipment/${id}/status`, { method: "PUT", body: JSON.stringify({ status, notes }) });
}

export function listEquipmentByRoom(roomId: number): Promise<EquipmentResponse[]> {
  return apiRequest<EquipmentResponse[]>(`/rooms/${roomId}/equipment`);
}

export interface PartnerSiteResponse {
  siteId: number;
  siteCode: string;
  siteName: string;
}

/** UC-29: tự resolve điểm trường liên kết của Đại diện trường liên kết đang đăng nhập (không nhận siteId từ client). */
export function getMyPartnerSite(): Promise<PartnerSiteResponse> {
  return apiRequest<PartnerSiteResponse>("/portal/partner/site");
}

export function getSite(id: number): Promise<SiteResponse> {
  return apiRequest<SiteResponse>(`/sites/${id}`);
}

export function createSite(request: CreateSiteRequest): Promise<SiteResponse> {
  return apiRequest<SiteResponse>("/sites", { method: "POST", body: JSON.stringify(request) });
}

export function updateSite(id: number, request: UpdateSiteRequest): Promise<SiteResponse> {
  return apiRequest<SiteResponse>(`/sites/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

/** UC-36 Main Flow bước 4 / A1: gán hoặc đổi Quản lý điểm trường phụ trách. */
export function assignSiteManager(id: number, managerUserId: number): Promise<SiteResponse> {
  return apiRequest<SiteResponse>(`/sites/${id}/manager`, { method: "PUT", body: JSON.stringify({ managerUserId }) });
}

export function listPartnerContractsBySite(siteId: number): Promise<PartnerContractResponse[]> {
  return apiRequest<PartnerContractResponse[]>(`/sites/${siteId}/partner-contracts`);
}

export function createPartnerContract(request: CreatePartnerContractRequest): Promise<PartnerContractResponse> {
  return apiRequest<PartnerContractResponse>("/partner-contracts", { method: "POST", body: JSON.stringify(request) });
}

export function updatePartnerContract(id: number, request: UpdatePartnerContractRequest): Promise<PartnerContractResponse> {
  return apiRequest<PartnerContractResponse>(`/partner-contracts/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function terminatePartnerContract(id: number): Promise<PartnerContractResponse> {
  return apiRequest<PartnerContractResponse>(`/partner-contracts/${id}/terminate`, { method: "POST" });
}

/** A3: chỉ xóa được hợp đồng đang ở trạng thái DRAFT (BE tự chặn, trả lỗi rõ nếu không phải). */
export function deletePartnerContract(id: number): Promise<void> {
  return apiRequest<void>(`/partner-contracts/${id}`, { method: "DELETE" });
}

export function listExpiringContracts(withinDays: number): Promise<ExpiringPartnerContractResponse[]> {
  return apiRequest<ExpiringPartnerContractResponse[]>(`/partner-contracts/expiring?withinDays=${withinDays}`);
}

// ===================== UC-38/39: Ý kiến phản hồi từ trường liên kết (FR-FAC-05) =====================

export type PartnerFeedbackType = "TEACHER" | "CLASS" | "OPERATIONS" | "OTHER";
export type PartnerFeedbackPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type PartnerFeedbackStatus = "NEW" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

/**
 * Khớp PartnerFeedbackResponse thật của backend — CHƯA có siteName/submittedByName
 * (chỉ trả id thô), và chưa có API đọc lại lịch sử trao đổi (partner_feedbacks_history)
 * dù bảng đó đã ghi đủ — xem tài liệu yêu cầu BE bổ sung trong plan.
 */
export interface PartnerFeedbackResponse {
  id: number;
  siteId: number;
  submittedBy: number;
  content: string;
  feedbackType: PartnerFeedbackType;
  priority: PartnerFeedbackPriority;
  status: PartnerFeedbackStatus;
  assignedTo: number | null;
  resolutionNotes: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

/** UC-39 Main Flow bước 1: hàng chờ phản hồi của (các) điểm trường mình phụ trách — chỉ actor có site_managers row cho site đó mới thấy/xử lý được (BE tự chặn). */
export function listPartnerFeedbacksForMySites(): Promise<PartnerFeedbackResponse[]> {
  return apiRequest<PartnerFeedbackResponse[]>("/partner-feedbacks/my-sites");
}

/** UC-39 Main Flow bước 2: Mới → Đang xử lý. */
export function startProcessingPartnerFeedback(id: number): Promise<PartnerFeedbackResponse> {
  return apiRequest<PartnerFeedbackResponse>(`/partner-feedbacks/${id}/start-processing`, { method: "POST" });
}

/** UC-39 Main Flow bước 3-4: ghi phương án khắc phục, chuyển Đang xử lý → Đã giải quyết. */
export function resolvePartnerFeedback(id: number, resolutionNotes: string): Promise<PartnerFeedbackResponse> {
  return apiRequest<PartnerFeedbackResponse>(`/partner-feedbacks/${id}/resolve`, { method: "POST", body: JSON.stringify({ resolutionNotes }) });
}

/** UC-39 Main Flow bước 5: Đã giải quyết → Đóng. */
export function closePartnerFeedback(id: number): Promise<PartnerFeedbackResponse> {
  return apiRequest<PartnerFeedbackResponse>(`/partner-feedbacks/${id}/close`, { method: "POST" });
}

// ===================== Tiết học theo điểm trường (bổ sung ngoài SDD gốc, xác nhận 2026-08-19/2026-08-20) =====================

/** Buổi trong ngày — mỗi buổi đánh số tiết riêng (VD Tiết 1 sáng khác Tiết 1 chiều), khớp thời khóa biểu giấy thực tế. */
export type DayPart = "MORNING" | "AFTERNOON" | "EVENING";

export const dayPartLabels: Record<DayPart, string> = { MORNING: "Sáng", AFTERNOON: "Chiều", EVENING: "Tối" };
export const dayPartOrder: DayPart[] = ["MORNING", "AFTERNOON", "EVENING"];

export interface SitePeriodTemplateResponse {
  id: number;
  siteId: number;
  dayPart: DayPart;
  periodNumber: number;
  label: string | null;
  startTime: string;
  endTime: string;
}

export interface CreateSitePeriodTemplateRequest {
  dayPart: DayPart;
  periodNumber: number;
  label?: string;
  startTime: string;
  endTime: string;
}

export interface UpdateSitePeriodTemplateRequest {
  label?: string;
  startTime: string;
  endTime: string;
}

/** Danh sách tiết học cố định của 1 điểm trường — dùng làm nguồn "chọn tiết" khi xếp lịch (UC-48/56/57). */
export function listSitePeriodTemplates(siteId: number): Promise<SitePeriodTemplateResponse[]> {
  return apiRequest<SitePeriodTemplateResponse[]>(`/sites/${siteId}/period-templates`);
}

export function createSitePeriodTemplate(siteId: number, request: CreateSitePeriodTemplateRequest): Promise<SitePeriodTemplateResponse> {
  return apiRequest<SitePeriodTemplateResponse>(`/sites/${siteId}/period-templates`, { method: "POST", body: JSON.stringify(request) });
}

export function updateSitePeriodTemplate(
  siteId: number,
  id: number,
  request: UpdateSitePeriodTemplateRequest
): Promise<SitePeriodTemplateResponse> {
  return apiRequest<SitePeriodTemplateResponse>(`/sites/${siteId}/period-templates/${id}`, { method: "PUT", body: JSON.stringify(request) });
}

export function deleteSitePeriodTemplate(siteId: number, id: number): Promise<void> {
  return apiRequest<void>(`/sites/${siteId}/period-templates/${id}`, { method: "DELETE" });
}
