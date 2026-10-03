import { apiRequest } from "@/lib/apiClient";

// ===================== UC-30: Hóa đơn & thanh toán =====================

export type InvoiceStatus = "DRAFT" | "ISSUED" | "PARTIAL_PAID" | "PAID" | "OVERDUE" | "CANCELLED";
export const INVOICE_STATUSES: InvoiceStatus[] = ["ISSUED", "PARTIAL_PAID", "PAID", "OVERDUE", "CANCELLED"];

export interface InvoiceItemResponse {
  id: number;
  itemType: string;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

/** Khớp InvoiceResponse thật — classId/className/siteId/siteName null nếu hóa đơn không gắn ghi danh lớp. */
export interface InvoiceResponse {
  id: number;
  invoiceNumber: string;
  studentId: number;
  studentFullName: string;
  studentCode: string;
  classEnrollmentId: number | null;
  payerParentId: number | null;
  billingPeriodFrom: string | null;
  billingPeriodTo: string | null;
  issueDate: string;
  dueDate: string;
  subtotal: number;
  discountTotal: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  outstandingAmount: number;
  status: InvoiceStatus;
  qrCodeData: string | null;
  items: InvoiceItemResponse[];
  classId: number | null;
  className: string | null;
  siteId: number | null;
  siteName: string | null;
}

export type PaymentMethod = "QR_BANK" | "CASH" | "BANK_TRANSFER";
export const MANUAL_PAYMENT_METHODS: PaymentMethod[] = ["CASH", "BANK_TRANSFER"];

export interface PaymentResponse {
  id: number;
  paymentReference: string;
  invoiceId: number;
  amount: number;
  paymentMethod: string;
  paidAt: string;
  bankTransactionId: string | null;
  receiptNumber: string | null;
  status: string;
  confirmedBy: number | null;
  confirmedAt: string | null;
  /** null = xác nhận tự động qua webhook ngân hàng. */
  confirmedByName: string | null;
}

/** changedById/changedByName null = hệ thống tự động (cron sinh hóa đơn, đánh dấu quá hạn, webhook). */
export interface InvoiceHistoryResponse {
  id: number;
  action: "CREATED" | "UPDATED";
  changedById: number | null;
  changedByName: string | null;
  details: Record<string, unknown> | null;
  createdAt: string;
}

export interface InvoiceSearchParams {
  from: string;
  to: string;
  status?: string;
  siteId?: number;
  classId?: number;
  keyword?: string;
}

export function searchInvoices(params: InvoiceSearchParams): Promise<InvoiceResponse[]> {
  const qs = new URLSearchParams({ from: params.from, to: params.to });
  if (params.status) qs.set("status", params.status);
  if (params.siteId) qs.set("siteId", String(params.siteId));
  if (params.classId) qs.set("classId", String(params.classId));
  if (params.keyword?.trim()) qs.set("keyword", params.keyword.trim());
  return apiRequest<InvoiceResponse[]>(`/finance/invoices?${qs.toString()}`);
}

export function getInvoiceDetail(id: number): Promise<InvoiceResponse> {
  return apiRequest<InvoiceResponse>(`/finance/invoices/${id}/detail`);
}

export function listInvoicePayments(id: number): Promise<PaymentResponse[]> {
  return apiRequest<PaymentResponse[]>(`/finance/invoices/${id}/payments`);
}

export function listInvoiceHistory(id: number): Promise<InvoiceHistoryResponse[]> {
  return apiRequest<InvoiceHistoryResponse[]>(`/finance/invoices/${id}/history`);
}

export interface GenerateInvoicesRequest {
  classId?: number;
  billingPeriodFrom: string;
  billingPeriodTo: string;
  issueDate: string;
  dueDate: string;
}

/** UC-30 Main Flow bước 1 — sinh thủ công, bỏ qua học sinh đã có hóa đơn cùng kỳ. Trả về các hóa đơn mới sinh. */
export function generateInvoices(request: GenerateInvoicesRequest): Promise<InvoiceResponse[]> {
  return apiRequest<InvoiceResponse[]>("/finance/invoices/generate", { method: "POST", body: JSON.stringify(request) });
}

export interface RecordManualPaymentRequest {
  amount: number;
  paymentMethod: PaymentMethod;
  /** ISO-8601 có offset; bỏ trống = thời điểm hiện tại. */
  paidAt?: string;
  receiptNumber?: string;
}

/** UC-30 A2: Kế toán ghi nhận thanh toán thủ công. */
export function recordManualPayment(invoiceId: number, request: RecordManualPaymentRequest): Promise<PaymentResponse> {
  return apiRequest<PaymentResponse>(`/finance/invoices/${invoiceId}/payments`, { method: "POST", body: JSON.stringify(request) });
}

/** Hủy hóa đơn phát hành sai (V210) — chỉ khi chưa có khoản thu nào. */
export function cancelInvoice(invoiceId: number, reason: string): Promise<InvoiceResponse> {
  return apiRequest<InvoiceResponse>(`/finance/invoices/${invoiceId}/cancel`, { method: "POST", body: JSON.stringify({ reason }) });
}

// ===================== Định mức học phí =====================

export type PricingModel = "COURSE" | "PER_SESSION" | "MONTHLY";
export const PRICING_MODELS: PricingModel[] = ["MONTHLY", "COURSE", "PER_SESSION"];

export interface TuitionPlanResponse {
  id: number;
  code: string;
  name: string;
  curriculumId: number;
  pricingModel: PricingModel;
  classTypeFilter: "LINKED" | "OPEN" | null;
  basePrice: number;
  pricePerUnit: number | null;
  unitCount: number | null;
  currency: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  status: "ACTIVE" | "INACTIVE";
  curriculumName: string;
}

export interface CreateTuitionPlanRequest {
  code: string;
  name: string;
  curriculumId: number;
  pricingModel: PricingModel;
  classTypeFilter?: "LINKED" | "OPEN";
  basePrice: number;
  pricePerUnit?: number;
  unitCount?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export interface TuitionPlanAssignmentResponse {
  id: number;
  classId: number;
  tuitionPlanId: number;
  priceOverride: number | null;
  overrideReason: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  className: string;
  tuitionPlanCode: string;
  tuitionPlanName: string;
}

export interface AssignTuitionPlanRequest {
  classId: number;
  tuitionPlanId: number;
  priceOverride?: number;
  overrideReason?: string;
  effectiveFrom?: string;
}

export function listTuitionPlans(status?: string): Promise<TuitionPlanResponse[]> {
  return apiRequest<TuitionPlanResponse[]>(`/finance/tuition-plans${status ? `?status=${status}` : ""}`);
}

export function createTuitionPlan(request: CreateTuitionPlanRequest): Promise<TuitionPlanResponse> {
  return apiRequest<TuitionPlanResponse>("/finance/tuition-plans", { method: "POST", body: JSON.stringify(request) });
}

export function updateTuitionPlanStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<TuitionPlanResponse> {
  return apiRequest<TuitionPlanResponse>(`/finance/tuition-plans/${id}/status`, { method: "PUT", body: JSON.stringify({ status }) });
}

/** Không truyền classId: các gán đang hiệu lực của mọi lớp; có classId: toàn bộ lịch sử gán của lớp đó. */
export function listTuitionPlanAssignments(classId?: number): Promise<TuitionPlanAssignmentResponse[]> {
  return apiRequest<TuitionPlanAssignmentResponse[]>(`/finance/tuition-plan-assignments${classId ? `?classId=${classId}` : ""}`);
}

export function assignTuitionPlan(request: AssignTuitionPlanRequest): Promise<TuitionPlanAssignmentResponse> {
  return apiRequest<TuitionPlanAssignmentResponse>("/finance/tuition-plan-assignments", { method: "POST", body: JSON.stringify(request) });
}

// ===================== Học bổng / Miễn giảm =====================

export interface ScholarshipResponse {
  id: number;
  studentId: number;
  code: string;
  name: string;
  discountType: "PERCENTAGE" | "FIXED_AMOUNT";
  discountValue: number;
  applicableScope: "PER_INVOICE" | "ONE_TIME";
  validFrom: string | null;
  validTo: string | null;
  maxAmount: number | null;
  status: "ACTIVE" | "EXPIRED" | "REVOKED";
  approvedBy: number;
  approvedAt: string;
  studentCode: string;
  studentFullName: string;
}

export interface CreateScholarshipRequest {
  studentId: number;
  code: string;
  name: string;
  discountType: "PERCENTAGE" | "FIXED_AMOUNT";
  discountValue: number;
  applicableScope?: "PER_INVOICE" | "ONE_TIME";
  validFrom?: string;
  validTo?: string;
  maxAmount?: number;
}

export function listScholarships(params?: { studentId?: number; status?: string }): Promise<ScholarshipResponse[]> {
  const qs = new URLSearchParams();
  if (params?.studentId) qs.set("studentId", String(params.studentId));
  if (params?.status) qs.set("status", params.status);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  return apiRequest<ScholarshipResponse[]>(`/finance/scholarships${suffix}`);
}

export function createScholarship(request: CreateScholarshipRequest): Promise<ScholarshipResponse> {
  return apiRequest<ScholarshipResponse>("/finance/scholarships", { method: "POST", body: JSON.stringify(request) });
}

export function revokeScholarship(id: number): Promise<ScholarshipResponse> {
  return apiRequest<ScholarshipResponse>(`/finance/scholarships/${id}/revoke`, { method: "POST" });
}

// ===================== UC-31: Chi vận hành =====================

export type ExpenseStatus = "RECORDED" | "APPROVED" | "REJECTED";
export type ExpensePaymentMethod = "CASH" | "BANK_TRANSFER" | "CARD" | "OTHER";
export const EXPENSE_PAYMENT_METHODS: ExpensePaymentMethod[] = ["BANK_TRANSFER", "CASH", "CARD", "OTHER"];

export interface ExpenseCategoryResponse {
  id: number;
  code: string;
  name: string;
  categoryGroup: string | null;
}

/** siteId/siteName null = chi dùng chung nhiều điểm trường (UC-31 A1). */
export interface OperatingExpenseResponse {
  id: number;
  expenseNumber: string;
  expenseCategoryCode: string;
  expenseCategoryName: string;
  siteId: number | null;
  expenseDate: string;
  amount: number;
  description: string;
  paymentMethod: ExpensePaymentMethod;
  supplierName: string | null;
  receiptNumber: string | null;
  fileUrl: string | null;
  status: ExpenseStatus;
  recordedBy: number;
  approvedBy: number | null;
  rejectionReason: string | null;
  siteName: string | null;
  recordedByName: string;
  approvedByName: string | null;
  createdAt: string;
}

export interface CreateOperatingExpenseRequest {
  expenseCategoryCode: string;
  siteId?: number;
  expenseDate: string;
  amount: number;
  description: string;
  paymentMethod: ExpensePaymentMethod;
  supplierName?: string;
  receiptNumber?: string;
  fileUrl?: string;
}

export function listExpenseCategories(): Promise<ExpenseCategoryResponse[]> {
  return apiRequest<ExpenseCategoryResponse[]>("/finance/expense-categories");
}

export function listOperatingExpenses(params: { from: string; to: string; siteId?: number }): Promise<OperatingExpenseResponse[]> {
  const qs = new URLSearchParams({ from: params.from, to: params.to });
  if (params.siteId) qs.set("siteId", String(params.siteId));
  return apiRequest<OperatingExpenseResponse[]>(`/finance/operating-expenses?${qs.toString()}`);
}

export function createOperatingExpense(request: CreateOperatingExpenseRequest): Promise<OperatingExpenseResponse> {
  return apiRequest<OperatingExpenseResponse>("/finance/operating-expenses", { method: "POST", body: JSON.stringify(request) });
}

/** UC-31 A2: Ban giám đốc duyệt/từ chối — từ chối bắt buộc có lý do. */
export function decideOperatingExpense(id: number, decision: "APPROVED" | "REJECTED", rejectionReason?: string): Promise<OperatingExpenseResponse> {
  return apiRequest<OperatingExpenseResponse>(`/finance/operating-expenses/${id}/decision`, {
    method: "POST",
    body: JSON.stringify({ decision, rejectionReason })
  });
}

// ===================== UC-32: Báo cáo tài chính =====================

export interface FinancialReportResponse {
  siteId: number;
  siteName: string;
  periodFrom: string;
  periodTo: string;
  totalRevenue: number;
  totalExpense: number;
  totalOutstanding: number;
}

export interface ChainFinancialReportResponse {
  periodFrom: string;
  periodTo: string;
  totalRevenue: number;
  totalExpense: number;
  totalOutstanding: number;
  bySite: FinancialReportResponse[];
}

/** Ban giám đốc/Kế toán (finance.report.view): tổng hợp toàn chuỗi, chi tiết theo điểm trường. */
export function getChainReport(from: string, to: string): Promise<ChainFinancialReportResponse> {
  return apiRequest<ChainFinancialReportResponse>(`/finance/reports/chain?from=${from}&to=${to}`);
}

/** Quản lý điểm trường: chỉ các điểm trường mình phụ trách. */
export function getMySiteReports(from: string, to: string): Promise<FinancialReportResponse[]> {
  return apiRequest<FinancialReportResponse[]>(`/finance/reports/my-sites?from=${from}&to=${to}`);
}
