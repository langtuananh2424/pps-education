/**
 * Catalog quyền theo trang/nút (V202 — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30).
 *
 * Mỗi mục sidebar (id trùng NavItem.id trong navigation.ts) có:
 * - viewCodes: mã quyền "xem trang" — có đủ các mã này thì mục hiện trong sidebar và vào được trang;
 *   trên màn "Nhóm vai trò", tick mục = cấp toàn bộ viewCodes.
 * - actions: các nút trong trang, tick nút = cấp các mã tương ứng.
 * Sinh từ bảng quyền đã chốt, cùng nguồn với migration V202 — sửa bảng thì sinh lại cả 2.
 */
export interface PageAction {
  label: string;
  codes: string[];
}

export interface PagePermissions {
  viewCodes: string[];
  actions: PageAction[];
}

export const PAGE_PERMISSIONS: Record<string, PagePermissions> = {
  // Tổng quan · Bảng điều khiển
  "dash-all": {
    viewCodes: ["dashboard.view"],
    actions: []
  },
  // Quản trị hệ thống · Quản lý người dùng
  "sys-users": {
    viewCodes: ["user.view", "user.role.view"],
    actions: [
      { label: "Tạo tài khoản", codes: ["user.create"] },
      { label: "Lưu hồ sơ, Lưu email, Đặt lại mật khẩu", codes: ["user.update"] },
      { label: "Mở khoá, Tạm khoá, Ngừng hoạt động", codes: ["user.status.update"] },
    ]
  },
  // Quản trị hệ thống · Nhóm vai trò
  "sys-roles": {
    viewCodes: ["permission.role.view"],
    actions: [
      { label: "Tạo vai trò tùy biến", codes: ["permission.role.create"] },
      { label: "Lưu thay đổi quyền của vai trò", codes: ["permission.role.update"] },
      { label: "Xoá vai trò tùy biến", codes: ["permission.role.delete"] },
      { label: "Gán tài khoản vào vai trò", codes: ["user.role.assign"] },
      { label: "Gỡ tài khoản khỏi vai trò", codes: ["user.role.revoke"] },
    ]
  },
  // Quản trị hệ thống · Tùy chỉnh tài khoản
  "sys-override": {
    viewCodes: ["permission.override.view"],
    actions: [
      { label: "Áp dụng / Tước bỏ quyền riêng", codes: ["permission.override.set"] },
      { label: "Hủy ngoại lệ", codes: ["permission.override.delete"] },
    ]
  },
  // Quản trị hệ thống · Nhật ký thay đổi phân quyền
  "sys-audit": {
    viewCodes: ["permission.audit.view"],
    actions: []
  },
  // Quản trị hệ thống · Cài đặt hệ thống
  "sys-settings": {
    viewCodes: ["system.settings.view"],
    actions: [
      { label: "Sửa, Lưu cấu hình", codes: ["system.settings.manage"] },
    ]
  },
  // Quản trị hệ thống · Sử dụng token AI
  "sys-ai-token-usage": {
    viewCodes: ["system.ai-usage.view"],
    actions: []
  },
  // Quản trị hệ thống · Gửi thông báo
  "sys-send-notification": {
    viewCodes: ["notification.send.manual"],
    actions: []
  },
  // Nhân sự · Hồ sơ cán bộ
  "hrm-profile": {
    viewCodes: ["hrm.employee.view"],
    actions: [
      { label: "Thêm nhân sự", codes: ["hrm.employee.create"] },
      { label: "Nhập nhân sự theo lô", codes: ["hrm.employee.import"] },
      { label: "Lưu hồ sơ, Thêm bằng cấp, Ghi nhận khen thưởng/kỷ luật, Thêm/Chấm dứt hợp đồng", codes: ["hrm.employee.update"] },
    ]
  },
  // Nhân sự · Phòng ban & chức vụ
  "hrm-departments-positions": {
    viewCodes: ["hrm.department.view", "hrm.position.view"],
    actions: [
      { label: "Thêm, Sửa, Xoá phòng ban", codes: ["hrm.department.create", "hrm.department.update", "hrm.department.delete"] },
      { label: "Thêm, Xoá chức vụ, Lưu vai trò mặc định", codes: ["hrm.position.create", "hrm.position.update", "hrm.position.delete"] },
    ]
  },
  // Nhân sự · Dữ liệu chấm công
  "hrm-attendance": {
    viewCodes: ["hrm.attendance.self"],
    actions: [
      { label: "Xem chấm công tổng hợp, tab chấm công nhận lớp", codes: ["hrm.attendance.view-all"] },
    ]
  },
  // Nhân sự · Địa điểm chấm công
  "hrm-attendance-sites": {
    viewCodes: ["hrm.attendance-site.view"],
    actions: [
      { label: "Thêm, Sửa địa điểm, Lấy vị trí hiện tại", codes: ["hrm.attendance-site.manage"] },
    ]
  },
  // Nhân sự · Ca làm việc
  "hrm-shifts": {
    viewCodes: ["hrm.shift.view"],
    actions: [
      { label: "Thêm, Sửa, Vô hiệu hoá ca", codes: ["hrm.shift.create", "hrm.shift.update"] },
      { label: "Gán ca cho nhân sự", codes: ["hrm.employee-shift.assign"] },
    ]
  },
  // Nhân sự · Lịch nghỉ lễ
  "hrm-work-calendar": {
    viewCodes: ["hrm.work-calendar.view"],
    actions: [
      { label: "Thêm override", codes: ["hrm.work-calendar.create"] },
      { label: "Xoá override", codes: ["hrm.work-calendar.delete"] },
    ]
  },
  // Nhân sự · Lịch làm việc (tổng hợp)
  "hrm-employee-schedule": {
    viewCodes: ["hrm.employee-schedule.view"],
    actions: [
      { label: "Xếp, kéo đổi buổi trên lưới, Hoàn tác, Lưu", codes: ["academic.class-session.create", "academic.class-session.reschedule"] },
      { label: "Sửa, hủy buổi đã diễn ra (bắt buộc lý do)", codes: ["academic.class-session.correct-past"] },
    ]
  },
  // Nhân sự · Nghỉ phép & duyệt đơn
  "hrm-leaves": {
    viewCodes: ["hrm.leave.self"],
    actions: [
      { label: "Duyệt / Từ chối đơn, chọn giáo viên dạy thay", codes: ["hrm.leave.approve"] },
    ]
  },
  // Nhân sự · Bảng lương
  "hrm-payroll": {
    viewCodes: ["hrm.payroll.self"],
    actions: [
      { label: "Xem bảng lương toàn hệ thống", codes: ["hrm.payroll.view"] },
      { label: "Tính, chốt bảng lương", codes: ["hrm.payroll.calculate"] },
    ]
  },
  // Tuyển sinh & CRM · Khách hàng tiềm năng
  "crm-leads": {
    viewCodes: ["crm.lead.view"],
    actions: [
      { label: "Tạo lead, Cập nhật trạng thái lead", codes: ["crm.lead.create", "crm.lead.update"] },
      { label: "Chuyển lead thành học viên", codes: ["crm.lead.convert"] },
      { label: "Phân phối lead cho tư vấn viên", codes: ["crm.lead.assign"] },
      { label: "Nhập học theo lô từ trường liên kết", codes: ["crm.lead.import"] },
    ]
  },
  // Học sinh · Hồ sơ học sinh
  "stu-profile": {
    viewCodes: ["student.profile.view"],
    actions: [
      { label: "Thêm học sinh", codes: ["student.profile.create"] },
      { label: "Nhập học sinh theo lô", codes: ["student.profile.import"] },
      { label: "Lưu hồ sơ", codes: ["student.profile.update"] },
      { label: "Liên kết phụ huynh", codes: ["student.parent.link.create", "student.parent.link.delete"] },
      { label: "Ghi nhận chuyển lớp/điểm trường", codes: ["student.transfer.create"] },
      { label: "Đổi trạng thái học tập", codes: ["student.status.manage"] },
    ]
  },
  // Học sinh · Phụ huynh
  "stu-parents": {
    viewCodes: ["student.parent.view"],
    actions: [
      { label: "Thêm phụ huynh", codes: ["student.parent.create"] },
      { label: "Nhập phụ huynh theo lô", codes: ["student.parent.import"] },
      { label: "Sửa thông tin", codes: ["student.parent.update"] },
      { label: "Liên kết học sinh", codes: ["student.parent.link.create", "student.parent.link.delete"] },
    ]
  },
  // Học sinh · Điểm danh
  "acad-attendance": {
    viewCodes: ["academic.attendance.view"],
    actions: [
      { label: "Xác nhận & lưu điểm danh trong giờ học", codes: ["academic.attendance.mark"] },
      { label: "Điểm danh bổ sung, sửa sau giờ học", codes: ["academic.attendance.create", "academic.attendance.update", "academic.attendance.delete"] },
    ]
  },
  // Học thuật · Quản lý lớp học (ví dụ của bạn)
  "acad-classes": {
    viewCodes: ["academic.class.view"],
    actions: [
      { label: "Xem mọi lớp (không giới hạn phân công)", codes: ["academic.class.view-all"] },
      { label: "Tạo lớp", codes: ["academic.class.create"] },
      { label: "Chuyển lớp hàng loạt", codes: ["academic.class.promote"] },
      { label: "Tab Hồ sơ: Lưu hồ sơ", codes: ["academic.class.update"] },
      { label: "Tab Giáo viên: Gán, Đổi giáo viên, Kết thúc phụ trách", codes: ["academic.class.teacher.assign"] },
      { label: "Tab Học sinh: Ghi danh học sinh", codes: ["academic.class.enrollment.create"] },
      { label: "Tab Học sinh: Tải mẫu, Ghi danh theo lô", codes: ["academic.class.enrollment.import"] },
      { label: "Tab Học sinh: Rút học sinh khỏi lớp", codes: ["academic.class.enrollment.withdraw"] },
      { label: "Tab Buổi học: Xếp buổi học mới", codes: ["academic.class-session.create"] },
      { label: "Tab Buổi học: Sinh lịch hàng loạt, Kích hoạt sinh chu kỳ", codes: ["academic.class-session.generate"] },
      { label: "Tab Buổi học: Nhập lịch từ Excel", codes: ["academic.class-session.import"] },
      { label: "Tab Buổi học: Dời lịch", codes: ["academic.class-session.reschedule"] },
      { label: "Tab Buổi học: Hủy buổi", codes: ["academic.class-session.cancel"] },
      { label: "Tab Buổi học: Hủy, sửa buổi đã diễn ra (bắt buộc lý do)", codes: ["academic.class-session.correct-past"] },
      { label: "Tab Buổi học: Điểm danh trong giờ", codes: ["academic.attendance.mark"] },
      { label: "Tab Buổi học: Điểm danh bổ sung sau giờ", codes: ["academic.attendance.create", "academic.attendance.update", "academic.attendance.delete"] },
      { label: "Tab Sổ điểm: xem, So sánh qua các kỳ", codes: ["academic.grade.view"] },
      { label: "Tab Sổ điểm: Nhập/sửa điểm, Nhập Excel, Gửi duyệt", codes: ["academic.grade.entry"] },
      { label: "Tab Sổ điểm: Tạo/Xoá setup, Thêm/Xoá đầu điểm", codes: ["academic.grade.setup.create", "academic.grade.setup.update", "academic.grade.setup.delete", "academic.grade.component.create", "academic.grade.component.update", "academic.grade.component.delete", "academic.grade.manage"] },
    ]
  },
  // Học thuật · Lịch dạy
  "acad-my-schedule": {
    viewCodes: ["academic.my-timetable.view"],
    actions: [
      { label: "Nhận lớp (xác nhận có mặt dạy)", codes: ["academic.class-session.checkin"] },
    ]
  },
  // Học thuật · Khung chương trình
  "acad-syllabus": {
    viewCodes: ["academic.curriculum.view"],
    actions: [
      { label: "Thêm khung chương trình chuẩn", codes: ["academic.curriculum.create"] },
      { label: "Lưu khung, Thêm học phần", codes: ["academic.curriculum.update", "academic.skill.create", "academic.skill.update"] },
      { label: "Tạo bản tùy biến cho điểm trường, Nộp duyệt", codes: ["academic.curriculum.customize"] },
      { label: "Duyệt / Từ chối bản tùy biến", codes: ["academic.curriculum.approve"] },
    ]
  },
  // Học thuật · Đánh giá đầu vào
  "acad-entrance": {
    viewCodes: ["academic.entrance.view"],
    actions: [
      { label: "Tạo, sửa, xoá bộ đề, Thêm đầu điểm", codes: ["academic.entrance.setup.create", "academic.entrance.setup.update", "academic.entrance.setup.delete"] },
      { label: "Thêm thí sinh, Nhập điểm, Lưu", codes: ["academic.entrance.score.manage"] },
    ]
  },
  // Học thuật · Sổ điểm hệ thống
  "acad-grades": {
    viewCodes: ["academic.grade.book.view", "academic.grade.view"],
    actions: [
      { label: "Nhập/sửa điểm, Nhập Excel, Gửi duyệt", codes: ["academic.grade.entry"] },
      { label: "Tạo/Xoá setup, Thêm/Xoá đầu điểm", codes: ["academic.grade.setup.create", "academic.grade.setup.update", "academic.grade.setup.delete", "academic.grade.component.create", "academic.grade.component.update", "academic.grade.component.delete", "academic.grade.manage"] },
      { label: "Duyệt tất cả, Từ chối điểm", codes: ["academic.grade.approve"] },
      { label: "Sửa điểm bất kể trạng thái", codes: ["academic.grade.edit.override"] },
    ]
  },
  // Học thuật · Nhận xét học viên / Duyệt nhận xét
  "acad-comments": {
    viewCodes: ["academic.comment.view"],
    actions: [
      { label: "Viết, Lưu nháp, Nộp duyệt, Sửa & gửi lại, Nhập Excel, Áp dụng cho cả lớp", codes: ["academic.comment.write"] },
      { label: "Duyệt / Từ chối, Duyệt tất cả", codes: ["academic.comment.approve"] },
      { label: "Sửa nội dung nhận xét của giáo viên khác", codes: ["academic.comment.manage"] },
    ]
  },
  // Học thuật · Thống kê BTVN
  "acad-homework-stats": {
    viewCodes: ["lms.exercise.report.view"],
    actions: [
      { label: "Xem thống kê mọi lớp", codes: ["lms.exercise-report.manage"] },
      { label: "Xác nhận hạn chót nộp muộn", codes: ["lms.exercise.deadline.confirm"] },
      { label: "Xuất Excel, Tải audio và kết quả AI chấm", codes: ["lms.exercise.report.export"] },
    ]
  },
  // Học thuật · Hồ sơ giáo viên (V203)
  "acad-teachers": {
    viewCodes: ["hrm.teacher.view"],
    actions: [
      { label: "Tab Lịch dạy của giáo viên", codes: ["hrm.employee-schedule.view"] },
    ]
  },
  // Học thuật · Lịch sử thay đổi dữ liệu (V203)
  "acad-change-history": {
    viewCodes: ["academic.change-history.view"],
    actions: [
      { label: "Xem thay đổi của mọi nhân sự (không giới hạn phòng ban)", codes: ["academic.change-history.view-all"] },
    ]
  },
  // Phụ huynh & phản hồi · Duyệt thư mời phụ huynh
  "noti-meeting-invites": {
    viewCodes: ["notification.meeting-invite.approve"],
    actions: []
  },
  // Phụ huynh & phản hồi · Duyệt cảnh báo thái độ
  "noti-attitude-escalations": {
    viewCodes: ["notification.attitude-escalation.approve"],
    actions: []
  },
  // Phụ huynh & phản hồi · Ý kiến phản hồi
  "fac-feedback": {
    viewCodes: ["facility.feedback.view"],
    actions: [
      { label: "Bắt đầu xử lý, Xác nhận giải quyết, Đóng ticket", codes: ["facility.feedback.resolve"] },
    ]
  },
  // Tài liệu & khảo thí · Soạn & giao đề
  "lms-exercises": {
    viewCodes: ["lms.exam.view", "lms.exam-question.view"],
    actions: [
      { label: "Tạo Đề mới, Soạn Bài mới", codes: ["lms.exam.create", "lms.exercise.create"] },
      { label: "Sửa tên Đề, Sửa Bài", codes: ["lms.exam.update", "lms.exercise.update"] },
      { label: "Xoá Đề, Xoá Bài", codes: ["lms.exam.delete"] },
      { label: "Thêm, sửa, gỡ câu hỏi", codes: ["lms.exam-question.create", "lms.exam-question.update"] },
      { label: "Publish ngay, Gán Đề cho lớp", codes: ["lms.exam.assign", "lms.exercise.publish"] },
      { label: "Quản trị Đề của giáo viên khác", codes: ["lms.exam.manage"] },
    ]
  },
  // Tài liệu & khảo thí · Hàng chờ chấm bài
  "lms-exams": {
    viewCodes: ["lms.grading.view"],
    actions: [
      { label: "Chấm, Lưu kết quả chấm", codes: ["lms.grading.manage"] },
    ]
  },
  // Tài liệu & khảo thí · Kho video ôn tập
  "lms-lectures": {
    viewCodes: ["lms.review-video.view"],
    actions: [
      { label: "Tạo bộ video, Thêm video, Thêm câu hỏi, Nhập Excel, Sửa ngưỡng", codes: ["lms.review-video.create", "lms.review-video.update"] },
      { label: "Xoá bộ, Xoá video, Xoá câu hỏi", codes: ["lms.review-video.delete"] },
      { label: "Gán bộ video cho lớp", codes: ["lms.review-video.assign"] },
      { label: "Quản trị bộ video của giáo viên khác", codes: ["lms.review-video.manage"] },
    ]
  },
  // Tài liệu & khảo thí · Kho tài liệu
  "lms-documents": {
    viewCodes: ["lms.document.view"],
    actions: [
      { label: "Thêm, Sửa tài liệu", codes: ["lms.document.create", "lms.document.update"] },
    ]
  },
  // Tài liệu & khảo thí · Danh mục sách
  "lms-book-catalog": {
    viewCodes: ["lms.book-catalog.view"],
    actions: [
      { label: "Thêm, Xoá Sách / Unit / Sub Topic, Nhập Excel", codes: ["lms.book-catalog.manage"] },
    ]
  },
  // Báo cáo & thống kê · Mẫu báo cáo tự động
  "rep-templates": {
    viewCodes: ["report.template.view"],
    actions: [
      { label: "Tải lên mẫu mới, Cấu hình mapping", codes: ["report.template.create", "report.template.update"] },
      { label: "Xoá mẫu", codes: ["report.template.delete"] },
    ]
  },
  // Báo cáo & thống kê · Thống kê nhận xét
  "rep-daily": {
    viewCodes: ["report.daily-comment.view"],
    actions: [
      { label: "Xuất báo cáo ngày của lớp, Xuất hàng loạt, Xuất báo cáo nhận xét học sinh", codes: ["report.generate"] },
    ]
  },
  // Báo cáo & thống kê · Thống kê điểm
  "rep-grades": {
    viewCodes: ["report.grade.view"],
    actions: [
      { label: "Xuất báo cáo điểm của lớp, Xuất bảng điểm cá nhân", codes: ["report.generate"] },
    ]
  },
  // Báo cáo & thống kê · Hồ sơ học tập
  "rep-student": {
    viewCodes: ["report.student-progress.view"],
    actions: [
      { label: "Xuất hồ sơ PDF", codes: ["report.generate"] },
    ]
  },
  // Báo cáo & thống kê · Biến động học sinh
  "rep-enrollment-movement": {
    viewCodes: ["report.enrollment-stats.view"],
    actions: [
      { label: "Xuất Excel", codes: ["report.generate"] },
    ]
  },
  // Báo cáo & thống kê · Số tiết thực tế
  "rep-actual-periods": {
    viewCodes: ["report.actual-periods.view"],
    actions: []
  },
  // Báo cáo & thống kê · Thống kê giảng dạy theo GV (V203) — xem và Xuất Excel dùng chung 1 mã
  "rep-teaching-stats": {
    viewCodes: ["report.teacher-stats.view"],
    actions: []
  },
  // Báo cáo & thống kê · Nộp & duyệt báo cáo buổi học (V207) — xem, dòng thời gian, Xuất Excel dùng chung 1 mã
  "rep-session-reports": {
    viewCodes: ["report.session-report.view"],
    actions: []
  },
  // Tài chính · Thu phí & hoá đơn
  "fin-billing": {
    viewCodes: ["finance.invoice.view", "finance.tuition-plan.view"],
    actions: [
      { label: "Sinh hoá đơn", codes: ["finance.invoice.generate"] },
      { label: "Ghi nhận thanh toán", codes: ["finance.invoice.payment.record"] },
      { label: "Hủy hoá đơn", codes: ["finance.invoice.cancel"] },
      { label: "Tạo, sửa, gán gói học phí", codes: ["finance.tuition-plan.create", "finance.tuition-plan.update", "finance.tuition-plan.assign"] },
      { label: "Cấp, thu hồi học bổng/miễn giảm", codes: ["finance.scholarship.create", "finance.scholarship.revoke"] },
    ]
  },
  // Tài chính · Chi phí vận hành
  "fin-expenses": {
    viewCodes: ["finance.expense.view"],
    actions: [
      { label: "Tạo đề nghị chi", codes: ["finance.expense.create"] },
      { label: "Duyệt / Từ chối chi", codes: ["finance.expense.approve"] },
    ]
  },
  // Tài chính · Báo cáo kế toán
  "fin-reports": {
    viewCodes: ["finance.report.view"],
    actions: []
  },
  // Cơ sở vật chất · Điểm trường & hợp đồng
  "fac-campuses": {
    viewCodes: ["facility.site.view", "facility.partner-contract.view"],
    actions: [
      { label: "Thêm điểm trường, Lưu thông tin, Gán/Đổi quản lý điểm trường", codes: ["facility.site.create", "facility.site.update"] },
      { label: "Gán giáo viên vào điểm trường", codes: ["facility.site-teacher.assign", "facility.site-teacher.remove"] },
      { label: "Thêm, Chấm dứt, Xoá hợp đồng liên kết", codes: ["facility.partner-contract.create", "facility.partner-contract.update", "facility.partner-contract.delete"] },
      { label: "Quản lý năm học, học kỳ, khung tiết học", codes: ["academic.year.manage"] },
    ]
  },
  // Cơ sở vật chất · Phòng học & thiết bị
  "fac-rooms": {
    viewCodes: ["facility.room.view"],
    actions: [
      { label: "Thêm, Sửa phòng học", codes: ["facility.room.create", "facility.room.update"] },
      { label: "Thêm thiết bị, Báo hỏng, Khôi phục", codes: ["facility.equipment.create", "facility.equipment.update"] },
    ]
  },
};
