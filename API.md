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
- [academic-settings-controller](#academic-settings-controller)
- [academic-term-controller](#academic-term-controller)
- [academic-year-controller](#academic-year-controller)
- [class-schedule-import-controller](#class-schedule-import-controller)
- [curriculum-document-controller](#curriculum-document-controller)
- [department-controller](#department-controller)
- [employee-batch-import-controller](#employee-batch-import-controller)
- [enrollment-movement-report-controller](#enrollment-movement-report-controller)
- [exam-controller](#exam-controller)
- [exam-question-controller](#exam-question-controller)
- [exercise-report-controller](#exercise-report-controller)
- [grade-import-controller](#grade-import-controller)
- [leave-substitution-controller](#leave-substitution-controller)
- [leave-type-controller](#leave-type-controller)
- [listening-practice-controller](#listening-practice-controller)
- [listening-practice-grading-controller](#listening-practice-grading-controller)
- [media-controller](#media-controller)
- [parent-batch-import-controller](#parent-batch-import-controller)
- [parent-controller](#parent-controller)
- [position-controller](#position-controller)
- [question-import-controller](#question-import-controller)
- [report-generation-controller](#report-generation-controller)
- [report-template-controller](#report-template-controller)
- [review-video-controller](#review-video-controller)
- [skill-controller](#skill-controller)
- [student-portal-controller](#student-portal-controller)
- [system-setting-controller](#system-setting-controller)
- [task-settings-controller](#task-settings-controller)
- [teacher-schedule-controller](#teacher-schedule-controller)
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
| GET | `/api/users` | JWT + `user.view` | Query: `keyword`?, `departmentId`?, `status`?, `pageable` | [PageUserListItemResponse](#pageuserlistitemresponse) |
| POST | `/api/users` | JWT + `user.create` | Body: [CreateUserRequest](#createuserrequest) | [UserResponse](#userresponse) |
| GET | `/api/users/{userId}` | JWT + `user.view` | — | [UserDetailResponse](#userdetailresponse) |
| PUT | `/api/users/{userId}` | JWT + `user.update` | Body: [UpdateUserRequest](#updateuserrequest) | [UserResponse](#userresponse) |
| PUT | `/api/users/{userId}/email` | JWT + `user.update` | Body: [UpdateUserEmailRequest](#updateuseremailrequest) | [UserResponse](#userresponse) |
| PUT | `/api/users/{userId}/password` | JWT + `user.update` | Body: [AdminChangePasswordRequest](#adminchangepasswordrequest) | 200 (không có body) |
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

## Chấm công nhân sự (UC-09)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/attendance/check-in` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |
| POST | `/api/attendance/check-out` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |

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
| GET | `/api/students` | JWT + `student.profile.view` | Query: `query`?, `siteId`? | mảng [StudentResponse](#studentresponse) |
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
| POST | `/api/students/{id}/transfers` | JWT + `student.profile.update` | Body: [RecordTransferRequest](#recordtransferrequest) | [StudentTransferHistoryResponse](#studenttransferhistoryresponse) |

## Điểm danh học sinh (UC-15)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| DELETE | `/api/class-sessions/{classSessionId}/attendance` | JWT + `academic.attendance.delete` | — | 200 (không có body) |
| GET | `/api/class-sessions/{classSessionId}/attendance` | JWT | — | [AttendanceSessionResponse](#attendancesessionresponse) |
| POST | `/api/class-sessions/{classSessionId}/attendance` | JWT + `academic.attendance.mark hoặc academic.attendance.create` | Body: [MarkAttendanceRequest](#markattendancerequest) | [AttendanceSessionResponse](#attendancesessionresponse) |
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
| POST | `/api/curriculums/custom` | JWT | Body: [CreateCustomCurriculumRequest](#createcustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/custom/{id}` | JWT | Body: [UpdateCustomCurriculumRequest](#updatecustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| POST | `/api/curriculums/custom/{id}/submit` | JWT | — | [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| GET | `/api/curriculums/{id}` | JWT | — | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/{id}` | JWT + `academic.curriculum.update` | Body: [UpdateCurriculumRequest](#updatecurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| GET | `/api/curriculums/{id}/subjects` | JWT | — | mảng [CurriculumSubjectResponse](#curriculumsubjectresponse) |
| POST | `/api/curriculums/{id}/subjects` | JWT + `academic.curriculum.update` | Body: [CreateCurriculumSubjectRequest](#createcurriculumsubjectrequest) | [CurriculumSubjectResponse](#curriculumsubjectresponse) |

## Lớp học, giáo viên, ghi danh (UC-18)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes` | JWT | Query: `query`?, `siteId`?, `curriculumId`?, `classCategory`?, `academicYearId`? | mảng [ClassResponse](#classresponse) |
| POST | `/api/classes` | JWT + `academic.class.manage` | Body: [CreateClassRequest](#createclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}` | JWT | — | [ClassResponse](#classresponse) |
| PUT | `/api/classes/{id}` | JWT + `academic.class.manage` | Body: [UpdateClassRequest](#updateclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}/enrollments` | JWT | — | mảng [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments` | JWT + `academic.class.manage` | Body: [EnrollStudentRequest](#enrollstudentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments/import` | JWT + `academic.class.manage` | Form-data: `file` (tệp) | [ClassEnrollmentBatchImportResponse](#classenrollmentbatchimportresponse) |
| GET | `/api/classes/{id}/enrollments/import-template` | JWT + `academic.class.manage` | — | string |
| POST | `/api/classes/{id}/enrollments/{enrollmentId}/withdraw` | JWT + `academic.class.manage` | Body: [WithdrawEnrollmentRequest](#withdrawenrollmentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/promote` | JWT + `academic.class.manage` | Body: [PromoteClassRequest](#promoteclassrequest) | [PromoteClassResponse](#promoteclassresponse) |
| GET | `/api/classes/{id}/teachers` | JWT | — | mảng [ClassTeacherResponse](#classteacherresponse) |
| POST | `/api/classes/{id}/teachers` | JWT + `academic.class.manage` | Body: [AssignTeacherRequest](#assignteacherrequest) | [ClassTeacherResponse](#classteacherresponse) |
| PUT | `/api/classes/{id}/teachers/{classTeacherId}/end` | JWT + `academic.class.manage` | Body: [EndTeacherAssignmentRequest](#endteacherassignmentrequest) | [ClassTeacherResponse](#classteacherresponse) |

## Buổi học / xếp lịch

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/sessions` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions` | JWT + `academic.class.manage` | Body: [CreateClassSessionRequest](#createclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions/bulk` | JWT + `academic.class.manage` | Body: [BulkCreateClassSessionRequest](#bulkcreateclasssessionrequest) | [BulkCreateClassSessionResponse](#bulkcreateclasssessionresponse) |
| GET | `/api/classes/{classId}/sessions/cancelled-pending-makeup` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/classes/{classId}/sessions/today` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/cancel` | JWT + `academic.class.manage` | Body: [CancelClassSessionRequest](#cancelclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/classes/{classId}/sessions/{classSessionId}/periods` | JWT | — | mảng [SessionPeriodResponse](#sessionperiodresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/reschedule` | JWT + `academic.class.manage` | Body: [RescheduleClassSessionRequest](#rescheduleclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |

## Sổ điểm & duyệt điểm (UC-19/20)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/grade-component-setups` | JWT | Query: `academicTermId`? | mảng [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| POST | `/api/classes/{classId}/grade-component-setups` | JWT + `academic.grade.setup.create` | Body: [CreateGradeComponentSetupRequest](#creategradecomponentsetuprequest) | [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| GET | `/api/classes/{classId}/grade-component-setups/{setupId}/results` | JWT | — | mảng [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| GET | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| POST | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}` | JWT | Body: [EnterGradeRequest](#entergraderequest) | [GradeEntryResponse](#gradeentryresponse) |
| DELETE | `/api/classes/{classId}/grades/components/{gradeEvaluationComponentId}/students/{studentId}` | JWT | — | 200 (không có body) |
| DELETE | `/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result` | JWT | — | 200 (không có body) |
| POST | `/api/classes/{classId}/grades/students/{studentId}/setups/{setupId}/result` | JWT | Body: [EnterGradeEvaluationResultRequest](#entergradeevaluationresultrequest) | [GradeEvaluationResultResponse](#gradeevaluationresultresponse) |
| DELETE | `/api/grade-component-setups/{id}` | JWT + `academic.grade.setup.delete` | — | 200 (không có body) |
| PUT | `/api/grade-component-setups/{id}` | JWT + `academic.grade.setup.update` | Body: [UpdateGradeComponentSetupRequest](#updategradecomponentsetuprequest) | [GradeComponentSetupResponse](#gradecomponentsetupresponse) |
| GET | `/api/grade-component-setups/{id}/roster` | JWT | — | mảng [StudentResponse](#studentresponse) |
| GET | `/api/grade-component-setups/{setupId}/components` | JWT | — | mảng [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| POST | `/api/grade-component-setups/{setupId}/components` | JWT + `academic.grade.component.create` | Body: [CreateGradeEvaluationComponentRequest](#creategradeevaluationcomponentrequest) | [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| DELETE | `/api/grade-evaluation-components/{id}` | JWT + `academic.grade.component.delete` | — | 200 (không có body) |
| PUT | `/api/grade-evaluation-components/{id}` | JWT + `academic.grade.component.update` | Body: [UpdateGradeEvaluationComponentRequest](#updategradeevaluationcomponentrequest) | [GradeEvaluationComponentResponse](#gradeevaluationcomponentresponse) |
| POST | `/api/grades/decision` | JWT | Body: [PublishGradesRequest](#publishgradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/grades/pending` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| POST | `/api/grades/submit` | JWT | Body: [SubmitGradesRequest](#submitgradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |

## Nhận xét học sinh (UC-21/22)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/class-sessions/{classSessionId}/comments/import` | JWT + `academic.comment.write hoặc academic.comment.approve` | Form-data: `file` (tệp) | [DailyCommentImportResponse](#dailycommentimportresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/lesson-content` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateLessonContentRequest](#updatelessoncontentrequest) | [ClassSessionLessonContentResponse](#classsessionlessoncontentresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/teacher-name` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateActualTeacherNameRequest](#updateactualteachernamerequest) | [ClassSessionTeacherNameResponse](#classsessionteachernameresponse) |
| PUT | `/api/class-sessions/{classSessionId}/comments/teacher-type` | JWT + `academic.comment.write hoặc academic.comment.approve` | Body: [UpdateSessionTeacherTypeRequest](#updatesessionteachertyperequest) | [ClassSessionTeacherTypeResponse](#classsessionteachertyperesponse) |
| GET | `/api/class-sessions/{classSessionId}/comments/template` | JWT + `academic.comment.write hoặc academic.comment.approve` | — | string |
| GET | `/api/classes/{classId}/comments` | JWT | Query: `studentId` | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments` | JWT + `academic.comment.write` | Body: [CreateStudentCommentRequest](#createstudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments/submit` | JWT + `academic.comment.write` | Body: [SubmitCommentsRequest](#submitcommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/comments/decision` | JWT + `academic.comment.approve` | Body: [DecideCommentsRequest](#decidecommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/comments/pending` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| PUT | `/api/comments/pending/{id}/content` | JWT + `academic.comment.approve` | Body: [UpdateStudentCommentContentRequest](#updatestudentcommentcontentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| PUT | `/api/comments/{id}` | JWT + `academic.comment.write` | Body: [UpdateStudentCommentRequest](#updatestudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |

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
| POST | `/api/exercises` | JWT + `lms.exercise.create` | Body: [CreateExerciseRequest](#createexerciserequest) | [ExerciseResponse](#exerciseresponse) |
| DELETE | `/api/exercises/{id}` | JWT + `lms.exercise.update` | — | 200 (không có body) |
| GET | `/api/exercises/{id}` | JWT | — | [ExerciseResponse](#exerciseresponse) |
| PUT | `/api/exercises/{id}` | JWT + `lms.exercise.update` | Body: [UpdateExerciseRequest](#updateexerciserequest) | [ExerciseResponse](#exerciseresponse) |
| POST | `/api/exercises/{id}/publish` | JWT + `lms.exercise.publish` | — | [ExerciseResponse](#exerciseresponse) |
| GET | `/api/exercises/{id}/questions` | JWT | — | mảng [ExerciseQuestionResponse](#exercisequestionresponse) |
| POST | `/api/exercises/{id}/questions` | JWT + `lms.exercise.update` | Body: [AddExerciseQuestionRequest](#addexercisequestionrequest) | [ExerciseQuestionResponse](#exercisequestionresponse) |
| DELETE | `/api/exercises/{id}/questions/{exerciseQuestionId}` | JWT + `lms.exercise.update` | — | 200 (không có body) |

## Làm bài & nộp bài (LMS)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/attempts/{id}` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/attempts/{id}/answers` | JWT | — | mảng [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/answers` | JWT | Body: [SaveAnswerRequest](#saveanswerrequest) | [StudentAnswerResponse](#studentanswerresponse) |
| GET | `/api/attempts/{id}/answers/for-grading` | JWT + `lms.grading.manage` | — | mảng [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/integrity-events` | JWT | Body: [RecordIntegrityEventsRequest](#recordintegrityeventsrequest) | [IntegrityEventBatchResponse](#integrityeventbatchresponse) |
| GET | `/api/attempts/{id}/integrity-summary` | JWT + `lms.grading.manage` | — | [IntegritySummaryResponse](#integritysummaryresponse) |
| GET | `/api/attempts/{id}/listening-hint` | JWT | Query: `questionId` | [ListeningHintResponse](#listeninghintresponse) |
| POST | `/api/attempts/{id}/listening-plays` | JWT | Body: [RecordListeningPlayRequest](#recordlisteningplayrequest) | [ListeningPlayProgressResponse](#listeningplayprogressresponse) |
| POST | `/api/attempts/{id}/select-for-grading` | JWT + `lms.grading.manage` | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/attempts/{id}/submit` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/exercises/{exerciseId}/attempts` | JWT | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/exercises/{exerciseId}/attempts` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
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
| DELETE | `/api/notifications/device-token/{token}` | JWT | — | 200 (không có body) |
| GET | `/api/notifications/preferences/{notificationType}` | JWT | — | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| PUT | `/api/notifications/preferences/{notificationType}` | JWT | Body: [NotificationPreferenceRequest](#notificationpreferencerequest) | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| POST | `/api/notifications/send-manual` | JWT + `notification.send.manual` | Body: [SendNotificationRequest](#sendnotificationrequest) | [SendNotificationResponse](#sendnotificationresponse) |
| POST | `/api/notifications/{id}/read` | JWT | — | [NotificationResponse](#notificationresponse) |

## Biểu phí học phí

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/tuition-plan-assignments` | JWT + `finance.tuition-plan.assign` | Body: [AssignTuitionPlanRequest](#assigntuitionplanrequest) | [TuitionPlanAssignmentResponse](#tuitionplanassignmentresponse) |
| POST | `/api/finance/tuition-plans` | JWT + `finance.tuition-plan.create` | Body: [CreateTuitionPlanRequest](#createtuitionplanrequest) | [TuitionPlanResponse](#tuitionplanresponse) |
| GET | `/api/finance/tuition-plans/{id}` | JWT + `finance.tuition-plan.view` | — | [TuitionPlanResponse](#tuitionplanresponse) |
| PUT | `/api/finance/tuition-plans/{id}/status` | JWT + `finance.tuition-plan.update` | Body: [UpdateTuitionPlanStatusRequest](#updatetuitionplanstatusrequest) | [TuitionPlanResponse](#tuitionplanresponse) |

## Học bổng

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/scholarships` | JWT + `finance.scholarship.create` | Body: [CreateScholarshipRequest](#createscholarshiprequest) | [ScholarshipResponse](#scholarshipresponse) |
| POST | `/api/finance/scholarships/{id}/revoke` | JWT + `finance.scholarship.revoke` | — | [ScholarshipResponse](#scholarshipresponse) |

## Hóa đơn & thanh toán (UC-30)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/invoices/generate` | JWT + `finance.invoice.generate` | Body: [GenerateInvoicesRequest](#generateinvoicesrequest) | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/my` | JWT | — | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/{id}` | JWT | — | [InvoiceResponse](#invoiceresponse) |
| POST | `/api/finance/invoices/{id}/payments` | JWT + `finance.invoice.payment.record` | Body: [RecordManualPaymentRequest](#recordmanualpaymentrequest) | [PaymentResponse](#paymentresponse) |
| POST | `/api/webhooks/bank-payment` | Header `X-Webhook-Secret` | Body: [BankWebhookPaymentRequest](#bankwebhookpaymentrequest)<br>Header: `X-Webhook-Secret` | [PaymentResponse](#paymentresponse) |

## Chi phí vận hành (UC-31)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/operating-expenses` | JWT + `finance.expense.create hoặc finance.expense.approve` | Query: `siteId`?, `from`, `to` | mảng [OperatingExpenseResponse](#operatingexpenseresponse) |
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
| POST | `/api/academic-terms` | JWT + `academic.class.manage` | Body: [CreateAcademicTermRequest](#createacademictermrequest) | [AcademicTermResponse](#academictermresponse) |
| GET | `/api/academic-terms/{id}` | JWT | — | [AcademicTermResponse](#academictermresponse) |
| PUT | `/api/academic-terms/{id}` | JWT + `academic.class.manage` | Body: [UpdateAcademicTermRequest](#updateacademictermrequest) | [AcademicTermResponse](#academictermresponse) |

## academic-year-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic-years` | JWT | — | mảng [AcademicYearResponse](#academicyearresponse) |
| POST | `/api/academic-years` | JWT + `academic.class.manage` | Body: [CreateAcademicYearRequest](#createacademicyearrequest) | [AcademicYearResponse](#academicyearresponse) |
| GET | `/api/academic-years/{id}` | JWT | — | [AcademicYearResponse](#academicyearresponse) |
| PUT | `/api/academic-years/{id}` | JWT + `academic.class.manage` | Body: [UpdateAcademicYearRequest](#updateacademicyearrequest) | [AcademicYearResponse](#academicyearresponse) |

## class-schedule-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/session-imports` | JWT + `academic.class.manage` | Form-data: `file` (tệp) | [ClassScheduleImportResponse](#classscheduleimportresponse) |
| GET | `/api/classes/{classId}/session-imports/{id}` | JWT + `academic.class.manage` | — | [ClassScheduleImportResponse](#classscheduleimportresponse) |

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

## employee-batch-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/employee-imports` | JWT + `hrm.employee.import` | Form-data: `file` (tệp) | [EmployeeBatchImportResponse](#employeebatchimportresponse) |
| POST | `/api/employee-imports/accounts-export` | JWT + `hrm.employee.import` | Body: [AccountExportRequest](#accountexportrequest) | string |
| GET | `/api/employee-imports/template` | JWT + `hrm.employee.import` | — | string |
| GET | `/api/employee-imports/{id}` | JWT + `hrm.employee.import` | — | [EmployeeBatchImportResponse](#employeebatchimportresponse) |

## enrollment-movement-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-stats` | JWT + `report.enrollment-stats.view` | Query: `classId`? | [EnrollmentMovementStatsResponse](#enrollmentmovementstatsresponse) |
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-stats/export` | JWT + `report.enrollment-stats.view` | Query: `classId`? | string |
| GET | `/api/academic-terms/{academicTermId}/enrollment-movement-trend` | JWT + `report.enrollment-stats.view` | Query: `classId`? | [EnrollmentMovementTrendResponse](#enrollmentmovementtrendresponse) |

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
| GET | `/api/exams/question-imports/template.docx` | JWT + `lms.exam-question.create` | — | string |
| GET | `/api/exams/{examId}/questions` | JWT + `lms.exam-question.view` | — | mảng [QuestionResponse](#questionresponse) |
| POST | `/api/exams/{examId}/questions` | JWT + `lms.exam-question.create` | Body: [CreateExamQuestionRequest](#createexamquestionrequest) | [QuestionResponse](#questionresponse) |
| POST | `/api/exams/{examId}/questions/import` | JWT + `lms.exam-question.create` | Form-data: `file` (tệp) | [QuestionImportResponse](#questionimportresponse) |
| GET | `/api/exams/{examId}/questions/{questionId}` | JWT + `lms.exam-question.view` | — | [QuestionResponse](#questionresponse) |
| PUT | `/api/exams/{examId}/questions/{questionId}` | JWT + `lms.exam-question.update` | Body: [UpdateQuestionRequest](#updatequestionrequest) | [QuestionResponse](#questionresponse) |

## exercise-report-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/exercise-assignments/stats` | JWT + `lms.exercise.report.view` | — | mảng [ExerciseAssignmentStatsResponse](#exerciseassignmentstatsresponse) |
| GET | `/api/exercise-assignments/{assignmentId}/stats/export` | JWT + `lms.exercise.report.view` | — | string |
| GET | `/api/exercise-assignments/{assignmentId}/stats/questions` | JWT + `lms.exercise.report.view` | — | [ExerciseAssignmentQuestionStatsResponse](#exerciseassignmentquestionstatsresponse) |
| GET | `/api/exercise-assignments/{assignmentId}/stats/students` | JWT + `lms.exercise.report.view` | — | [ExerciseAssignmentStudentStatsResponse](#exerciseassignmentstudentstatsresponse) |

## grade-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/grade-component-setups/{setupId}/grades/import` | JWT | Form-data: `file` (tệp) | [GradeImportResponse](#gradeimportresponse) |
| GET | `/api/classes/{classId}/grade-component-setups/{setupId}/grades/import-template` | JWT | — | string |
| GET | `/api/grade-imports/{id}` | JWT | — | [GradeImportResponse](#gradeimportresponse) |

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
| POST | `/api/question-banks/{bankId}/questions/import` | JWT + `lms.question-bank.create` | Form-data: `file` (tệp) | [QuestionImportResponse](#questionimportresponse) |
| GET | `/api/question-imports/template.docx` | JWT + `lms.question-bank.create` | — | string |
| GET | `/api/question-imports/{id}` | JWT + `lms.question-bank.create` | — | [QuestionImportResponse](#questionimportresponse) |

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

## review-video-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/review-video-assignments` | JWT | — | mảng [ReviewVideoAssignmentResponse](#reviewvideoassignmentresponse) |
| GET | `/api/classes/{classId}/review-video-sets` | JWT | — | mảng [ReviewVideoSetResponse](#reviewvideosetresponse) |
| PUT | `/api/review-video-questions/{questionId}/submissions` | JWT | Body: [SubmitReviewVideoAudioRequest](#submitreviewvideoaudiorequest) | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-questions/{questionId}/submissions/history` | JWT | — | mảng [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-questions/{questionId}/submissions/latest` | JWT | — | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-sets` | JWT | Query: `curriculumId`?, `teacherType`? | mảng [ReviewVideoSetResponse](#reviewvideosetresponse) |
| POST | `/api/review-video-sets` | JWT + `lms.review-video.create` | Body: [CreateReviewVideoSetRequest](#createreviewvideosetrequest) | [ReviewVideoSetResponse](#reviewvideosetresponse) |
| PUT | `/api/review-video-sets/{id}` | JWT + `lms.review-video.update` | Body: [UpdateReviewVideoSetRequest](#updatereviewvideosetrequest) | [ReviewVideoSetResponse](#reviewvideosetresponse) |
| GET | `/api/review-video-sets/{id}/classes` | JWT | — | mảng [ClassResponse](#classresponse) |
| DELETE | `/api/review-video-sets/{id}/classes/{classId}` | JWT + `lms.review-video.assign` | — | 200 (không có body) |
| POST | `/api/review-video-sets/{id}/classes/{classId}` | JWT + `lms.review-video.assign` | — | 200 (không có body) |
| GET | `/api/review-video-sets/{setId}/stats` | JWT + `lms.review-video.view` | Query: `classId`? | [ReviewVideoSetStatsResponse](#reviewvideosetstatsresponse) |
| GET | `/api/review-video-sets/{setId}/submissions` | JWT + `lms.grading.manage` | Query: `classId`? | mảng [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| GET | `/api/review-video-sets/{setId}/videos` | JWT | — | mảng [ReviewVideoResponse](#reviewvideoresponse) |
| POST | `/api/review-video-sets/{setId}/videos` | JWT + `lms.review-video.update` | Body: [AddReviewVideoRequest](#addreviewvideorequest) | [ReviewVideoResponse](#reviewvideoresponse) |
| POST | `/api/review-video-submissions/{submissionId}/grade` | JWT + `lms.grading.manage` | Body: [GradeReviewVideoSubmissionRequest](#gradereviewvideosubmissionrequest) | [ReviewVideoSubmissionResponse](#reviewvideosubmissionresponse) |
| PUT | `/api/review-video-watch-sessions/{watchSessionId}/connection-answers` | JWT | Body: [SubmitConnectionAnswersRequest](#submitconnectionanswersrequest) | [ReviewVideoConnectionQuizResultResponse](#reviewvideoconnectionquizresultresponse) |
| GET | `/api/review-videos/{videoId}/connection-questions` | JWT | — | mảng [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| POST | `/api/review-videos/{videoId}/connection-questions` | JWT + `lms.review-video.update` | Body: [AddReviewVideoConnectionQuestionRequest](#addreviewvideoconnectionquestionrequest) | [ReviewVideoConnectionQuestionResponse](#reviewvideoconnectionquestionresponse) |
| GET | `/api/review-videos/{videoId}/progress` | JWT | — | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |
| PUT | `/api/review-videos/{videoId}/progress` | JWT | Body: [ReportVideoProgressRequest](#reportvideoprogressrequest) | [ReviewVideoProgressResponse](#reviewvideoprogressresponse) |
| GET | `/api/review-videos/{videoId}/questions` | JWT | — | mảng [ReviewVideoQuestionResponse](#reviewvideoquestionresponse) |
| POST | `/api/review-videos/{videoId}/questions` | JWT + `lms.review-video.update` | Body: [AddReviewVideoQuestionRequest](#addreviewvideoquestionrequest) | [ReviewVideoQuestionResponse](#reviewvideoquestionresponse) |
| POST | `/api/review-videos/{videoId}/watch-sessions` | JWT | — | [StartWatchSessionResponse](#startwatchsessionresponse) |

## skill-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/skills` | JWT | Query: `includeInactive`? | mảng [SkillResponse](#skillresponse) |
| POST | `/api/skills` | JWT + `academic.skill.create` | Body: [CreateSkillRequest](#createskillrequest) | [SkillResponse](#skillresponse) |
| PUT | `/api/skills/{id}` | JWT + `academic.skill.update` | Body: [UpdateSkillRequest](#updateskillrequest) | [SkillResponse](#skillresponse) |

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

## teacher-schedule-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/teachers/me/sessions` | JWT | Query: `fromDate`?, `toDate`? | mảng [ClassSessionResponse](#classsessionresponse) |

---

## Phụ lục: Schemas

### AcademicTermResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
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
| `prompt` | string |  |
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
| `classId` | integer (int64) |  |
| `className` | string |  |
| `dueAt` | string (date-time) |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseType` | string |  |
| `lateSubmissionAllowed` | boolean |  |
| `myLatestAttemptId` | integer (int64) |  |
| `myLatestAttemptStatus` | string |  |
| `myLatestPassed` | boolean |  |
| `myLatestPercentage` | number |  |
| `myLatestTotalScore` | number |  |
| `sessionDate` | string (date) |  |
| `teacherType` | string |  |
| `title` | string |  |

### AttendanceCheckRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `biometricVerified` | boolean |  |
| `latitude` | number |  |
| `longitude` | number |  |
| `method` | string | ✔ |
| `siteId` | integer (int64) |  |

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

### BulkCreateClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `daysOfWeek` | mảng string | ✔ |
| `endDate` | string (date) | ✔ |
| `endTime` | [LocalTime](#localtime) | ✔ |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `sessionType` | string | ✔ |
| `startDate` | string (date) | ✔ |
| `startTime` | [LocalTime](#localtime) | ✔ |
| `teacherType` | string |  |

### BulkCreateClassSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `created` | mảng [ClassSessionResponse](#classsessionresponse) |  |
| `createdCount` | integer |  |
| `skipped` | mảng object |  |
| `skippedCount` | integer |  |
| `totalDates` | integer |  |

### CancelClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |

### CancelTaskRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |

### ChainFinancialReportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `bySite` | mảng [FinancialReportResponse](#financialreportresponse) |  |
| `periodFrom` | string (date) |  |
| `periodTo` | string (date) |  |
| `totalExpense` | number |  |
| `totalOutstanding` | number |  |
| `totalRevenue` | number |  |

### ChangeOwnPasswordRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `currentPassword` | string |  |
| `newPassword` | string | ✔ |

### ChildResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

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

### ClassSessionLessonContentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `lessonContent` | string |  |

### ClassSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `actualTeacherName` | string |  |
| `cancellationReason` | string |  |
| `classId` | integer (int64) |  |
| `className` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `lessonContent` | string |  |
| `makeupForSessionId` | integer (int64) |  |
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
| `endTime` | [LocalTime](#localtime) | ✔ |
| `makeupForSessionId` | integer (int64) |  |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `sessionDate` | string (date) | ✔ |
| `sessionType` | string | ✔ |
| `startTime` | [LocalTime](#localtime) | ✔ |
| `teacherType` | string |  |

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
| `level` | string |  |
| `name` | string | ✔ |
| `totalPeriods` | integer |  |

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
| `subjectId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |
| `videoType` | string | ✔ |

### CreateRoleRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
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

### CreateSkillRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `description` | string |  |
| `name` | string | ✔ |

### CreateStudentCommentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `attitude` | string |  |
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) | ✔ |
| `commentType` | string | ✔ |
| `content` | string | ✔ |
| `homeworkNext` | string |  |
| `homeworkNextDueDate` | string (date-time) |  |
| `homeworkNextExerciseId` | integer (int64) |  |
| `homeworkNextReviewVideoSetId` | integer (int64) |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
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

### CreateUserRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `email` | string | ✔ |
| `fullName` | string | ✔ |
| `password` | string |  |
| `phone` | string |  |
| `username` | string | ✔ |

### CurrentUserResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
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
| `id` | integer (int64) |  |
| `level` | string |  |
| `name` | string |  |
| `parentCurriculumId` | integer (int64) |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `totalPeriods` | integer |  |

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

### DeviceTokenRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `platform` | string | ✔ |
| `token` | string | ✔ |

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

### EnrollmentMovementStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
| `academicTermName` | string |  |
| `classes` | mảng [EnrollmentMovementClassRow](#enrollmentmovementclassrow) |  |
| `endDate` | string (date) |  |
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
| `academicTermName` | string |  |
| `endDate` | string (date) |  |
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
| `teacherType` | string |  |
| `title` | string |  |
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
| `status` | string |  |
| `targetStudentIds` | mảng integer (int64) |  |
| `uuid` | string (uuid) |  |

### ExerciseAssignmentStatsResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignmentId` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `completedCount` | integer |  |
| `completionPercent` | number |  |
| `dueAt` | string (date-time) |  |
| `exerciseCode` | string |  |
| `exerciseId` | integer (int64) |  |
| `exerciseTitle` | string |  |
| `exerciseType` | string |  |
| `passRatePercent` | number |  |
| `passedCount` | integer |  |
| `status` | string |  |
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

### ExerciseQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [ExerciseQuestionChoiceResponse](#exercisequestionchoiceresponse) |  |
| `displayOrder` | integer |  |
| `exerciseId` | integer (int64) |  |
| `groupKey` | string |  |
| `id` | integer (int64) |  |
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
| `status` | string |  |
| `subjectId` | integer (int64) |  |
| `timeLimitMinutes` | integer |  |
| `title` | string |  |
| `totalPoints` | number |  |
| `uuid` | string (uuid) |  |

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
| `idToken` | string | ✔ |

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

### HomeworkProgressResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) |  |
| `commentId` | integer (int64) |  |
| `grammarAssignmentId` | integer (int64) |  |
| `grammarOfflineText` | string |  |
| `grammarPassed` | boolean |  |
| `grammarProgress` | string |  |
| `grammarTitle` | string |  |
| `videoAssignmentId` | integer (int64) |  |
| `videoPassed` | boolean |  |
| `videoProgress` | string |  |
| `videoTitle` | string |  |

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
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `subtotal` | number |  |
| `taxAmount` | number |  |
| `totalAmount` | number |  |

### JsonNode

_(không có trường — object rỗng hoặc kiểu đặc biệt)_

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
| `correctAnswerText` | string |  |
| `correctChoiceIds` | mảng integer (int64) |  |
| `explanation` | string |  |
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

### LoginRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `password` | string | ✔ |
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
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `sessionDate` | string (date) |  |
| `videoType` | string |  |

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
| `content` | string |  |
| `createdAt` | string (date-time) |  |
| `entityId` | integer (int64) |  |
| `entityType` | string |  |
| `id` | integer (int64) |  |
| `notificationType` | string |  |
| `priority` | string |  |
| `readAt` | string (date-time) |  |
| `title` | string |  |

### OperatingExpenseResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `approvedBy` | integer (int64) |  |
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
| `rejectionReason` | string |  |
| `siteId` | integer (int64) |  |
| `status` | string |  |
| `supplierName` | string |  |

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

### PaymentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `bankTransactionId` | string |  |
| `confirmedAt` | string (date-time) |  |
| `confirmedBy` | integer (int64) |  |
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
| `isCorrect` | boolean |  |

### QuestionChoiceResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `choiceLabel` | string |  |
| `content` | string |  |
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
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
| `content` | string |  |
| `displayOrder` | integer |  |
| `hintUsedCount` | integer |  |
| `hintUsedStudentCount` | integer |  |
| `questionId` | integer (int64) |  |
| `questionType` | string |  |
| `skill` | string |  |
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
| `newEndTime` | [LocalTime](#localtime) | ✔ |
| `newPrimaryTeacherId` | integer (int64) | ✔ |
| `newRoomId` | integer (int64) |  |
| `newSessionDate` | string (date) | ✔ |
| `newStartTime` | [LocalTime](#localtime) | ✔ |
| `reason` | string |  |

### ResolveFeedbackRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `resolutionNotes` | string | ✔ |

### ReviewVideoAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedBy` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `dueAt` | string (date-time) |  |
| `id` | integer (int64) |  |
| `reviewVideoSetId` | integer (int64) |  |
| `reviewVideoSetTitle` | string |  |
| `status` | string |  |
| `targetStudentIds` | mảng integer (int64) |  |
| `uuid` | string (uuid) |  |

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

### ReviewVideoQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `id` | integer (int64) |  |
| `maxAttempts` | integer |  |
| `maxRecordingSeconds` | integer |  |
| `prompt` | string |  |
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
| `subjectId` | integer (int64) |  |
| `teacherType` | string |  |
| `title` | string |  |
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
| `reviewVideoQuestionId` | integer (int64) |  |
| `score` | number |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |

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

### SaveAnswerRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `answerText` | string |  |
| `audioAnswerUrl` | string |  |
| `questionId` | integer (int64) | ✔ |
| `selectedChoiceIds` | mảng integer (int64) |  |
| `structuredAnswer` | mảng string |  |

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
| `content` | string | ✔ |
| `notificationType` | enum: ATTENDANCE_ABSENT \| TASK_ASSIGNED \| TASK_COMMENT \| INVOICE_DUE \| GRADE_PUBLISHED \| COMMENT_APPROVED \| ... | ✔ |
| `recipientUserIds` | mảng integer (int64) | ✔ |
| `title` | string | ✔ |

### SendNotificationResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `failures` | mảng [SendNotificationFailure](#sendnotificationfailure) |  |
| `succeeded` | integer |  |
| `totalRecipients` | integer |  |

### SessionPeriodResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classSessionId` | integer (int64) |  |
| `contentNote` | string |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `periodNumber` | integer |  |
| `startTime` | [LocalTime](#localtime) |  |
| `subjectId` | integer (int64) |  |
| `teacherId` | integer (int64) |  |

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
| `correctAnswerText` | string |  |
| `correctChoiceIds` | mảng integer (int64) |  |
| `correctStructuredContent` | object |  |
| `exerciseAttemptId` | integer (int64) |  |
| `explanation` | string |  |
| `id` | integer (int64) |  |
| `isAutoGradable` | boolean |  |
| `isCorrect` | boolean |  |
| `questionId` | integer (int64) |  |
| `selectedChoiceIds` | mảng integer (int64) |  |
| `structuredAnswer` | mảng string |  |

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

### StudentCommentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicTermId` | integer (int64) |  |
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
| `homeworkNextReviewVideoAssignmentId` | integer (int64) |  |
| `homeworkNextReviewVideoSetTitle` | string |  |
| `homeworkPreviousOfflineText` | string |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
| `id` | integer (int64) |  |
| `isWarning` | boolean |  |
| `lessonContent` | string |  |
| `note` | string |  |
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

### StudentProfileResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attendance` | mảng [StudentProfileAttendanceResponse](#studentprofileattendanceresponse) |  |
| `comments` | mảng [StudentProfileCommentResponse](#studentprofilecommentresponse) |  |
| `enrollments` | mảng [StudentProfileEnrollmentResponse](#studentprofileenrollmentresponse) |  |
| `gradeResults` | mảng [StudentProfileGradeResultResponse](#studentprofilegraderesultresponse) |  |
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
| `attemptId` | integer (int64) |  |
| `attemptNumber` | integer |  |
| `numberOfAttempts` | integer |  |
| `passed` | boolean |  |
| `percentage` | number |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `totalPoints` | number |  |
| `totalScore` | number |  |

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

### TeacherLookupResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `email` | string |  |
| `fullName` | string |  |
| `id` | integer (int64) |  |
| `username` | string |  |

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

### TuitionPlanAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `id` | integer (int64) |  |
| `overrideReason` | string |  |
| `priceOverride` | number |  |
| `tuitionPlanId` | integer (int64) |  |

### TuitionPlanResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `basePrice` | number |  |
| `classTypeFilter` | string |  |
| `code` | string |  |
| `currency` | string |  |
| `curriculumId` | integer (int64) |  |
| `effectiveFrom` | string (date) |  |
| `effectiveTo` | string (date) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `pricePerUnit` | number |  |
| `pricingModel` | string |  |
| `status` | string |  |
| `unitCount` | integer |  |

### UpdateAcademicTermRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
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

### UpdateClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYearId` | integer (int64) |  |
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
| `level` | string |  |
| `name` | string | ✔ |
| `status` | string | ✔ |
| `totalPeriods` | integer |  |

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

### UpdateEquipmentStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `notes` | string |  |
| `status` | string | ✔ |

### UpdateExamRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `examType` | string | ✔ |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |

### UpdateExerciseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `maxAttempts` | integer |  |
| `passThresholdPercent` | number |  |
| `showCorrectAnswers` | boolean |  |
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
| `referencePassage` | string |  |
| `status` | string |  |
| `structuredContent` | object |  |
| `tags` | mảng string |  |

### UpdateReportTemplateRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `description` | string |  |
| `name` | string | ✔ |

### UpdateReviewVideoSetRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `status` | string | ✔ |
| `subjectId` | integer (int64) |  |
| `teacherType` | string | ✔ |
| `title` | string | ✔ |

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

### UpdateSessionTeacherTypeRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `teacherType` | string | ✔ |

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
| `content` | string | ✔ |
| `homeworkNext` | string |  |
| `homeworkNextDueDate` | string (date-time) |  |
| `homeworkNextExerciseId` | integer (int64) |  |
| `homeworkNextReviewVideoSetId` | integer (int64) |  |
| `homeworkPreviousScore` | string |  |
| `homeworkPreviousSpeakingScore` | string |  |
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

### VideoHeader

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `durationSeconds` | integer |  |
| `requiredViewCount` | integer |  |
| `title` | string |  |
| `videoId` | integer (int64) |  |

### WithdrawEnrollmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `withdrawnDate` | string (date) | ✔ |

### WrongStudent

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |

