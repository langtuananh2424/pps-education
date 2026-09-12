import { ClassSessionResponse } from "./api";

/**
 * GVNN không có tài khoản hệ thống — "primaryTeacherName" của buổi FOREIGN thực chất là tài khoản CM
 * đứng thay để vận hành (điểm danh/check-in...), KHÔNG phải tên GV thật. Ưu tiên hiện actualTeacherName
 * (nhập tay, khớp Nhận xét học viên) làm tên GV chính hiển thị; dùng chung ở TimetableSessionCard +
 * SessionInfoModal để tránh lệch quy tắc giữa 2 nơi — bổ sung ngoài SDD gốc, xác nhận 2026-09-12.
 */
export function getDisplayTeacherName(session: Pick<ClassSessionResponse, "teacherType" | "actualTeacherName" | "primaryTeacherName">): string | null {
  return session.teacherType === "FOREIGN" ? session.actualTeacherName || session.primaryTeacherName : session.primaryTeacherName;
}

/**
 * Dòng "CM" hiển thị — ưu tiên field CM riêng (cmTeacherName) nếu có chọn; nếu buổi FOREIGN và đã
 * nhập được actualTeacherName (nên primaryTeacherName không còn hiện làm tên GV chính nữa), rơi về
 * hiện primaryTeacherName ở đây (tài khoản CM đứng thay). Không hiện gì nếu chưa nhập actualTeacherName
 * (tránh lặp lại đúng tên vừa hiện ở dòng GV chính).
 */
export function getCmDisplayName(session: Pick<ClassSessionResponse, "teacherType" | "actualTeacherName" | "primaryTeacherName" | "cmTeacherName">): string | null {
  return session.teacherType === "FOREIGN"
    ? session.cmTeacherName || (session.actualTeacherName ? session.primaryTeacherName : null)
    : session.cmTeacherName;
}

/**
 * CM sửa lại actualTeacherName qua Nhận xét học viên (VD GVNN nghỉ đột xuất, đổi người dạy) mà khác
 * originalTeacherName (chụp lúc xếp/sửa lịch qua Lịch làm việc) ⇒ có thay GV ngoài kế hoạch.
 */
export function hasTeacherSubstitution(session: Pick<ClassSessionResponse, "actualTeacherName" | "originalTeacherName">): boolean {
  return Boolean(session.actualTeacherName && session.originalTeacherName && session.actualTeacherName !== session.originalTeacherName);
}
