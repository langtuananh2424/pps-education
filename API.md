# Tài liệu API — PPS Education Backend

> File này được sinh từ đặc tả OpenAPI của app (`GET /v3/api-docs`, springdoc)
> bằng `scripts/gen-api-md.pl` — xem README > "Tài liệu API" để biết cách sinh
> lại khi API thay đổi. Nguồn sống & luôn mới nhất: chạy app rồi mở **Swagger
> UI** (http://localhost:8080/swagger-ui.html); file này là bản chụp để đọc
> offline. KHÔNG sửa tay file này — sửa sẽ bị ghi đè lần sinh sau.

## Quy ước chung

- **Base URL** (dev local): `http://localhost:8080`
- **Xác thực**: trừ nhóm `Công khai`, mọi endpoint yêu cầu header
  `Authorization: Bearer <accessToken>`. Lấy token qua `POST /api/auth/login`
  (access token sống 15 phút — trường `accessTokenExpiresInSeconds`; làm mới
  bằng `POST /api/auth/refresh` với refresh token, sống 14 ngày).
- **Cột Auth**: `JWT + permission` nghĩa là ngoài JWT còn cần permission code đó
  trong effective permissions (Hybrid PBAC — role mặc định hoặc override UC-04).
  Một số endpoint chỉ ghi `JWT` nhưng vẫn kiểm tra phạm vi dữ liệu trong Service
  (VD: đúng site quản lý, đúng lớp được phân công, đúng con của phụ huynh).
- **Tài khoản demo** cho dev local: xem mục "Tài khoản demo" trong [README](./README.md).
- Ký hiệu trong cột Input: `tên?` = tham số không bắt buộc. Kiểu dữ liệu của
  body/response xem chi tiết ở [Phụ lục: Schemas](#phụ-lục-schemas).

## Mục lục

- [Xác thực (UC-01)](#xác-thực-uc-01)
- [Khởi tạo tài khoản người dùng (UC-43)](#khởi-tạo-tài-khoản-người-dùng-uc-43)
- [Danh mục quyền (UC-02)](#danh-mục-quyền-uc-02)
- [Nhóm quyền mặc định (UC-03)](#nhóm-quyền-mặc-định-uc-03)
- [Quyền ngoại lệ theo tài khoản (UC-04)](#quyền-ngoại-lệ-theo-tài-khoản-uc-04)
- [Gán/Thu hồi vai trò cho tài khoản (UC-46)](#gánthu-hồi-vai-trò-cho-tài-khoản-uc-46)
- [Nhật ký phân quyền (UC-05)](#nhật-ký-phân-quyền-uc-05)
- [Quản lý công việc (UC-06/07)](#quản-lý-công-việc-uc-0607)
- [Hồ sơ nhân sự, hợp đồng, bằng cấp (UC-08)](#hồ-sơ-nhân-sự-hợp-đồng-bằng-cấp-uc-08)
- [Chấm công nhân sự (UC-09)](#chấm-công-nhân-sự-uc-09)
- [Đơn từ (UC-10/11)](#đơn-từ-uc-1011)
- [Bảng lương (UC-12)](#bảng-lương-uc-12)
- [Hồ sơ học sinh & trạng thái học tập (UC-13/14)](#hồ-sơ-học-sinh-trạng-thái-học-tập-uc-1314)
- [Điểm danh học sinh (UC-15)](#điểm-danh-học-sinh-uc-15)
- [Import học sinh từ Excel (UC-35)](#import-học-sinh-từ-excel-uc-35)
- [Khung chương trình (UC-16/16b/17)](#khung-chương-trình-uc-1616b17)
- [Lớp học, giáo viên, ghi danh (UC-18)](#lớp-học-giáo-viên-ghi-danh-uc-18)
- [Buổi học / xếp lịch](#buổi-học-xếp-lịch)
- [Sổ điểm & duyệt điểm (UC-19/20)](#sổ-điểm-duyệt-điểm-uc-1920)
- [Nhận xét học sinh (UC-21/22)](#nhận-xét-học-sinh-uc-2122)
- [Ngân hàng câu hỏi (UC-40)](#ngân-hàng-câu-hỏi-uc-40)
- [Soạn & giao đề (UC-40)](#soạn-giao-đề-uc-40)
- [Làm bài & nộp bài (LMS)](#làm-bài-nộp-bài-lms)
- [Chấm bài thủ công (UC-41)](#chấm-bài-thủ-công-uc-41)
- [Kế hoạch giảng dạy](#kế-hoạch-giảng-dạy)
- [Chọn lớp đang xem — cổng HS/PH (UC-42)](#chọn-lớp-đang-xem-cổng-hsph-uc-42)
- [Cổng phụ huynh](#cổng-phụ-huynh)
- [Thông báo](#thông-báo)
- [Biểu phí học phí](#biểu-phí-học-phí)
- [Học bổng](#học-bổng)
- [Hóa đơn & thanh toán (UC-30)](#hóa-đơn-thanh-toán-uc-30)
- [Chi phí vận hành (UC-31)](#chi-phí-vận-hành-uc-31)
- [Báo cáo tài chính (UC-32)](#báo-cáo-tài-chính-uc-32)
- [Lead & chuyển đổi tuyển sinh (UC-33/34)](#lead-chuyển-đổi-tuyển-sinh-uc-3334)
- [Điểm trường (UC-36)](#điểm-trường-uc-36)
- [Hợp đồng trường liên kết (UC-36b)](#hợp-đồng-trường-liên-kết-uc-36b)
- [Phòng học (UC-37)](#phòng-học-uc-37)
- [Thiết bị dạy học (UC-37)](#thiết-bị-dạy-học-uc-37)
- [Phản hồi trường liên kết (UC-38/39)](#phản-hồi-trường-liên-kết-uc-3839)
- [Cổng trường liên kết](#cổng-trường-liên-kết)
- [academic-dashboard-controller](#academic-dashboard-controller)
- [academic-settings-controller](#academic-settings-controller)
- [academic-term-controller](#academic-term-controller)
- [academic-year-controller](#academic-year-controller)
- [actual-periods-report-controller](#actual-periods-report-controller)
- [ai-token-usage-controller](#ai-token-usage-controller)
- [attendance-summary-export-controller](#attendance-summary-export-controller)
- [book-catalog-import-controller](#book-catalog-import-controller)
- [book-controller](#book-controller)
- [change-history-controller](#change-history-controller)
- [class-schedule-import-controller](#class-schedule-import-controller)
- [class-session-check-in-controller](#class-session-check-in-controller)
- [comment-ai-draft-controller](#comment-ai-draft-controller)
- [comment-ai-review-controller](#comment-ai-review-controller)
- [curriculum-document-controller](#curriculum-document-controller)
- [department-controller](#department-controller)
- [employee-batch-import-controller](#employee-batch-import-controller)
- [employee-schedule-controller](#employee-schedule-controller)
- [enrollment-movement-report-controller](#enrollment-movement-report-controller)
- [entrance-assessment-controller](#entrance-assessment-controller)
- [exam-controller](#exam-controller)
- [exam-question-controller](#exam-question-controller)
- [exercise-export-controller](#exercise-export-controller)
- [exercise-report-controller](#exercise-report-controller)
- [grade-import-controller](#grade-import-controller)
- [homework-parent-meeting-invite-controller](#homework-parent-meeting-invite-controller)
- [homework-skill-batch-controller](#homework-skill-batch-controller)
- [leave-substitution-controller](#leave-substitution-controller)
- [leave-type-controller](#leave-type-controller)
- [listening-practice-controller](#listening-practice-controller)
- [listening-practice-grading-controller](#listening-practice-grading-controller)
- [media-controller](#media-controller)
- [parent-batch-import-controller](#parent-batch-import-controller)
- [parent-controller](#parent-controller)
- [payment-link-controller](#payment-link-controller)
- [position-controller](#position-controller)
- [question-import-controller](#question-import-controller)
- [reflex-question-format-controller](#reflex-question-format-controller)
- [reflex-sequential-grading-controller](#reflex-sequential-grading-controller)
- [report-generation-controller](#report-generation-controller)
- [report-template-controller](#report-template-controller)
- [review-video-catalog-import-controller](#review-video-catalog-import-controller)
- [review-video-controller](#review-video-controller)
- [review-video-report-controller](#review-video-report-controller)
- [session-report-tracking-controller](#session-report-tracking-controller)
- [shift-controller](#shift-controller)
- [site-period-template-controller](#site-period-template-controller)
- [skill-controller](#skill-controller)
- [speaking-ai-grading-test-controller](#speaking-ai-grading-test-controller)
- [student-attendance-settings-controller](#student-attendance-settings-controller)
- [student-attitude-escalation-controller](#student-attitude-escalation-controller)
- [student-portal-controller](#student-portal-controller)
- [sub-topic-controller](#sub-topic-controller)
- [system-setting-controller](#system-setting-controller)
- [task-settings-controller](#task-settings-controller)
- [teacher-profile-controller](#teacher-profile-controller)
- [teacher-schedule-controller](#teacher-schedule-controller)
- [teaching-stats-controller](#teaching-stats-controller)
- [term-comment-ai-draft-controller](#term-comment-ai-draft-controller)
- [unit-controller](#unit-controller)
- [work-calendar-controller](#work-calendar-controller)
- [Phụ lục: Schemas](#phụ-lục-schemas)

---

## Xác thực (UC-01)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/auth/login` | Công khai | Body: [LoginRequest](#loginrequest) | [LoginResponse](#loginresponse) |
| POST | `/api/auth/login/google` | Công khai | Body: [GoogleLoginRequest](#googleloginrequest) | [LoginResponse](#loginresponse) |
| POST | `/api/auth/logout` | Công khai | Body: [LogoutRequest](#logoutrequest) | 200 (không có body) |
| GET | `/api/auth/me` | JWT | — | [CurrentUserResponse](#currentuserresponse) |
| PUT | `/api/auth/me/password` | JWT | Body: [ChangeOwnPasswordRequest](#changeownpasswordrequest) | 200 (không có body) |
| POST | `/api/auth/refresh` | Công khai | Body: [RefreshTokenRequest](#refreshtokenrequest) | [RefreshTokenResponse](#refreshtokenresponse) |

## Khởi tạo tài khoản người dùng (UC-43)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/users` | JWT + `user.view` | Query: `keyword`?, `departmentId`?, `status`?, `roleCode`?, `pageable` | [PageUserListItemResponse](#pageuserlistitemresponse) |
| POST | `/api/users` | JWT + `user.create` | Body: [CreateUserRequest](#createuserrequest) | [UserResponse](#userresponse) |
| GET | `/api/users/{userId}` | JWT + `user.view` | — | [UserDetailResponse](#userdetailresponse) |
| PUT | `/api/users/{userId}` | JWT + `user.update` | Body: [UpdateUserRequest](#updateuserrequest) | [UserResponse](#userresponse) |
| PUT | `/api/users/{userId}/email` | JWT + `user.update` | Body: [UpdateUserEmailRequest](#updateuseremailrequest) | [UserResponse](#userresponse) |
| GET | `/api/users/{userId}/login-history` | JWT + `user.view` | Query: `pageable` | [PageLoginHistoryItemResponse](#pageloginhistoryitemresponse) |
| PUT | `/api/users/{userId}/password` | JWT + `user.update` | Body: [AdminChangePasswordRequest](#adminchangepasswordrequest) | 200 (không có body) |
| DELETE | `/api/users/{userId}/sessions` | JWT + `user.update` | — | 200 (không có body) |
| GET | `/api/users/{userId}/sessions` | JWT + `user.view` | — | mảng [UserSessionResponse](#usersessionresponse) |
| DELETE | `/api/users/{userId}/sessions/{sessionId}` | JWT + `user.update` | — | 200 (không có body) |
| PUT | `/api/users/{userId}/status` | JWT + `user.update` | Body: [UpdateUserStatusRequest](#updateuserstatusrequest) | [UserResponse](#userresponse) |

## Danh mục quyền (UC-02)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/permissions` | JWT + `permission.catalog.view` | — | mảng [PermissionResponse](#permissionresponse) |
| POST | `/api/permissions` | JWT + `permission.catalog.create` | Body: [CreatePermissionRequest](#createpermissionrequest) | [PermissionResponse](#permissionresponse) |
| DELETE | `/api/permissions/{id}` | JWT + `permission.catalog.delete` | — | 200 (không có body) |
| PUT | `/api/permissions/{id}` | JWT + `permission.catalog.update` | Body: [UpdatePermissionRequest](#updatepermissionrequest) | [PermissionResponse](#permissionresponse) |

## Nhóm quyền mặc định (UC-03)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/roles` | JWT + `permission.role.view` | — | mảng [RoleResponse](#roleresponse) |
| POST | `/api/roles` | JWT + `permission.role.create` | Body: [CreateRoleRequest](#createrolerequest) | [RoleResponse](#roleresponse) |
| DELETE | `/api/roles/{id}` | JWT + `permission.role.delete` | — | 200 (không có body) |
| PUT | `/api/roles/{id}/data-scope` | JWT + `permission.role.update` | Body: [UpdateRoleDataScopeRequest](#updateroledatascoperequest) | [RoleResponse](#roleresponse) |
| GET | `/api/roles/{id}/permissions` | JWT + `permission.role.view` | — | [RolePermissionMatrixResponse](#rolepermissionmatrixresponse) |
| PUT | `/api/roles/{id}/permissions` | JWT + `permission.role.update` | Body: [UpdateRolePermissionsRequest](#updaterolepermissionsrequest) | 200 (không có body) |

## Quyền ngoại lệ theo tài khoản (UC-04)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/users/{userId}/effective-permissions` | JWT + `permission.override.view` | — | [EffectivePermissionsResponse](#effectivepermissionsresponse) |
| DELETE | `/api/users/{userId}/permission-overrides/{permissionId}` | JWT + `permission.override.delete` | — | 200 (không có body) |
| PUT | `/api/users/{userId}/permission-overrides/{permissionId}` | JWT + `permission.override.set` | Body: [UserPermissionOverrideRequest](#userpermissionoverriderequest) | 200 (không có body) |

## Gán/Thu hồi vai trò cho tài khoản (UC-46)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/users/{userId}/roles` | JWT + `user.role.view` | — | mảng [RoleResponse](#roleresponse) |
| DELETE | `/api/users/{userId}/roles/{roleId}` | JWT + `user.role.revoke` | — | 200 (không có body) |
| PUT | `/api/users/{userId}/roles/{roleId}` | JWT + `user.role.assign` | — | 200 (không có body) |

## Nhật ký phân quyền (UC-05)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/permission-audit-logs` | JWT + `permission.audit.view` | Query: `actorUserId`?, `targetUserId`?, `action`?, `fromDate`?, `toDate`?, `pageable` | [PagePermissionAuditLogResponse](#pagepermissionauditlogresponse) |

## Quản lý công việc (UC-06/07)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| PUT | `/api/task-assignments/{id}/status` | JWT + `task.receive hoặc task.assign` | Body: [UpdateAssignmentStatusRequest](#updateassignmentstatusrequest) | [TaskAssignmentResponse](#taskassignmentresponse) |
| POST | `/api/tasks` | JWT + `task.assign` | Body: [CreateTaskRequest](#createtaskrequest) | [TaskResponse](#taskresponse) |
| GET | `/api/tasks/created-by-me` | JWT | — | mảng [TaskResponse](#taskresponse) |
| GET | `/api/tasks/my-assignments` | JWT + `task.receive` | — | mảng [TaskAssignmentResponse](#taskassignmentresponse) |
| GET | `/api/tasks/overview` | JWT | — | mảng [TaskResponse](#taskresponse) |
| GET | `/api/tasks/{id}` | JWT | — | [TaskResponse](#taskresponse) |
| GET | `/api/tasks/{id}/assignments` | JWT | — | mảng [TaskAssignmentResponse](#taskassignmentresponse) |
| GET | `/api/tasks/{id}/attachments` | JWT | — | mảng [TaskAttachmentResponse](#taskattachmentresponse) |
| POST | `/api/tasks/{id}/attachments` | JWT | Body: [AddTaskAttachmentRequest](#addtaskattachmentrequest) | [TaskAttachmentResponse](#taskattachmentresponse) |
| POST | `/api/tasks/{id}/cancel` | JWT + `task.assign` | Body: [CancelTaskRequest](#canceltaskrequest) | [TaskResponse](#taskresponse) |
| GET | `/api/tasks/{id}/comments` | JWT | — | mảng [TaskCommentResponse](#taskcommentresponse) |
| POST | `/api/tasks/{id}/comments` | JWT | Body: [AddTaskCommentRequest](#addtaskcommentrequest) | [TaskCommentResponse](#taskcommentresponse) |
| POST | `/api/tasks/{id}/reassign` | JWT + `task.assign` | Body: [ReassignTaskRequest](#reassigntaskrequest) | [TaskAssignmentResponse](#taskassignmentresponse) |

## Hồ sơ nhân sự, hợp đồng, bằng cấp (UC-08)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/employees` | JWT + `hrm.employee.view` | Query: `query`?, `departmentId`? | mảng [EmployeeResponse](#employeeresponse) |
| POST | `/api/employees` | JWT + `hrm.employee.create` | Body: [CreateEmployeeRequest](#createemployeerequest) | [EmployeeResponse](#employeeresponse) |
| GET | `/api/employees/contracts/expiring` | JWT + `hrm.employee.view` | Query: `withinDays` | mảng [ExpiringContractResponse](#expiringcontractresponse) |
| GET | `/api/employees/me` | JWT | — | [EmployeeResponse](#employeeresponse) |
| PUT | `/api/employees/me` | JWT | Body: [UpdateOwnEmployeeProfileRequest](#updateownemployeeprofilerequest) | [EmployeeResponse](#employeeresponse) |
| GET | `/api/employees/{id}` | JWT + `hrm.employee.view` | — | [EmployeeResponse](#employeeresponse) |
| PUT | `/api/employees/{id}` | JWT + `hrm.employee.update` | Body: [UpdateEmployeeRequest](#updateemployeerequest) | [EmployeeResponse](#employeeresponse) |
| GET | `/api/employees/{id}/commendations` | JWT + `hrm.employee.view` | — | mảng [CommendationResponse](#commendationresponse) |
| POST | `/api/employees/{id}/commendations` | JWT + `hrm.employee.update` | Body: [CreateCommendationRequest](#createcommendationrequest) | [CommendationResponse](#commendationresponse) |
| GET | `/api/employees/{id}/contracts` | JWT + `hrm.employee.view` | — | mảng [EmploymentContractResponse](#employmentcontractresponse) |
| POST | `/api/employees/{id}/contracts` | JWT + `hrm.employee.update` | Body: [CreateEmploymentContractRequest](#createemploymentcontractrequest) | [EmploymentContractResponse](#employmentcontractresponse) |
| PUT | `/api/employees/{id}/contracts/{contractId}` | JWT + `hrm.employee.update` | Body: [UpdateEmploymentContractRequest](#updateemploymentcontractrequest) | [EmploymentContractResponse](#employmentcontractresponse) |
| GET | `/api/employees/{id}/qualifications` | JWT + `hrm.employee.view` | — | mảng [QualificationResponse](#qualificationresponse) |
| POST | `/api/employees/{id}/qualifications` | JWT + `hrm.employee.update` | Body: [CreateQualificationRequest](#createqualificationrequest) | [QualificationResponse](#qualificationresponse) |
| GET | `/api/employees/{id}/teaching-sessions` | JWT + `hrm.employee-schedule.view` | Query: `fromDate`?, `toDate`? | mảng [ClassSessionResponse](#classsessionresponse) |

## Chấm công nhân sự (UC-09)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/attendance/check-in` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |
| POST | `/api/attendance/check-out` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |
| GET | `/api/attendance/detect-site` | JWT | Query: `latitude`, `longitude` | [DetectedSiteResponse](#detectedsiteresponse) |
| GET | `/api/attendance/records` | JWT + `hrm.attendance.view-all` | Query: `employeeId`?, `siteId`?, `from`, `to` | mảng [AttendanceRecordAdminResponse](#attendancerecordadminresponse) |
| GET | `/api/attendance/records/me` | JWT | — | [AttendanceRecordResponse](#attendancerecordresponse) |

## Đơn từ (UC-10/11)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/leave-requests` | JWT | Body: [CreateLeaveRequestRequest](#createleaverequestrequest) | [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/mine` | JWT | — | mảng [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/pending-for-me` | JWT | — | mảng [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/substitute-teacher-candidates` | JWT | Query: `keyword`? | mảng [TeacherLookupResponse](#teacherlookupresponse) |
| GET | `/api/leave-requests/teaching-sessions` | JWT | Query: `startDate`, `endDate` | mảng [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/leave-requests/{id}` | JWT | — | [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/{id}/approvals` | JWT | — | mảng [LeaveRequestApprovalResponse](#leaverequestapprovalresponse) |
| POST | `/api/leave-requests/{id}/decision` | JWT | Body: [DecideLeaveRequestRequest](#decideleaverequestrequest) | [LeaveRequestResponse](#leaverequestresponse) |

## Bảng lương (UC-12)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/payroll/entries` | JWT | Query: `periodId`, `departmentId`?, `employeeId`? | mảng [PayrollEntryResponse](#payrollentryresponse) |
| GET | `/api/payroll/mine` | JWT | Query: `periodCode`? | [PayrollEntryResponse](#payrollentryresponse) |

## Hồ sơ học sinh & trạng thái học tập (UC-13/14)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/students` | JWT + `student.profile.view` | Query: `query`?, `siteId`?, `classId`? | mảng [StudentResponse](#studentresponse) |
| POST | `/api/students` | JWT + `student.profile.create` | Body: [CreateStudentRequest](#createstudentrequest) | [StudentResponse](#studentresponse) |
| GET | `/api/students/{id}` | JWT + `student.profile.view` | — | [StudentResponse](#studentresponse) |
| PUT | `/api/students/{id}` | JWT + `student.profile.update` | Body: [UpdateStudentRequest](#updatestudentrequest) | [StudentResponse](#studentresponse) |
| GET | `/api/students/{id}/parents` | JWT + `student.parent.view` | — | mảng [ParentStudentResponse](#parentstudentresponse) |
| POST | `/api/students/{id}/parents` | JWT + `student.parent.link.create` | Body: [LinkParentRequest](#linkparentrequest) | [ParentStudentResponse](#parentstudentresponse) |
| DELETE | `/api/students/{id}/parents/{parentStudentId}` | JWT + `student.parent.link.delete` | — | 200 (không có body) |
| GET | `/api/students/{id}/profile` | JWT + `student.profile.view` | — | [StudentProfileResponse](#studentprofileresponse) |
| POST | `/api/students/{id}/status` | JWT + `student.status.manage` | Body: [UpdateStudentStatusRequest](#updatestudentstatusrequest) | [StudentStatusHistoryResponse](#studentstatushistoryresponse) |
| GET | `/api/students/{id}/status-history` | JWT | — | mảng [StudentStatusHistoryResponse](#studentstatushistoryresponse) |
| GET | `/api/students/{id}/transfers` | JWT + `student.profile.view` | — | mảng [StudentTransferHistoryResponse](#studenttransferhistoryresponse) |
| POST | `/api/students/{id}/transfers` | JWT + `student.transfer.create` | Body: [RecordTransferRequest](#recordtransferrequest) | [StudentTransferHistoryResponse](#studenttransferhistoryresponse) |

## Điểm danh học sinh (UC-15)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| DELETE | `/api/class-sessions/{classSessionId}/attendance` | JWT + `academic.attendance.delete` | — | 200 (không có body) |
| GET | `/api/class-sessions/{classSessionId}/attendance` | JWT | — | [AttendanceSessionResponse](#attendancesessionresponse) |
| POST | `/api/class-sessions/{classSessionId}/attendance` | JWT + `academic.attendance.mark hoặc academic.attendance.create` | Body: [MarkAttendanceRequest](#markattendancerequest) | [AttendanceSessionResponse](#attendancesessionresponse) |
| GET | `/api/class-sessions/{classSessionId}/attendance/history` | JWT | — | mảng [AttendanceMarkHistoryResponse](#attendancemarkhistoryresponse) |
| PUT | `/api/class-sessions/{classSessionId}/attendance/students/{studentId}/periods/{sessionPeriodId}` | JWT + `academic.attendance.mark hoặc academic.attendance.update` | Body: [UpdatePeriodMarkRequest](#updateperiodmarkrequest) | [AttendanceMarkResponse](#attendancemarkresponse) |
| POST | `/api/class-sessions/{classSessionId}/attendance/submit` | JWT + `academic.attendance.mark hoặc academic.attendance.create` | — | [AttendanceSessionResponse](#attendancesessionresponse) |

## Import học sinh từ Excel (UC-35)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/student-imports` | JWT + `student.profile.import` | Form-data: `file` (tệp) | [StudentBatchImportResponse](#studentbatchimportresponse) |
| POST | `/api/student-imports/accounts-export` | JWT + `student.profile.import` | Body: [AccountExportRequest](#accountexportrequest) | string |
| GET | `/api/student-imports/template` | JWT + `student.profile.import` | — | string |
| GET | `/api/student-imports/{id}` | JWT + `student.profile.import` | — | [StudentBatchImportResponse](#studentbatchimportresponse) |

## Khung chương trình (UC-16/16b/17)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums` | JWT | — | mảng [CurriculumResponse](#curriculumresponse) |
| POST | `/api/curriculums` | JWT + `academic.curriculum.create` | Body: [CreateCurriculumRequest](#createcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| GET | `/api/curriculums/approvals/pending` | JWT + `academic.curriculum.approve` | — | mảng [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| POST | `/api/curriculums/approvals/{approvalFlowId}/decision` | JWT + `academic.curriculum.approve` | Body: [DecideCurriculumApprovalRequest](#decidecurriculumapprovalrequest) | [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| POST | `/api/curriculums/custom` | JWT + `academic.curriculum.customize` | Body: [CreateCustomCurriculumRequest](#createcustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/custom/{id}` | JWT + `academic.curriculum.customize` | Body: [UpdateCustomCurriculumRequest](#updatecustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| POST | `/api/curriculums/custom/{id}/submit` | JWT + `academic.curriculum.customize` | — | [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| GET | `/api/curriculums/{id}` | JWT | — | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/{id}` | JWT + `academic.curriculum.update` | Body: [UpdateCurriculumRequest](#updatecurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| GET | `/api/curriculums/{id}/books` | JWT | — | mảng [BookResponse](#bookresponse) |
| POST | `/api/curriculums/{id}/books` | JWT + `lms.exercise.create` | Body: [CreateBookRequest](#createbookrequest) | [BookResponse](#bookresponse) |
| GET | `/api/curriculums/{id}/subjects` | JWT | — | mảng [CurriculumSubjectResponse](#curriculumsubjectresponse) |
| POST | `/api/curriculums/{id}/subjects` | JWT + `academic.curriculum.update` | Body: [CreateCurriculumSubjectRequest](#createcurriculumsubjectrequest) | [CurriculumSubjectResponse](#curriculumsubjectresponse) |

## Lớp học, giáo viên, ghi danh (UC-18)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes` | JWT | Query: `query`?, `siteId`?, `curriculumId`?, `classCategory`?, `academicYearId`? | mảng [ClassResponse](#classresponse) |
| POST | `/api/classes` | JWT + `academic.class.create` | Body: [CreateClassRequest](#createclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}` | JWT | — | [ClassResponse](#classresponse) |
| PUT | `/api/classes/{id}` | JWT + `academic.class.update` | Body: [UpdateClassRequest](#updateclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}/enrollments` | JWT | — | mảng [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments` | JWT + `academic.class.enrollment.create` | Body: [EnrollStudentRequest](#enrollstudentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments/import` | JWT + `academic.class.enrollment.import` | Form-data: `file` (tệp) | [ClassEnrollmentBatchImportResponse](#classenrollmentbatchimportresponse) |
| GET | `/api/classes/{id}/enrollments/import-template` | JWT + `academic.class.enrollment.import` | — | string |
| POST | `/api/classes/{id}/enrollments/{enrollmentId}/withdraw` | JWT + `academic.class.enrollment.withdraw` | Body: [WithdrawEnrollmentRequest](#withdrawenrollmentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/promote` | JWT + `academic.class.promote` | Body: [PromoteClassRequest](#promoteclassrequest) | [PromoteClassResponse](#promoteclassresponse) |
| GET | `/api/classes/{id}/teachers` | JWT | — | mảng [ClassTeacherResponse](#classteacherresponse) |
| POST | `/api/classes/{id}/teachers` | JWT + `academic.class.teacher.assign` | Body: [AssignTeacherRequest](#assignteacherrequest) | [ClassTeacherResponse](#classteacherresponse) |
| GET | `/api/classes/{id}/teachers/history` | JWT | — | mảng [ClassTeacherHistoryResponse](#classteacherhistoryresponse) |
| PUT | `/api/classes/{id}/teachers/{classTeacherId}/change` | JWT + `academic.class.teacher.assign` | Body: [ChangeTeacherRequest](#changeteacherrequest) | [ClassTeacherResponse](#classteacherresponse) |
| PUT | `/api/classes/{id}/teachers/{classTeacherId}/end` | JWT + `academic.class.teacher.assign` | Body: [EndTeacherAssignmentRequest](#endteacherassignmentrequest) | [ClassTeacherResponse](#classteacherresponse) |

## Buổi học / xếp lịch

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/sessions` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions` | JWT + `academic.class-session.create` | Body: [CreateClassSessionRequest](#createclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions/bulk` | JWT + `academic.class-session.create hoặc academic.class-session.generate` | Body: [BulkCreateClassSessionRequest](#bulkcreateclasssessionrequest) | [BulkCreateClassSessionResponse](#bulkcreateclasssessionresponse) |
| GET | `/api/classes/{classId}/sessions/cancelled-pending-makeup` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/classes/{classId}/sessions/today` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| PATCH | `/api/classes/{classId}/sessions/{classSessionId}/assignment` | JWT + `academic.class-session.reschedule` | Body: [UpdateSessionAssignmentRequest](#updatesessionassignmentrequest) | [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/cancel` | JWT + `academic.class-session.cancel` | Body: [CancelClassSessionRequest](#cancelclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/classes/{classId}/sessions/{classSessionId}/periods` | JWT | — | mảng [SessionPeriodResponse](#sessionperiodresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/reschedule` | JWT + `academic.class-session.reschedule` | Body: [RescheduleClassSessionRequest](#rescheduleclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |

## Sổ điểm & duyệt điểm (UC-19/20)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/grade-component-setups` | JWT | Query: `academicTermId`? | mảng [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| POST | `/api/classes/{classId}/grade-component-setups` | JWT + `academic.grade.setup.create` | Body: [CreateGradeComponentSetupRequest](#creategradecomponentsetuprequest) | [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| GET | `/api/classes/{classId}/grade-component-setups/{setupId}/results` | JWT | — | mảng [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| GET | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| POST | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Body: [EnterGradeRequest](#entergraderequest) | [GradeEntryResponse](#gradeentryresponse) |
| DELETE | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}/students/{studentId}` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | — | 200 (không có body) |
| DELETE | `/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | — | 200 (không có body) |
| POST | `/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Body: [EnterGradeEvaluationResultRequest](#entergradeevaluationresultrequest) | [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| DELETE | `/api/grade-component-setups/{id}` | JWT + `academic.grade.setup.delete` | — | 200 (không có body) |
| PUT | `/api/grade-component-setups/{id}` | JWT + `academic.grade.setup.update` | Body: [UpdateGradeComponentSetupRequest](#updategradecomponentsetuprequest) | [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| GET | `/api/grade-component-setups/{id}/roster` | JWT | — | mảng [StudentResponse](#studentresponse) |
| GET | `/api/grade-component-setups/{setupId}/components` | JWT | — | mảng [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| POST | `/api/grade-component-setups/{setupId}/components` | JWT + `academic.grade.component.create` | Body: [CreateGradeEvaluationComponentRequest](#creategradeevaluationcomponentrequest) | [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| DELETE | `/api/grade-evaluation-components/{id}` | JWT + `academic.grade.component.delete` | — | 200 (không có body) |
| PUT | `/api/grade-evaluation-components/{id}` | JWT + `academic.grade.component.update` | Body: [UpdateGradeEvaluationComponentRequest](#updategradeevaluationcomponentrequest) | [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| POST | `/api/grades/decision` | JWT | Body: [PublishGradesRequest](#publishgradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/grades/pending` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| POST | `/api/grades/submit` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Body: [SubmitGradesRequest](#submitgradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |

## Nhận xét học sinh (UC-21/22)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/class-sessions/{classSessionId}/comments/apply-homework` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [ApplyClassHomeworkRequest](#applyclasshomeworkrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/class-sessions/{classSessionId}/comments/auto-progress-preview` | JWT + `academic.comment.write hoặc academic.comment.approve` | — | mảng [AutoProgressPreviewResponse](#autoprogresspreviewresponse) |
| POST | `/api/class-sessions/{classSessionId}/comments/import` | JWT + `academic.comment.write hoặc academic.comment.approve` | Form-data: `file` (tệp) | [DailyCommentImportResponse](#dailycommentimportresponse) |
| POST | `/api/class-sessions/{classSessionId}/comments/import-preview` | JWT + `academic.comment.write hoặc academic.comment.approve` | Form-data: `file` (tệp) | [DailyCommentImportPreviewResponse](#dailycommentimportpreviewresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/lesson-content` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateLessonContentRequest](#updatelessoncontentrequest) | [ClassSessionLessonContentResponse](#classsessionlessoncontentresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/teacher-name` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateActualTeacherNameRequest](#updateactualteachernamerequest) | [ClassSessionTeacherNameResponse](#classsessionteachernameresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/teacher-type` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateSessionTeacherTypeRequest](#updatesessionteachertyperequest) | [ClassSessionTeacherTypeResponse](#classsessionteachertyperesponse) |
| GET | `/api/class-sessions/{classSessionId}/comments/template` | JWT + `academic.comment.write hoặc academic.comment.approve` | — | string |
| GET | `/api/class-sessions/{sessionId}/comments/history` | JWT | — | mảng [StudentCommentHistoryResponse](#studentcommenthistoryresponse) |
| POST | `/api/classes/{classId}/class-sessions/{classSessionId}/comments/draft-batch` | JWT + `academic.comment.write` | Body: [SaveDraftCommentsRequest](#savedraftcommentsrequest) | [SaveDraftCommentsResponse](#savedraftcommentsresponse) |
| GET | `/api/classes/{classId}/comments` | JWT | Query: `studentId`? | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments` | JWT + `academic.comment.write` | Body: [CreateStudentCommentRequest](#createstudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments/submit` | JWT + `academic.comment.write` | Body: [SubmitCommentsRequest](#submitcommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/comments/decision` | JWT + `academic.comment.approve` | Body: [DecideCommentsRequest](#decidecommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/comments/pending` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| PUT | `/api/comments/pending/{id}/content` | JWT + `academic.comment.approve` | Body: [UpdateStudentCommentContentRequest](#updatestudentcommentcontentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| PUT | `/api/comments/{id}` | JWT + `academic.comment.write` | Body: [UpdateStudentCommentRequest](#updatestudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/student-comments/{id}/history` | JWT | — | mảng [StudentCommentHistoryResponse](#studentcommenthistoryresponse) |

## Ngân hàng câu hỏi (UC-40)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums/{curriculumId}/question-banks` | JWT + `lms.question-bank.view` | — | mảng [QuestionBankResponse](#questionbankresponse) |
| POST | `/api/question-banks` | JWT + `lms.question-bank.create` | Body: [CreateQuestionBankRequest](#createquestionbankrequest) | [QuestionBankResponse](#questionbankresponse) |
| GET | `/api/question-banks/{bankId}/questions` | JWT + `lms.question-bank.view` | — | mảng [QuestionResponse](#questionresponse) |
| PUT | `/api/question-banks/{id}/status` | JWT + `lms.question-bank.update` | Body: [UpdateQuestionBankStatusRequest](#updatequestionbankstatusrequest) | [QuestionBankResponse](#questionbankresponse) |
| POST | `/api/questions` | JWT + `lms.question-bank.create` | Body: [CreateQuestionRequest](#createquestionrequest) | [QuestionResponse](#questionresponse) |
| GET | `/api/questions/{id}` | JWT + `lms.question-bank.view` | — | [QuestionResponse](#questionresponse) |
| PUT | `/api/questions/{id}` | JWT + `lms.question-bank.update` | Body: [UpdateQuestionRequest](#updatequestionrequest) | [QuestionResponse](#questionresponse) |

## Soạn & giao đề (UC-40)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/exercises` | JWT | — | mảng [ExerciseAssignmentResponse](#exerciseassignmentresponse) |
| GET | `/api/classes/{classId}/exercises/published` | JWT | — | mảng [ExerciseResponse](#exerciseresponse) |
| PUT | `/api/exercise-assignments/{id}/late-submission-allowed` | JWT + `lms.exercise.deadline.confirm` | Body: [UpdateLateSubmissionAllowedRequest](#updatelatesubmissionallowedrequest) | [ExerciseAssignmentResponse](#exerciseassignmentresponse) |
| POST | `/api/exercises` | JWT + `lms.exercise.create` | Body: [CreateExerciseRequest](#createexerciserequest) | [ExerciseResponse](#exerciseresponse) |
| DELETE | `/api/exercises/{id}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| GET | `/api/exercises/{id}` | JWT | — | [ExerciseResponse](#exerciseresponse) |
| PUT | `/api/exercises/{id}` | JWT + `lms.exercise.update` | Body: [UpdateExerciseRequest](#updateexerciserequest) | [ExerciseResponse](#exerciseresponse) |
| POST | `/api/exercises/{id}/publish` | JWT + `lms.exercise.publish` | — | [ExerciseResponse](#exerciseresponse) |
| GET | `/api/exercises/{id}/questions` | JWT | — | mảng [ExerciseQuestionResponse](#exercisequestionresponse) |
| POST | `/api/exercises/{id}/questions` | JWT + `lms.exercise.update` | Body: [AddExerciseQuestionRequest](#addexercisequestionrequest) | [ExerciseQuestionResponse](#exercisequestionresponse) |
| DELETE | `/api/exercises/{id}/questions/{exerciseQuestionId}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| PUT | `/api/exercises/{id}/questions/{exerciseQuestionId}/points` | JWT + `lms.exercise.update` | Body: [UpdateExerciseQuestionPointsRequest](#updateexercisequestionpointsrequest) | [ExerciseQuestionResponse](#exercisequestionresponse) |

## Làm bài & nộp bài (LMS)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/attempts/{id}` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/attempts/{id}/answers` | JWT | — | mảng [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/answers` | JWT | Body: [SaveAnswerRequest](#saveanswerrequest) | [StudentAnswerResponse](#studentanswerresponse) |
| GET | `/api/attempts/{id}/answers/for-grading` | JWT + `lms.grading.manage` | — | mảng [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/batch-integrity-violation-notify` | JWT | Query: `violationCount` | 200 (không có body) |
| POST | `/api/attempts/{id}/integrity-events` | JWT | Body: [RecordIntegrityEventsRequest](#recordintegrityeventsrequest) | [IntegrityEventBatchResponse](#integrityeventbatchresponse) |
| GET | `/api/attempts/{id}/integrity-summary` | JWT + `lms.grading.manage` | — | [IntegritySummaryResponse](#integritysummaryresponse) |
| GET | `/api/attempts/{id}/listening-hint` | JWT | Query: `questionId` | [ListeningHintResponse](#listeninghintresponse) |
| POST | `/api/attempts/{id}/listening-plays` | JWT | Body: [RecordListeningPlayRequest](#recordlisteningplayrequest) | [ListeningPlayProgressResponse](#listeningplayprogressresponse) |
| POST | `/api/attempts/{id}/reveal-and-close` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/attempts/{id}/select-for-grading` | JWT + `lms.grading.manage` | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/attempts/{id}/submit` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/exercises/{exerciseId}/attempts` | JWT | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/exercises/{exerciseId}/attempts` | JWT | Query: `assignmentId` | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/exercises/{exerciseId}/students/{studentId}/attempts` | JWT + `lms.grading.manage` | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |

## Chấm bài thủ công (UC-41)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/answers/{studentAnswerId}/grade` | JWT + `lms.grading.manage` | Body: [GradeAnswerRequest](#gradeanswerrequest) | [StudentAnswerGradingResponse](#studentanswergradingresponse) |
| GET | `/api/grading/pending` | JWT + `lms.grading.manage` | — | mảng [PendingGradingResponse](#pendinggradingresponse) |

## Kế hoạch giảng dạy

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/teaching-plans` | JWT | — | mảng [TeachingPlanResponse](#teachingplanresponse) |
| POST | `/api/teaching-plans` | JWT + `lms.teaching-plan.mark hoặc lms.teaching-plan.manage` | Body: [CreateTeachingPlanRequest](#createteachingplanrequest) | [TeachingPlanResponse](#teachingplanresponse) |
| GET | `/api/teaching-plans/{id}` | JWT | — | [TeachingPlanResponse](#teachingplanresponse) |
| PUT | `/api/teaching-plans/{id}` | JWT + `lms.teaching-plan.mark hoặc lms.teaching-plan.manage` | Body: [UpdateTeachingPlanRequest](#updateteachingplanrequest) | [TeachingPlanResponse](#teachingplanresponse) |
| GET | `/api/teaching-plans/{id}/items` | JWT | — | mảng [TeachingPlanItemResponse](#teachingplanitemresponse) |
| POST | `/api/teaching-plans/{id}/items` | JWT + `lms.teaching-plan.mark hoặc lms.teaching-plan.manage` | Body: [AddTeachingPlanItemRequest](#addteachingplanitemrequest) | [TeachingPlanItemResponse](#teachingplanitemresponse) |
| PUT | `/api/teaching-plans/{id}/items/{itemId}` | JWT + `lms.teaching-plan.mark hoặc lms.teaching-plan.manage` | Body: [UpdateTeachingPlanItemRequest](#updateteachingplanitemrequest) | [TeachingPlanItemResponse](#teachingplanitemresponse) |

## Chọn lớp đang xem — cổng HS/PH (UC-42)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/portal/students/{studentId}/class-options` | JWT | — | mảng [PortalClassOptionResponse](#portalclassoptionresponse) |

## Cổng phụ huynh

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/portal/parent/children` | JWT | — | mảng [ChildResponse](#childresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/academic-terms/{academicTermId}/evaluation/{evaluationType}/result` | JWT | — | [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/attendance` | JWT | — | mảng [AttendanceMarkResponse](#attendancemarkresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/comments` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/grades` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/homework` | JWT | — | mảng [HomeworkProgressResponse](#homeworkprogressresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/schedule` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |

## Thông báo

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/notifications` | JWT | Query: `pageable` | [PageNotificationResponse](#pagenotificationresponse) |
| POST | `/api/notifications/device-token` | JWT | Body: [DeviceTokenRequest](#devicetokenrequest) | 200 (không có body) |
| GET | `/api/notifications/device-token-counts` | JWT + `notification.send.manual` | Query: `userIds` | mảng [DeviceTokenCountResponse](#devicetokencountresponse) |
| DELETE | `/api/notifications/device-token/{token}` | JWT | — | 200 (không có body) |
| GET | `/api/notifications/preferences/{notificationType}` | JWT | — | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| PUT | `/api/notifications/preferences/{notificationType}` | JWT | Body: [NotificationPreferenceRequest](#notificationpreferencerequest) | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| POST | `/api/notifications/push-setup-log` | JWT | Body: [PushSetupLogRequest](#pushsetuplogrequest) | 200 (không có body) |
| POST | `/api/notifications/send-manual` | JWT + `notification.send.manual` | Body: [SendNotificationRequest](#sendnotificationrequest) | [SendNotificationResponse](#sendnotificationresponse) |
| POST | `/api/notifications/{id}/read` | JWT | — | [NotificationResponse](#notificationresponse) |

## Biểu phí học phí

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/tuition-plan-assignments` | JWT + `finance.tuition-plan.view` | Query: `classId`? | mảng [TuitionPlanAssignmentResponse](#tuitionplanassignmentresponse) |
| POST | `/api/finance/tuition-plan-assignments` | JWT + `finance.tuition-plan.assign` | Body: [AssignTuitionPlanRequest](#assigntuitionplanrequest) | [TuitionPlanAssignmentResponse](#tuitionplanassignmentresponse) |
| GET | `/api/finance/tuition-plans` | JWT + `finance.tuition-plan.view` | Query: `status`? | mảng [TuitionPlanResponse](#tuitionplanresponse) |
| POST | `/api/finance/tuition-plans` | JWT + `finance.tuition-plan.create` | Body: [CreateTuitionPlanRequest](#createtuitionplanrequest) | [TuitionPlanResponse](#tuitionplanresponse) |
| GET | `/api/finance/tuition-plans/{id}` | JWT + `finance.tuition-plan.view` | — | [TuitionPlanResponse](#tuitionplanresponse) |
| PUT | `/api/finance/tuition-plans/{id}/status` | JWT + `finance.tuition-plan.update` | Body: [UpdateTuitionPlanStatusRequest](#updatetuitionplanstatusrequest) | [TuitionPlanResponse](#tuitionplanresponse) |

## Học bổng

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/scholarships` | JWT + `finance.invoice.view hoặc finance.scholarship.create hoặc finance.scholarship.revoke` | Query: `studentId`?, `status`? | mảng [ScholarshipResponse](#scholarshipresponse) |
| POST | `/api/finance/scholarships` | JWT + `finance.scholarship.create` | Body: [CreateScholarshipRequest](#createscholarshiprequest) | [ScholarshipResponse](#scholarshipresponse) |
| POST | `/api/finance/scholarships/{id}/revoke` | JWT + `finance.scholarship.revoke` | — | [ScholarshipResponse](#scholarshipresponse) |

## Hóa đơn & thanh toán (UC-30)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/invoices` | JWT + `finance.invoice.view` | Query: `from`, `to`, `status`?, `siteId`?, `classId`?, `keyword`? | mảng [InvoiceResponse](#invoiceresponse) |
| POST | `/api/finance/invoices/generate` | JWT + `finance.invoice.generate` | Body: [GenerateInvoicesRequest](#generateinvoicesrequest) | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/my` | JWT | — | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/{id}` | JWT | — | [InvoiceResponse](#invoiceresponse) |
| POST | `/api/finance/invoices/{id}/cancel` | JWT + `finance.invoice.cancel` | Body: [CancelInvoiceRequest](#cancelinvoicerequest) | [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/{id}/detail` | JWT + `finance.invoice.view` | — | [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/{id}/history` | JWT + `finance.invoice.view` | — | mảng [InvoiceHistoryResponse](#invoicehistoryresponse) |
| GET | `/api/finance/invoices/{id}/payments` | JWT + `finance.invoice.view` | — | mảng [PaymentResponse](#paymentresponse) |
| POST | `/api/finance/invoices/{id}/payments` | JWT + `finance.invoice.payment.record` | Body: [RecordManualPaymentRequest](#recordmanualpaymentrequest) | [PaymentResponse](#paymentresponse) |
| POST | `/api/webhooks/bank-payment` | Header `X-Webhook-Secret` | Body: [BankWebhookPaymentRequest](#bankwebhookpaymentrequest)<br>Header: `X-Webhook-Secret`? | [PaymentResponse](#paymentresponse) |

## Chi phí vận hành (UC-31)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/expense-categories` | JWT + `finance.expense.view hoặc finance.expense.create` | — | mảng [ExpenseCategoryResponse](#expensecategoryresponse) |
| GET | `/api/finance/operating-expenses` | JWT + `finance.expense.view hoặc finance.expense.create hoặc finance.expense.approve` | Query: `siteId`?, `from`, `to` | mảng [OperatingExpenseResponse](#operatingexpenseresponse) |
| POST | `/api/finance/operating-expenses` | JWT + `finance.expense.create` | Body: [CreateOperatingExpenseRequest](#createoperatingexpenserequest) | [OperatingExpenseResponse](#operatingexpenseresponse) |
| POST | `/api/finance/operating-expenses/{id}/decision` | JWT + `finance.expense.approve` | Body: [DecideOperatingExpenseRequest](#decideoperatingexpenserequest) | [OperatingExpenseResponse](#operatingexpenseresponse) |

## Báo cáo tài chính (UC-32)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/reports/chain` | JWT + `finance.report.view` | Query: `from`, `to` | [ChainFinancialReportResponse](#chainfinancialreportresponse) |
| GET | `/api/finance/reports/my-sites` | JWT | Query: `from`, `to` | mảng [FinancialReportResponse](#financialreportresponse) |

## Lead & chuyển đổi tuyển sinh (UC-33/34)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/leads` | JWT + `crm.lead.create` | Body: [CreateLeadRequest](#createleadrequest) | [LeadResponse](#leadresponse) |
| GET | `/api/leads/my-leads` | JWT | — | mảng [LeadResponse](#leadresponse) |
| GET | `/api/leads/open` | JWT | — | mảng [LeadResponse](#leadresponse) |
| GET | `/api/leads/{id}` | JWT | — | [LeadResponse](#leadresponse) |
| PUT | `/api/leads/{id}/assign` | JWT + `crm.lead.assign` | Body: [AssignLeadRequest](#assignleadrequest) | [LeadResponse](#leadresponse) |
| POST | `/api/leads/{id}/convert` | JWT + `crm.lead.convert` | Body: [ConvertLeadRequest](#convertleadrequest) | [LeadResponse](#leadresponse) |
| PUT | `/api/leads/{id}/status` | JWT + `crm.lead.update` | Body: [UpdateLeadStatusRequest](#updateleadstatusrequest) | [LeadResponse](#leadresponse) |

## Điểm trường (UC-36)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/sites` | JWT | — | mảng [SiteResponse](#siteresponse) |
| POST | `/api/sites` | JWT + `facility.site.create` | Body: [CreateSiteRequest](#createsiterequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}` | JWT | — | [SiteResponse](#siteresponse) |
| PUT | `/api/sites/{id}` | JWT + `facility.site.update` | Body: [UpdateSiteRequest](#updatesiterequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}/attendance-summary` | JWT | — | mảng [PartnerAttendanceSummaryResponse](#partnerattendancesummaryresponse) |
| PUT | `/api/sites/{id}/manager` | JWT + `facility.site.update` | Body: [AssignSiteManagerRequest](#assignsitemanagerrequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}/sessions` | JWT | Query: `fromDate`, `toDate` | mảng [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/sites/{id}/teachers` | JWT | — | mảng [SiteTeacherResponse](#siteteacherresponse) |
| POST | `/api/sites/{id}/teachers` | JWT + `facility.site-teacher.assign` | Body: [AssignSiteTeacherRequest](#assignsiteteacherrequest) | [SiteTeacherResponse](#siteteacherresponse) |
| DELETE | `/api/sites/{id}/teachers/{siteTeacherId}` | JWT + `facility.site-teacher.remove` | — | 200 (không có body) |

## Hợp đồng trường liên kết (UC-36b)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/partner-contracts` | JWT + `facility.partner-contract.create` | Body: [CreatePartnerContractRequest](#createpartnercontractrequest) | [PartnerContractResponse](#partnercontractresponse) |
| GET | `/api/partner-contracts/expiring` | JWT + `facility.partner-contract.view` | Query: `withinDays` | mảng [ExpiringPartnerContractResponse](#expiringpartnercontractresponse) |
| DELETE | `/api/partner-contracts/{id}` | JWT + `facility.partner-contract.delete` | — | 200 (không có body) |
| PUT | `/api/partner-contracts/{id}` | JWT + `facility.partner-contract.update` | Body: [UpdatePartnerContractRequest](#updatepartnercontractrequest) | [PartnerContractResponse](#partnercontractresponse) |
| POST | `/api/partner-contracts/{id}/terminate` | JWT + `facility.partner-contract.update` | — | [PartnerContractResponse](#partnercontractresponse) |
| GET | `/api/sites/{siteId}/partner-contracts` | JWT + `facility.partner-contract.view` | — | mảng [PartnerContractResponse](#partnercontractresponse) |

## Phòng học (UC-37)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/rooms` | JWT + `facility.room.create` | Body: [CreateRoomRequest](#createroomrequest) | [RoomResponse](#roomresponse) |
| PUT | `/api/rooms/{id}` | JWT + `facility.room.update` | Body: [UpdateRoomRequest](#updateroomrequest) | [RoomResponse](#roomresponse) |
| GET | `/api/sites/{siteId}/rooms` | JWT | — | mảng [RoomResponse](#roomresponse) |

## Thiết bị dạy học (UC-37)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/equipment` | JWT + `facility.equipment.create` | Body: [CreateEquipmentRequest](#createequipmentrequest) | [EquipmentResponse](#equipmentresponse) |
| PUT | `/api/equipment/{id}/status` | JWT + `facility.equipment.update` | Body: [UpdateEquipmentStatusRequest](#updateequipmentstatusrequest) | [EquipmentResponse](#equipmentresponse) |
| GET | `/api/rooms/{roomId}/equipment` | JWT | — | mảng [EquipmentResponse](#equipmentresponse) |
| GET | `/api/sites/{siteId}/equipment` | JWT | — | mảng [EquipmentResponse](#equipmentresponse) |

## Phản hồi trường liên kết (UC-38/39)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/partner-feedbacks` | JWT | Body: [SubmitPartnerFeedbackRequest](#submitpartnerfeedbackrequest) | [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| GET | `/api/partner-feedbacks/my-sites` | JWT | — | mảng [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| GET | `/api/partner-feedbacks/my-submitted` | JWT | — | mảng [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| POST | `/api/partner-feedbacks/{id}/close` | JWT | — | [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| POST | `/api/partner-feedbacks/{id}/exchange` | JWT | Body: [AddFeedbackExchangeRequest](#addfeedbackexchangerequest) | [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| POST | `/api/partner-feedbacks/{id}/resolve` | JWT | Body: [ResolveFeedbackRequest](#resolvefeedbackrequest) | [PartnerFeedbackResponse](#partnerfeedbackresponse) |
| POST | `/api/partner-feedbacks/{id}/start-processing` | JWT | — | [PartnerFeedbackResponse](#partnerfeedbackresponse) |

## Cổng trường liên kết

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/portal/partner/attendance-summary` | JWT | — | mảng [PartnerAttendanceSummaryResponse](#partnerattendancesummaryresponse) |
| GET | `/api/portal/partner/comments` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/portal/partner/grades` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/portal/partner/site` | JWT | — | [PartnerSiteResponse](#partnersiteresponse) |
| GET | `/api/portal/partner/teaching-plans` | JWT | — | mảng [TeachingPlanResponse](#teachingplanresponse) |

## academic-dashboard-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/dashboard/academic-overview` | JWT + `academic.class.view` | Query: `siteId`? | [AcademicDashboardResponse](#academicdashboardresponse) |

## academic-settings-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic/settings/comment-edit-window-days` | JWT | — | [CommentEditWindowResponse](#commenteditwindowresponse) |
| PUT | `/api/academic/settings/comment-edit-window-days` | JWT + `academic.comment.approve` | Body: [UpdateCommentEditWindowRequest](#updatecommenteditwindowrequest) | [CommentEditWindowResponse](#commenteditwindowresponse) |
| GET | `/api/academic/settings/grade-edit-window-days` | JWT | — | [GradeEditWindowResponse](#gradeeditwindowresponse) |
| PUT | `/api/academic/settings/grade-edit-window-days` | JWT + `academic.grade.manage` | Body: [UpdateGradeEditWindowRequest](#updategradeeditwindowrequest) | [GradeEditWindowResponse](#gradeeditwindowresponse) |

## academic-term-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic-terms` | JWT | Query: `siteId` | mảng [AcademicTermResponse](#academictermresponse) |
| POST | `/api/academic-terms` | JWT + `academic.year.manage` | Body: [CreateAcademicTermRequest](#createacademictermrequest) | [AcademicTermResponse](#academictermresponse) |
| GET | `/api/academic-terms/{id}` | JWT | — | [AcademicTermResponse](#academictermresponse) |
| PUT | `/api/academic-terms/{id}` | JWT + `academic.year.manage` | Body: [UpdateAcademicTermRequest](#updateacademictermrequest) | [AcademicTermResponse](#academictermresponse) |

## academic-year-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic-years` | JWT | — | mảng [AcademicYearResponse](#academicyearresponse) |
| POST | `/api/academic-years` | JWT + `academic.year.manage` | Body: [CreateAcademicYearRequest](#createacademicyearrequest) | [AcademicYearResponse](#academicyearresponse) |
| GET | `/api/academic-years/{id}` | JWT | — | [AcademicYearResponse](#academicyearresponse) |
| PUT | `/api/academic-years/{id}` | JWT + `academic.year.manage` | Body: [UpdateAcademicYearRequest](#updateacademicyearrequest) | [AcademicYearResponse](#academicyearresponse) |

## actual-periods-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/sites/{siteId}/actual-periods-grid` | JWT + `report.actual-periods.view` | Query: `periodType`, `year`?, `classId`? | [ActualPeriodsGridResponse](#actualperiodsgridresponse) |
| GET | `/api/sites/{siteId}/actual-periods-grid/export` | JWT + `report.actual-periods.view` | Query: `periodType`, `year`?, `classId`? | string |
| GET | `/api/sites/{siteId}/actual-periods-stats` | JWT + `report.actual-periods.view` | Query: `fromDate`, `toDate`, `periodType`, `periodLabel`, `classId`? | [ActualPeriodsStatsResponse](#actualperiodsstatsresponse) |
| GET | `/api/sites/{siteId}/actual-periods-stats/export` | JWT + `report.actual-periods.view` | Query: `fromDate`, `toDate`, `periodType`, `periodLabel`, `classId`? | string |

## ai-token-usage-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/ai-token-usage/stream` | JWT + `system.settings.manage` | — | [SseEmitter](#sseemitter) |
| GET | `/api/ai-token-usage/summary` | JWT + `system.settings.manage` | Query: `from`?, `to`? | [AiTokenUsageSummaryResponse](#aitokenusagesummaryresponse) |

## attendance-summary-export-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/attendance-summary/export` | JWT + `academic.attendance.view` | Query: `fromDate`?, `toDate`? | string |

## book-catalog-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/book-catalog-imports/{id}` | JWT + `lms.exercise.create hoặc lms.exam.create` | — | [BookCatalogImportResponse](#bookcatalogimportresponse) |
| POST | `/api/curriculums/{curriculumId}/book-catalog-imports` | JWT + `lms.exercise.create hoặc lms.exam.create` | Form-data: `file` (tệp)<br>Query: `teacherType`, `examType`, `exerciseType`, `totalPoints` | [BookCatalogImportResponse](#bookcatalogimportresponse) |

## book-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/books/{bookId}/units` | JWT | — | mảng [UnitResponse](#unitresponse) |
| POST | `/api/books/{bookId}/units` | JWT + `lms.exercise.create` | Body: [CreateUnitRequest](#createunitrequest) | [UnitResponse](#unitresponse) |
| DELETE | `/api/books/{id}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| PUT | `/api/books/{id}` | JWT + `lms.exercise.update` | Body: [UpdateBookRequest](#updatebookrequest) | [BookResponse](#bookresponse) |

## change-history-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/change-history` | JWT + `academic.change-history.view` | Query: `entityType`?, `fromDate`?, `toDate`?, `siteId`?, `classId`?, `studentId`?, `keyword`?, `page`?, `size`? | [PageChangeHistoryItemResponse](#pagechangehistoryitemresponse) |

## class-schedule-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/session-imports` | JWT + `academic.class-session.import` | Form-data: `file` (tệp) | [ClassScheduleImportResponse](#classscheduleimportresponse) |
| GET | `/api/classes/{classId}/session-imports/{id}` | JWT + `academic.class-session.import` | — | [ClassScheduleImportResponse](#classscheduleimportresponse) |

## class-session-check-in-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/class-sessions/check-ins` | JWT + `hrm.attendance.view-all` | Query: `employeeId`?, `siteId`?, `from`, `to` | mảng [ClassSessionCheckInAdminResponse](#classsessioncheckinadminresponse) |
| GET | `/api/class-sessions/my-check-in-status` | JWT | Query: `from`?, `to`? | mảng [ClassSessionCheckInStatusResponse](#classsessioncheckinstatusresponse) |
| POST | `/api/class-sessions/{id}/check-in` | JWT | Body: [ClassSessionCheckInRequest](#classsessioncheckinrequest) | [ClassSessionCheckInResponse](#classsessioncheckinresponse) |

## comment-ai-draft-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/class-sessions/{classSessionId}/comments/ai-draft` | JWT + `academic.comment.write` | Form-data: `audio` (tệp), `homeworkScores`<br>Query: `note`? | [CommentAiDraftJobResponse](#commentaidraftjobresponse) |
| POST | `/api/class-sessions/{classSessionId}/comments/ai-draft/revise` | JWT + `academic.comment.write` | Body: [ReviseCommentAiDraftRequest](#revisecommentaidraftrequest) | [CommentAiDraftJobResponse](#commentaidraftjobresponse) |
| GET | `/api/comment-ai-drafts/{jobId}` | JWT + `academic.comment.write` | — | [CommentAiDraftJobResponse](#commentaidraftjobresponse) |

## comment-ai-review-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/comment-ai-instructions/{jobId}` | JWT + `academic.comment.approve` | — | [CommentAiInstructionJobResponse](#commentaiinstructionjobresponse) |
| GET | `/api/comment-ai-rejection-reasons/{jobId}` | JWT + `academic.comment.approve` | — | [CommentAiRejectionReasonJobResponse](#commentairejectionreasonjobresponse) |
| GET | `/api/comment-ai-reviews/{jobId}` | JWT + `academic.comment.approve` | — | [CommentAiReviewJobResponse](#commentaireviewjobresponse) |
| GET | `/api/comment-ai-suggestions/{jobId}` | JWT + `academic.comment.approve` | — | [CommentAiSuggestionJobResponse](#commentaisuggestionjobresponse) |
| POST | `/api/comments/ai-instruction` | JWT + `academic.comment.approve` | Form-data: `audio` (tệp)<br>Query: `commentIds`, `note`? | [CommentAiInstructionJobResponse](#commentaiinstructionjobresponse) |
| POST | `/api/comments/ai-review` | JWT + `academic.comment.approve` | Body: [CommentAiReviewRequest](#commentaireviewrequest) | [CommentAiReviewJobResponse](#commentaireviewjobresponse) |
| POST | `/api/comments/attitude-alert-preview` | JWT + `academic.comment.approve` | Body: [CommentAiReviewRequest](#commentaireviewrequest) | [CommentAttitudeAlertPreviewResponse](#commentattitudealertpreviewresponse) |
| POST | `/api/comments/{id}/ai-rejection-reason` | JWT + `academic.comment.approve` | Body: [CommentAiSuggestionRequest](#commentaisuggestionrequest) | [CommentAiRejectionReasonJobResponse](#commentairejectionreasonjobresponse) |
| POST | `/api/comments/{id}/ai-suggestion` | JWT + `academic.comment.approve` | Body: [CommentAiSuggestionRequest](#commentaisuggestionrequest) | [CommentAiSuggestionJobResponse](#commentaisuggestionjobresponse) |

## curriculum-document-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums/{curriculumId}/documents` | JWT + `lms.document.view` | — | mảng [CurriculumDocumentResponse](#curriculumdocumentresponse) |
| POST | `/api/curriculums/{curriculumId}/documents` | JWT + `lms.document.create` | Body: [CreateCurriculumDocumentRequest](#createcurriculumdocumentrequest) | [CurriculumDocumentResponse](#curriculumdocumentresponse) |
| PUT | `/api/documents/{id}` | JWT + `lms.document.update` | Body: [UpdateCurriculumDocumentRequest](#updatecurriculumdocumentrequest) | [CurriculumDocumentResponse](#curriculumdocumentresponse) |

## department-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/departments` | JWT | — | mảng [DepartmentResponse](#departmentresponse) |
| POST | `/api/departments` | JWT + `hrm.department.create` | Body: [CreateDepartmentRequest](#createdepartmentrequest) | [DepartmentResponse](#departmentresponse) |
| DELETE | `/api/departments/{id}` | JWT + `hrm.department.delete` | — | 200 (không có body) |
| GET | `/api/departments/{id}` | JWT | — | [DepartmentResponse](#departmentresponse) |
| PUT | `/api/departments/{id}` | JWT + `hrm.department.update` | Body: [UpdateDepartmentRequest](#updatedepartmentrequest) | [DepartmentResponse](#departmentresponse) |
| GET | `/api/departments/{id}/member-candidates` | JWT + `hrm.department.update` | Query: `query`? | mảng [DepartmentMemberResponse](#departmentmemberresponse) |
| GET | `/api/departments/{id}/members` | JWT + `hrm.department.view` | — | mảng [DepartmentMemberResponse](#departmentmemberresponse) |
| POST | `/api/departments/{id}/members` | JWT + `hrm.department.update` | Body: [AddDepartmentMembersRequest](#adddepartmentmembersrequest) | mảng [DepartmentMemberResponse](#departmentmemberresponse) |
| DELETE | `/api/departments/{id}/members/{employeeId}` | JWT + `hrm.department.update` | — | 200 (không có body) |

## employee-batch-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/employee-imports` | JWT + `hrm.employee.import` | Form-data: `file` (tệp) | [EmployeeBatchImportResponse](#employeebatchimportresponse) |
| POST | `/api/employee-imports/accounts-export` | JWT + `hrm.employee.import` | Body: [AccountExportRequest](#accountexportrequest) | string |
| GET | `/api/employee-imports/template` | JWT + `hrm.employee.import` | — | string |
| GET | `/api/employee-imports/{id}` | JWT + `hrm.employee.import` | — | [EmployeeBatchImportResponse](#employeebatchimportresponse) |

## employee-schedule-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/hrm/employee-schedules` | JWT + `hrm.employee-schedule.view` | Query: `from`, `to`, `departmentId`?, `siteId`?, `classId`?, `employeeId`? | [EmployeeScheduleOverviewResponse](#employeescheduleoverviewresponse) |

## enrollment-movement-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-stats` | JWT + `report.enrollment-stats.view` | Query: `classId`? | [EnrollmentMovementStatsResponse](#enrollmentmovementstatsresponse) |
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-stats/export` | JWT + `report.enrollment-stats.view` | Query: `classId`? | string |
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-trend` | JWT + `report.enrollment-stats.view` | Query: `classId`? | [EnrollmentMovementTrendResponse](#enrollmentmovementtrendresponse) |
| GET | `/api/sites/{siteId}/enrollment-movement-grid` | JWT + `report.enrollment-stats.view` | Query: `periodType`, `year`?, `classId`? | [EnrollmentMovementGridResponse](#enrollmentmovementgridresponse) |
| GET | `/api/sites/{siteId}/enrollment-movement-stats` | JWT + `report.enrollment-stats.view` | Query: `fromDate`, `toDate`, `periodType`, `periodLabel`, `classId`? | [EnrollmentMovementStatsResponse](#enrollmentmovementstatsresponse) |
| GET | `/api/sites/{siteId}/enrollment-movement-stats/export` | JWT + `report.enrollment-stats.view` | Query: `fromDate`, `toDate`, `periodType`, `periodLabel`, `classId`? | string |
| GET | `/api/sites/{siteId}/enrollment-movement-trend` | JWT + `report.enrollment-stats.view` | Query: `fromDate`, `toDate`, `periodType`, `periodLabel`, `classId`? | [EnrollmentMovementTrendResponse](#enrollmentmovementtrendresponse) |

## entrance-assessment-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| DELETE | `/api/entrance-assessment-components/{id}` | JWT + `academic.entrance.setup.delete` | — | 200 (không có body) |
| PUT | `/api/entrance-assessment-components/{id}` | JWT + `academic.entrance.setup.update` | Body: [UpdateEntranceAssessmentComponentRequest](#updateentranceassessmentcomponentrequest) | [EntranceAssessmentComponentResponse](#entranceassessmentcomponentresponse) |
| DELETE | `/api/entrance-assessment-results/{id}` | JWT + `academic.entrance.score.manage` | — | 200 (không có body) |
| GET | `/api/entrance-assessment-results/{id}` | JWT | — | [EntranceAssessmentResultResponse](#entranceassessmentresultresponse) |
| POST | `/api/entrance-assessment-results/{id}/mark-placed` | JWT + `academic.entrance.score.manage` | — | [EntranceAssessmentResultResponse](#entranceassessmentresultresponse) |
| GET | `/api/entrance-assessment-setups` | JWT | Query: `siteId`, `academicYearId`? | mảng [EntranceAssessmentSetupResponse](#entranceassessmentsetupresponse) |
| POST | `/api/entrance-assessment-setups` | JWT + `academic.entrance.setup.create` | Body: [CreateEntranceAssessmentSetupRequest](#createentranceassessmentsetuprequest) | [EntranceAssessmentSetupResponse](#entranceassessmentsetupresponse) |
| DELETE | `/api/entrance-assessment-setups/{id}` | JWT + `academic.entrance.setup.delete` | — | 200 (không có body) |
| GET | `/api/entrance-assessment-setups/{id}` | JWT | — | [EntranceAssessmentSetupResponse](#entranceassessmentsetupresponse) |
| PUT | `/api/entrance-assessment-setups/{id}` | JWT + `academic.entrance.setup.update` | Body: [UpdateEntranceAssessmentSetupRequest](#updateentranceassessmentsetuprequest) | [EntranceAssessmentSetupResponse](#entranceassessmentsetupresponse) |
| POST | `/api/entrance-assessment-setups/{setupId}/components` | JWT + `academic.entrance.setup.update` | Body: [CreateEntranceAssessmentComponentRequest](#createentranceassessmentcomponentrequest) | [EntranceAssessmentComponentResponse](#entranceassessmentcomponentresponse) |
| GET | `/api/entrance-assessment-setups/{setupId}/results` | JWT | — | mảng [EntranceAssessmentResultResponse](#entranceassessmentresultresponse) |
| POST | `/api/entrance-assessment-setups/{setupId}/results` | JWT + `academic.entrance.score.manage` | Body: [UpsertEntranceAssessmentResultRequest](#upsertentranceassessmentresultrequest) | [EntranceAssessmentResultResponse](#entranceassessmentresultresponse) |

## exam-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/exams` | JWT | Query: `curriculumId`?, `teacherType`? | mảng [ExamResponse](#examresponse) |
| POST | `/api/exams` | JWT + `lms.exam.create` | Body: [CreateExamRequest](#createexamrequest) | [ExamResponse](#examresponse) |
| DELETE | `/api/exams/{id}` | JWT + `lms.exam.delete` | — | 200 (không có body) |
| GET | `/api/exams/{id}` | JWT | — | [ExamResponse](#examresponse) |
| PUT | `/api/exams/{id}` | JWT + `lms.exam.update` | Body: [UpdateExamRequest](#updateexamrequest) | [ExamResponse](#examresponse) |
| GET | `/api/exams/{id}/classes` | JWT | — | mảng [ClassResponse](#classresponse) |
| DELETE | `/api/exams/{id}/classes/{classId}` | JWT + `lms.exam.assign` | — | 200 (không có body) |
| POST | `/api/exams/{id}/classes/{classId}` | JWT + `lms.exam.assign` | — | 200 (không có body) |
| GET | `/api/exams/{id}/exercises` | JWT | — | mảng [ExerciseResponse](#exerciseresponse) |

## exam-question-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/exams/question-imports/template.docx` | JWT + `lms.exam-question.create` | Query: `skillCategory`?, `teacherType`?, `defaultKind`? | string |
| GET | `/api/exams/{examId}/questions` | JWT + `lms.exam-question.view` | — | mảng [QuestionResponse](#questionresponse) |
| POST | `/api/exams/{examId}/questions` | JWT + `lms.exam-question.create` | Body: [CreateExamQuestionRequest](#createexamquestionrequest) | [QuestionResponse](#questionresponse) |
| POST | `/api/exams/{examId}/questions/import` | JWT + `lms.exam-question.create` | Form-data: `file` (tệp)<br>Query: `defaultKind`? | [QuestionImportResponse](#questionimportresponse) |
| GET | `/api/exams/{examId}/questions/{questionId}` | JWT + `lms.exam-question.view` | — | [QuestionResponse](#questionresponse) |
| PUT | `/api/exams/{examId}/questions/{questionId}` | JWT + `lms.exam-question.update` | Body: [UpdateQuestionRequest](#updatequestionrequest) | [QuestionResponse](#questionresponse) |
| GET | `/api/exams/{examId}/questions/{questionId}/key-grammar-options` | JWT + `lms.exam-question.view` | — | mảng [KeyGrammarStructureResponse](#keygrammarstructureresponse) |

## exercise-export-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/exercises/{id}/questions/export.xlsx` | JWT + `lms.exercise.update` | — | string |

## exercise-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/exercise-assignments/stats` | JWT + `lms.exercise.report.view` | — | mảng [ExerciseAssignmentStatsResponse](#exerciseassignmentstatsresponse) |
| GET | `/api/exercise-assignments/{assignmentId}/stats/export` | JWT + `lms.exercise.report.export` | — | string |
| GET | `/api/exercise-assignments/{assignmentId}/stats/questions` | JWT + `lms.exercise.report.view` | — | [ExerciseAssignmentQuestionStatsResponse](#exerciseassignmentquestionstatsresponse) |
| GET | `/api/exercise-assignments/{assignmentId}/stats/students` | JWT + `lms.exercise.report.view` | — | [ExerciseAssignmentStudentStatsResponse](#exerciseassignmentstudentstatsresponse) |
| GET | `/api/homework-skill-batches/{batchId}/stats/export` | JWT + `lms.exercise.report.export` | — | string |
| GET | `/api/homework-skill-batches/{batchId}/stats/students` | JWT + `lms.exercise.report.view` | — | [ExerciseAssignmentStudentStatsResponse](#exerciseassignmentstudentstatsresponse) |

## grade-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/grade-component-setups/{setupId}/grades/import` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Form-data: `file` (tệp) | [GradeImportResponse](#gradeimportresponse) |
| GET | `/api/classes/{classId}/grade-component-setups/{setupId}/grades/import-template` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | — | string |
| GET | `/api/grade-imports/{id}` | JWT | — | [GradeImportResponse](#gradeimportresponse) |

## homework-parent-meeting-invite-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/homework-meeting-invites/decision` | JWT | Body: [DecideHomeworkParentMeetingInvitesRequest](#decidehomeworkparentmeetinginvitesrequest) | mảng [HomeworkParentMeetingInviteResponse](#homeworkparentmeetinginviteresponse) |
| GET | `/api/homework-meeting-invites/pending` | JWT | — | mảng [HomeworkParentMeetingInviteResponse](#homeworkparentmeetinginviteresponse) |

## homework-skill-batch-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/homework-skill-groups` | JWT | Query: `skillCategory` | mảng [HomeworkSkillGroupResponse](#homeworkskillgroupresponse) |
| PUT | `/api/homework-skill-batches/{id}/late-submission-allowed` | JWT + `lms.exercise.deadline.confirm` | Body: [UpdateLateSubmissionAllowedRequest](#updatelatesubmissionallowedrequest) | 200 (không có body) |

## leave-substitution-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/leave-substitutions` | JWT | — | mảng [LeaveSubstitutionResponse](#leavesubstitutionresponse) |

## leave-type-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/leave-types` | JWT | — | mảng [LeaveTypeResponse](#leavetyperesponse) |

## listening-practice-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums/{curriculumId}/listening-practice-items` | JWT + `lms.listening-practice.view` | — | mảng [ListeningPracticeItemResponse](#listeningpracticeitemresponse) |
| GET | `/api/listening-practice-attempts/{id}` | JWT | — | [ListeningPracticeAttemptResponse](#listeningpracticeattemptresponse) |
| POST | `/api/listening-practice-attempts/{id}/pause` | JWT | Body: [PauseListeningPracticeAttemptRequest](#pauselisteningpracticeattemptrequest) | [ListeningPracticeAttemptResponse](#listeningpracticeattemptresponse) |
| POST | `/api/listening-practice-attempts/{id}/submit` | JWT | Body: [SubmitListeningPracticeAttemptRequest](#submitlisteningpracticeattemptrequest) | [ListeningPracticeAttemptResponse](#listeningpracticeattemptresponse) |
| POST | `/api/listening-practice-items` | JWT + `lms.listening-practice.create` | Body: [CreateListeningPracticeItemRequest](#createlisteningpracticeitemrequest) | [ListeningPracticeItemResponse](#listeningpracticeitemresponse) |
| PUT | `/api/listening-practice-items/{id}` | JWT + `lms.listening-practice.update` | Body: [UpdateListeningPracticeItemRequest](#updatelisteningpracticeitemrequest) | [ListeningPracticeItemResponse](#listeningpracticeitemresponse) |
| POST | `/api/listening-practice-items/{id}/attempts` | JWT | — | [ListeningPracticeAttemptResponse](#listeningpracticeattemptresponse) |

## listening-practice-grading-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/listening-practice-attempts/{id}/grade` | JWT + `lms.grading.manage` | Body: [GradeListeningAttemptRequest](#gradelisteningattemptrequest) | [ListeningPracticeGradingResponse](#listeningpracticegradingresponse) |
| GET | `/api/listening-practice/grading/pending` | JWT + `lms.grading.manage` | — | mảng [PendingListeningGradingResponse](#pendinglisteninggradingresponse) |

## media-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/media/upload` | JWT | Form-data: `file` (tệp)<br>Query: `module` | [MediaUploadResponse](#mediauploadresponse) |

## parent-batch-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/parent-imports` | JWT + `student.parent.import` | Form-data: `file` (tệp) | [ParentBatchImportResponse](#parentbatchimportresponse) |
| POST | `/api/parent-imports/accounts-export` | JWT + `student.parent.import` | Body: [AccountExportRequest](#accountexportrequest) | string |
| GET | `/api/parent-imports/template` | JWT + `student.parent.import` | — | string |
| GET | `/api/parent-imports/{id}` | JWT + `student.parent.import` | — | [ParentBatchImportResponse](#parentbatchimportresponse) |

## parent-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/parents` | JWT + `student.parent.view` | Query: `query`? | mảng [ParentResponse](#parentresponse) |
| POST | `/api/parents` | JWT + `student.parent.create` | Body: [CreateParentRequest](#createparentrequest) | [ParentResponse](#parentresponse) |
| GET | `/api/parents/me` | JWT | — | [ParentResponse](#parentresponse) |
| PUT | `/api/parents/me` | JWT | Body: [UpdateOwnParentProfileRequest](#updateownparentprofilerequest) | [ParentResponse](#parentresponse) |
| GET | `/api/parents/{id}` | JWT + `student.parent.view` | — | [ParentResponse](#parentresponse) |
| PUT | `/api/parents/{id}` | JWT + `student.parent.update` | Body: [UpdateParentRequest](#updateparentrequest) | [ParentResponse](#parentresponse) |

## payment-link-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/invoices/{id}/payment-link` | JWT | — | [PaymentLinkResponse](#paymentlinkresponse) |
| POST | `/api/webhooks/payment/{provider}` | Header `X-Webhook-Secret` | Body: [JsonNode](#jsonnode) | object |

## position-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/positions` | JWT | — | mảng [PositionResponse](#positionresponse) |
| POST | `/api/positions` | JWT + `hrm.position.create` | Body: [CreatePositionRequest](#createpositionrequest) | [PositionResponse](#positionresponse) |
| DELETE | `/api/positions/{id}` | JWT + `hrm.position.delete` | — | 200 (không có body) |
| GET | `/api/positions/{id}` | JWT | — | [PositionResponse](#positionresponse) |
| PUT | `/api/positions/{id}` | JWT + `hrm.position.update` | Body: [UpdatePositionRequest](#updatepositionrequest) | [PositionResponse](#positionresponse) |
| GET | `/api/positions/{id}/default-roles` | JWT + `hrm.position.view` | — | [PositionDefaultRolesResponse](#positiondefaultrolesresponse) |
| PUT | `/api/positions/{id}/default-roles` | JWT + `hrm.position.update` | Body: [UpdatePositionDefaultRolesRequest](#updatepositiondefaultrolesrequest) | 200 (không có body) |

## question-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/question-banks/{bankId}/questions/import` | JWT + `lms.question-bank.create` | Form-data: `file` (tệp)<br>Query: `defaultKind`? | [QuestionImportResponse](#questionimportresponse) |
| GET | `/api/question-imports/template.docx` | JWT + `lms.question-bank.create` | Query: `defaultKind`? | string |
| GET | `/api/question-imports/{id}` | JWT + `lms.question-bank.create` | — | [QuestionImportResponse](#questionimportresponse) |

## reflex-question-format-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/reflex-picture/brief` | JWT + `lms.review-video.update` | Body: [DraftReflexPictureBriefRequest](#draftreflexpicturebriefrequest) | [ReflexPictureBriefResponse](#reflexpicturebriefresponse) |
| POST | `/api/reflex-picture/capture` | JWT + `lms.review-video.update` | Body: [CaptureReflexPictureRequest](#capturereflexpicturerequest) | [ReflexPictureCaptureResponse](#reflexpicturecaptureresponse) |
| GET | `/api/reflex-question-formats` | JWT + `lms.review-video.update` | Query: `curriculumId` | mảng [ReflexQuestionFormatOptionResponse](#reflexquestionformatoptionresponse) |

## reflex-sequential-grading-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/reflex-recording-config` | JWT | — | [ReflexRecordingConfigResponse](#reflexrecordingconfigresponse) |
| GET | `/api/review-video-assignments/{assignmentId}/reflex-progress` | JWT | — | mảng [ReflexQuestionProgressResponse](#reflexquestionprogressresponse) |
| PUT | `/api/review-video-questions/{questionId}/reflex-progress/speaking` | JWT | Body: [SubmitReflexSpokenAnswerRequest](#submitreflexspokenanswerrequest)<br>Query: `assignmentId` | [ReflexQuestionProgressResponse](#reflexquestionprogressresponse) |
| PUT | `/api/review-video-questions/{questionId}/reflex-progress/writing` | JWT | Body: [SubmitReflexWrittenAnswerRequest](#submitreflexwrittenanswerrequest)<br>Query: `assignmentId` | [ReflexQuestionProgressResponse](#reflexquestionprogressresponse) |

## report-generation-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/reports/download/{id}` | JWT + `report.generate` | — | string |
| POST | `/api/reports/generate` | JWT + `report.generate` | Body: [GenerateReportRequest](#generatereportrequest) | [GeneratedReportResponse](#generatedreportresponse) |

## report-template-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/report-templates` | JWT + `report.template.view` | Query: `templateType`? | mảng [ReportTemplateResponse](#reporttemplateresponse) |
| POST | `/api/report-templates` | JWT + `report.template.create` | Form-data: `file` (tệp)<br>Query: `name`, `templateType`, `description`? | [ReportTemplateResponse](#reporttemplateresponse) |
| GET | `/api/report-templates/available-fields` | JWT + `report.template.view` | — | object |
| DELETE | `/api/report-templates/{id}` | JWT + `report.template.delete` | — | 200 (không có body) |
| GET | `/api/report-templates/{id}` | JWT + `report.template.view` | — | [ReportTemplateResponse](#reporttemplateresponse) |
| PUT | `/api/report-templates/{id}` | JWT + `report.template.update` | Body: [UpdateReportTemplateRequest](#updatereporttemplaterequest) | [ReportTemplateResponse](#reporttemplateresponse) |
| PUT | `/api/report-templates/{id}/field-mappings` | JWT + `report.template.update` | Body: [UpdateFieldMappingsRequest](#updatefieldmappingsrequest) | [ReportTemplateResponse](#reporttemplateresponse) |

## review-video-catalog-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/review-video-sets/imports` | JWT + `lms.review-video.create hoặc lms.review-video.update` | Form-data: `file` (tệp) | [ReviewVideoCatalogImportResponse](#reviewvideocatalogimportresponse) |
| GET | `/api/review-video-sets/imports/{id}` | JWT + `lms.review-video.create hoặc lms.review-video.update` | — | [ReviewVideoCatalogImportResponse](#reviewvideocatalogimportresponse) |

## review-video-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/review-video-assignments` | JWT | — | mảng [ReviewVideoAssignmentResponse](#reviewvideoassignmentresponse) |
| GET | `/api/classes/{classId}/review-video-assignments/stats` | JWT + `lms.review-video.view` | — | mảng [ReviewVideoAssignmentStatsResponse](#reviewvideoassignmentstatsresponse) |
| GET | `/api/classes/{classId}/review-video-sets` | JWT | — | mảng [ReviewVideoSetResponse](#reviewvideosetresponse) |
| GET | `/api/classes/{classId}/review-video-submissions` | JWT + `lms.grading.manage` | — | mảng [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| PUT | `/api/review-video-assignments/{id}/late-submission-allowed` | JWT + `lms.exercise.deadline.confirm` | Body: [UpdateLateSubmissionAllowedRequest](#updatelatesubmissionallowedrequest) | [ReviewVideoAssignmentResponse](#reviewvideoassignmentresponse) |
| DELETE | `/api/review-video-connection-questions/{questionId}` | JWT + `lms.review-video.update` | — | 200 (không có body) |
| PUT | `/api/review-video-connection-questions/{questionId}` | JWT + `lms.review-video.update` | Body: [UpdateReviewVideoConnectionQuestionRequest](#updatereviewvideoconnectionquestionrequest) | [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| DELETE | `/api/review-video-questions/{questionId}` | JWT + `lms.review-video.update` | — | 200 (không có body) |
| PUT | `/api/review-video-questions/{questionId}` | JWT + `lms.review-video.update` | Body: [UpdateReviewVideoQuestionRequest](#updatereviewvideoquestionrequest) | [ReviewVideoQuestionResponse](#reviewvideoquestionresponse) |
| PUT | `/api/review-video-questions/{questionId}/submissions` | JWT | Body: [SubmitReviewVideoAudioRequest](#submitreviewvideoaudiorequest)<br>Query: `assignmentId` | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-questions/{questionId}/submissions/history` | JWT | Query: `assignmentId` | mảng [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-questions/{questionId}/submissions/latest` | JWT | Query: `assignmentId` | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-sets` | JWT | Query: `curriculumId`?, `teacherType`? | mảng [ReviewVideoSetResponse](#reviewvideosetresponse) |
| POST | `/api/review-video-sets` | JWT + `lms.review-video.create` | Body: [CreateReviewVideoSetRequest](#createreviewvideosetrequest) | [ReviewVideoSetResponse](#reviewvideosetresponse) |
| DELETE | `/api/review-video-sets/{id}` | JWT + `lms.review-video.delete` | — | 200 (không có body) |
| PUT | `/api/review-video-sets/{id}` | JWT + `lms.review-video.update` | Body: [UpdateReviewVideoSetRequest](#updatereviewvideosetrequest) | [ReviewVideoSetResponse](#reviewvideosetresponse) |
| GET | `/api/review-video-sets/{id}/classes` | JWT | — | mảng [ClassResponse](#classresponse) |
| DELETE | `/api/review-video-sets/{id}/classes/{classId}` | JWT + `lms.review-video.assign` | — | 200 (không có body) |
| POST | `/api/review-video-sets/{id}/classes/{classId}` | JWT + `lms.review-video.assign` | — | 200 (không có body) |
| GET | `/api/review-video-sets/{setId}/stats` | JWT + `lms.review-video.view` | Query: `classId`? | [ReviewVideoSetStatsResponse](#reviewvideosetstatsresponse) |
| GET | `/api/review-video-sets/{setId}/submissions` | JWT + `lms.grading.manage` | Query: `classId`? | mảng [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-sets/{setId}/videos` | JWT | — | mảng [ReviewVideoResponse](#reviewvideoresponse) |
| POST | `/api/review-video-sets/{setId}/videos` | JWT + `lms.review-video.update` | Body: [AddReviewVideoRequest](#addreviewvideorequest) | [ReviewVideoResponse](#reviewvideoresponse) |
| GET | `/api/review-video-submissions/pending-grading` | JWT + `lms.grading.manage` | — | mảng [PendingGradingClassSummaryResponse](#pendinggradingclasssummaryresponse) |
| POST | `/api/review-video-submissions/{submissionId}/grade` | JWT + `lms.grading.manage` | Body: [GradeReviewVideoSubmissionRequest](#gradereviewvideosubmissionrequest) | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| PUT | `/api/review-video-watch-sessions/{watchSessionId}/connection-answers` | JWT | Body: [SubmitConnectionAnswersRequest](#submitconnectionanswersrequest) | [ReviewVideoConnectionQuizResultResponse](#reviewvideoconnectionquizresultresponse) |
| GET | `/api/review-video-watch-sessions/{watchSessionId}/connection-questions` | JWT | — | mảng [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| DELETE | `/api/review-videos/{videoId}` | JWT + `lms.review-video.update` | — | 200 (không có body) |
| GET | `/api/review-videos/{videoId}/connection-answer-history` | JWT | Query: `assignmentId` | [ReviewVideoConnectionAnswerHistoryResponse](#reviewvideoconnectionanswerhistoryresponse) |
| GET | `/api/review-videos/{videoId}/connection-questions` | JWT | — | mảng [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| POST | `/api/review-videos/{videoId}/connection-questions` | JWT + `lms.review-video.update` | Body: [AddReviewVideoConnectionQuestionRequest](#addreviewvideoconnectionquestionrequest) | [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| POST | `/api/review-videos/{videoId}/connection-questions/import` | JWT + `lms.review-video.update` | Form-data: `file` (tệp) | [ReviewVideoQuestionImportResponse](#reviewvideoquestionimportresponse) |
| GET | `/api/review-videos/{videoId}/progress` | JWT | Query: `assignmentId` | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |
| PUT | `/api/review-videos/{videoId}/progress` | JWT | Body: [ReportVideoProgressRequest](#reportvideoprogressrequest) | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |
| GET | `/api/review-videos/{videoId}/questions` | JWT | — | mảng [ReviewVideoQuestionResponse](#reviewvideoquestionresponse) |
| POST | `/api/review-videos/{videoId}/questions` | JWT + `lms.review-video.update` | Body: [AddReviewVideoQuestionRequest](#addreviewvideoquestionrequest) | [ReviewVideoQuestionResponse](#reviewvideoquestionresponse) |
| POST | `/api/review-videos/{videoId}/questions/import` | JWT + `lms.review-video.update` | Form-data: `file` (tệp) | [ReviewVideoQuestionImportResponse](#reviewvideoquestionimportresponse) |
| POST | `/api/review-videos/{videoId}/stop-early` | JWT | Query: `assignmentId` | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |
| PUT | `/api/review-videos/{videoId}/thresholds` | JWT + `lms.review-video.update` | Body: [UpdateReviewVideoThresholdsRequest](#updatereviewvideothresholdsrequest) | [ReviewVideoResponse](#reviewvideoresponse) |
| POST | `/api/review-videos/{videoId}/watch-sessions` | JWT | Query: `assignmentId` | [StartWatchSessionResponse](#startwatchsessionresponse) |

## review-video-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/review-video-assignments/{assignmentId}/export-reflex-data` | JWT + `lms.review-video.view` | — | string |
| GET | `/api/review-video-assignments/{assignmentId}/stats/questions` | JWT + `lms.review-video.view` | — | [ReviewVideoAssignmentQuestionStatsResponse](#reviewvideoassignmentquestionstatsresponse) |
| GET | `/api/review-video-assignments/{assignmentId}/stats/students` | JWT + `lms.review-video.view` | — | [ReviewVideoAssignmentStudentStatsResponse](#reviewvideoassignmentstudentstatsresponse) |
| GET | `/api/review-video-assignments/{assignmentId}/stats/students/{studentId}/reflex-history` | JWT + `lms.review-video.view` | — | mảng [ReflexQuestionProgressHistoryResponse](#reflexquestionprogresshistoryresponse) |

## session-report-tracking-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/reports/session-reports` | JWT + `report.session-report.view` | Query: `siteId`?, `fromDate`, `toDate` | [SessionReportTrackingResponse](#sessionreporttrackingresponse) |
| GET | `/api/reports/session-reports/export` | JWT + `report.session-report.view` | Query: `siteId`?, `fromDate`, `toDate` | string |
| GET | `/api/reports/session-reports/sessions/{sessionId}/timeline` | JWT + `report.session-report.view` | — | mảng [SessionReportTimelineEvent](#sessionreporttimelineevent) |

## shift-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/employee-shifts` | JWT + `hrm.employee-shift.assign` | Body: [AssignEmployeeShiftRequest](#assignemployeeshiftrequest) | [EmployeeShiftResponse](#employeeshiftresponse) |
| PUT | `/api/employee-shifts/{id}/end` | JWT + `hrm.employee-shift.assign` | Body: [EndEmployeeShiftRequest](#endemployeeshiftrequest) | [EmployeeShiftResponse](#employeeshiftresponse) |
| GET | `/api/employees/{id}/shifts` | JWT + `hrm.employee-shift.assign hoặc hrm.employee-schedule.view` | — | mảng [EmployeeShiftResponse](#employeeshiftresponse) |
| GET | `/api/shifts` | JWT | — | mảng [ShiftResponse](#shiftresponse) |
| POST | `/api/shifts` | JWT + `hrm.shift.create` | Body: [CreateShiftRequest](#createshiftrequest) | [ShiftResponse](#shiftresponse) |
| PUT | `/api/shifts/{id}` | JWT + `hrm.shift.update` | Body: [UpdateShiftRequest](#updateshiftrequest) | [ShiftResponse](#shiftresponse) |
| PUT | `/api/shifts/{id}/deactivate` | JWT + `hrm.shift.update` | — | [ShiftResponse](#shiftresponse) |

## site-period-template-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/sites/{siteId}/period-templates` | JWT | — | mảng [SitePeriodTemplateResponse](#siteperiodtemplateresponse) |
| POST | `/api/sites/{siteId}/period-templates` | JWT + `facility.site.update` | Body: [CreateSitePeriodTemplateRequest](#createsiteperiodtemplaterequest) | [SitePeriodTemplateResponse](#siteperiodtemplateresponse) |
| DELETE | `/api/sites/{siteId}/period-templates/{id}` | JWT + `facility.site.update` | — | 200 (không có body) |
| PUT | `/api/sites/{siteId}/period-templates/{id}` | JWT + `facility.site.update` | Body: [UpdateSitePeriodTemplateRequest](#updatesiteperiodtemplaterequest) | [SitePeriodTemplateResponse](#siteperiodtemplateresponse) |

## skill-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/skills` | JWT | Query: `includeInactive`? | mảng [SkillResponse](#skillresponse) |
| POST | `/api/skills` | JWT + `academic.skill.create` | Body: [CreateSkillRequest](#createskillrequest) | [SkillResponse](#skillresponse) |
| PUT | `/api/skills/{id}` | JWT + `academic.skill.update` | Body: [UpdateSkillRequest](#updateskillrequest) | [SkillResponse](#skillresponse) |

## speaking-ai-grading-test-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/dev-tools/speaking-grading-test` | JWT + `system.settings.manage` | Form-data: `audio` (tệp)<br>Query: `writingText`?, `provider`? | [SpeakingAiGradingTestResponse](#speakingaigradingtestresponse) |

## student-attendance-settings-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic/settings/student-attendance-grace-period-minutes` | JWT | — | [StudentAttendanceGracePeriodResponse](#studentattendancegraceperiodresponse) |

## student-attitude-escalation-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/student-attitude-escalations/decision` | JWT | Body: [DecideStudentAttitudeEscalationsRequest](#decidestudentattitudeescalationsrequest) | mảng [StudentAttitudeEscalationResponse](#studentattitudeescalationresponse) |
| GET | `/api/student-attitude-escalations/pending` | JWT | — | mảng [StudentAttitudeEscalationResponse](#studentattitudeescalationresponse) |

## student-portal-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/students/me` | JWT | — | [StudentResponse](#studentresponse) |
| PUT | `/api/students/me` | JWT | Body: [UpdateOwnStudentProfileRequest](#updateownstudentprofilerequest) | [StudentResponse](#studentresponse) |
| GET | `/api/students/me/classes/{classId}/academic-terms/{academicTermId}/evaluation/{evaluationType}/result` | JWT | — | [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| GET | `/api/students/me/classes/{classId}/attendance` | JWT | — | mảng [AttendanceMarkResponse](#attendancemarkresponse) |
| GET | `/api/students/me/classes/{classId}/comments` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/students/me/documents` | JWT | Query: `curriculumId`? | mảng [CurriculumDocumentResponse](#curriculumdocumentresponse) |
| GET | `/api/students/me/exercises` | JWT | Query: `classId`? | mảng [AssignedExerciseResponse](#assignedexerciseresponse) |
| GET | `/api/students/me/grades` | JWT | Query: `classId`? | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/students/me/listening-practice` | JWT | Query: `mode`?, `curriculumId`? | mảng [ListeningPracticeItemResponse](#listeningpracticeitemresponse) |
| GET | `/api/students/me/parents` | JWT | — | mảng [ParentStudentResponse](#parentstudentresponse) |
| GET | `/api/students/me/review-video-assignments` | JWT | Query: `classId`? | mảng [MyReviewVideoAssignmentResponse](#myreviewvideoassignmentresponse) |
| GET | `/api/students/me/sessions` | JWT | Query: `fromDate`?, `toDate`?, `classId`? | mảng [ClassSessionResponse](#classsessionresponse) |

## sub-topic-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| DELETE | `/api/sub-topics/{id}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| PUT | `/api/sub-topics/{id}` | JWT + `lms.exercise.update` | Body: [UpdateSubTopicRequest](#updatesubtopicrequest) | [SubTopicResponse](#subtopicresponse) |

## system-setting-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/system-settings` | JWT + `system.settings.manage` | — | mảng [SystemSettingResponse](#systemsettingresponse) |
| PUT | `/api/system-settings/{settingKey}` | JWT + `system.settings.manage` | Body: [SystemSettingUpdateRequest](#systemsettingupdaterequest) | [SystemSettingResponse](#systemsettingresponse) |
| GET | `/api/system-settings/{settingKey}/history` | JWT + `system.settings.manage` | — | mảng [SystemSettingHistoryResponse](#systemsettinghistoryresponse) |

## task-settings-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/task/settings/cancelled-retention-days` | JWT | — | [TaskCancelledRetentionResponse](#taskcancelledretentionresponse) |
| PUT | `/api/task/settings/cancelled-retention-days` | JWT + `task.manage` | Body: [UpdateTaskCancelledRetentionRequest](#updatetaskcancelledretentionrequest) | [TaskCancelledRetentionResponse](#taskcancelledretentionresponse) |

## teacher-profile-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/teacher-profiles` | JWT + `hrm.teacher.view` | Query: `query`?, `siteId`? | mảng [TeacherProfileSummaryResponse](#teacherprofilesummaryresponse) |
| GET | `/api/teacher-profiles/{employeeId}` | JWT + `hrm.teacher.view` | — | [TeacherProfileDetailResponse](#teacherprofiledetailresponse) |

## teacher-schedule-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/teachers/me/sessions` | JWT | Query: `fromDate`?, `toDate`? | mảng [ClassSessionResponse](#classsessionresponse) |

## teaching-stats-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/reports/teaching-stats` | JWT + `report.teacher-stats.view` | Query: `siteId`?, `fromDate`, `toDate` | [TeachingStatsResponse](#teachingstatsresponse) |
| GET | `/api/reports/teaching-stats/export` | JWT + `report.teacher-stats.view` | Query: `siteId`?, `fromDate`, `toDate` | string |

## term-comment-ai-draft-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Form-data: `audio` (tệp)<br>Query: `instruction`?, `studentIds`? | [TermCommentAiDraftJobResponse](#termcommentaidraftjobresponse) |
| POST | `/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft/apply` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Body: [ApplyTermCommentAiDraftRequest](#applytermcommentaidraftrequest) | mảng [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| POST | `/api/classes/{classId}/grade-component-setups/{setupId}/comments/ai-draft/revise` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | Form-data: `audio` (tệp), `request`<br>Query: `instruction`? | [TermCommentAiDraftJobResponse](#termcommentaidraftjobresponse) |
| GET | `/api/term-comment-ai-drafts/{jobId}` | JWT + `academic.grade.entry hoặc academic.grade.edit.override` | — | [TermCommentAiDraftJobResponse](#termcommentaidraftjobresponse) |

## unit-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| DELETE | `/api/units/{id}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| PUT | `/api/units/{id}` | JWT + `lms.exercise.update` | Body: [UpdateUnitRequest](#updateunitrequest) | [UnitResponse](#unitresponse) |
| GET | `/api/units/{unitId}/sub-topics` | JWT | — | mảng [SubTopicResponse](#subtopicresponse) |
| POST | `/api/units/{unitId}/sub-topics` | JWT + `lms.exercise.create` | Body: [CreateSubTopicRequest](#createsubtopicrequest) | [SubTopicResponse](#subtopicresponse) |

## work-calendar-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/work-calendar` | JWT | Query: `from`, `to` | mảng [WorkCalendarResponse](#workcalendarresponse) |
| POST | `/api/work-calendar` | JWT + `hrm.work-calendar.create` | Body: [CreateWorkCalendarRequest](#createworkcalendarrequest) | [WorkCalendarResponse](#workcalendarresponse) |
| DELETE | `/api/work-calendar/{id}` | JWT + `hrm.work-calendar.delete` | — | 200 (không có body) |

---

## Phụ lục: Schemas

### AcademicDashboardResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `activeStudents` | integer (int64) |  |
| `activeTeachers` | integer (int64) |  |
| `attendanceAbsentCount` | integer (int64) |  |
| `attendanceEarlyLeaveCount` | integer (int64) |  |
| `attendanceExcusedCount` | integer (int64) |  |
| `attendanceFromDate` | string (date) |  |
| `attendanceLateCount` | integer (int64) |  |
| `attendancePresentCount` | integer (int64) |  |
| `attendanceRate` | number |  |
| `attendanceToDate` | string (date) |  |
| `attendanceTotalMarks` | integer (int64) |  |
| `inProgressClasses` | integer (int64) |  |
| `openEnrollmentClasses` | integer (int64) |  |
| `plannedClasses` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `teacherAlerts` | mảng [TeacherTeachingStatsRow](#teacherteachingstatsrow) |  |
| `today` | string (date) |  |
| `todaySessions` | mảng [AcademicDashboardSessionItem](#academicdashboardsessionitem) |  |

### AcademicDashboardSessionItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInState` | string |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `endTime` | string |  |
| `sessionId` | integer (int64) |  |
| `siteName` | string |  |
| `startTime` | string |  |
| `teacherName` | string |  |

### AcademicTermResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearCode` | string |  |
| `academicYearId` | integer (int64) |  |
| `academicYearName` | string |  |
| `code` | string |  |
| `endDate` | string (date) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |

### AcademicYearResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `endDate` | string (date) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `startDate` | string (date) |  |
| `status` | string |  |

### AccountEntry

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `fullName` | string |  |
| `temporaryPassword` | string | ✔ |
| `username` | string | ✔ |

### AccountExportRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `accounts` | mảng [AccountEntry](#accountentry) | ✔ |

### ActualPeriodsClassRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualPeriods` | integer (int64) |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |

### ActualPeriodsGridColumn

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endDate` | string (date) |  |
| `key` | string |  |
| `label` | string |  |
| `startDate` | string (date) |  |

### ActualPeriodsGridResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `columns` | mảng [ActualPeriodsGridColumn](#actualperiodsgridcolumn) |  |
| `periodType` | string |  |
| `rows` | mảng [ActualPeriodsGridRow](#actualperiodsgridrow) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### ActualPeriodsGridRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualPeriodsByColumnKey` | object |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |

### ActualPeriodsStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classes` | mảng [ActualPeriodsClassRow](#actualperiodsclassrow) |  |
| `endDate` | string (date) |  |
| `periodLabel` | string |  |
| `periodType` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |
| `totalActualPeriods` | integer (int64) |  |

### AddDepartmentMembersRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `employeeIds` | mảng integer (int64) | ✔ |

### AddExerciseQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `points` | number | ✔ |
| `questionId` | integer (int64) | ✔ |

### AddFeedbackExchangeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `note` | string | ✔ |

### AddReviewVideoConnectionQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choices` | mảng [ConnectionChoiceRequest](#connectionchoicerequest) | ✔ |
| `displayOrder` | integer |  |
| `prompt` | string | ✔ |

### AddReviewVideoQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `maxAttempts` | integer |  |
| `maxRecordingSeconds` | integer | ✔ |
| `pictureBrief` | string |  |
| `pictureImageUrl` | string |  |
| `prompt` | string |  |
| `questionFormat` | string |  |
| `timestampSeconds` | integer | ✔ |

### AddReviewVideoRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completionThresholdPercent` | integer |  |
| `displayOrder` | integer |  |
| `durationSeconds` | integer | ✔ |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string | ✔ |
| `requiredViewCount` | integer |  |
| `sessionPassRatioThresholdPercent` | integer |  |
| `sourceType` | string | ✔ |
| `title` | string | ✔ |

### AddTaskAttachmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `fileName` | string | ✔ |
| `fileUrl` | string | ✔ |

### AddTaskCommentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attachmentUrl` | string |  |
| `content` | string | ✔ |

### AddTeachingPlanItemRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `contentOutline` | string |  |
| `homeworkNote` | string |  |
| `itemOrder` | integer |  |
| `objectives` | string |  |
| `plannedDate` | string (date) |  |
| `skillsFocus` | string |  |
| `topic` | string | ✔ |

### AdminChangePasswordRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `newPassword` | string | ✔ |

### AiTokenUsageSummaryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `byAssignment` | mảng [ByAssignment](#byassignment) |  |
| `byStep` | mảng [ByStep](#bystep) |  |
| `byStudent` | mảng [ByStudent](#bystudent) |  |
| `totals` | [Totals](#totals) |  |

### AnsweredQuestion

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choices` | mảng [ChoiceOption](#choiceoption) |  |
| `correct` | boolean |  |
| `correctChoiceId` | integer (int64) |  |
| `prompt` | string |  |
| `questionId` | integer (int64) |  |
| `selectedChoiceId` | integer (int64) |  |

### ApplyClassHomeworkRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dueDate` | string (date-time) |  |
| `grammarExamId` | integer (int64) |  |
| `grammarExerciseIds` | mảng integer (int64) |  |
| `lateSubmissionAllowed` | boolean |  |
| `readingExamId` | integer (int64) |  |
| `readingExerciseIds` | mảng integer (int64) |  |
| `videoSetId` | integer (int64) |  |
| `writingExamId` | integer (int64) |  |
| `writingExerciseIds` | mảng integer (int64) |  |

### ApplyTermCommentAiDraftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `rows` | mảng [Row](#row) | ✔ |

### AssignEmployeeShiftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveFrom` | string (date) | ✔ |
| `employeeId` | integer (int64) | ✔ |
| `shiftId` | integer (int64) | ✔ |

### AssignLeadRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignToUserId` | integer (int64) | ✔ |

### AssignSiteManagerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `managerUserId` | integer (int64) | ✔ |

### AssignSiteTeacherRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedFrom` | string (date) | ✔ |
| `notes` | string |  |
| `teacherUserId` | integer (int64) | ✔ |

### AssignTeacherRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedFrom` | string (date) |  |
| `subjectId` | integer (int64) |  |
| `teacherRole` | string |  |
| `teacherType` | string |  |
| `teacherUserId` | integer (int64) | ✔ |

### AssignTuitionPlanRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) | ✔ |
| `effectiveFrom` | string (date) |  |
| `overrideReason` | string |  |
| `priceOverride` | number |  |
| `tuitionPlanId` | integer (int64) | ✔ |

### AssignedExerciseResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `canStartNewAttempt` | boolean |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `dueAt` | string (date-time) |  |
| `examId` | integer (int64) |  |
| `examTitle` | string |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTotalPoints` | number |  |
| `exerciseType` | string |  |
| `homeworkBatchId` | integer (int64) |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `myLatestAttemptId` | integer (int64) |  |
| `myLatestAttemptStatus` | string |  |
| `myLatestPassed` | boolean |  |
| `myLatestPercentage` | number |  |
| `myLatestTotalScore` | number |  |
| `sessionDate` | string (date) |  |
| `skillCategory` | string |  |
| `subTopicTitle` | string |  |
| `teacherType` | string |  |
| `title` | string |  |
| `unitTitle` | string |  |

### AttendanceCheckRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `biometricVerified` | boolean |  |
| `latitude` | number |  |
| `longitude` | number |  |
| `method` | string | ✔ |
| `siteId` | integer (int64) |  |

### AttendanceMarkHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `changedByName` | string |  |
| `changedByUserId` | integer (int64) |  |
| `createdAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### AttendanceMarkResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceReason` | string |  |
| `attendanceSessionId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `minutesEarlyLeave` | integer |  |
| `minutesLate` | integer |  |
| `notifiedParentAt` | string (date-time) |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### AttendanceRecordAdminResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInAt` | string (date-time) |  |
| `checkInMethod` | string |  |
| `checkOutAt` | string (date-time) |  |
| `checkOutMethod` | string |  |
| `employeeCode` | string |  |
| `employeeFullName` | string |  |
| `employeeId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `status` | string |  |
| `workDate` | string (date) |  |

### AttendanceRecordResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInAt` | string (date-time) |  |
| `checkInMethod` | string |  |
| `checkOutAt` | string (date-time) |  |
| `checkOutMethod` | string |  |
| `employeeId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `workDate` | string (date) |  |

### AttendanceSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `markedAt` | string (date-time) |  |
| `markedBy` | integer (int64) |  |
| `marks` | mảng [AttendanceMarkResponse](#attendancemarkresponse) |  |
| `mode` | string |  |
| `status` | string |  |
| `submittedAt` | string (date-time) |  |

### AutoProgressPreviewResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `grammarPreviousProgress` | string |  |
| `readingPreviousProgress` | string |  |
| `studentId` | integer (int64) |  |
| `videoPreviousProgress` | string |  |
| `writingPreviousProgress` | string |  |

### AvailableReportFieldResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `description` | string |  |
| `fieldType` | string |  |
| `key` | string |  |
| `label` | string |  |

### BankWebhookPaymentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number | ✔ |
| `bankTransactionId` | string | ✔ |
| `invoiceNumber` | string | ✔ |
| `paidAt` | string (date-time) |  |

### BookCatalogImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### BookResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `curriculumId` | integer (int64) |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `title` | string |  |

### BulkCreateClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `allowRoomOverlap` | boolean |  |
| `allowTeacherOverlap` | boolean |  |
| `assistantTeacherId` | integer (int64) |  |
| `cmTeacherId` | integer (int64) |  |
| `dayPart` | string | ✔ |
| `daysOfWeek` | mảng string | ✔ |
| `endDate` | string (date) | ✔ |
| `periodNumbers` | mảng integer | ✔ |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `sessionType` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `teacherType` | string | ✔ |

### BulkCreateClassSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `created` | mảng [ClassSessionResponse](#classsessionresponse) |  |
| `createdCount` | integer |  |
| `skipped` | mảng object |  |
| `skippedCount` | integer |  |
| `totalDates` | integer |  |

### ByAssignment

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `assignmentName` | string |  |
| `cachedTokens` | integer (int64) |  |
| `callCount` | integer (int64) |  |
| `completionTokens` | integer (int64) |  |
| `promptTokens` | integer (int64) |  |
| `reasoningTokens` | integer (int64) |  |

### ByStep

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `avgElapsedMs` | integer (int64) |  |
| `cachedTokens` | integer (int64) |  |
| `callCount` | integer (int64) |  |
| `completionTokens` | integer (int64) |  |
| `promptTokens` | integer (int64) |  |
| `reasoningTokens` | integer (int64) |  |
| `step` | string |  |

### ByStudent

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `cachedTokens` | integer (int64) |  |
| `callCount` | integer (int64) |  |
| `completionTokens` | integer (int64) |  |
| `promptTokens` | integer (int64) |  |
| `reasoningTokens` | integer (int64) |  |
| `studentId` | integer (int64) |  |
| `studentName` | string |  |

### CancelClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |

### CancelInvoiceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string | ✔ |

### CancelTaskRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |

### CaptureReflexPictureRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `timestampSeconds` | integer | ✔ |
| `videoUrl` | string | ✔ |

### ChainFinancialReportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bySite` | mảng [FinancialReportResponse](#financialreportresponse) |  |
| `periodFrom` | string (date) |  |
| `periodTo` | string (date) |  |
| `totalExpense` | number |  |
| `totalOutstanding` | number |  |
| `totalRevenue` | number |  |

### Change

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentId` | integer (int64) |  |
| `originalContent` | string |  |
| `studentFullName` | string |  |
| `suggestedContent` | string |  |
| `warnings` | mảng string |  |

### ChangeHistoryItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `changedAt` | string (date-time) |  |
| `changedById` | integer (int64) |  |
| `changedByName` | string |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `details` | object |  |
| `entityId` | integer (int64) |  |
| `entityType` | string |  |
| `id` | string |  |
| `previousDetails` | object |  |
| `sessionDate` | string (date) |  |
| `studentId` | integer (int64) |  |
| `subjectCode` | string |  |
| `subjectName` | string |  |
| `valueLabels` | object |  |

### ChangeOwnPasswordRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `currentPassword` | string |  |
| `newPassword` | string | ✔ |

### ChangeTeacherRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveDate` | string (date) | ✔ |
| `newTeacherUserId` | integer (int64) | ✔ |

### ChannelResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `channel` | string |  |
| `errorMessage` | string |  |
| `recipientUserId` | integer (int64) |  |
| `status` | string |  |

### ChatTurn

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `role` | string |  |
| `text` | string |  |

### ChildResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### ChoiceOption

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string |  |
| `content` | string |  |
| `id` | integer (int64) |  |

### ClassEnrollmentBatchImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### ClassEnrollmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `academicYearId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `enrolledDate` | string (date) |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentDateOfBirth` | string (date) |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `withdrawReason` | string |  |
| `withdrawnDate` | string (date) |  |

### ClassResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `academicYearId` | integer (int64) |  |
| `classCategory` | string |  |
| `classCode` | string |  |
| `classType` | string |  |
| `color` | string |  |
| `curriculumCode` | string |  |
| `curriculumId` | integer (int64) |  |
| `endDate` | string (date) |  |
| `id` | integer (int64) |  |
| `maxStudents` | integer |  |
| `minStudents` | integer |  |
| `name` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |
| `status` | string |  |

### ClassScheduleImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### ClassSessionCheckInAdminResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInTime` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `classSessionId` | integer (int64) |  |
| `effectiveStatus` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `sessionDate` | string (date) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startTime` | [LocalTime](#localtime) |  |
| `teacherCode` | string |  |
| `teacherFullName` | string |  |
| `teacherId` | integer (int64) |  |

### ClassSessionCheckInRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `latitude` | number | ✔ |
| `longitude` | number | ✔ |

### ClassSessionCheckInResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInTime` | string (date-time) |  |
| `classSessionId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `teacherId` | integer (int64) |  |
| `teacherName` | string |  |

### ClassSessionCheckInStatusResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `checkInTime` | string (date-time) |  |
| `classSessionId` | integer (int64) |  |
| `effectiveStatus` | string |  |

### ClassSessionLessonContentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `lessonContent` | string |  |

### ClassSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `assistantTeacherId` | integer (int64) |  |
| `assistantTeacherName` | string |  |
| `cancellationReason` | string |  |
| `classColor` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `cmTeacherId` | integer (int64) |  |
| `cmTeacherName` | string |  |
| `dayPart` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `lessonContent` | string |  |
| `makeupForSessionId` | integer (int64) |  |
| `originalTeacherName` | string |  |
| `periodNumbers` | mảng integer |  |
| `primaryTeacherId` | integer (int64) |  |
| `primaryTeacherName` | string |  |
| `rescheduledToSessionId` | integer (int64) |  |
| `roomId` | integer (int64) |  |
| `roomName` | string |  |
| `sessionDate` | string (date) |  |
| `sessionNumber` | integer |  |
| `sessionType` | string |  |
| `startTime` | [LocalTime](#localtime) |  |
| `status` | string |  |
| `teacherType` | string |  |

### ClassSessionTeacherNameResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `classSessionId` | integer (int64) |  |

### ClassSessionTeacherTypeResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `teacherType` | string |  |

### ClassTeacherHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `changedByName` | string |  |
| `changedByUserId` | integer (int64) |  |
| `classTeacherId` | integer (int64) |  |
| `createdAt` | string (date-time) |  |
| `details` | object |  |
| `id` | integer (int64) |  |
| `teacherFullName` | string |  |
| `teacherRole` | string |  |
| `teacherUserId` | integer (int64) |  |

### ClassTeacherResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedFrom` | string (date) |  |
| `assignedTo` | string (date) |  |
| `classId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `subjectId` | integer (int64) |  |
| `teacherFullName` | string |  |
| `teacherRole` | string |  |
| `teacherType` | string |  |
| `teacherUserId` | integer (int64) |  |

### CommendationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `decidedByUserId` | integer (int64) |  |
| `employeeId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `recordDate` | string (date) |  |
| `recordType` | string |  |
| `title` | string |  |

### CommentAiDraftJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [CommentAiDraftResult](#commentaidraftresult) |  |
| `status` | string |  |

### CommentAiDraftResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assistantMessage` | string |  |
| `extraction` | [Extraction](#extraction) |  |
| `rows` | mảng [Row](#row) |  |
| `skippedStudents` | mảng [SkippedStudent](#skippedstudent) |  |
| `transcript` | string |  |
| `unmatchedMentions` | mảng [UnmatchedMention](#unmatchedmention) |  |

### CommentAiInstructionJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [CommentAiInstructionResult](#commentaiinstructionresult) |  |
| `status` | string |  |

### CommentAiInstructionResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assistantMessage` | string |  |
| `changes` | mảng [Change](#change) |  |
| `transcript` | string |  |

### CommentAiRejectionReasonJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [CommentAiRejectionReasonResult](#commentairejectionreasonresult) |  |
| `status` | string |  |

### CommentAiRejectionReasonResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentId` | integer (int64) |  |
| `reason` | string |  |

### CommentAiReviewJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [CommentAiReviewResult](#commentaireviewresult) |  |
| `status` | string |  |

### CommentAiReviewRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentIds` | mảng integer (int64) | ✔ |

### CommentAiReviewResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `aiCheckComplete` | boolean |  |
| `checkedCount` | integer |  |
| `flaggedCount` | integer |  |
| `message` | string |  |
| `reviews` | mảng [Review](#review) |  |
| `summary` | [Summary](#summary) |  |

### CommentAiSuggestionJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [CommentAiSuggestionResult](#commentaisuggestionresult) |  |
| `status` | string |  |

### CommentAiSuggestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `issues` | mảng string |  |

### CommentAiSuggestionResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentId` | integer (int64) |  |
| `explanation` | string |  |
| `originalContent` | string |  |
| `suggestedContent` | string |  |
| `warnings` | mảng string |  |

### CommentAttitudeAlertPreviewResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `items` | mảng [Item](#item) |  |

### CommentEditWindowResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### ConnectionAnswerItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `questionId` | integer (int64) | ✔ |
| `selectedChoiceId` | integer (int64) | ✔ |

### ConnectionAnswerResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `correct` | boolean |  |
| `correctChoiceId` | integer (int64) |  |
| `questionId` | integer (int64) |  |
| `selectedChoiceId` | integer (int64) |  |

### ConnectionChoiceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string | ✔ |
| `content` | string | ✔ |
| `displayOrder` | integer |  |
| `isCorrect` | boolean |  |

### ConvertLeadRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string | ✔ |

### CreateAcademicTermRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) | ✔ |
| `code` | string | ✔ |
| `endDate` | string (date) | ✔ |
| `name` | string | ✔ |
| `siteId` | integer (int64) | ✔ |
| `startDate` | string (date) | ✔ |

### CreateAcademicYearRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `endDate` | string (date) |  |
| `name` | string | ✔ |
| `startDate` | string (date) |  |

### CreateBookRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### CreateClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) |  |
| `classCode` | string | ✔ |
| `classType` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `endDate` | string (date) |  |
| `maxStudents` | integer | ✔ |
| `minStudents` | integer |  |
| `name` | string | ✔ |
| `siteId` | integer (int64) | ✔ |
| `startDate` | string (date) | ✔ |

### CreateClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `allowTeacherOverlap` | boolean |  |
| `assistantTeacherId` | integer (int64) |  |
| `cmTeacherId` | integer (int64) |  |
| `dayPart` | string | ✔ |
| `makeupForSessionId` | integer (int64) |  |
| `periodNumbers` | mảng integer | ✔ |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `sessionDate` | string (date) | ✔ |
| `sessionType` | string | ✔ |
| `teacherType` | string | ✔ |

### CreateCommendationRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `recordDate` | string (date) | ✔ |
| `recordType` | string | ✔ |
| `title` | string | ✔ |

### CreateCurriculumDocumentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `coverImageUrl` | string |  |
| `curriculumId` | integer (int64) |  |
| `description` | string |  |
| `displayOrder` | integer |  |
| `documentType` | string | ✔ |
| `fileUrl` | string | ✔ |
| `title` | string | ✔ |

### CreateCurriculumRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCategory` | string | ✔ |
| `code` | string | ✔ |
| `defaultGradePassThreshold` | number |  |
| `gradeLevel` | string |  |
| `level` | string |  |
| `name` | string | ✔ |
| `totalPeriods` | integer |  |
| `track` | string |  |

### CreateCurriculumSubjectRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `name` | string | ✔ |
| `periodCount` | integer |  |
| `skillId` | integer (int64) |  |
| `subjectCode` | string | ✔ |

### CreateCustomCurriculumRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `name` | string |  |
| `parentCurriculumId` | integer (int64) | ✔ |
| `siteId` | integer (int64) | ✔ |

### CreateDepartmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `headUserId` | integer (int64) |  |
| `name` | string | ✔ |
| `parentDepartmentId` | integer (int64) |  |

### CreateEmployeeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bankAccountNumber` | string |  |
| `bankName` | string |  |
| `currentAddress` | string |  |
| `dateOfBirth` | string (date) | ✔ |
| `departmentId` | integer (int64) |  |
| `employeeCode` | string | ✔ |
| `employeeType` | string | ✔ |
| `hireDate` | string (date) | ✔ |
| `idCardIssuedDate` | string (date) |  |
| `idCardIssuedPlace` | string |  |
| `idCardNumber` | string |  |
| `isDefaultShiftRequired` | boolean |  |
| `isManagement` | boolean |  |
| `newAccount` | [CreateUserRequest](#createuserrequest) |  |
| `permanentAddress` | string |  |
| `portraitUrl` | string |  |
| `positionId` | integer (int64) |  |
| `socialInsuranceNumber` | string |  |
| `taxCode` | string |  |
| `userId` | integer (int64) |  |

### CreateEmploymentContractRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `baseSalary` | number | ✔ |
| `contractNumber` | string | ✔ |
| `contractType` | string | ✔ |
| `endDate` | string (date) |  |
| `fileUrl` | string |  |
| `salaryType` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `status` | string | ✔ |

### CreateEntranceAssessmentComponentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `displayOrder` | integer |  |
| `maxScore` | number | ✔ |
| `name` | string | ✔ |
| `skillId` | integer (int64) |  |

### CreateEntranceAssessmentSetupRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) | ✔ |
| `name` | string | ✔ |
| `scaleType` | string | ✔ |
| `siteId` | integer (int64) | ✔ |

### CreateEquipmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `equipmentType` | string | ✔ |
| `name` | string | ✔ |
| `roomId` | integer (int64) |  |

### CreateExamQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [QuestionChoiceRequest](#questionchoicerequest) |  |
| `content` | string | ✔ |
| `correctAnswerText` | string |  |
| `defaultPoints` | number |  |
| `difficulty` | string |  |
| `explanation` | string |  |
| `groupKey` | string |  |
| `imageUrl` | string |  |
| `questionType` | string | ✔ |
| `referencePassage` | string |  |
| `skill` | string |  |
| `structuredContent` | object |  |
| `tags` | mảng string |  |

### CreateExamRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `examType` | string | ✔ |
| `subTopicId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |

### CreateExerciseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `code` | string | ✔ |
| `examId` | integer (int64) | ✔ |
| `exerciseType` | string | ✔ |
| `maxAttempts` | integer |  |
| `passThresholdPercent` | number |  |
| `showCorrectAnswers` | boolean |  |
| `skillCategory` | string |  |
| `subjectId` | integer (int64) |  |
| `timeLimitMinutes` | integer |  |
| `title` | string | ✔ |
| `totalPoints` | number | ✔ |

### CreateGradeComponentSetupRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) | ✔ |
| `commentRequired` | boolean |  |
| `evaluationType` | string | ✔ |
| `rosterAsOfDate` | string (date) | ✔ |
| `scaleType` | string | ✔ |

### CreateGradeEvaluationComponentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `displayOrder` | integer |  |
| `maxScore` | number |  |
| `name` | string | ✔ |
| `passThreshold` | number |  |
| `scaleType` | string |  |
| `skillId` | integer (int64) |  |
| `subjectId` | integer (int64) |  |

### CreateLeadRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `contactRelationship` | string |  |
| `email` | string |  |
| `fullName` | string | ✔ |
| `initialMessage` | string |  |
| `interestedCurriculumId` | integer (int64) |  |
| `interestedSiteId` | integer (int64) |  |
| `leadSourceCode` | string | ✔ |
| `phone` | string | ✔ |
| `studentCurrentSchool` | string |  |
| `studentDob` | string (date) |  |
| `studentGrade` | string |  |
| `studentName` | string |  |

### CreateLeaveRequestRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attachmentUrl` | string |  |
| `endDate` | string (date) | ✔ |
| `endTime` | [LocalTime](#localtime) |  |
| `leaveType` | string | ✔ |
| `reason` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `startTime` | [LocalTime](#localtime) |  |
| `substitutes` | mảng [SubstituteAssignmentRequest](#substituteassignmentrequest) |  |

### CreateListeningPracticeItemRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `curriculumId` | integer (int64) | ✔ |
| `difficulty` | string |  |
| `displayOrder` | integer |  |
| `mode` | string | ✔ |
| `scriptText` | string | ✔ |
| `title` | string | ✔ |

### CreateOperatingExpenseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number | ✔ |
| `description` | string | ✔ |
| `expenseCategoryCode` | string | ✔ |
| `expenseDate` | string (date) | ✔ |
| `fileUrl` | string |  |
| `paymentMethod` | string | ✔ |
| `receiptNumber` | string |  |
| `siteId` | integer (int64) |  |
| `supplierName` | string |  |

### CreateParentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `newAccount` | [CreateUserRequest](#createuserrequest) |  |
| `notes` | string |  |
| `occupation` | string |  |
| `portraitUrl` | string |  |
| `userId` | integer (int64) |  |
| `workplace` | string |  |

### CreatePartnerContractRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `contractType` | string | ✔ |
| `endDate` | string (date) | ✔ |
| `fileUrl` | string |  |
| `parentContractId` | integer (int64) |  |
| `revenueShareNotes` | string |  |
| `signedAt` | string (date) |  |
| `signedByCenter` | string |  |
| `signedByPartner` | string |  |
| `siteId` | integer (int64) | ✔ |
| `startDate` | string (date) | ✔ |
| `termsSummary` | string |  |

### CreatePermissionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `description` | string |  |
| `module` | string | ✔ |
| `name` | string | ✔ |

### CreatePositionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `name` | string | ✔ |

### CreateQualificationRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `expiryDate` | string (date) |  |
| `fileUrl` | string |  |
| `issuedDate` | string (date) |  |
| `issuer` | string |  |
| `qualificationType` | string | ✔ |
| `title` | string | ✔ |

### CreateQuestionBankRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `curriculumId` | integer (int64) |  |
| `level` | string |  |
| `name` | string | ✔ |
| `subjectId` | integer (int64) |  |

### CreateQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [QuestionChoiceRequest](#questionchoicerequest) |  |
| `content` | string | ✔ |
| `correctAnswerText` | string |  |
| `defaultPoints` | number |  |
| `difficulty` | string |  |
| `explanation` | string |  |
| `groupKey` | string |  |
| `imageUrl` | string |  |
| `questionBankId` | integer (int64) | ✔ |
| `questionType` | string | ✔ |
| `referencePassage` | string |  |
| `skill` | string |  |
| `structuredContent` | object |  |
| `tags` | mảng string |  |

### CreateReviewVideoSetRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `displayOrder` | integer |  |
| `subTopicId` | integer (int64) |  |
| `subjectId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |
| `videoType` | string | ✔ |

### CreateRoleRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `copyFromRoleId` | integer (int64) |  |
| `dataScope` | enum: ALL \| SITE \| CLASS \| SELF | ✔ |
| `description` | string |  |
| `name` | string | ✔ |

### CreateRoomRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `capacity` | integer |  |
| `code` | string | ✔ |
| `flexible` | boolean |  |
| `managedByCenter` | boolean |  |
| `name` | string |  |
| `roomType` | string | ✔ |
| `siteId` | integer (int64) | ✔ |

### CreateScholarshipRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `applicableScope` | string |  |
| `code` | string | ✔ |
| `discountType` | string | ✔ |
| `discountValue` | number | ✔ |
| `maxAmount` | number |  |
| `name` | string | ✔ |
| `studentId` | integer (int64) | ✔ |
| `validFrom` | string (date) |  |
| `validTo` | string (date) |  |

### CreateShiftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `appliesToWeekdays` | string |  |
| `checkInTime` | [LocalTime](#localtime) | ✔ |
| `checkInWindowAfterMinutes` | integer |  |
| `checkInWindowBeforeMinutes` | integer |  |
| `checkOutTime` | [LocalTime](#localtime) | ✔ |
| `checkOutWindowAfterMinutes` | integer |  |
| `checkOutWindowBeforeMinutes` | integer |  |
| `code` | string | ✔ |
| `name` | string | ✔ |
| `weekParity` | string |  |

### CreateSitePeriodTemplateRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dayPart` | string | ✔ |
| `endTime` | [LocalTime](#localtime) | ✔ |
| `label` | string |  |
| `periodNumber` | integer | ✔ |
| `startTime` | [LocalTime](#localtime) | ✔ |

### CreateSiteRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `code` | string | ✔ |
| `district` | string |  |
| `latitude` | number |  |
| `longitude` | number |  |
| `managerUserId` | integer (int64) |  |
| `name` | string | ✔ |
| `partnerInfo` | [PartnerSchoolInfoRequest](#partnerschoolinforequest) |  |
| `phone` | string |  |
| `siteType` | string | ✔ |
| `usedForAttendance` | boolean |  |
| `usedForClasses` | boolean |  |

### CreateSkillRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `description` | string |  |
| `name` | string | ✔ |

### CreateStudentCommentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attitude` | string |  |
| `classSessionId` | integer (int64) | ✔ |
| `commentDate` | string (date) | ✔ |
| `content` | string |  |
| `homeworkNext` | string |  |
| `homeworkNextReading` | string |  |
| `homeworkNextWriting` | string |  |
| `homeworkPreviousReadingScore` | string |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
| `homeworkPreviousWritingScore` | string |  |
| `isWarning` | boolean |  |
| `note` | string |  |
| `severity` | string |  |
| `structuredContent` | object |  |
| `studentId` | integer (int64) | ✔ |

### CreateStudentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dateOfBirth` | string (date) | ✔ |
| `enrollmentDate` | string (date) | ✔ |
| `gender` | string |  |
| `newAccount` | [CreateUserRequest](#createuserrequest) |  |
| `notes` | string |  |
| `originalClass` | string |  |
| `originalSchool` | string |  |
| `portraitUrl` | string |  |
| `primarySiteId` | integer (int64) |  |
| `studentCode` | string | ✔ |
| `userId` | integer (int64) |  |

### CreateSubTopicRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### CreateTaskRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assigneeUserIds` | mảng integer (int64) | ✔ |
| `description` | string |  |
| `dueAt` | string (date-time) |  |
| `priority` | string |  |
| `tags` | mảng string |  |
| `taskType` | string |  |
| `title` | string | ✔ |

### CreateTeachingPlanRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) |  |
| `classId` | integer (int64) | ✔ |
| `objectives` | string |  |
| `planType` | string | ✔ |
| `summary` | string |  |
| `visibleToPartner` | boolean |  |
| `weekEndDate` | string (date) |  |
| `weekNumber` | integer |  |
| `weekStartDate` | string (date) |  |

### CreateTuitionPlanRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `basePrice` | number | ✔ |
| `classTypeFilter` | string |  |
| `code` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `name` | string | ✔ |
| `pricePerUnit` | number |  |
| `pricingModel` | string | ✔ |
| `unitCount` | integer |  |

### CreateUnitRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### CreateUserRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `email` | string | ✔ |
| `fullName` | string | ✔ |
| `password` | string |  |
| `phone` | string |  |
| `username` | string | ✔ |

### CreateWorkCalendarRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `appliesToScope` | string | ✔ |
| `calendarDate` | string (date) | ✔ |
| `dayType` | string | ✔ |
| `description` | string |  |
| `employeeId` | integer (int64) |  |
| `shiftId` | integer (int64) |  |

### CriteriaScoreItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `criterion` | string |  |
| `percent` | integer |  |

### CurrentRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string |  |
| `studentId` | integer (int64) | ✔ |

### CurrentUserResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dataScope` | string |  |
| `departmentName` | string |  |
| `email` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `permissions` | mảng string |  |
| `phone` | string |  |
| `roleCodes` | mảng string |  |
| `studentId` | integer (int64) |  |
| `username` | string |  |

### CurriculumApprovalResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approverId` | integer (int64) |  |
| `comment` | string |  |
| `curriculumCode` | string |  |
| `curriculumId` | integer (int64) |  |
| `curriculumName` | string |  |
| `decidedAt` | string (date-time) |  |
| `decision` | string |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `submittedAt` | string (date-time) |  |
| `submittedBy` | integer (int64) |  |

### CurriculumDocumentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `coverImageUrl` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumId` | integer (int64) |  |
| `description` | string |  |
| `displayOrder` | integer |  |
| `documentType` | string |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `title` | string |  |

### CurriculumResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvedBy` | integer (int64) |  |
| `classCategory` | string |  |
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `defaultGradePassThreshold` | number |  |
| `gradeLevel` | string |  |
| `id` | integer (int64) |  |
| `level` | string |  |
| `name` | string |  |
| `parentCurriculumId` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `totalPeriods` | integer |  |
| `track` | string |  |

### CurriculumSubjectResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `curriculumId` | integer (int64) |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `periodCount` | integer |  |
| `skillId` | integer (int64) |  |
| `subjectCode` | string |  |

### DailyCommentImportPreviewResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dueDate` | string (date-time) |  |
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `lessonContent` | string |  |
| `rows` | mảng [DailyCommentImportPreviewRow](#dailycommentimportpreviewrow) |  |
| `successRows` | integer |  |
| `teacherName` | string |  |
| `totalRows` | integer |  |

### DailyCommentImportPreviewRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attitude` | string |  |
| `content` | string |  |
| `homeworkNext` | string |  |
| `homeworkNextReading` | string |  |
| `homeworkNextWriting` | string |  |
| `homeworkPreviousReadingScore` | string |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
| `homeworkPreviousWritingScore` | string |  |
| `note` | string |  |
| `studentId` | integer (int64) |  |

### DailyCommentImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### DecideCommentsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `commentIds` | mảng integer (int64) | ✔ |
| `decision` | string | ✔ |

### DecideCurriculumApprovalRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `decision` | string | ✔ |

### DecideHomeworkParentMeetingInvitesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `decision` | string | ✔ |
| `inviteIds` | mảng integer (int64) | ✔ |

### DecideLeaveRequestRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `decision` | string | ✔ |

### DecideOperatingExpenseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `decision` | string | ✔ |
| `rejectionReason` | string |  |

### DecideStudentAttitudeEscalationsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `decision` | string | ✔ |
| `escalationIds` | mảng integer (int64) | ✔ |

### DepartmentMemberResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `departmentId` | integer (int64) |  |
| `departmentName` | string |  |
| `employeeCode` | string |  |
| `employeeId` | integer (int64) |  |
| `employeeType` | string |  |
| `fullName` | string |  |
| `positionName` | string |  |
| `status` | string |  |
| `userId` | integer (int64) |  |

### DepartmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `headUserFullName` | string |  |
| `headUserId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `parentDepartmentId` | integer (int64) |  |
| `parentDepartmentName` | string |  |

### DetectedSiteResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `distanceMeters` | number |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### DeviceTokenCountResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `activeTokenCount` | integer |  |
| `userId` | integer (int64) |  |

### DeviceTokenRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `deviceId` | string |  |
| `platform` | string | ✔ |
| `token` | string | ✔ |

### DraftReflexPictureBriefRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `imageUrl` | string | ✔ |

### EffectivePermissionsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `permissions` | mảng string |  |
| `userId` | integer (int64) |  |

### EmployeeBatchImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `generatedCredentials` | mảng object |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### EmployeeResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bankAccountNumber` | string |  |
| `bankName` | string |  |
| `currentAddress` | string |  |
| `dateOfBirth` | string (date) |  |
| `departmentId` | integer (int64) |  |
| `employeeCode` | string |  |
| `employeeType` | string |  |
| `fullName` | string |  |
| `hireDate` | string (date) |  |
| `id` | integer (int64) |  |
| `idCardIssuedDate` | string (date) |  |
| `idCardIssuedPlace` | string |  |
| `idCardNumber` | string |  |
| `isDefaultShiftRequired` | boolean |  |
| `isManagement` | boolean |  |
| `permanentAddress` | string |  |
| `portraitUrl` | string |  |
| `positionId` | integer (int64) |  |
| `positionName` | string |  |
| `socialInsuranceNumber` | string |  |
| `status` | string |  |
| `taxCode` | string |  |
| `terminationDate` | string (date) |  |
| `userId` | integer (int64) |  |

### EmployeeScheduleOverviewResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionCheckIns` | mảng [ClassSessionCheckInStatusResponse](#classsessioncheckinstatusresponse) |  |
| `employeeShifts` | mảng [EmployeeShiftResponse](#employeeshiftresponse) |  |
| `employees` | mảng [EmployeeResponse](#employeeresponse) |  |
| `sessions` | mảng [ClassSessionResponse](#classsessionresponse) |  |
| `shiftDefinitions` | mảng [ShiftResponse](#shiftresponse) |  |
| `workCalendarOverrides` | mảng [WorkCalendarResponse](#workcalendarresponse) |  |

### EmployeeShiftResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `employeeId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `shiftId` | integer (int64) |  |
| `shiftName` | string |  |

### EmploymentContractResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `baseSalary` | number |  |
| `contractNumber` | string |  |
| `contractType` | string |  |
| `employeeId` | integer (int64) |  |
| `endDate` | string (date) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `salaryType` | string |  |
| `startDate` | string (date) |  |
| `status` | string |  |

### EndEmployeeShiftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveTo` | string (date) | ✔ |

### EndTeacherAssignmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedTo` | string (date) | ✔ |

### EnrollStudentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `enrolledDate` | string (date) | ✔ |
| `studentId` | integer (int64) | ✔ |

### EnrollmentMovementClassRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `closingHeadcount` | integer |  |
| `completedCount` | integer |  |
| `newEnrollments` | integer |  |
| `openingHeadcount` | integer |  |
| `transferredCount` | integer |  |
| `withdrawnCount` | integer |  |

### EnrollmentMovementGridColumn

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endDate` | string (date) |  |
| `key` | string |  |
| `label` | string |  |
| `startDate` | string (date) |  |

### EnrollmentMovementGridResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `columns` | mảng [EnrollmentMovementGridColumn](#enrollmentmovementgridcolumn) |  |
| `periodType` | string |  |
| `rows` | mảng [EnrollmentMovementGridRow](#enrollmentmovementgridrow) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### EnrollmentMovementGridRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `headcountByColumnKey` | object |  |

### EnrollmentMovementStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `classes` | mảng [EnrollmentMovementClassRow](#enrollmentmovementclassrow) |  |
| `endDate` | string (date) |  |
| `periodLabel` | string |  |
| `periodType` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |
| `totals` | [EnrollmentMovementClassRow](#enrollmentmovementclassrow) |  |

### EnrollmentMovementTrendPoint

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completedCount` | integer |  |
| `headcount` | integer |  |
| `monthIndex` | integer |  |
| `newEnrollments` | integer |  |
| `periodEnd` | string (date) |  |
| `periodStart` | string (date) |  |
| `transferredCount` | integer |  |
| `withdrawnCount` | integer |  |

### EnrollmentMovementTrendResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `endDate` | string (date) |  |
| `periodLabel` | string |  |
| `periodType` | string |  |
| `points` | mảng [EnrollmentMovementTrendPoint](#enrollmentmovementtrendpoint) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |

### EnterAttendanceMarkRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceReason` | string |  |
| `minutesEarlyLeave` | integer |  |
| `minutesLate` | integer |  |
| `status` | string | ✔ |
| `studentId` | integer (int64) | ✔ |

### EnterGradeEvaluationResultRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `disclaimer` | string |  |
| `level` | string |  |
| `note` | string |  |
| `overallScore` | number |  |
| `scaleType` | string |  |

### EnterGradeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceFlag` | boolean |  |
| `score` | number | ✔ |
| `studentId` | integer (int64) | ✔ |
| `teacherNote` | string |  |

### EntranceAssessmentComponentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `name` | string |  |
| `setupId` | integer (int64) |  |
| `skillId` | integer (int64) |  |
| `skillName` | string |  |

### EntranceAssessmentResultResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assessedDate` | string (date) |  |
| `candidateName` | string |  |
| `enteredByName` | string |  |
| `id` | integer (int64) |  |
| `leadId` | integer (int64) |  |
| `note` | string |  |
| `overallScore` | number |  |
| `placedFlag` | boolean |  |
| `recommendedClassId` | integer (int64) |  |
| `recommendedClassName` | string |  |
| `recommendedLevel` | string |  |
| `scores` | mảng [EntranceScoreResponse](#entrancescoreresponse) |  |
| `setupId` | integer (int64) |  |
| `studentId` | integer (int64) |  |

### EntranceAssessmentSetupResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearCode` | string |  |
| `academicYearId` | integer (int64) |  |
| `academicYearName` | string |  |
| `components` | mảng [EntranceAssessmentComponentResponse](#entranceassessmentcomponentresponse) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `scaleType` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### EntranceScoreInput

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceFlag` | boolean |  |
| `componentId` | integer (int64) | ✔ |
| `score` | number |  |

### EntranceScoreResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceFlag` | boolean |  |
| `componentCode` | string |  |
| `componentId` | integer (int64) |  |
| `componentName` | string |  |
| `maxScore` | number |  |
| `score` | number |  |

### EquipmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `equipmentType` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `notes` | string |  |
| `roomId` | integer (int64) |  |
| `status` | string |  |

### Event

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endedAt` | string (date-time) | ✔ |
| `eventType` | string | ✔ |
| `startedAt` | string (date-time) | ✔ |
| `userAgent` | string |  |

### ExamResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumCode` | string |  |
| `curriculumId` | integer (int64) |  |
| `examType` | string |  |
| `id` | integer (int64) |  |
| `questionBankId` | integer (int64) |  |
| `subTopicId` | integer (int64) |  |
| `subTopicTitle` | string |  |
| `teacherType` | string |  |
| `title` | string |  |
| `unitTitle` | string |  |
| `uuid` | string (uuid) |  |

### ExerciseAssignmentQuestionStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `questions` | mảng [QuestionRow](#questionrow) |  |

### ExerciseAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedBy` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `dueAt` | string (date-time) |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTitle` | string |  |
| `id` | integer (int64) |  |
| `latePenaltyPercent` | number |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `status` | string |  |
| `targetStudentIds` | mảng integer (int64) |  |
| `uuid` | string (uuid) |  |

### ExerciseAssignmentStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `batchMembers` | mảng [ExerciseAssignmentStatsResponse](#exerciseassignmentstatsresponse) |  |
| `completedCount` | integer |  |
| `completionPercent` | number |  |
| `dueAt` | string (date-time) |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTitle` | string |  |
| `exerciseType` | string |  |
| `homeworkBatchId` | integer (int64) |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `passRatePercent` | number |  |
| `passedCount` | integer |  |
| `status` | string |  |
| `teacherType` | string |  |
| `totalStudents` | integer |  |

### ExerciseAssignmentStudentStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignment` | [ExerciseAssignmentStatsResponse](#exerciseassignmentstatsresponse) |  |
| `students` | mảng [StudentRow](#studentrow) |  |

### ExerciseAttemptResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptNumber` | integer |  |
| `autoGradeScore` | number |  |
| `exerciseAssignmentId` | integer (int64) |  |
| `exerciseId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `isLateSubmission` | boolean |  |
| `manualGradeScore` | number |  |
| `passed` | boolean |  |
| `percentage` | number |  |
| `selectedForGrading` | boolean |  |
| `startedAt` | string (date-time) |  |
| `status` | string |  |
| `stoppedByIntegrityViolation` | boolean |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `totalScore` | number |  |

### ExerciseQuestionChoiceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string |  |
| `content` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `imageUrl` | string |  |

### ExerciseQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [ExerciseQuestionChoiceResponse](#exercisequestionchoiceresponse) |  |
| `displayOrder` | integer |  |
| `exerciseId` | integer (int64) |  |
| `groupKey` | string |  |
| `id` | integer (int64) |  |
| `imageUrl` | string |  |
| `points` | number |  |
| `questionContent` | string |  |
| `questionId` | integer (int64) |  |
| `questionType` | string |  |
| `referencePassage` | string |  |
| `skill` | string |  |
| `structuredContent` | object |  |

### ExerciseResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `examCode` | string |  |
| `examId` | integer (int64) |  |
| `examTeacherType` | string |  |
| `examTitle` | string |  |
| `exerciseType` | string |  |
| `hasEssayOrSpeaking` | boolean |  |
| `id` | integer (int64) |  |
| `maxAttempts` | integer |  |
| `passThresholdPercent` | number |  |
| `showCorrectAnswers` | boolean |  |
| `skillCategory` | string |  |
| `status` | string |  |
| `subTopicTitle` | string |  |
| `subjectId` | integer (int64) |  |
| `timeLimitMinutes` | integer |  |
| `title` | string |  |
| `totalPoints` | number |  |
| `unitTitle` | string |  |
| `uuid` | string (uuid) |  |

### ExerciseSummary

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `id` | integer (int64) |  |
| `questionCount` | integer (int64) |  |
| `title` | string |  |

### ExpenseCategoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `categoryGroup` | string |  |
| `code` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |

### ExpiringContractResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `contractId` | integer (int64) |  |
| `contractNumber` | string |  |
| `employeeCode` | string |  |
| `employeeFullName` | string |  |
| `employeeId` | integer (int64) |  |
| `endDate` | string (date) |  |

### ExpiringPartnerContractResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `contractId` | integer (int64) |  |
| `contractNumber` | string |  |
| `endDate` | string (date) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### Extraction

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classAttitude` | string |  |
| `classPoints` | mảng string |  |
| `individuals` | mảng [IndividualPoints](#individualpoints) |  |
| `teacherPronoun` | string |  |

### FieldMappingItemRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dataPath` | string |  |
| `description` | string |  |
| `placeholderKey` | string | ✔ |

### FinancialReportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `periodFrom` | string (date) |  |
| `periodTo` | string (date) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `totalExpense` | number |  |
| `totalOutstanding` | number |  |
| `totalRevenue` | number |  |

### GenerateInvoicesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `billingPeriodFrom` | string (date) | ✔ |
| `billingPeriodTo` | string (date) | ✔ |
| `classId` | integer (int64) |  |
| `dueDate` | string (date) | ✔ |
| `issueDate` | string (date) | ✔ |

### GenerateReportRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `outputFormat` | string |  |
| `periods` | mảng [ReportPeriodSelector](#reportperiodselector) |  |
| `scope` | string |  |
| `studentId` | integer (int64) |  |
| `templateId` | integer (int64) |  |

### GeneratedReportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `fileFormat` | string |  |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string |  |
| `generatedBy` | integer (int64) |  |
| `id` | integer (int64) |  |
| `scope` | string |  |
| `studentId` | integer (int64) |  |
| `templateId` | integer (int64) |  |

### GoogleLoginRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `browserLanguage` | string |  |
| `confirm` | boolean |  |
| `idToken` | string | ✔ |
| `screenResolution` | string |  |
| `timezone` | string |  |

### GradeAnswerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `feedback` | string |  |
| `maxScore` | number | ✔ |
| `score` | number | ✔ |

### GradeComponentSetupResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `academicTermName` | string |  |
| `classId` | integer (int64) |  |
| `commentRequired` | boolean |  |
| `evaluationType` | string |  |
| `id` | integer (int64) |  |
| `rosterAsOfDate` | string (date) |  |
| `scaleType` | string |  |

### GradeEditWindowResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### GradeEntryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceFlag` | boolean |  |
| `academicTermId` | integer (int64) |  |
| `academicYear` | string |  |
| `academicYearId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `enteredBy` | integer (int64) |  |
| `evaluationType` | string |  |
| `finalizedAt` | string (date-time) |  |
| `gradeEvaluationComponentId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `publishedAt` | string (date-time) |  |
| `publishedBy` | integer (int64) |  |
| `score` | number |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `teacherNote` | string |  |

### GradeEvaluationComponentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `displayOrder` | integer |  |
| `gradeComponentSetupId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `name` | string |  |
| `passThreshold` | number |  |
| `scaleType` | string |  |
| `skillId` | integer (int64) |  |
| `subjectId` | integer (int64) |  |

### GradeEvaluationResultResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `comment` | string |  |
| `disclaimer` | string |  |
| `enteredBy` | integer (int64) |  |
| `evaluationType` | string |  |
| `finalizedAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `importJobId` | integer (int64) |  |
| `level` | string |  |
| `note` | string |  |
| `overallScore` | number |  |
| `publishedAt` | string (date-time) |  |
| `publishedBy` | integer (int64) |  |
| `scaleType` | string |  |
| `source` | string |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### GradeImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### GradeListeningAttemptRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `feedback` | string |  |
| `maxScore` | number | ✔ |
| `score` | number | ✔ |

### GradeReviewVideoSubmissionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `feedback` | string |  |
| `maxScore` | number | ✔ |
| `score` | number | ✔ |

### HomeworkParentMeetingInviteResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `channelLabel` | string |  |
| `className` | string |  |
| `createdAt` | string (date-time) |  |
| `decidedAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `missCount` | integer |  |
| `rejectionReason` | string |  |
| `schoolClassId` | integer (int64) |  |
| `status` | string |  |
| `studentId` | integer (int64) |  |
| `studentName` | string |  |

### HomeworkProgressResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) |  |
| `commentId` | integer (int64) |  |
| `grammarAssignmentId` | integer (int64) |  |
| `grammarDueAt` | string (date-time) |  |
| `grammarItems` | mảng [HomeworkSkillItemResponse](#homeworkskillitemresponse) |  |
| `grammarOfflineText` | string |  |
| `grammarPassed` | boolean |  |
| `grammarProgress` | string |  |
| `grammarSkillCategory` | string |  |
| `grammarTitle` | string |  |
| `grammarUnitTitle` | string |  |
| `readingAssignmentId` | integer (int64) |  |
| `readingDueAt` | string (date-time) |  |
| `readingItems` | mảng [HomeworkSkillItemResponse](#homeworkskillitemresponse) |  |
| `readingOfflineText` | string |  |
| `readingPassed` | boolean |  |
| `readingProgress` | string |  |
| `readingTitle` | string |  |
| `readingUnitTitle` | string |  |
| `videoAssignmentId` | integer (int64) |  |
| `videoDueAt` | string (date-time) |  |
| `videoPassed` | boolean |  |
| `videoProgress` | string |  |
| `videoTeacherType` | string |  |
| `videoTitle` | string |  |
| `videoType` | string |  |
| `videoUnitTitle` | string |  |
| `writingAssignmentId` | integer (int64) |  |
| `writingDueAt` | string (date-time) |  |
| `writingItems` | mảng [HomeworkSkillItemResponse](#homeworkskillitemresponse) |  |
| `writingOfflineText` | string |  |
| `writingPassed` | boolean |  |
| `writingProgress` | string |  |
| `writingTitle` | string |  |
| `writingUnitTitle` | string |  |

### HomeworkScoreInput

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `offline` | string |  |
| `reading` | string |  |
| `speaking` | string |  |
| `studentId` | integer (int64) |  |
| `writing` | string |  |

### HomeworkSkillGroupResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `examCode` | string |  |
| `examId` | integer (int64) |  |
| `examTeacherType` | string |  |
| `examTitle` | string |  |
| `exerciseCount` | integer |  |
| `exercises` | mảng [ExerciseSummary](#exercisesummary) |  |
| `questionCount` | integer (int64) |  |
| `skillCategory` | string |  |
| `subTopicTitle` | string |  |
| `unitTitle` | string |  |

### HomeworkSkillItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `exerciseAssignmentId` | integer (int64) |  |
| `passed` | boolean |  |
| `progress` | string |  |
| `title` | string |  |

### ImportedQuestion

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `id` | integer (int64) |  |
| `summary` | string |  |

### IndividualPoints

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attitude` | string |  |
| `evidence` | string |  |
| `points` | mảng string |  |
| `sharedWith` | mảng integer (int64) |  |
| `studentId` | integer (int64) |  |

### IntegrityEventBatchResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptStopped` | boolean |  |
| `notifiedByThisBatch` | boolean |  |
| `savedCount` | integer |  |
| `totalViolationCount` | integer |  |
| `totalViolationDurationSeconds` | integer |  |

### IntegritySummaryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `parentAndTeacherNotified` | boolean |  |
| `violationCount` | integer |  |
| `violationTotalDurationSeconds` | integer |  |

### InvoiceHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `changedById` | integer (int64) |  |
| `changedByName` | string |  |
| `createdAt` | string (date-time) |  |
| `details` | object |  |
| `id` | integer (int64) |  |

### InvoiceItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `description` | string |  |
| `id` | integer (int64) |  |
| `itemType` | string |  |
| `quantity` | number |  |
| `unitPrice` | number |  |

### InvoiceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `billingPeriodFrom` | string (date) |  |
| `billingPeriodTo` | string (date) |  |
| `classEnrollmentId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `discountTotal` | number |  |
| `dueDate` | string (date) |  |
| `id` | integer (int64) |  |
| `invoiceNumber` | string |  |
| `issueDate` | string (date) |  |
| `items` | mảng [InvoiceItemResponse](#invoiceitemresponse) |  |
| `outstandingAmount` | number |  |
| `paidAmount` | number |  |
| `payerParentId` | integer (int64) |  |
| `qrCodeData` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `subtotal` | number |  |
| `taxAmount` | number |  |
| `totalAmount` | number |  |

### Issue

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `message` | string |  |
| `source` | string |  |
| `type` | string |  |

### IssueCount

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `count` | integer |  |
| `type` | string |  |

### Item

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentId` | integer (int64) |  |
| `consecutiveLowCount` | integer |  |
| `escalation` | boolean |  |
| `message` | string |  |

### JsonNode

_(không có trường — object rỗng hoặc kiểu đặc biệt)_

### KeyGrammarOutcome

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attempts` | integer |  |
| `correct` | integer |  |
| `note` | string |  |
| `redoRequired` | boolean |  |
| `status` | string |  |

### KeyGrammarStructureResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `base` | boolean |  |
| `id` | string |  |
| `name` | string |  |

### LeadResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedAt` | string (date-time) |  |
| `assignedTo` | integer (int64) |  |
| `contactRelationship` | string |  |
| `convertedAt` | string (date-time) |  |
| `convertedStudentId` | integer (int64) |  |
| `email` | string |  |
| `finalNote` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `initialMessage` | string |  |
| `interestedCurriculumId` | integer (int64) |  |
| `interestedSiteId` | integer (int64) |  |
| `leadCode` | string |  |
| `leadSourceCode` | string |  |
| `outcome` | string |  |
| `phone` | string |  |
| `status` | string |  |
| `studentCurrentSchool` | string |  |
| `studentDob` | string (date) |  |
| `studentGrade` | string |  |
| `studentName` | string |  |

### LeaveRequestApprovalResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approverName` | string |  |
| `approverRole` | string |  |
| `approverUserId` | integer (int64) |  |
| `comment` | string |  |
| `decidedAt` | string (date-time) |  |
| `decision` | string |  |
| `id` | integer (int64) |  |
| `stepOrder` | integer |  |

### LeaveRequestResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attachmentUrl` | string |  |
| `currentApproverUserId` | integer (int64) |  |
| `currentStep` | integer |  |
| `departmentName` | string |  |
| `employeeCode` | string |  |
| `employeeFullName` | string |  |
| `employeeId` | integer (int64) |  |
| `endDate` | string (date) |  |
| `endTime` | [LocalTime](#localtime) |  |
| `finalizedAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `leaveType` | string |  |
| `reason` | string |  |
| `startDate` | string (date) |  |
| `startTime` | [LocalTime](#localtime) |  |
| `status` | string |  |
| `submittedAt` | string (date-time) |  |
| `totalDays` | number |  |

### LeaveSubstitutionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `className` | string |  |
| `classSessionId` | integer (int64) |  |
| `createdAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `leaveRequestId` | integer (int64) |  |
| `originalTeacherId` | integer (int64) |  |
| `originalTeacherName` | string |  |
| `revokedAt` | string (date-time) |  |
| `sessionDate` | string (date) |  |
| `substituteTeacherId` | integer (int64) |  |
| `substituteTeacherName` | string |  |

### LeaveTypeResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `label` | string |  |
| `sortOrder` | integer |  |

### LinkParentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `isFinancialResponsible` | boolean |  |
| `isPrimaryContact` | boolean |  |
| `notes` | string |  |
| `parentId` | integer (int64) | ✔ |
| `relationship` | string | ✔ |

### ListeningHintResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `transcript` | string |  |

### ListeningPlayProgressResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `hintUnlockThreshold` | integer |  |
| `hintUnlocked` | boolean |  |
| `playCount` | integer |  |

### ListeningPracticeAttemptResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptNumber` | integer |  |
| `audioAnswerUrl` | string |  |
| `dictationAnswerText` | string |  |
| `dictationScore` | number |  |
| `id` | integer (int64) |  |
| `pausedPositionSeconds` | integer |  |
| `practiceItemId` | integer (int64) |  |
| `startedAt` | string (date-time) |  |
| `status` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |

### ListeningPracticeGradingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `feedback` | string |  |
| `gradedAt` | string (date-time) |  |
| `graderUserId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `practiceAttemptId` | integer (int64) |  |
| `score` | number |  |

### ListeningPracticeItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumId` | integer (int64) |  |
| `difficulty` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `mode` | string |  |
| `scriptText` | string |  |
| `status` | string |  |
| `title` | string |  |

### LocalTime

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `hour` | integer |  |
| `minute` | integer |  |
| `nano` | integer |  |
| `second` | integer |  |

### LoginHistoryItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `browserLanguage` | string |  |
| `createdAt` | string (date-time) |  |
| `failureReason` | string |  |
| `ipAddress` | string |  |
| `screenResolution` | string |  |
| `success` | boolean |  |
| `timezone` | string |  |
| `userAgent` | string |  |

### LoginRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `browserLanguage` | string |  |
| `confirm` | boolean |  |
| `password` | string | ✔ |
| `screenResolution` | string |  |
| `timezone` | string |  |
| `usernameOrEmail` | string | ✔ |

### LoginResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `accessToken` | string |  |
| `accessTokenExpiresInSeconds` | integer (int64) |  |
| `refreshToken` | string |  |

### LogoutRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `refreshToken` | string | ✔ |

### MarkAttendanceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `marks` | mảng [EnterAttendanceMarkRequest](#enterattendancemarkrequest) | ✔ |
| `mode` | string | ✔ |

### MediaUploadResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `url` | string |  |

### MyReviewVideoAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `dueAt` | string (date-time) |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `sessionDate` | string (date) |  |
| `videoType` | string |  |

### Notice

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `message` | string |  |
| `source` | string |  |
| `type` | string |  |

### NotificationPreferenceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `emailEnabled` | boolean | ✔ |
| `inAppEnabled` | boolean | ✔ |
| `pushEnabled` | boolean | ✔ |
| `smsEnabled` | boolean | ✔ |
| `zaloEnabled` | boolean | ✔ |

### NotificationPreferenceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `emailEnabled` | boolean |  |
| `inAppEnabled` | boolean |  |
| `notificationType` | string |  |
| `pushEnabled` | boolean |  |
| `smsEnabled` | boolean |  |
| `zaloEnabled` | boolean |  |

### NotificationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `content` | string |  |
| `createdAt` | string (date-time) |  |
| `entityId` | integer (int64) |  |
| `entityType` | string |  |
| `exerciseAssignmentId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `notificationType` | string |  |
| `priority` | string |  |
| `readAt` | string (date-time) |  |
| `reviewVideoAssignmentId` | integer (int64) |  |
| `studentId` | integer (int64) |  |
| `title` | string |  |

### OperatingExpenseResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `approvedBy` | integer (int64) |  |
| `approvedByName` | string |  |
| `createdAt` | string (date-time) |  |
| `description` | string |  |
| `expenseCategoryCode` | string |  |
| `expenseCategoryName` | string |  |
| `expenseDate` | string (date) |  |
| `expenseNumber` | string |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `paymentMethod` | string |  |
| `receiptNumber` | string |  |
| `recordedBy` | integer (int64) |  |
| `recordedByName` | string |  |
| `rejectionReason` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `status` | string |  |
| `supplierName` | string |  |

### PageChangeHistoryItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | mảng [ChangeHistoryItemResponse](#changehistoryitemresponse) |  |
| `empty` | boolean |  |
| `first` | boolean |  |
| `last` | boolean |  |
| `number` | integer |  |
| `numberOfElements` | integer |  |
| `pageable` | [PageableObject](#pageableobject) |  |
| `size` | integer |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `totalElements` | integer (int64) |  |
| `totalPages` | integer |  |

### PageLoginHistoryItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | mảng [LoginHistoryItemResponse](#loginhistoryitemresponse) |  |
| `empty` | boolean |  |
| `first` | boolean |  |
| `last` | boolean |  |
| `number` | integer |  |
| `numberOfElements` | integer |  |
| `pageable` | [PageableObject](#pageableobject) |  |
| `size` | integer |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `totalElements` | integer (int64) |  |
| `totalPages` | integer |  |

### PageNotificationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | mảng [NotificationResponse](#notificationresponse) |  |
| `empty` | boolean |  |
| `first` | boolean |  |
| `last` | boolean |  |
| `number` | integer |  |
| `numberOfElements` | integer |  |
| `pageable` | [PageableObject](#pageableobject) |  |
| `size` | integer |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `totalElements` | integer (int64) |  |
| `totalPages` | integer |  |

### PagePermissionAuditLogResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | mảng [PermissionAuditLogResponse](#permissionauditlogresponse) |  |
| `empty` | boolean |  |
| `first` | boolean |  |
| `last` | boolean |  |
| `number` | integer |  |
| `numberOfElements` | integer |  |
| `pageable` | [PageableObject](#pageableobject) |  |
| `size` | integer |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `totalElements` | integer (int64) |  |
| `totalPages` | integer |  |

### PageUserListItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | mảng [UserListItemResponse](#userlistitemresponse) |  |
| `empty` | boolean |  |
| `first` | boolean |  |
| `last` | boolean |  |
| `number` | integer |  |
| `numberOfElements` | integer |  |
| `pageable` | [PageableObject](#pageableobject) |  |
| `size` | integer |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `totalElements` | integer (int64) |  |
| `totalPages` | integer |  |

### Pageable

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `page` | integer |  |
| `size` | integer |  |
| `sort` | mảng string |  |

### PageableObject

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `offset` | integer (int64) |  |
| `pageNumber` | integer |  |
| `pageSize` | integer |  |
| `paged` | boolean |  |
| `sort` | mảng [SortObject](#sortobject) |  |
| `unpaged` | boolean |  |

### ParentBatchImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `generatedCredentials` | mảng object |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### ParentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `notes` | string |  |
| `occupation` | string |  |
| `portraitUrl` | string |  |
| `userId` | integer (int64) |  |
| `workplace` | string |  |

### ParentStudentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `id` | integer (int64) |  |
| `isFinancialResponsible` | boolean |  |
| `isPrimaryContact` | boolean |  |
| `notes` | string |  |
| `parentFullName` | string |  |
| `parentId` | integer (int64) |  |
| `parentPhone` | string |  |
| `relationship` | string |  |
| `studentId` | integer (int64) |  |

### PartnerAttendanceSummaryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absentCount` | integer (int64) |  |
| `attendanceRatePercent` | number |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `earlyLeaveCount` | integer (int64) |  |
| `excusedCount` | integer (int64) |  |
| `lateCount` | integer (int64) |  |
| `presentCount` | integer (int64) |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `totalMarks` | integer (int64) |  |

### PartnerContractResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `contractNumber` | string |  |
| `contractType` | string |  |
| `endDate` | string (date) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `parentContractId` | integer (int64) |  |
| `revenueShareNotes` | string |  |
| `signedAt` | string (date) |  |
| `signedByCenter` | string |  |
| `signedByPartner` | string |  |
| `siteId` | integer (int64) |  |
| `startDate` | string (date) |  |
| `status` | string |  |
| `termsSummary` | string |  |

### PartnerFeedbackResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedTo` | integer (int64) |  |
| `content` | string |  |
| `createdAt` | string (date-time) |  |
| `feedbackType` | string |  |
| `id` | integer (int64) |  |
| `priority` | string |  |
| `resolutionNotes` | string |  |
| `resolvedAt` | string (date-time) |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `submittedBy` | integer (int64) |  |

### PartnerSchoolInfoRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `additionalInfo` | string |  |
| `contactEmail` | string |  |
| `contactPersonName` | string |  |
| `contactPersonTitle` | string |  |
| `contactPhone` | string |  |

### PartnerSchoolInfoResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `additionalInfo` | string |  |
| `contactEmail` | string |  |
| `contactPersonName` | string |  |
| `contactPersonTitle` | string |  |
| `contactPhone` | string |  |

### PartnerSiteResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `siteCode` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |

### PauseListeningPracticeAttemptRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `positionSeconds` | integer | ✔ |

### PaymentLinkResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `accountName` | string |  |
| `accountNumber` | string |  |
| `amount` | number |  |
| `bin` | string |  |
| `checkoutUrl` | string |  |
| `description` | string |  |
| `expiresAt` | string (date-time) |  |
| `invoiceId` | integer (int64) |  |
| `orderCode` | integer (int64) |  |
| `qrCode` | string |  |
| `status` | string |  |

### PaymentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `bankTransactionId` | string |  |
| `confirmedAt` | string (date-time) |  |
| `confirmedBy` | integer (int64) |  |
| `confirmedByName` | string |  |
| `id` | integer (int64) |  |
| `invoiceId` | integer (int64) |  |
| `paidAt` | string (date-time) |  |
| `paymentMethod` | string |  |
| `paymentReference` | string |  |
| `receiptNumber` | string |  |
| `status` | string |  |

### PayrollEntryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `baseSalary` | number |  |
| `bonuses` | number |  |
| `employeeCode` | string |  |
| `employeeFullName` | string |  |
| `employeeId` | integer (int64) |  |
| `fallbackToLatestAvailable` | boolean |  |
| `grossSalary` | number |  |
| `healthInsurance` | number |  |
| `hourlyRate` | number |  |
| `id` | integer (int64) |  |
| `netSalary` | number |  |
| `penalties` | number |  |
| `periodCode` | string |  |
| `periodEndDate` | string (date) |  |
| `periodStartDate` | string (date) |  |
| `socialInsurance` | number |  |
| `status` | string |  |
| `tax` | number |  |
| `teachingHours` | number |  |
| `totalDeductions` | number |  |
| `unemploymentInsurance` | number |  |
| `workDays` | number |  |

### PendingGradingClassSummaryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `pendingSubmissionCount` | integer |  |

### PendingGradingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `audioAnswerUrl` | string |  |
| `exerciseAttemptId` | integer (int64) |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTitle` | string |  |
| `questionContent` | string |  |
| `questionId` | integer (int64) |  |
| `questionType` | string |  |
| `studentAnswerId` | integer (int64) |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### PendingListeningGradingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioAnswerUrl` | string |  |
| `practiceAttemptId` | integer (int64) |  |
| `practiceItemId` | integer (int64) |  |
| `practiceItemTitle` | string |  |
| `scriptText` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### PermissionAuditLogResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `actorUserId` | integer (int64) |  |
| `createdAt` | string (date-time) |  |
| `details` | object |  |
| `id` | integer (int64) |  |
| `ipAddress` | string |  |
| `targetPermissionId` | integer (int64) |  |
| `targetRoleId` | integer (int64) |  |
| `targetUserId` | integer (int64) |  |

### PermissionMatrixItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `granted` | boolean |  |
| `module` | string |  |
| `name` | string |  |
| `permissionId` | integer (int64) |  |

### PermissionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `description` | string |  |
| `id` | integer (int64) |  |
| `module` | string |  |
| `name` | string |  |

### PortalClassOptionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCode` | string |  |
| `classEnrollmentId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `enrolledDate` | string (date) |  |
| `recommended` | boolean |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `withdrawnDate` | string (date) |  |

### PositionDefaultRolesResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `defaultRoles` | mảng [RoleResponse](#roleresponse) |  |
| `positionCode` | string |  |
| `positionId` | integer (int64) |  |

### PositionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |

### PromoteClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) | ✔ |
| `classCode` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `endDate` | string (date) |  |
| `maxStudents` | integer | ✔ |
| `minStudents` | integer |  |
| `name` | string | ✔ |
| `startDate` | string (date) | ✔ |

### PromoteClassResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `movedStudentCount` | integer |  |
| `newClass` | [ClassResponse](#classresponse) |  |
| `oldClassId` | integer (int64) |  |
| `skippedStudentCount` | integer |  |
| `skippedStudents` | mảng [SkippedStudentInfo](#skippedstudentinfo) |  |

### PublishGradesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `evaluationResultComments` | object |  |
| `evaluationResultNotes` | object |  |
| `gradeEntryIds` | mảng integer (int64) |  |
| `gradeEvaluationResultIds` | mảng integer (int64) |  |
| `rejectReason` | string |  |

### PushSetupLogRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `platform` | string |  |
| `status` | string | ✔ |

### QualificationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `employeeId` | integer (int64) |  |
| `expiryDate` | string (date) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `issuedDate` | string (date) |  |
| `issuer` | string |  |
| `qualificationType` | string |  |
| `title` | string |  |

### QuestionBankResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `curriculumId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `isActive` | boolean |  |
| `level` | string |  |
| `name` | string |  |
| `subjectId` | integer (int64) |  |

### QuestionChoiceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string | ✔ |
| `content` | string | ✔ |
| `displayOrder` | integer |  |
| `imageUrl` | string |  |
| `isCorrect` | boolean |  |

### QuestionChoiceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string |  |
| `content` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `imageUrl` | string |  |
| `isCorrect` | boolean |  |

### QuestionImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `createdQuestions` | mảng [QuestionImportedRow](#questionimportedrow) |  |
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### QuestionImportedRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string |  |
| `defaultPoints` | number |  |
| `id` | integer (int64) |  |

### QuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [QuestionChoiceResponse](#questionchoiceresponse) |  |
| `content` | string |  |
| `correctAnswerText` | string |  |
| `createdBy` | integer (int64) |  |
| `defaultPoints` | number |  |
| `difficulty` | string |  |
| `explanation` | string |  |
| `groupKey` | string |  |
| `id` | integer (int64) |  |
| `imageUrl` | string |  |
| `keyGrammar` | mảng string |  |
| `questionBankId` | integer (int64) |  |
| `questionType` | string |  |
| `referencePassage` | string |  |
| `skill` | string |  |
| `status` | string |  |
| `structuredContent` | object |  |
| `tags` | mảng string |  |

### QuestionRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answeredCount` | integer |  |
| `displayOrder` | integer |  |
| `prompt` | string |  |
| `questionId` | integer (int64) |  |
| `reviewVideoId` | integer (int64) |  |
| `reviewVideoTitle` | string |  |
| `wrongCount` | integer |  |
| `wrongRatePercent` | number |  |
| `wrongStudents` | mảng [WrongStudent](#wrongstudent) |  |

### ReassignTaskRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `fromAssignmentId` | integer (int64) | ✔ |
| `newAssigneeUserId` | integer (int64) | ✔ |

### RecordIntegrityEventsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `events` | mảng [Event](#event) | ✔ |

### RecordListeningPlayRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `questionId` | integer (int64) | ✔ |

### RecordManualPaymentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number | ✔ |
| `paidAt` | string (date-time) |  |
| `paymentMethod` | string | ✔ |
| `receiptNumber` | string |  |

### RecordTransferRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveDate` | string (date) | ✔ |
| `fromClassId` | integer (int64) |  |
| `reason` | string |  |
| `toClassId` | integer (int64) |  |
| `toSiteId` | integer (int64) |  |
| `transferType` | string | ✔ |

### ReflexPictureBriefResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `aiAvailable` | boolean |  |
| `brief` | string |  |
| `pictureFound` | boolean |  |

### ReflexPictureCaptureResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `imageUrl` | string |  |

### ReflexQuestionFormatOptionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `format` | string |  |
| `label` | string |  |
| `recommendedSeconds` | integer |  |
| `requiresPictureBrief` | boolean |  |

### ReflexQuestionProgressHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `attemptNumber` | integer |  |
| `attemptType` | enum: WRITING \| SPEAKING |  |
| `audioUrl` | string |  |
| `criteriaScores` | mảng [CriteriaScoreItem](#criteriascoreitem) |  |
| `feedback` | string |  |
| `gradedAt` | string (date-time) |  |
| `grammarReviewQuotes` | mảng string |  |
| `grammarReviewRequired` | boolean |  |
| `hint` | string |  |
| `markedAnswer` | string |  |
| `maxScore` | number |  |
| `questionDisplayOrder` | integer |  |
| `questionId` | integer (int64) |  |
| `questionPrompt` | string |  |
| `recordingFilter` | boolean |  |
| `score` | number |  |
| `transcript` | string |  |

### ReflexQuestionProgressResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `audioUrl` | string |  |
| `questionId` | integer (int64) |  |
| `questionPassed` | boolean |  |
| `speakingAttemptCount` | integer |  |
| `speakingCriteriaScores` | mảng [CriteriaScoreItem](#criteriascoreitem) |  |
| `speakingFeedback` | string |  |
| `speakingHint` | string |  |
| `speakingPassed` | boolean |  |
| `speakingScorePercent` | integer |  |
| `speakingTranscript` | string |  |
| `updatedAt` | string (date-time) |  |
| `writingAttemptCount` | integer |  |
| `writingCorrectedAnswer` | string |  |
| `writingFeedback` | string |  |
| `writingHint` | string |  |
| `writingMarkedAnswer` | string |  |
| `writingPassed` | boolean |  |
| `writingScorePercent` | integer |  |

### ReflexRecordingConfigResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `filterEnabled` | boolean |  |

### RefreshTokenRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `refreshToken` | string | ✔ |

### RefreshTokenResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `accessToken` | string |  |
| `accessTokenExpiresInSeconds` | integer (int64) |  |
| `refreshToken` | string |  |

### ReportPeriodSelector

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `evaluationType` | string |  |
| `label` | string |  |

### ReportTemplateFieldMappingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dataPath` | string |  |
| `description` | string |  |
| `fieldType` | string |  |
| `id` | integer (int64) |  |
| `placeholderKey` | string |  |

### ReportTemplateResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `active` | boolean |  |
| `createdBy` | integer (int64) |  |
| `description` | string |  |
| `fieldMappings` | mảng [ReportTemplateFieldMappingResponse](#reporttemplatefieldmappingresponse) |  |
| `fileFormat` | string |  |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `originalFilename` | string |  |
| `placeholderKeys` | mảng string |  |
| `templateType` | string |  |

### ReportVideoProgressRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `watchSessionId` | integer (int64) | ✔ |
| `watchedSeconds` | integer | ✔ |

### RescheduleClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRoomOverlap` | boolean |  |
| `allowTeacherOverlap` | boolean |  |
| `newDayPart` | string | ✔ |
| `newPeriodNumbers` | mảng integer | ✔ |
| `newRoomId` | integer (int64) |  |
| `newSessionDate` | string (date) | ✔ |
| `reason` | string |  |

### ResolveFeedbackRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `resolutionNotes` | string | ✔ |

### Review

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentId` | integer (int64) |  |
| `issues` | mảng [Issue](#issue) |  |
| `notices` | mảng [Notice](#notice) |  |
| `studentFullName` | string |  |

### ReviewVideoAssignmentQuestionStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `questions` | mảng [QuestionRow](#questionrow) |  |

### ReviewVideoAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedBy` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `dueAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `status` | string |  |
| `targetStudentIds` | mảng integer (int64) |  |
| `uuid` | string (uuid) |  |

### ReviewVideoAssignmentStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `completedCount` | integer |  |
| `completionPercent` | integer |  |
| `dueAt` | string (date-time) |  |
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |
| `passRatePercent` | integer |  |
| `passedCount` | integer |  |
| `reviewVideoSetCode` | string |  |
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `status` | enum: ACTIVE \| CANCELLED \| COMPLETED |  |
| `teacherType` | enum: VIETNAMESE \| FOREIGN |  |
| `totalStudents` | integer |  |
| `videoType` | enum: CONNECTION \| REFLEX |  |

### ReviewVideoAssignmentStudentStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignment` | [ReviewVideoAssignmentStatsResponse](#reviewvideoassignmentstatsresponse) |  |
| `students` | mảng [StudentRow](#studentrow) |  |

### ReviewVideoCatalogImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### ReviewVideoConnectionAnswerHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `sessions` | mảng [SessionAnswers](#sessionanswers) |  |

### ReviewVideoConnectionChoiceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string |  |
| `content` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `isCorrect` | boolean |  |

### ReviewVideoConnectionQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choices` | mảng [ReviewVideoConnectionChoiceResponse](#reviewvideoconnectionchoiceresponse) |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `prompt` | string |  |
| `reviewVideoId` | integer (int64) |  |

### ReviewVideoConnectionQuizResultResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptsUsed` | integer |  |
| `finalized` | boolean |  |
| `maxAttempts` | integer |  |
| `passed` | boolean |  |
| `progress` | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |  |
| `results` | mảng [ConnectionAnswerResult](#connectionanswerresult) |  |

### ReviewVideoProgressResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completed` | boolean |  |
| `durationSeconds` | integer |  |
| `requiredViewCount` | integer |  |
| `reviewVideoId` | integer (int64) |  |
| `viewCount` | integer |  |
| `watchedPercent` | integer |  |
| `watchedSeconds` | integer |  |

### ReviewVideoQuestionImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `createdQuestions` | mảng [ImportedQuestion](#importedquestion) |  |
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `jobId` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### ReviewVideoQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `maxAttempts` | integer |  |
| `maxRecordingSeconds` | integer |  |
| `pictureBrief` | string |  |
| `pictureImageUrl` | string |  |
| `prompt` | string |  |
| `questionFormat` | string |  |
| `reviewVideoId` | integer (int64) |  |
| `timestampSeconds` | integer |  |

### ReviewVideoResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completionThresholdPercent` | integer |  |
| `displayOrder` | integer |  |
| `durationSeconds` | integer |  |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `requiredViewCount` | integer |  |
| `reviewVideoSetId` | integer (int64) |  |
| `sessionPassRatioThresholdPercent` | integer |  |
| `sourceType` | string |  |
| `title` | string |  |

### ReviewVideoSetResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumCode` | string |  |
| `curriculumId` | integer (int64) |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `publishedAt` | string (date-time) |  |
| `status` | string |  |
| `subTopicId` | integer (int64) |  |
| `subTopicTitle` | string |  |
| `subjectId` | integer (int64) |  |
| `teacherType` | string |  |
| `title` | string |  |
| `unitTitle` | string |  |
| `uuid` | string (uuid) |  |
| `videoType` | string |  |

### ReviewVideoSetStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `cells` | mảng [StatsCell](#statscell) |  |
| `videos` | mảng [VideoHeader](#videoheader) |  |

### ReviewVideoSubmissionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptNumber` | integer |  |
| `audioUrl` | string |  |
| `feedback` | string |  |
| `gradedAt` | string (date-time) |  |
| `gradedByUserId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `questionPrompt` | string |  |
| `reviewVideoDisplayOrder` | integer |  |
| `reviewVideoId` | integer (int64) |  |
| `reviewVideoQuestionId` | integer (int64) |  |
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `reviewVideoTitle` | string |  |
| `score` | number |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `timestampSeconds` | integer |  |

### ReviseCommentAiDraftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `currentRows` | mảng [CurrentRow](#currentrow) | ✔ |
| `extraction` | [Extraction](#extraction) |  |
| `history` | mảng [ChatTurn](#chatturn) |  |
| `homeworkScores` | mảng [HomeworkScoreInput](#homeworkscoreinput) |  |
| `instruction` | string |  |
| `mode` | enum: INSTRUCTION \| REWRITE_ALL | ✔ |
| `transcript` | string |  |

### ReviseTermCommentAiDraftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `currentRows` | mảng [CurrentRow](#currentrow) | ✔ |
| `history` | mảng [ChatTurn](#chatturn) |  |
| `mode` | enum: INSTRUCTION \| REWRITE_ALL | ✔ |

### RolePermissionMatrixResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `permissions` | mảng [PermissionMatrixItem](#permissionmatrixitem) |  |
| `roleCode` | string |  |
| `roleId` | integer (int64) |  |

### RoleResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `dataScope` | string |  |
| `description` | string |  |
| `id` | integer (int64) |  |
| `isSystem` | boolean |  |
| `name` | string |  |

### RoomResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `capacity` | integer |  |
| `code` | string |  |
| `flexible` | boolean |  |
| `id` | integer (int64) |  |
| `managedByCenter` | boolean |  |
| `name` | string |  |
| `notes` | string |  |
| `roomType` | string |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |

### Row

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string |  |
| `existingComment` | string |  |
| `scoreSummary` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `warnings` | mảng [Warning](#warning) |  |

### SaveAnswerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `audioAnswerUrl` | string |  |
| `questionId` | integer (int64) | ✔ |
| `selectedChoiceIds` | mảng integer (int64) |  |
| `structuredAnswer` | mảng string |  |

### SaveDraftCommentsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentDate` | string (date) | ✔ |
| `rows` | mảng [Row](#row) | ✔ |

### SaveDraftCommentsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `saved` | mảng [StudentCommentResponse](#studentcommentresponse) |  |
| `skipped` | mảng [SkippedRow](#skippedrow) |  |

### ScholarshipResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `applicableScope` | string |  |
| `approvedAt` | string (date-time) |  |
| `approvedBy` | integer (int64) |  |
| `code` | string |  |
| `discountType` | string |  |
| `discountValue` | number |  |
| `id` | integer (int64) |  |
| `maxAmount` | number |  |
| `name` | string |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `validFrom` | string (date) |  |
| `validTo` | string (date) |  |

### SendNotificationFailure

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `recipientUserId` | integer (int64) |  |

### SendNotificationRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `channels` | mảng enum: IN_APP \| EMAIL \| SMS \| ZALO \| PUSH |  |
| `content` | string | ✔ |
| `notificationType` | enum: ATTENDANCE_ABSENT \| ATTENDANCE_PRESENT \| ATTENDANCE_LATE \| ATTENDANCE_EXCUSED \| ATTENDANCE_EARLY_LEAVE \| TASK_ASSIGNED \| ... | ✔ |
| `recipientUserIds` | mảng integer (int64) | ✔ |
| `title` | string | ✔ |

### SendNotificationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `channelResults` | mảng [ChannelResult](#channelresult) |  |
| `failures` | mảng [SendNotificationFailure](#sendnotificationfailure) |  |
| `succeeded` | integer |  |
| `totalRecipients` | integer |  |

### SessionAnswers

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answers` | mảng [AnsweredQuestion](#answeredquestion) |  |
| `viewNumber` | integer |  |
| `watchSessionId` | integer (int64) |  |

### SessionPeriodResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `contentNote` | string |  |
| `dayPart` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `periodNumber` | integer |  |
| `startTime` | [LocalTime](#localtime) |  |
| `subjectId` | integer (int64) |  |
| `teacherId` | integer (int64) |  |

### SessionReportApproverSummary

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approverName` | string |  |
| `approverUserId` | integer (int64) |  |
| `averageWaitMinutes` | integer (int64) |  |
| `decidedSessionCount` | integer |  |
| `lateSessionCount` | integer |  |
| `rejectedSessionCount` | integer |  |

### SessionReportStatusRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvalLateMinutes` | integer (int64) |  |
| `approvalState` | string |  |
| `approvedCount` | integer |  |
| `approverNames` | mảng string |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `commentCount` | integer |  |
| `endTime` | [LocalTime](#localtime) |  |
| `firstSubmittedAt` | string (date-time) |  |
| `fullyApprovedAt` | string (date-time) |  |
| `openApprovalSince` | string (date-time) |  |
| `openRejectionSince` | string (date-time) |  |
| `pendingCount` | integer |  |
| `rejectedCount` | integer |  |
| `rejectionCount` | integer |  |
| `resubmitLateMinutes` | integer (int64) |  |
| `resubmitState` | string |  |
| `sessionDate` | string (date) |  |
| `sessionId` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startTime` | [LocalTime](#localtime) |  |
| `submitDeadline` | string (date-time) |  |
| `submitLateMinutes` | integer (int64) |  |
| `submitState` | string |  |
| `teacherName` | string |  |
| `teacherUserId` | integer (int64) |  |

### SessionReportTeacherSummary

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `lateCount` | integer |  |
| `missingCount` | integer |  |
| `onTimeCount` | integer |  |
| `onTimeRate` | number |  |
| `rejectionCount` | integer |  |
| `resubmitLateCount` | integer |  |
| `sessionCount` | integer |  |
| `teacherName` | string |  |
| `teacherUserId` | integer (int64) |  |

### SessionReportTimelineEvent

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actorName` | string |  |
| `actorUserId` | integer (int64) |  |
| `at` | string (date-time) |  |
| `commentCount` | integer |  |
| `deadline` | string (date-time) |  |
| `lateMinutes` | integer (int64) |  |
| `reason` | string |  |
| `timeliness` | string |  |
| `type` | string |  |

### SessionReportTrackingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvalDeadlineHours` | integer |  |
| `approvers` | mảng [SessionReportApproverSummary](#sessionreportapproversummary) |  |
| `fromDate` | string (date) |  |
| `resubmitDeadlineHours` | integer |  |
| `sessions` | mảng [SessionReportStatusRow](#sessionreportstatusrow) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `submitDeadlineHours` | integer |  |
| `teachers` | mảng [SessionReportTeacherSummary](#sessionreportteachersummary) |  |
| `toDate` | string (date) |  |

### ShiftResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `active` | boolean |  |
| `appliesToWeekdays` | string |  |
| `checkInTime` | [LocalTime](#localtime) |  |
| `checkInWindowAfterMinutes` | integer |  |
| `checkInWindowBeforeMinutes` | integer |  |
| `checkOutTime` | [LocalTime](#localtime) |  |
| `checkOutWindowAfterMinutes` | integer |  |
| `checkOutWindowBeforeMinutes` | integer |  |
| `code` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `weekParity` | string |  |

### SitePeriodTemplateResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dayPart` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `label` | string |  |
| `periodNumber` | integer |  |
| `siteId` | integer (int64) |  |
| `startTime` | [LocalTime](#localtime) |  |

### SiteResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `code` | string |  |
| `currentManagerFullName` | string |  |
| `currentManagerUserId` | integer (int64) |  |
| `district` | string |  |
| `id` | integer (int64) |  |
| `latitude` | number |  |
| `longitude` | number |  |
| `name` | string |  |
| `partnerInfo` | [PartnerSchoolInfoResponse](#partnerschoolinforesponse) |  |
| `phone` | string |  |
| `siteType` | string |  |
| `status` | string |  |
| `usedForAttendance` | boolean |  |
| `usedForClasses` | boolean |  |

### SiteTeacherResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedFrom` | string (date) |  |
| `assignedTo` | string (date) |  |
| `id` | integer (int64) |  |
| `notes` | string |  |
| `siteId` | integer (int64) |  |
| `teacherFullName` | string |  |
| `teacherUserId` | integer (int64) |  |

### SkillResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `active` | boolean |  |
| `code` | string |  |
| `description` | string |  |
| `id` | integer (int64) |  |
| `name` | string |  |

### SkippedRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `studentId` | integer (int64) |  |

### SkippedStudent

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### SkippedStudentInfo

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### SortObject

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `ascending` | boolean |  |
| `direction` | string |  |
| `ignoreCase` | boolean |  |
| `nullHandling` | string |  |
| `property` | string |  |

### SpeakingAiGradingTestResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `contentFeedback` | string |  |
| `contentScorePercent` | integer |  |
| `grammarFeedback` | string |  |
| `grammarScorePercent` | integer |  |
| `transcript` | string |  |

### SseEmitter

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `timeout` | integer (int64) |  |

### StartWatchSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `sessionId` | integer (int64) |  |

### StatsCell

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completed` | boolean |  |
| `studentId` | integer (int64) |  |
| `videoId` | integer (int64) |  |
| `viewCount` | integer |  |
| `watchedPercent` | integer |  |
| `watchedSeconds` | integer |  |

### StudentAnswerGradingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `feedback` | string |  |
| `gradedAt` | string (date-time) |  |
| `graderUserId` | integer (int64) |  |
| `gradingSource` | string |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `score` | number |  |
| `studentAnswerId` | integer (int64) |  |

### StudentAnswerResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `audioAnswerUrl` | string |  |
| `autoScore` | number |  |
| `carriedOverFromPreviousAttempt` | boolean |  |
| `correctAnswerText` | string |  |
| `correctChoiceIds` | mảng integer (int64) |  |
| `correctStructuredContent` | object |  |
| `exerciseAttemptId` | integer (int64) |  |
| `explanation` | string |  |
| `gradingCriteriaScores` | mảng [CriteriaScoreItem](#criteriascoreitem) |  |
| `gradingFeedback` | string |  |
| `gradingKeyGrammar` | [KeyGrammarOutcome](#keygrammaroutcome) |  |
| `gradingMarkedAnswer` | string |  |
| `gradingMaxScore` | number |  |
| `gradingScore` | number |  |
| `gradingSource` | string |  |
| `id` | integer (int64) |  |
| `isAutoGradable` | boolean |  |
| `isCorrect` | boolean |  |
| `questionId` | integer (int64) |  |
| `selectedChoiceIds` | mảng integer (int64) |  |
| `structuredAnswer` | mảng string |  |

### StudentAttendanceGracePeriodResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `gracePeriodMinutes` | integer |  |

### StudentAttitudeEscalationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `className` | string |  |
| `createdAt` | string (date-time) |  |
| `decidedAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `rejectionReason` | string |  |
| `schoolClassId` | integer (int64) |  |
| `status` | string |  |
| `streakCount` | integer |  |
| `studentId` | integer (int64) |  |
| `studentName` | string |  |

### StudentBatchImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `generatedCredentials` | mảng object |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### StudentCommentHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `action` | string |  |
| `changedByName` | string |  |
| `changedByUserId` | integer (int64) |  |
| `createdAt` | string (date-time) |  |
| `details` | object |  |
| `id` | integer (int64) |  |
| `studentCommentId` | integer (int64) |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

### StudentCommentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `academicYearId` | integer (int64) |  |
| `approvedAt` | string (date-time) |  |
| `approvedBy` | integer (int64) |  |
| `attitude` | string |  |
| `classId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) |  |
| `commentType` | string |  |
| `content` | string |  |
| `grammarPreviousProgress` | string |  |
| `homeworkNext` | string |  |
| `homeworkNextDueAt` | string (date-time) |  |
| `homeworkNextExerciseAssignmentId` | integer (int64) |  |
| `homeworkNextExerciseTitle` | string |  |
| `homeworkNextLateSubmissionAllowed` | boolean |  |
| `homeworkNextReading` | string |  |
| `homeworkNextReadingExerciseAssignmentId` | integer (int64) |  |
| `homeworkNextReadingExerciseTitle` | string |  |
| `homeworkNextReviewVideoAssignmentId` | integer (int64) |  |
| `homeworkNextReviewVideoSetTitle` | string |  |
| `homeworkNextWriting` | string |  |
| `homeworkNextWritingExerciseAssignmentId` | integer (int64) |  |
| `homeworkNextWritingExerciseTitle` | string |  |
| `homeworkPreviousOfflineText` | string |  |
| `homeworkPreviousReadingScore` | string |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
| `homeworkPreviousWritingScore` | string |  |
| `id` | integer (int64) |  |
| `isWarning` | boolean |  |
| `lessonContent` | string |  |
| `note` | string |  |
| `pendingHomeworkNextDueDate` | string (date-time) |  |
| `pendingHomeworkNextExerciseId` | integer (int64) |  |
| `pendingHomeworkNextExerciseTitle` | string |  |
| `pendingHomeworkNextReadingExerciseId` | integer (int64) |  |
| `pendingHomeworkNextReadingExerciseTitle` | string |  |
| `pendingHomeworkNextReviewVideoSetId` | integer (int64) |  |
| `pendingHomeworkNextReviewVideoSetTitle` | string |  |
| `pendingHomeworkNextWritingExerciseId` | integer (int64) |  |
| `pendingHomeworkNextWritingExerciseTitle` | string |  |
| `readingPreviousProgress` | string |  |
| `rejectionReason` | string |  |
| `severity` | string |  |
| `status` | string |  |
| `structuredContent` | object |  |
| `studentDateOfBirth` | string (date) |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `teacherId` | integer (int64) |  |
| `videoPreviousProgress` | string |  |
| `visibleToParentAt` | string (date-time) |  |
| `writingPreviousProgress` | string |  |

### StudentProfileAttendanceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermName` | string |  |
| `academicYear` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `classSessionId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `minutesEarlyLeave` | integer |  |
| `minutesLate` | integer |  |
| `sessionDate` | string (date) |  |
| `sessionNumber` | integer |  |
| `status` | string |  |

### StudentProfileCommentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `academicTermName` | string |  |
| `academicYear` | string |  |
| `attitude` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) |  |
| `commentType` | string |  |
| `content` | string |  |
| `id` | integer (int64) |  |
| `isWarning` | boolean |  |
| `note` | string |  |
| `sessionDate` | string (date) |  |
| `sessionNumber` | integer |  |
| `severity` | string |  |
| `status` | string |  |

### StudentProfileEnrollmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `enrolledDate` | string (date) |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `withdrawnDate` | string (date) |  |

### StudentProfileGradeResultResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `academicTermName` | string |  |
| `academicYear` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `comment` | string |  |
| `evaluationType` | string |  |
| `id` | integer (int64) |  |
| `level` | string |  |
| `note` | string |  |
| `overallScore` | number |  |
| `scaleType` | string |  |
| `status` | string |  |

### StudentProfileHomeworkResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attemptId` | integer (int64) |  |
| `attemptNumber` | integer |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `dueAt` | string (date-time) |  |
| `exerciseAssignmentId` | integer (int64) |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTitle` | string |  |
| `passed` | boolean |  |
| `percentage` | number |  |
| `startedAt` | string (date-time) |  |
| `status` | string |  |
| `submittedAt` | string (date-time) |  |
| `totalPoints` | number |  |
| `totalScore` | number |  |

### StudentProfileResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attendance` | mảng [StudentProfileAttendanceResponse](#studentprofileattendanceresponse) |  |
| `comments` | mảng [StudentProfileCommentResponse](#studentprofilecommentresponse) |  |
| `enrollments` | mảng [StudentProfileEnrollmentResponse](#studentprofileenrollmentresponse) |  |
| `gradeResults` | mảng [StudentProfileGradeResultResponse](#studentprofilegraderesultresponse) |  |
| `homeworkResults` | mảng [StudentProfileHomeworkResponse](#studentprofilehomeworkresponse) |  |
| `skillScores` | mảng [StudentProfileSkillScoreResponse](#studentprofileskillscoreresponse) |  |
| `student` | [StudentProfileStudentResponse](#studentprofilestudentresponse) |  |

### StudentProfileSkillScoreResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `academicTermName` | string |  |
| `academicYear` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `evaluationType` | string |  |
| `maxScore` | number |  |
| `score` | number |  |
| `skillCode` | string |  |
| `skillName` | string |  |

### StudentProfileStudentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dateOfBirth` | string (date) |  |
| `enrollmentDate` | string (date) |  |
| `fullName` | string |  |
| `gender` | string |  |
| `id` | integer (int64) |  |
| `portraitUrl` | string |  |
| `primarySiteId` | integer (int64) |  |
| `primarySiteName` | string |  |
| `status` | string |  |
| `studentCode` | string |  |

### StudentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dateOfBirth` | string (date) |  |
| `enrollmentDate` | string (date) |  |
| `fullName` | string |  |
| `gender` | string |  |
| `graduationDate` | string (date) |  |
| `id` | integer (int64) |  |
| `notes` | string |  |
| `originalClass` | string |  |
| `originalSchool` | string |  |
| `portraitUrl` | string |  |
| `primarySiteId` | integer (int64) |  |
| `primarySiteName` | string |  |
| `status` | string |  |
| `studentCode` | string |  |
| `userId` | integer (int64) |  |

### StudentRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answeredQuestionCount` | integer |  |
| `averageMaxScore` | number |  |
| `averageScore` | number |  |
| `completed` | boolean |  |
| `correctCount` | integer |  |
| `grammarReviewRequired` | boolean |  |
| `lateSubmission` | boolean |  |
| `passed` | boolean |  |
| `requiredViewCount` | integer |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `totalQuestions` | integer |  |
| `totalReflexQuestions` | integer |  |
| `viewCount` | integer |  |

### StudentStatusHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `changedAt` | string (date-time) |  |
| `changedBy` | integer (int64) |  |
| `effectiveDate` | string (date) |  |
| `id` | integer (int64) |  |
| `newStatus` | string |  |
| `oldStatus` | string |  |
| `reason` | string |  |
| `studentId` | integer (int64) |  |

### StudentTransferHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvedBy` | integer (int64) |  |
| `effectiveDate` | string (date) |  |
| `fromClassId` | integer (int64) |  |
| `fromSiteId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `reason` | string |  |
| `studentId` | integer (int64) |  |
| `toClassId` | integer (int64) |  |
| `toSiteId` | integer (int64) |  |
| `transferType` | string |  |

### SubTopicResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `title` | string |  |
| `unitId` | integer (int64) |  |

### SubmitCommentsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentIds` | mảng integer (int64) | ✔ |

### SubmitConnectionAnswersRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answers` | mảng [ConnectionAnswerItem](#connectionansweritem) | ✔ |

### SubmitGradesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `gradeEntryIds` | mảng integer (int64) |  |
| `gradeEvaluationResultIds` | mảng integer (int64) |  |

### SubmitListeningPracticeAttemptRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioAnswerUrl` | string |  |
| `dictationAnswerText` | string |  |

### SubmitPartnerFeedbackRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string | ✔ |
| `feedbackType` | string | ✔ |
| `priority` | string |  |

### SubmitReflexSpokenAnswerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string | ✔ |
| `recordingFilter` | boolean |  |

### SubmitReflexWrittenAnswerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string | ✔ |

### SubmitReviewVideoAudioRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string | ✔ |
| `integrityEvents` | [RecordIntegrityEventsRequest](#recordintegrityeventsrequest) |  |

### SubstituteAssignmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) | ✔ |
| `substituteTeacherId` | integer (int64) | ✔ |

### Summary

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `cleanCount` | integer |  |
| `escalationCount` | integer |  |
| `homeworkMismatchCount` | integer |  |
| `issueCounts` | mảng [IssueCount](#issuecount) |  |
| `parentAlertCount` | integer |  |
| `repeatedPatternCount` | integer |  |

### SystemSettingHistoryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `changedByName` | string |  |
| `createdAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `newValue` | [JsonNode](#jsonnode) |  |
| `oldValue` | [JsonNode](#jsonnode) |  |

### SystemSettingResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `category` | string |  |
| `description` | string |  |
| `id` | integer (int64) |  |
| `settingKey` | string |  |
| `settingValue` | [JsonNode](#jsonnode) |  |
| `updatedAt` | string (date-time) |  |
| `updatedByName` | string |  |

### SystemSettingUpdateRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `settingValue` | [JsonNode](#jsonnode) | ✔ |

### TaskAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedAt` | string (date-time) |  |
| `assigneeFullName` | string |  |
| `assigneeUserId` | integer (int64) |  |
| `assignmentStatus` | string |  |
| `completedAt` | string (date-time) |  |
| `declineReason` | string |  |
| `id` | integer (int64) |  |
| `progressPercent` | number |  |
| `startedAt` | string (date-time) |  |
| `taskId` | integer (int64) |  |
| `taskTitle` | string |  |

### TaskAttachmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `fileName` | string |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `taskId` | integer (int64) |  |
| `uploadedBy` | integer (int64) |  |

### TaskCancelledRetentionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### TaskCommentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attachmentUrl` | string |  |
| `commenterFullName` | string |  |
| `commenterUserId` | integer (int64) |  |
| `content` | string |  |
| `createdAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `taskId` | integer (int64) |  |

### TaskResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completedAt` | string (date-time) |  |
| `createdBy` | integer (int64) |  |
| `createdByFullName` | string |  |
| `departmentId` | integer (int64) |  |
| `description` | string |  |
| `dueAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `parentTaskId` | integer (int64) |  |
| `priority` | string |  |
| `status` | string |  |
| `tags` | mảng string |  |
| `taskCode` | string |  |
| `taskType` | string |  |
| `title` | string |  |

### TeacherClassAssignmentItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedFrom` | string (date) |  |
| `classCode` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `classStatus` | string |  |
| `siteName` | string |  |
| `teacherRole` | string |  |
| `teacherType` | string |  |

### TeacherCommendationItem

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `id` | integer (int64) |  |
| `recordDate` | string (date) |  |
| `recordType` | string |  |
| `title` | string |  |

### TeacherLookupResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `email` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `username` | string |  |

### TeacherProfileDetailResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classes` | mảng [TeacherClassAssignmentItem](#teacherclassassignmentitem) |  |
| `commendations` | mảng [TeacherCommendationItem](#teachercommendationitem) |  |
| `profile` | [TeacherProfileSummaryResponse](#teacherprofilesummaryresponse) |  |
| `qualifications` | mảng [QualificationResponse](#qualificationresponse) |  |

### TeacherProfileSummaryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `activeClassCount` | integer (int64) |  |
| `departmentName` | string |  |
| `email` | string |  |
| `employeeCode` | string |  |
| `employeeId` | integer (int64) |  |
| `fullName` | string |  |
| `hireDate` | string (date) |  |
| `phone` | string |  |
| `portraitUrl` | string |  |
| `positionName` | string |  |
| `siteNames` | mảng string |  |
| `status` | string |  |
| `userId` | integer (int64) |  |

### TeacherTeachingStatsRow

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assistantPeriods` | integer (int64) |  |
| `cancelledSessions` | integer (int64) |  |
| `classCount` | integer (int64) |  |
| `cmPeriods` | integer (int64) |  |
| `employeeCode` | string |  |
| `heldSessions` | integer (int64) |  |
| `lateCheckIns` | integer (int64) |  |
| `missingCheckIns` | integer (int64) |  |
| `onTimeCheckIns` | integer (int64) |  |
| `onTimeRate` | number |  |
| `reportLateCount` | integer |  |
| `reportMissingCount` | integer |  |
| `reportOnTimeCount` | integer |  |
| `roles` | mảng string |  |
| `scheduledSessions` | integer (int64) |  |
| `taughtPeriods` | integer (int64) |  |
| `teacherName` | string |  |
| `teacherUserId` | integer (int64) |  |
| `totalPeriods` | integer (int64) |  |

### TeachingPlanItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `contentOutline` | string |  |
| `homeworkNote` | string |  |
| `id` | integer (int64) |  |
| `itemOrder` | integer |  |
| `objectives` | string |  |
| `plannedDate` | string (date) |  |
| `skillsFocus` | string |  |
| `teachingPlanId` | integer (int64) |  |
| `topic` | string |  |

### TeachingPlanResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `academicYearId` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `objectives` | string |  |
| `planType` | string |  |
| `publishedAt` | string (date-time) |  |
| `status` | string |  |
| `summary` | string |  |
| `teacherId` | integer (int64) |  |
| `visibleToPartner` | boolean |  |
| `weekEndDate` | string (date) |  |
| `weekNumber` | integer |  |
| `weekStartDate` | string (date) |  |

### TeachingStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `fromDate` | string (date) |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `teachers` | mảng [TeacherTeachingStatsRow](#teacherteachingstatsrow) |  |
| `toDate` | string (date) |  |
| `totals` | [TeacherTeachingStatsRow](#teacherteachingstatsrow) |  |

### TermCommentAiDraftJobResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorMessage` | string |  |
| `jobId` | string |  |
| `result` | [TermCommentAiDraftResult](#termcommentaidraftresult) |  |
| `status` | string |  |

### TermCommentAiDraftResult

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assistantMessage` | string |  |
| `evaluationType` | string |  |
| `rows` | mảng [Row](#row) |  |
| `skippedStudents` | mảng [SkippedStudent](#skippedstudent) |  |
| `transcript` | string |  |

### Totals

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `cachedTokens` | integer (int64) |  |
| `callCount` | integer (int64) |  |
| `completionTokens` | integer (int64) |  |
| `promptTokens` | integer (int64) |  |
| `reasoningTokens` | integer (int64) |  |
| `rejectedCalls` | integer (int64) |  |
| `unattributedCalls` | integer (int64) |  |

### TuitionPlanAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `className` | string |  |
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `id` | integer (int64) |  |
| `overrideReason` | string |  |
| `priceOverride` | number |  |
| `tuitionPlanCode` | string |  |
| `tuitionPlanId` | integer (int64) |  |
| `tuitionPlanName` | string |  |

### TuitionPlanResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `basePrice` | number |  |
| `classTypeFilter` | string |  |
| `code` | string |  |
| `currency` | string |  |
| `curriculumId` | integer (int64) |  |
| `curriculumName` | string |  |
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `pricePerUnit` | number |  |
| `pricingModel` | string |  |
| `status` | string |  |
| `unitCount` | integer |  |

### UnitResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bookId` | integer (int64) |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `title` | string |  |

### UnmatchedMention

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `candidateStudentIds` | mảng integer (int64) |  |
| `quote` | string |  |

### UpdateAcademicTermRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) | ✔ |
| `endDate` | string (date) | ✔ |
| `name` | string | ✔ |
| `startDate` | string (date) | ✔ |

### UpdateAcademicYearRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endDate` | string (date) |  |
| `name` | string | ✔ |
| `startDate` | string (date) |  |
| `status` | string | ✔ |

### UpdateActualTeacherNameRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string | ✔ |

### UpdateAssignmentStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `status` | string | ✔ |

### UpdateBookRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### UpdateClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) |  |
| `color` | string |  |
| `endDate` | string (date) |  |
| `maxStudents` | integer | ✔ |
| `minStudents` | integer |  |
| `name` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `status` | string | ✔ |

### UpdateCommentEditWindowRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### UpdateConnectionChoiceRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceId` | integer (int64) | ✔ |
| `content` | string | ✔ |
| `isCorrect` | boolean |  |

### UpdateCurriculumDocumentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `coverImageUrl` | string |  |
| `description` | string |  |
| `displayOrder` | integer |  |
| `status` | string | ✔ |
| `title` | string | ✔ |

### UpdateCurriculumRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `confirm` | boolean |  |
| `defaultGradePassThreshold` | number |  |
| `gradeLevel` | string |  |
| `level` | string |  |
| `name` | string | ✔ |
| `status` | string | ✔ |
| `totalPeriods` | integer |  |
| `track` | string |  |

### UpdateCustomCurriculumRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `defaultGradePassThreshold` | number |  |
| `level` | string |  |
| `name` | string | ✔ |
| `totalPeriods` | integer |  |

### UpdateDepartmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `headUserId` | integer (int64) |  |
| `name` | string | ✔ |
| `parentDepartmentId` | integer (int64) |  |

### UpdateEmployeeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bankAccountNumber` | string |  |
| `bankName` | string |  |
| `currentAddress` | string |  |
| `dateOfBirth` | string (date) | ✔ |
| `departmentId` | integer (int64) |  |
| `employeeType` | string | ✔ |
| `idCardIssuedDate` | string (date) |  |
| `idCardIssuedPlace` | string |  |
| `idCardNumber` | string |  |
| `isDefaultShiftRequired` | boolean |  |
| `isManagement` | boolean | ✔ |
| `permanentAddress` | string |  |
| `portraitUrl` | string |  |
| `positionId` | integer (int64) |  |
| `socialInsuranceNumber` | string |  |
| `status` | string | ✔ |
| `taxCode` | string |  |
| `terminationDate` | string (date) |  |

### UpdateEmploymentContractRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `baseSalary` | number | ✔ |
| `contractType` | string | ✔ |
| `endDate` | string (date) |  |
| `fileUrl` | string |  |
| `salaryType` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `status` | string | ✔ |

### UpdateEntranceAssessmentComponentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `maxScore` | number | ✔ |
| `name` | string | ✔ |
| `skillId` | integer (int64) |  |

### UpdateEntranceAssessmentSetupRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `name` | string | ✔ |
| `scaleType` | string | ✔ |

### UpdateEquipmentStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `notes` | string |  |
| `status` | string | ✔ |

### UpdateExamRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `examType` | string | ✔ |
| `subTopicId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |

### UpdateExerciseQuestionPointsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `points` | number | ✔ |

### UpdateExerciseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `maxAttempts` | integer |  |
| `passThresholdPercent` | number |  |
| `showCorrectAnswers` | boolean |  |
| `skillCategory` | string |  |
| `subjectId` | integer (int64) |  |
| `title` | string | ✔ |
| `totalPoints` | number | ✔ |

### UpdateFieldMappingsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `mappings` | mảng [FieldMappingItemRequest](#fieldmappingitemrequest) | ✔ |

### UpdateGradeComponentSetupRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `commentRequired` | boolean |  |
| `rosterAsOfDate` | string (date) | ✔ |

### UpdateGradeEditWindowRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### UpdateGradeEvaluationComponentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `maxScore` | number |  |
| `name` | string | ✔ |
| `passThreshold` | number |  |

### UpdateLateSubmissionAllowedRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `lateSubmissionAllowed` | boolean |  |
| `lateSubmissionDeadline` | string (date-time) |  |

### UpdateLeadStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `finalNote` | string |  |
| `outcome` | string |  |
| `status` | string | ✔ |

### UpdateLessonContentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `lessonContent` | string | ✔ |

### UpdateListeningPracticeItemRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `difficulty` | string |  |
| `displayOrder` | integer |  |
| `scriptText` | string | ✔ |
| `status` | string | ✔ |
| `title` | string | ✔ |

### UpdateOwnEmployeeProfileRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `currentAddress` | string |  |
| `permanentAddress` | string |  |
| `portraitUrl` | string |  |

### UpdateOwnParentProfileRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `occupation` | string |  |
| `portraitUrl` | string |  |
| `workplace` | string |  |

### UpdateOwnStudentProfileRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `portraitUrl` | string |  |

### UpdateParentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `notes` | string |  |
| `occupation` | string |  |
| `portraitUrl` | string |  |
| `workplace` | string |  |

### UpdatePartnerContractRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endDate` | string (date) | ✔ |
| `fileUrl` | string |  |
| `revenueShareNotes` | string |  |
| `signedAt` | string (date) |  |
| `signedByCenter` | string |  |
| `signedByPartner` | string |  |
| `status` | string | ✔ |
| `termsSummary` | string |  |

### UpdatePeriodMarkRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `note` | string |  |
| `status` | string | ✔ |

### UpdatePermissionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `description` | string |  |
| `name` | string | ✔ |

### UpdatePositionDefaultRolesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `roleIds` | mảng integer (int64) |  |

### UpdatePositionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `name` | string | ✔ |

### UpdateQuestionBankStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `isActive` | boolean | ✔ |

### UpdateQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [QuestionChoiceRequest](#questionchoicerequest) |  |
| `content` | string | ✔ |
| `correctAnswerText` | string |  |
| `defaultPoints` | number |  |
| `explanation` | string |  |
| `imageUrl` | string |  |
| `keyGrammarIds` | mảng string |  |
| `referencePassage` | string |  |
| `status` | string |  |
| `structuredContent` | object |  |
| `tags` | mảng string |  |

### UpdateReportTemplateRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `description` | string |  |
| `name` | string | ✔ |

### UpdateReviewVideoConnectionQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choices` | mảng [UpdateConnectionChoiceRequest](#updateconnectionchoicerequest) | ✔ |
| `displayOrder` | integer |  |
| `prompt` | string | ✔ |

### UpdateReviewVideoQuestionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `maxAttempts` | integer |  |
| `maxRecordingSeconds` | integer | ✔ |
| `pictureBrief` | string |  |
| `pictureImageUrl` | string |  |
| `prompt` | string |  |
| `questionFormat` | string |  |
| `timestampSeconds` | integer | ✔ |

### UpdateReviewVideoSetRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `status` | string | ✔ |
| `subTopicId` | integer (int64) |  |
| `subjectId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |

### UpdateReviewVideoThresholdsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `completionThresholdPercent` | integer | ✔ |
| `requiredViewCount` | integer | ✔ |
| `sessionPassRatioThresholdPercent` | integer | ✔ |

### UpdateRoleDataScopeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dataScope` | enum: ALL \| SITE \| CLASS \| SELF | ✔ |

### UpdateRolePermissionsRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `confirm` | boolean |  |
| `permissionIds` | mảng integer (int64) | ✔ |

### UpdateRoomRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `capacity` | integer |  |
| `flexible` | boolean |  |
| `managedByCenter` | boolean |  |
| `name` | string |  |
| `notes` | string |  |
| `status` | string | ✔ |

### UpdateSessionAssignmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `allowRoomOverlap` | boolean |  |
| `allowTeacherOverlap` | boolean |  |
| `assistantTeacherId` | integer (int64) |  |
| `cmTeacherId` | integer (int64) |  |
| `correctionReason` | string |  |
| `dayPart` | string | ✔ |
| `periodNumbers` | mảng integer | ✔ |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `teacherType` | string | ✔ |

### UpdateSessionTeacherTypeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `teacherType` | string | ✔ |

### UpdateShiftRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `appliesToWeekdays` | string |  |
| `checkInTime` | [LocalTime](#localtime) | ✔ |
| `checkInWindowAfterMinutes` | integer |  |
| `checkInWindowBeforeMinutes` | integer |  |
| `checkOutTime` | [LocalTime](#localtime) | ✔ |
| `checkOutWindowAfterMinutes` | integer |  |
| `checkOutWindowBeforeMinutes` | integer |  |
| `name` | string | ✔ |
| `weekParity` | string |  |

### UpdateSitePeriodTemplateRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endTime` | [LocalTime](#localtime) | ✔ |
| `label` | string |  |
| `startTime` | [LocalTime](#localtime) | ✔ |

### UpdateSiteRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `district` | string |  |
| `latitude` | number |  |
| `longitude` | number |  |
| `name` | string | ✔ |
| `partnerInfo` | [PartnerSchoolInfoRequest](#partnerschoolinforequest) |  |
| `phone` | string |  |
| `siteType` | string | ✔ |
| `status` | string |  |
| `usedForAttendance` | boolean |  |
| `usedForClasses` | boolean |  |

### UpdateSkillRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `active` | boolean | ✔ |
| `description` | string |  |
| `name` | string | ✔ |

### UpdateStudentCommentContentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string | ✔ |
| `structuredContent` | object |  |

### UpdateStudentCommentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attitude` | string |  |
| `content` | string |  |
| `homeworkNext` | string |  |
| `homeworkNextReading` | string |  |
| `homeworkNextWriting` | string |  |
| `homeworkPreviousReadingScore` | string |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
| `homeworkPreviousWritingScore` | string |  |
| `isWarning` | boolean |  |
| `note` | string |  |
| `severity` | string |  |
| `structuredContent` | object |  |

### UpdateStudentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `dateOfBirth` | string (date) | ✔ |
| `gender` | string |  |
| `notes` | string |  |
| `originalClass` | string |  |
| `originalSchool` | string |  |
| `portraitUrl` | string |  |

### UpdateStudentStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `effectiveDate` | string (date) | ✔ |
| `newStatus` | string | ✔ |
| `reason` | string |  |

### UpdateSubTopicRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### UpdateTaskCancelledRetentionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `days` | integer |  |

### UpdateTeachingPlanItemRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `contentOutline` | string |  |
| `homeworkNote` | string |  |
| `itemOrder` | integer |  |
| `objectives` | string |  |
| `plannedDate` | string (date) |  |
| `skillsFocus` | string |  |
| `topic` | string | ✔ |

### UpdateTeachingPlanRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `objectives` | string |  |
| `status` | string | ✔ |
| `summary` | string |  |
| `visibleToPartner` | boolean |  |

### UpdateTuitionPlanStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `status` | string | ✔ |

### UpdateUnitRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `title` | string | ✔ |

### UpdateUserEmailRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `newEmail` | string | ✔ |

### UpdateUserRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `fullName` | string | ✔ |
| `phone` | string |  |

### UpdateUserStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `status` | string | ✔ |

### UpsertEntranceAssessmentResultRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assessedDate` | string (date) | ✔ |
| `candidateName` | string | ✔ |
| `leadId` | integer (int64) |  |
| `note` | string |  |
| `overallScore` | number |  |
| `recommendedClassId` | integer (int64) |  |
| `recommendedLevel` | string |  |
| `scores` | mảng [EntranceScoreInput](#entrancescoreinput) |  |
| `studentId` | integer (int64) |  |

### UserDetailResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `departmentId` | integer (int64) |  |
| `email` | string |  |
| `failedLoginCount` | integer |  |
| `fullName` | string |  |
| `googleLinked` | boolean |  |
| `id` | integer (int64) |  |
| `isManagement` | boolean |  |
| `lastLoginAt` | string (date-time) |  |
| `lockedUntil` | string (date-time) |  |
| `passwordSet` | boolean |  |
| `permissionOverrides` | mảng [UserPermissionOverrideSummary](#userpermissionoverridesummary) |  |
| `phone` | string |  |
| `roles` | mảng [RoleResponse](#roleresponse) |  |
| `status` | string |  |
| `username` | string |  |

### UserListItemResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `departmentId` | integer (int64) |  |
| `email` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `isManagement` | boolean |  |
| `phone` | string |  |
| `roles` | mảng [RoleResponse](#roleresponse) |  |
| `status` | string |  |
| `username` | string |  |

### UserPermissionOverrideRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `expiresAt` | string (date-time) |  |
| `overrideType` | string | ✔ |
| `reason` | string | ✔ |

### UserPermissionOverrideSummary

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `expiresAt` | string (date-time) |  |
| `overrideType` | string |  |
| `permissionCode` | string |  |
| `permissionId` | integer (int64) |  |
| `reason` | string |  |

### UserResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `departmentId` | integer (int64) |  |
| `email` | string |  |
| `fullName` | string |  |
| `googleLinked` | boolean |  |
| `id` | integer (int64) |  |
| `isManagement` | boolean |  |
| `passwordSet` | boolean |  |
| `phone` | string |  |
| `status` | string |  |
| `username` | string |  |

### UserSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `deviceInfo` | string |  |
| `expiresAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `ipAddress` | string |  |
| `lastActiveAt` | string (date-time) |  |

### VideoHeader

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `durationSeconds` | integer |  |
| `requiredViewCount` | integer |  |
| `title` | string |  |
| `videoId` | integer (int64) |  |

### Warning

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `message` | string |  |
| `similarity` | number |  |
| `type` | string |  |

### WithdrawEnrollmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `withdrawnDate` | string (date) | ✔ |

### WorkCalendarResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `appliesToScope` | string |  |
| `calendarDate` | string (date) |  |
| `dayType` | string |  |
| `description` | string |  |
| `employeeFullName` | string |  |
| `employeeId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `shiftId` | integer (int64) |  |
| `shiftName` | string |  |

### WrongStudent

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

