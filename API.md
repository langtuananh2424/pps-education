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
- [Bài giảng (LMS)](#bài-giảng-lms)
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
- [department-controller](#department-controller)
- [employee-batch-import-controller](#employee-batch-import-controller)
- [grade-import-controller](#grade-import-controller)
- [parent-batch-import-controller](#parent-batch-import-controller)
- [parent-controller](#parent-controller)
- [position-controller](#position-controller)
- [skill-controller](#skill-controller)
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
| GET | `/api/users` | JWT + `user.manage` | Query: `keyword`?, `departmentId`?, `status`?, `pageable` | [PageUserListItemResponse](#pageuserlistitemresponse) |
| POST | `/api/users` | JWT + `user.manage` | Body: [CreateUserRequest](#createuserrequest) | [UserResponse](#userresponse) |
| GET | `/api/users/{userId}` | JWT + `user.manage` | — | [UserDetailResponse](#userdetailresponse) |
| PUT | `/api/users/{userId}` | JWT + `user.manage` | Body: [UpdateUserRequest](#updateuserrequest) | [UserResponse](#userresponse) |
| PUT | `/api/users/{userId}/password` | JWT + `user.manage` | Body: [AdminChangePasswordRequest](#adminchangepasswordrequest) | 200 (không có body) |
| PUT | `/api/users/{userId}/status` | JWT + `user.manage` | Body: [UpdateUserStatusRequest](#updateuserstatusrequest) | [UserResponse](#userresponse) |

## Danh mục quyền (UC-02)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/permissions` | JWT + `permission.catalog.manage` | — | mảng [PermissionResponse](#permissionresponse) |
| POST | `/api/permissions` | JWT + `permission.catalog.manage` | Body: [CreatePermissionRequest](#createpermissionrequest) | [PermissionResponse](#permissionresponse) |
| DELETE | `/api/permissions/{id}` | JWT + `permission.catalog.manage` | — | 200 (không có body) |
| PUT | `/api/permissions/{id}` | JWT + `permission.catalog.manage` | Body: [UpdatePermissionRequest](#updatepermissionrequest) | [PermissionResponse](#permissionresponse) |

## Nhóm quyền mặc định (UC-03)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/roles` | JWT + `permission.role.manage` | — | mảng [RoleResponse](#roleresponse) |
| POST | `/api/roles` | JWT + `permission.role.manage` | Body: [CreateRoleRequest](#createrolerequest) | [RoleResponse](#roleresponse) |
| DELETE | `/api/roles/{id}` | JWT + `permission.role.manage` | — | 200 (không có body) |
| GET | `/api/roles/{id}/permissions` | JWT + `permission.role.manage` | — | [RolePermissionMatrixResponse](#rolepermissionmatrixresponse) |
| PUT | `/api/roles/{id}/permissions` | JWT + `permission.role.manage` | Body: [UpdateRolePermissionsRequest](#updaterolepermissionsrequest) | 200 (không có body) |

## Quyền ngoại lệ theo tài khoản (UC-04)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/users/{userId}/effective-permissions` | JWT + `permission.override.manage` | — | [EffectivePermissionsResponse](#effectivepermissionsresponse) |
| DELETE | `/api/users/{userId}/permission-overrides/{permissionId}` | JWT + `permission.override.manage` | — | 200 (không có body) |
| PUT | `/api/users/{userId}/permission-overrides/{permissionId}` | JWT + `permission.override.manage` | Body: [UserPermissionOverrideRequest](#userpermissionoverriderequest) | 200 (không có body) |

## Gán/Thu hồi vai trò cho tài khoản (UC-46)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/users/{userId}/roles` | JWT + `user.role.manage` | — | mảng [RoleResponse](#roleresponse) |
| DELETE | `/api/users/{userId}/roles/{roleId}` | JWT + `user.role.manage` | — | 200 (không có body) |
| PUT | `/api/users/{userId}/roles/{roleId}` | JWT + `user.role.manage` | — | 200 (không có body) |

## Nhật ký phân quyền (UC-05)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/permission-audit-logs` | JWT + `permission.audit.view` | Query: `actorUserId`?, `targetUserId`?, `action`?, `fromDate`?, `toDate`?, `pageable` | [PagePermissionAuditLogResponse](#pagepermissionauditlogresponse) |

## Quản lý công việc (UC-06/07)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| PUT | `/api/task-assignments/{id}/status` | JWT | Body: [UpdateAssignmentStatusRequest](#updateassignmentstatusrequest) | [TaskAssignmentResponse](#taskassignmentresponse) |
| POST | `/api/tasks` | JWT + `task.create` | Body: [CreateTaskRequest](#createtaskrequest) | [TaskResponse](#taskresponse) |
| GET | `/api/tasks/created-by-me` | JWT | — | mảng [TaskResponse](#taskresponse) |
| GET | `/api/tasks/my-assignments` | JWT | — | mảng [TaskAssignmentResponse](#taskassignmentresponse) |
| GET | `/api/tasks/{id}` | JWT | — | [TaskResponse](#taskresponse) |
| GET | `/api/tasks/{id}/assignments` | JWT | — | mảng [TaskAssignmentResponse](#taskassignmentresponse) |
| GET | `/api/tasks/{id}/attachments` | JWT | — | mảng [TaskAttachmentResponse](#taskattachmentresponse) |
| POST | `/api/tasks/{id}/attachments` | JWT | Body: [AddTaskAttachmentRequest](#addtaskattachmentrequest) | [TaskAttachmentResponse](#taskattachmentresponse) |
| GET | `/api/tasks/{id}/comments` | JWT | — | mảng [TaskCommentResponse](#taskcommentresponse) |
| POST | `/api/tasks/{id}/comments` | JWT | Body: [AddTaskCommentRequest](#addtaskcommentrequest) | [TaskCommentResponse](#taskcommentresponse) |

## Hồ sơ nhân sự, hợp đồng, bằng cấp (UC-08)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/employees` | JWT + `hrm.manage` | Query: `query`? | mảng [EmployeeResponse](#employeeresponse) |
| POST | `/api/employees` | JWT + `hrm.manage` | Body: [CreateEmployeeRequest](#createemployeerequest) | [EmployeeResponse](#employeeresponse) |
| GET | `/api/employees/contracts/expiring` | JWT + `hrm.manage` | Query: `withinDays` | mảng [ExpiringContractResponse](#expiringcontractresponse) |
| GET | `/api/employees/{id}` | JWT + `hrm.manage` | — | [EmployeeResponse](#employeeresponse) |
| PUT | `/api/employees/{id}` | JWT + `hrm.manage` | Body: [UpdateEmployeeRequest](#updateemployeerequest) | [EmployeeResponse](#employeeresponse) |
| GET | `/api/employees/{id}/commendations` | JWT + `hrm.manage` | — | mảng [CommendationResponse](#commendationresponse) |
| POST | `/api/employees/{id}/commendations` | JWT + `hrm.manage` | Body: [CreateCommendationRequest](#createcommendationrequest) | [CommendationResponse](#commendationresponse) |
| GET | `/api/employees/{id}/contracts` | JWT + `hrm.manage` | — | mảng [EmploymentContractResponse](#employmentcontractresponse) |
| POST | `/api/employees/{id}/contracts` | JWT + `hrm.manage` | Body: [CreateEmploymentContractRequest](#createemploymentcontractrequest) | [EmploymentContractResponse](#employmentcontractresponse) |
| PUT | `/api/employees/{id}/contracts/{contractId}` | JWT + `hrm.manage` | Body: [UpdateEmploymentContractRequest](#updateemploymentcontractrequest) | [EmploymentContractResponse](#employmentcontractresponse) |
| GET | `/api/employees/{id}/qualifications` | JWT + `hrm.manage` | — | mảng [QualificationResponse](#qualificationresponse) |
| POST | `/api/employees/{id}/qualifications` | JWT + `hrm.manage` | Body: [CreateQualificationRequest](#createqualificationrequest) | [QualificationResponse](#qualificationresponse) |

## Chấm công nhân sự (UC-09)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/attendance/check-in` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |
| POST | `/api/attendance/check-out` | JWT | Body: [AttendanceCheckRequest](#attendancecheckrequest) | [AttendanceRecordResponse](#attendancerecordresponse) |

## Đơn từ (UC-10/11)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/leave-requests` | JWT | Body: [CreateLeaveRequestRequest](#createleaverequestrequest) | [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/pending-for-me` | JWT | — | mảng [LeaveRequestResponse](#leaverequestresponse) |
| GET | `/api/leave-requests/{id}` | JWT | — | [LeaveRequestResponse](#leaverequestresponse) |
| POST | `/api/leave-requests/{id}/decision` | JWT | Body: [DecideLeaveRequestRequest](#decideleaverequestrequest) | [LeaveRequestResponse](#leaverequestresponse) |

## Bảng lương (UC-12)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/payroll/entries` | JWT | Query: `periodId`, `departmentId`?, `employeeId`? | mảng [PayrollEntryResponse](#payrollentryresponse) |
| GET | `/api/payroll/mine` | JWT | Query: `periodCode`? | [PayrollEntryResponse](#payrollentryresponse) |

## Hồ sơ học sinh & trạng thái học tập (UC-13/14)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/students` | JWT + `student.manage` | Query: `query`?, `siteId`? | mảng [StudentResponse](#studentresponse) |
| POST | `/api/students` | JWT + `student.manage` | Body: [CreateStudentRequest](#createstudentrequest) | [StudentResponse](#studentresponse) |
| GET | `/api/students/{id}` | JWT + `student.manage` | — | [StudentResponse](#studentresponse) |
| PUT | `/api/students/{id}` | JWT + `student.manage` | Body: [UpdateStudentRequest](#updatestudentrequest) | [StudentResponse](#studentresponse) |
| GET | `/api/students/{id}/parents` | JWT + `student.manage` | — | mảng [ParentStudentResponse](#parentstudentresponse) |
| POST | `/api/students/{id}/parents` | JWT + `student.manage` | Body: [LinkParentRequest](#linkparentrequest) | [ParentStudentResponse](#parentstudentresponse) |
| DELETE | `/api/students/{id}/parents/{parentStudentId}` | JWT + `student.manage` | — | 200 (không có body) |
| POST | `/api/students/{id}/status` | JWT + `student.status.manage` | Body: [UpdateStudentStatusRequest](#updatestudentstatusrequest) | [StudentStatusHistoryResponse](#studentstatushistoryresponse) |
| GET | `/api/students/{id}/status-history` | JWT | — | mảng [StudentStatusHistoryResponse](#studentstatushistoryresponse) |
| GET | `/api/students/{id}/transfers` | JWT + `student.manage` | — | mảng [StudentTransferHistoryResponse](#studenttransferhistoryresponse) |
| POST | `/api/students/{id}/transfers` | JWT + `student.manage` | Body: [RecordTransferRequest](#recordtransferrequest) | [StudentTransferHistoryResponse](#studenttransferhistoryresponse) |

## Điểm danh học sinh (UC-15)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/class-sessions/{classSessionId}/attendance` | JWT | — | [AttendanceSessionResponse](#attendancesessionresponse) |
| POST | `/api/class-sessions/{classSessionId}/attendance` | JWT | Body: [MarkAttendanceRequest](#markattendancerequest) | [AttendanceSessionResponse](#attendancesessionresponse) |
| PUT | `/api/class-sessions/{classSessionId}/attendance/students/{studentId}/periods/{sessionPeriodId}` | JWT | Body: [UpdatePeriodMarkRequest](#updateperiodmarkrequest) | [AttendanceMarkResponse](#attendancemarkresponse) |
| POST | `/api/class-sessions/{classSessionId}/attendance/submit` | JWT | — | [AttendanceSessionResponse](#attendancesessionresponse) |

## Import học sinh từ Excel (UC-35)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/student-imports` | JWT + `student.manage` | Form-data: `file` (tệp) | [StudentBatchImportResponse](#studentbatchimportresponse) |
| GET | `/api/student-imports/{id}` | JWT + `student.manage` | — | [StudentBatchImportResponse](#studentbatchimportresponse) |

## Khung chương trình (UC-16/16b/17)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums` | JWT | — | mảng [CurriculumResponse](#curriculumresponse) |
| POST | `/api/curriculums` | JWT + `academic.curriculum.manage` | Body: [CreateCurriculumRequest](#createcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| GET | `/api/curriculums/approvals/pending` | JWT + `academic.curriculum.manage` | — | mảng [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| POST | `/api/curriculums/approvals/{approvalFlowId}/decision` | JWT + `academic.curriculum.manage` | Body: [DecideCurriculumApprovalRequest](#decidecurriculumapprovalrequest) | [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| POST | `/api/curriculums/custom` | JWT | Body: [CreateCustomCurriculumRequest](#createcustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/custom/{id}` | JWT | Body: [UpdateCustomCurriculumRequest](#updatecustomcurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| POST | `/api/curriculums/custom/{id}/submit` | JWT | — | [CurriculumApprovalResponse](#curriculumapprovalresponse) |
| GET | `/api/curriculums/{id}` | JWT | — | [CurriculumResponse](#curriculumresponse) |
| PUT | `/api/curriculums/{id}` | JWT + `academic.curriculum.manage` | Body: [UpdateCurriculumRequest](#updatecurriculumrequest) | [CurriculumResponse](#curriculumresponse) |
| GET | `/api/curriculums/{id}/subjects` | JWT | — | mảng [CurriculumSubjectResponse](#curriculumsubjectresponse) |
| POST | `/api/curriculums/{id}/subjects` | JWT + `academic.curriculum.manage` | Body: [CreateCurriculumSubjectRequest](#createcurriculumsubjectrequest) | [CurriculumSubjectResponse](#curriculumsubjectresponse) |

## Lớp học, giáo viên, ghi danh (UC-18)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes` | JWT | Query: `query`?, `siteId`?, `curriculumId`?, `classCategory`? | mảng [ClassResponse](#classresponse) |
| POST | `/api/classes` | JWT + `academic.class.manage` | Body: [CreateClassRequest](#createclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}` | JWT | — | [ClassResponse](#classresponse) |
| PUT | `/api/classes/{id}` | JWT + `academic.class.manage` | Body: [UpdateClassRequest](#updateclassrequest) | [ClassResponse](#classresponse) |
| GET | `/api/classes/{id}/enrollments` | JWT | — | mảng [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments` | JWT + `academic.class.manage` | Body: [EnrollStudentRequest](#enrollstudentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| POST | `/api/classes/{id}/enrollments/{enrollmentId}/withdraw` | JWT + `academic.class.manage` | Body: [WithdrawEnrollmentRequest](#withdrawenrollmentrequest) | [ClassEnrollmentResponse](#classenrollmentresponse) |
| GET | `/api/classes/{id}/teachers` | JWT | — | mảng [ClassTeacherResponse](#classteacherresponse) |
| POST | `/api/classes/{id}/teachers` | JWT + `academic.class.manage` | Body: [AssignTeacherRequest](#assignteacherrequest) | [ClassTeacherResponse](#classteacherresponse) |

## Buổi học / xếp lịch

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/sessions` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions` | JWT + `academic.class.manage` | Body: [CreateClassSessionRequest](#createclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/cancel` | JWT + `academic.class.manage` | Body: [CancelClassSessionRequest](#cancelclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |
| GET | `/api/classes/{classId}/sessions/{classSessionId}/periods` | JWT | — | mảng [SessionPeriodResponse](#sessionperiodresponse) |
| POST | `/api/classes/{classId}/sessions/{classSessionId}/reschedule` | JWT + `academic.class.manage` | Body: [RescheduleClassSessionRequest](#rescheduleclasssessionrequest) | [ClassSessionResponse](#classsessionresponse) |

## Sổ điểm & duyệt điểm (UC-19/20)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/grade-periods/{gradePeriodId}/results` | JWT | — | mảng [GradePeriodResultResponse](#gradeperiodresultresponse) |
| GET | `/api/classes/{classId}/grades/components/{gradeComponentId}` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| POST | `/api/classes/{classId}/grades/components/{gradeComponentId}` | JWT | Body: [EnterGradeRequest](#entergraderequest) | [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/classes/{classId}/grades/students/{studentId}/periods/{gradePeriodId}/average` | JWT | — | [PeriodAverageResponse](#periodaverageresponse) |
| POST | `/api/classes/{classId}/grades/students/{studentId}/periods/{gradePeriodId}/result` | JWT | Body: [EnterGradePeriodResultRequest](#entergradeperiodresultrequest) | [GradePeriodResultResponse](#gradeperiodresultresponse) |
| POST | `/api/classes/{classId}/grades/submit` | JWT | Body: [SubmitGradesRequest](#submitgradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/curriculums/{curriculumId}/grade-periods` | JWT | — | mảng [GradePeriodResponse](#gradeperiodresponse) |
| POST | `/api/curriculums/{curriculumId}/grade-periods` | JWT + `academic.grade.manage` | Body: [CreateGradePeriodRequest](#creategradeperiodrequest) | [GradePeriodResponse](#gradeperiodresponse) |
| PUT | `/api/grade-components/{id}` | JWT + `academic.grade.manage` | Body: [UpdateGradeComponentRequest](#updategradecomponentrequest) | [GradeComponentResponse](#gradecomponentresponse) |
| GET | `/api/grade-periods/{gradePeriodId}/components` | JWT | — | mảng [GradeComponentResponse](#gradecomponentresponse) |
| POST | `/api/grade-periods/{gradePeriodId}/components` | JWT + `academic.grade.manage` | Body: [CreateGradeComponentRequest](#creategradecomponentrequest) | [GradeComponentResponse](#gradecomponentresponse) |
| PUT | `/api/grade-periods/{id}` | JWT + `academic.grade.manage` | Body: [UpdateGradePeriodRequest](#updategradeperiodrequest) | [GradePeriodResponse](#gradeperiodresponse) |
| POST | `/api/grades/decision` | JWT | Body: [DecideGradesRequest](#decidegradesrequest) | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/grades/pending` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |

## Nhận xét học sinh (UC-21/22)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/comments` | JWT | Query: `studentId` | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments` | JWT | Body: [CreateStudentCommentRequest](#createstudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/classes/{classId}/comments/submit` | JWT | Body: [SubmitCommentsRequest](#submitcommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| POST | `/api/comments/decision` | JWT | Body: [DecideCommentsRequest](#decidecommentsrequest) | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/comments/pending` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| PUT | `/api/comments/{id}` | JWT | Body: [UpdateStudentCommentRequest](#updatestudentcommentrequest) | [StudentCommentResponse](#studentcommentresponse) |

## Bài giảng (LMS)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/lessons` | JWT | — | mảng [LessonResponse](#lessonresponse) |
| GET | `/api/curriculums/{curriculumId}/lessons` | JWT | — | mảng [LessonResponse](#lessonresponse) |
| POST | `/api/lessons` | JWT | Body: [CreateLessonRequest](#createlessonrequest) | [LessonResponse](#lessonresponse) |
| PUT | `/api/lessons/{id}` | JWT | Body: [UpdateLessonRequest](#updatelessonrequest) | [LessonResponse](#lessonresponse) |
| GET | `/api/lessons/{lessonId}/materials` | JWT | — | mảng [LessonMaterialResponse](#lessonmaterialresponse) |
| POST | `/api/lessons/{lessonId}/materials` | JWT | Body: [AddLessonMaterialRequest](#addlessonmaterialrequest) | [LessonMaterialResponse](#lessonmaterialresponse) |

## Ngân hàng câu hỏi (UC-40)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/curriculums/{curriculumId}/question-banks` | JWT | — | mảng [QuestionBankResponse](#questionbankresponse) |
| POST | `/api/question-banks` | JWT + `lms.exercise.manage` | Body: [CreateQuestionBankRequest](#createquestionbankrequest) | [QuestionBankResponse](#questionbankresponse) |
| GET | `/api/question-banks/{bankId}/questions` | JWT | — | mảng [QuestionResponse](#questionresponse) |
| PUT | `/api/question-banks/{id}/status` | JWT + `lms.exercise.manage` | Body: [UpdateQuestionBankStatusRequest](#updatequestionbankstatusrequest) | [QuestionBankResponse](#questionbankresponse) |
| POST | `/api/questions` | JWT + `lms.exercise.manage` | Body: [CreateQuestionRequest](#createquestionrequest) | [QuestionResponse](#questionresponse) |
| GET | `/api/questions/{id}` | JWT | — | [QuestionResponse](#questionresponse) |
| PUT | `/api/questions/{id}` | JWT + `lms.exercise.manage` | Body: [UpdateQuestionRequest](#updatequestionrequest) | [QuestionResponse](#questionresponse) |

## Soạn & giao đề (UC-40)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/exercises` | JWT + `lms.exercise.manage` | Body: [CreateExerciseRequest](#createexerciserequest) | [ExerciseResponse](#exerciseresponse) |
| GET | `/api/exercises/{id}` | JWT | — | [ExerciseResponse](#exerciseresponse) |
| POST | `/api/exercises/{id}/assign` | JWT | Body: [AssignExerciseRequest](#assignexerciserequest) | [ExerciseAssignmentResponse](#exerciseassignmentresponse) |
| POST | `/api/exercises/{id}/publish` | JWT + `lms.exercise.manage` | — | [ExerciseResponse](#exerciseresponse) |
| GET | `/api/exercises/{id}/questions` | JWT | — | mảng [ExerciseQuestionResponse](#exercisequestionresponse) |
| POST | `/api/exercises/{id}/questions` | JWT + `lms.exercise.manage` | Body: [AddExerciseQuestionRequest](#addexercisequestionrequest) | [ExerciseQuestionResponse](#exercisequestionresponse) |

## Làm bài & nộp bài (LMS)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/attempts/{id}` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/attempts/{id}/answers` | JWT | — | mảng [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/answers` | JWT | Body: [SaveAnswerRequest](#saveanswerrequest) | [StudentAnswerResponse](#studentanswerresponse) |
| POST | `/api/attempts/{id}/submit` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |
| GET | `/api/exercises/{exerciseId}/attempts` | JWT | — | mảng [ExerciseAttemptResponse](#exerciseattemptresponse) |
| POST | `/api/exercises/{exerciseId}/attempts` | JWT | — | [ExerciseAttemptResponse](#exerciseattemptresponse) |

## Chấm bài thủ công (UC-41)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/answers/{studentAnswerId}/grade` | JWT + `lms.grading.manage` | Body: [GradeAnswerRequest](#gradeanswerrequest) | [StudentAnswerGradingResponse](#studentanswergradingresponse) |
| GET | `/api/grading/pending` | JWT + `lms.grading.manage` | — | mảng [PendingGradingResponse](#pendinggradingresponse) |

## Kế hoạch giảng dạy

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/classes/{classId}/teaching-plans` | JWT | — | mảng [TeachingPlanResponse](#teachingplanresponse) |
| POST | `/api/teaching-plans` | JWT | Body: [CreateTeachingPlanRequest](#createteachingplanrequest) | [TeachingPlanResponse](#teachingplanresponse) |
| GET | `/api/teaching-plans/{id}` | JWT | — | [TeachingPlanResponse](#teachingplanresponse) |
| PUT | `/api/teaching-plans/{id}` | JWT | Body: [UpdateTeachingPlanRequest](#updateteachingplanrequest) | [TeachingPlanResponse](#teachingplanresponse) |
| GET | `/api/teaching-plans/{id}/items` | JWT | — | mảng [TeachingPlanItemResponse](#teachingplanitemresponse) |
| POST | `/api/teaching-plans/{id}/items` | JWT | Body: [AddTeachingPlanItemRequest](#addteachingplanitemrequest) | [TeachingPlanItemResponse](#teachingplanitemresponse) |
| PUT | `/api/teaching-plans/{id}/items/{itemId}` | JWT | Body: [UpdateTeachingPlanItemRequest](#updateteachingplanitemrequest) | [TeachingPlanItemResponse](#teachingplanitemresponse) |

## Chọn lớp đang xem — cổng HS/PH (UC-42)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/portal/students/{studentId}/class-options` | JWT | — | mảng [PortalClassOptionResponse](#portalclassoptionresponse) |

## Cổng phụ huynh

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/portal/parent/children` | JWT | — | mảng [ChildResponse](#childresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/attendance` | JWT | — | mảng [AttendanceMarkResponse](#attendancemarkresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/comments` | JWT | — | mảng [StudentCommentResponse](#studentcommentresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/grades` | JWT | — | mảng [GradeEntryResponse](#gradeentryresponse) |
| GET | `/api/portal/parent/children/{studentId}/classes/{classId}/schedule` | JWT | — | mảng [ClassSessionResponse](#classsessionresponse) |

## Thông báo

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/notifications` | JWT | Query: `pageable` | [PageNotificationResponse](#pagenotificationresponse) |
| GET | `/api/notifications/preferences/{notificationType}` | JWT | — | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| PUT | `/api/notifications/preferences/{notificationType}` | JWT | Body: [NotificationPreferenceRequest](#notificationpreferencerequest) | [NotificationPreferenceResponse](#notificationpreferenceresponse) |
| POST | `/api/notifications/{id}/read` | JWT | — | [NotificationResponse](#notificationresponse) |

## Biểu phí học phí

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/tuition-plan-assignments` | JWT + `finance.manage` | Body: [AssignTuitionPlanRequest](#assigntuitionplanrequest) | [TuitionPlanAssignmentResponse](#tuitionplanassignmentresponse) |
| POST | `/api/finance/tuition-plans` | JWT + `finance.manage` | Body: [CreateTuitionPlanRequest](#createtuitionplanrequest) | [TuitionPlanResponse](#tuitionplanresponse) |
| GET | `/api/finance/tuition-plans/{id}` | JWT + `finance.manage` | — | [TuitionPlanResponse](#tuitionplanresponse) |
| PUT | `/api/finance/tuition-plans/{id}/status` | JWT + `finance.manage` | Body: [UpdateTuitionPlanStatusRequest](#updatetuitionplanstatusrequest) | [TuitionPlanResponse](#tuitionplanresponse) |

## Học bổng

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/scholarships` | JWT + `finance.manage` | Body: [CreateScholarshipRequest](#createscholarshiprequest) | [ScholarshipResponse](#scholarshipresponse) |
| POST | `/api/finance/scholarships/{id}/revoke` | JWT + `finance.manage` | — | [ScholarshipResponse](#scholarshipresponse) |

## Hóa đơn & thanh toán (UC-30)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/finance/invoices/generate` | JWT + `finance.manage` | Body: [GenerateInvoicesRequest](#generateinvoicesrequest) | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/my` | JWT | — | mảng [InvoiceResponse](#invoiceresponse) |
| GET | `/api/finance/invoices/{id}` | JWT | — | [InvoiceResponse](#invoiceresponse) |
| POST | `/api/finance/invoices/{id}/payments` | JWT + `finance.manage` | Body: [RecordManualPaymentRequest](#recordmanualpaymentrequest) | [PaymentResponse](#paymentresponse) |
| POST | `/api/webhooks/bank-payment` | Header `X-Webhook-Secret` | Body: [BankWebhookPaymentRequest](#bankwebhookpaymentrequest)<br>Header: `X-Webhook-Secret` | [PaymentResponse](#paymentresponse) |

## Chi phí vận hành (UC-31)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/operating-expenses` | JWT | Query: `siteId`?, `from`, `to` | mảng [OperatingExpenseResponse](#operatingexpenseresponse) |
| POST | `/api/finance/operating-expenses` | JWT + `finance.manage` | Body: [CreateOperatingExpenseRequest](#createoperatingexpenserequest) | [OperatingExpenseResponse](#operatingexpenseresponse) |
| POST | `/api/finance/operating-expenses/{id}/decision` | JWT + `finance.expense.approve` | Body: [DecideOperatingExpenseRequest](#decideoperatingexpenserequest) | [OperatingExpenseResponse](#operatingexpenseresponse) |

## Báo cáo tài chính (UC-32)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/finance/reports/chain` | JWT + `finance.report.view` | Query: `from`, `to` | [ChainFinancialReportResponse](#chainfinancialreportresponse) |
| GET | `/api/finance/reports/my-sites` | JWT | Query: `from`, `to` | mảng [FinancialReportResponse](#financialreportresponse) |

## Lead & chuyển đổi tuyển sinh (UC-33/34)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/leads` | JWT + `crm.lead.manage` | Body: [CreateLeadRequest](#createleadrequest) | [LeadResponse](#leadresponse) |
| GET | `/api/leads/my-leads` | JWT | — | mảng [LeadResponse](#leadresponse) |
| GET | `/api/leads/open` | JWT | — | mảng [LeadResponse](#leadresponse) |
| GET | `/api/leads/{id}` | JWT | — | [LeadResponse](#leadresponse) |
| PUT | `/api/leads/{id}/assign` | JWT + `crm.lead.assign` | Body: [AssignLeadRequest](#assignleadrequest) | [LeadResponse](#leadresponse) |
| POST | `/api/leads/{id}/convert` | JWT + `crm.lead.manage` | Body: [ConvertLeadRequest](#convertleadrequest) | [LeadResponse](#leadresponse) |
| PUT | `/api/leads/{id}/status` | JWT + `crm.lead.manage` | Body: [UpdateLeadStatusRequest](#updateleadstatusrequest) | [LeadResponse](#leadresponse) |

## Điểm trường (UC-36)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/sites` | JWT | — | mảng [SiteResponse](#siteresponse) |
| POST | `/api/sites` | JWT + `facility.manage` | Body: [CreateSiteRequest](#createsiterequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}` | JWT | — | [SiteResponse](#siteresponse) |
| PUT | `/api/sites/{id}` | JWT + `facility.manage` | Body: [UpdateSiteRequest](#updatesiterequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}/attendance-summary` | JWT | — | mảng [PartnerAttendanceSummaryResponse](#partnerattendancesummaryresponse) |
| PUT | `/api/sites/{id}/manager` | JWT + `facility.manage` | Body: [AssignSiteManagerRequest](#assignsitemanagerrequest) | [SiteResponse](#siteresponse) |
| GET | `/api/sites/{id}/teachers` | JWT | — | mảng [SiteTeacherResponse](#siteteacherresponse) |
| POST | `/api/sites/{id}/teachers` | JWT + `facility.manage` | Body: [AssignSiteTeacherRequest](#assignsiteteacherrequest) | [SiteTeacherResponse](#siteteacherresponse) |
| DELETE | `/api/sites/{id}/teachers/{siteTeacherId}` | JWT + `facility.manage` | — | 200 (không có body) |

## Hợp đồng trường liên kết (UC-36b)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/partner-contracts` | JWT + `facility.manage` | Body: [CreatePartnerContractRequest](#createpartnercontractrequest) | [PartnerContractResponse](#partnercontractresponse) |
| GET | `/api/partner-contracts/expiring` | JWT + `facility.manage` | Query: `withinDays` | mảng [ExpiringPartnerContractResponse](#expiringpartnercontractresponse) |
| DELETE | `/api/partner-contracts/{id}` | JWT + `facility.manage` | — | 200 (không có body) |
| PUT | `/api/partner-contracts/{id}` | JWT + `facility.manage` | Body: [UpdatePartnerContractRequest](#updatepartnercontractrequest) | [PartnerContractResponse](#partnercontractresponse) |
| POST | `/api/partner-contracts/{id}/terminate` | JWT + `facility.manage` | — | [PartnerContractResponse](#partnercontractresponse) |
| GET | `/api/sites/{siteId}/partner-contracts` | JWT + `facility.manage` | — | mảng [PartnerContractResponse](#partnercontractresponse) |

## Phòng học (UC-37)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/rooms` | JWT + `facility.room.manage` | Body: [CreateRoomRequest](#createroomrequest) | [RoomResponse](#roomresponse) |
| PUT | `/api/rooms/{id}` | JWT + `facility.room.manage` | Body: [UpdateRoomRequest](#updateroomrequest) | [RoomResponse](#roomresponse) |
| GET | `/api/sites/{siteId}/rooms` | JWT | — | mảng [RoomResponse](#roomresponse) |

## Thiết bị dạy học (UC-37)

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/equipment` | JWT + `facility.room.manage` | Body: [CreateEquipmentRequest](#createequipmentrequest) | [EquipmentResponse](#equipmentresponse) |
| PUT | `/api/equipment/{id}/status` | JWT + `facility.room.manage` | Body: [UpdateEquipmentStatusRequest](#updateequipmentstatusrequest) | [EquipmentResponse](#equipmentresponse) |
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

## department-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/departments` | JWT | — | mảng [DepartmentResponse](#departmentresponse) |
| POST | `/api/departments` | JWT + `hrm.manage` | Body: [CreateDepartmentRequest](#createdepartmentrequest) | [DepartmentResponse](#departmentresponse) |
| DELETE | `/api/departments/{id}` | JWT + `hrm.manage` | — | 200 (không có body) |
| GET | `/api/departments/{id}` | JWT | — | [DepartmentResponse](#departmentresponse) |
| PUT | `/api/departments/{id}` | JWT + `hrm.manage` | Body: [UpdateDepartmentRequest](#updatedepartmentrequest) | [DepartmentResponse](#departmentresponse) |

## employee-batch-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/employee-imports` | JWT + `hrm.manage` | Form-data: `file` (tệp) | [EmployeeBatchImportResponse](#employeebatchimportresponse) |
| GET | `/api/employee-imports/{id}` | JWT + `hrm.manage` | — | [EmployeeBatchImportResponse](#employeebatchimportresponse) |

## grade-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/classes/{classId}/grade-periods/{gradePeriodId}/grades/import` | JWT | Form-data: `file` (tệp) | [GradeImportResponse](#gradeimportresponse) |
| GET | `/api/grade-imports/{id}` | JWT | — | [GradeImportResponse](#gradeimportresponse) |

## parent-batch-import-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| POST | `/api/parent-imports` | JWT + `student.manage` | Form-data: `file` (tệp) | [ParentBatchImportResponse](#parentbatchimportresponse) |
| GET | `/api/parent-imports/{id}` | JWT + `student.manage` | — | [ParentBatchImportResponse](#parentbatchimportresponse) |

## parent-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/parents` | JWT + `student.manage` | Query: `query`? | mảng [ParentResponse](#parentresponse) |
| POST | `/api/parents` | JWT + `student.manage` | Body: [CreateParentRequest](#createparentrequest) | [ParentResponse](#parentresponse) |
| GET | `/api/parents/{id}` | JWT + `student.manage` | — | [ParentResponse](#parentresponse) |
| PUT | `/api/parents/{id}` | JWT + `student.manage` | Body: [UpdateParentRequest](#updateparentrequest) | [ParentResponse](#parentresponse) |

## position-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/positions` | JWT | — | mảng [PositionResponse](#positionresponse) |
| POST | `/api/positions` | JWT + `hrm.manage` | Body: [CreatePositionRequest](#createpositionrequest) | [PositionResponse](#positionresponse) |
| DELETE | `/api/positions/{id}` | JWT + `hrm.manage` | — | 200 (không có body) |
| GET | `/api/positions/{id}` | JWT | — | [PositionResponse](#positionresponse) |
| PUT | `/api/positions/{id}` | JWT + `hrm.manage` | Body: [UpdatePositionRequest](#updatepositionrequest) | [PositionResponse](#positionresponse) |
| GET | `/api/positions/{id}/default-roles` | JWT + `hrm.manage` | — | [PositionDefaultRolesResponse](#positiondefaultrolesresponse) |
| PUT | `/api/positions/{id}/default-roles` | JWT + `hrm.manage` | Body: [UpdatePositionDefaultRolesRequest](#updatepositiondefaultrolesrequest) | 200 (không có body) |

## skill-controller

| Method | Path | Auth | Input | Output |
|---|---|---|---|---|
| GET | `/api/skills` | JWT | Query: `includeInactive`? | mảng [SkillResponse](#skillresponse) |
| POST | `/api/skills` | JWT + `academic.grade.manage` | Body: [CreateSkillRequest](#createskillrequest) | [SkillResponse](#skillresponse) |
| PUT | `/api/skills/{id}` | JWT + `academic.grade.manage` | Body: [UpdateSkillRequest](#updateskillrequest) | [SkillResponse](#skillresponse) |

---

## Phụ lục: Schemas

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

### AddLessonMaterialRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `durationSeconds` | integer |  |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string | ✔ |
| `isDownloadable` | boolean |  |
| `materialType` | string | ✔ |
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

### AssignExerciseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) | ✔ |
| `dueAt` | string (date-time) |  |
| `latePenaltyPercent` | number |  |
| `lateSubmissionAllowed` | boolean |  |
| `targetStudentIds` | mảng integer (int64) |  |

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

### BankWebhookPaymentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number | ✔ |
| `bankTransactionId` | string | ✔ |
| `invoiceNumber` | string | ✔ |
| `paidAt` | string (date-time) |  |

### CancelClassSessionRequest

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

### ClassEnrollmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `enrolledDate` | string (date) |  |
| `id` | integer (int64) |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `withdrawReason` | string |  |
| `withdrawnDate` | string (date) |  |

### ClassResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
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
| `semester` | string |  |
| `siteId` | integer (int64) |  |
| `siteName` | string |  |
| `startDate` | string (date) |  |
| `status` | string |  |

### ClassSessionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `cancellationReason` | string |  |
| `classId` | integer (int64) |  |
| `endTime` | [LocalTime](#localtime) |  |
| `id` | integer (int64) |  |
| `primaryTeacherId` | integer (int64) |  |
| `primaryTeacherName` | string |  |
| `rescheduledToSessionId` | integer (int64) |  |
| `roomId` | integer (int64) |  |
| `roomName` | string |  |
| `sessionDate` | string (date) |  |
| `sessionType` | string |  |
| `startTime` | [LocalTime](#localtime) |  |
| `status` | string |  |

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

### ConvertLeadRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `studentCode` | string | ✔ |

### CreateClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `classCode` | string | ✔ |
| `classType` | string | ✔ |
| `curriculumId` | integer (int64) | ✔ |
| `endDate` | string (date) |  |
| `maxStudents` | integer | ✔ |
| `minStudents` | integer |  |
| `name` | string | ✔ |
| `semester` | string |  |
| `siteId` | integer (int64) | ✔ |
| `startDate` | string (date) | ✔ |

### CreateClassSessionRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `endTime` | [LocalTime](#localtime) | ✔ |
| `primaryTeacherId` | integer (int64) | ✔ |
| `roomId` | integer (int64) |  |
| `sessionDate` | string (date) | ✔ |
| `sessionType` | string | ✔ |
| `startTime` | [LocalTime](#localtime) | ✔ |

### CreateCommendationRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `amount` | number |  |
| `recordDate` | string (date) | ✔ |
| `recordType` | string | ✔ |
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

### CreateExerciseRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `code` | string | ✔ |
| `curriculumId` | integer (int64) |  |
| `exerciseType` | string | ✔ |
| `maxAttempts` | integer |  |
| `showCorrectAnswers` | boolean |  |
| `subjectId` | integer (int64) |  |
| `timeLimitMinutes` | integer |  |
| `title` | string | ✔ |
| `totalPoints` | number | ✔ |

### CreateGradeComponentRequest

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
| `weightInPeriod` | number | ✔ |

### CreateGradePeriodRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string | ✔ |
| `displayOrder` | integer |  |
| `endDate` | string (date) |  |
| `name` | string | ✔ |
| `startDate` | string (date) |  |
| `weightInFinal` | number | ✔ |

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

### CreateLessonRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `code` | string | ✔ |
| `curriculumId` | integer (int64) |  |
| `durationMinutes` | integer |  |
| `lessonOrder` | integer |  |
| `lessonType` | string | ✔ |
| `subjectId` | integer (int64) |  |
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
| `defaultPoints` | number |  |
| `difficulty` | string |  |
| `explanation` | string |  |
| `imageUrl` | string |  |
| `questionBankId` | integer (int64) | ✔ |
| `questionType` | string | ✔ |
| `referencePassage` | string |  |
| `skill` | string |  |
| `tags` | mảng string |  |

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
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) | ✔ |
| `commentType` | string | ✔ |
| `content` | string | ✔ |
| `gradePeriodId` | integer (int64) |  |
| `isWarning` | boolean |  |
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
| `academicYear` | string |  |
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
| `phone` | string |  |
| `roleCodes` | mảng string |  |
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

### DecideGradesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `decision` | string | ✔ |
| `gradeEntryIds` | mảng integer (int64) |  |
| `gradePeriodResultIds` | mảng integer (int64) |  |

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

### EnrollStudentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `enrolledDate` | string (date) | ✔ |
| `studentId` | integer (int64) | ✔ |

### EnterAttendanceMarkRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceReason` | string |  |
| `minutesEarlyLeave` | integer |  |
| `minutesLate` | integer |  |
| `status` | string | ✔ |
| `studentId` | integer (int64) | ✔ |

### EnterGradePeriodResultRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `level` | string |  |
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

### ExerciseAssignmentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `assignedBy` | integer (int64) |  |
| `availableFrom` | string (date-time) |  |
| `classId` | integer (int64) |  |
| `dueAt` | string (date-time) |  |
| `exerciseId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `latePenaltyPercent` | number |  |
| `lateSubmissionAllowed` | boolean |  |
| `status` | string |  |
| `targetStudentIds` | mảng integer (int64) |  |

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
| `startedAt` | string (date-time) |  |
| `status` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `totalScore` | number |  |

### ExerciseQuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `exerciseId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `points` | number |  |
| `questionContent` | string |  |
| `questionId` | integer (int64) |  |
| `questionType` | string |  |

### ExerciseResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `allowRetake` | boolean |  |
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumId` | integer (int64) |  |
| `exerciseType` | string |  |
| `hasEssayOrSpeaking` | boolean |  |
| `id` | integer (int64) |  |
| `maxAttempts` | integer |  |
| `showCorrectAnswers` | boolean |  |
| `status` | string |  |
| `subjectId` | integer (int64) |  |
| `timeLimitMinutes` | integer |  |
| `title` | string |  |
| `totalPoints` | number |  |

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

### GradeComponentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `displayOrder` | integer |  |
| `gradePeriodId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `maxScore` | number |  |
| `name` | string |  |
| `passThreshold` | number |  |
| `scaleType` | string |  |
| `skillId` | integer (int64) |  |
| `subjectId` | integer (int64) |  |
| `weightInPeriod` | number |  |

### GradeEntryResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `absenceFlag` | boolean |  |
| `approvedAt` | string (date-time) |  |
| `approvedBy` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `enteredBy` | integer (int64) |  |
| `gradeComponentId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `score` | number |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `teacherNote` | string |  |

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

### GradePeriodResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `code` | string |  |
| `curriculumId` | integer (int64) |  |
| `displayOrder` | integer |  |
| `endDate` | string (date) |  |
| `id` | integer (int64) |  |
| `name` | string |  |
| `startDate` | string (date) |  |
| `status` | string |  |
| `weightInFinal` | number |  |

### GradePeriodResultResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvedAt` | string (date-time) |  |
| `approvedBy` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `enteredBy` | integer (int64) |  |
| `gradePeriodId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `importJobId` | integer (int64) |  |
| `level` | string |  |
| `overallScore` | number |  |
| `scaleType` | string |  |
| `source` | string |  |
| `status` | string |  |
| `studentCode` | string |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |

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

### LeaveRequestResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `attachmentUrl` | string |  |
| `currentApproverUserId` | integer (int64) |  |
| `currentStep` | integer |  |
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

### LessonMaterialResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `durationSeconds` | integer |  |
| `fileSizeBytes` | integer (int64) |  |
| `fileUrl` | string |  |
| `id` | integer (int64) |  |
| `isDownloadable` | boolean |  |
| `lessonId` | integer (int64) |  |
| `materialType` | string |  |
| `title` | string |  |

### LessonResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `classId` | integer (int64) |  |
| `code` | string |  |
| `createdBy` | integer (int64) |  |
| `curriculumId` | integer (int64) |  |
| `durationMinutes` | integer |  |
| `id` | integer (int64) |  |
| `lessonOrder` | integer |  |
| `lessonType` | string |  |
| `publishedAt` | string (date-time) |  |
| `status` | string |  |
| `subjectId` | integer (int64) |  |
| `title` | string |  |

### LinkParentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `isFinancialResponsible` | boolean |  |
| `isPrimaryContact` | boolean |  |
| `notes` | string |  |
| `parentId` | integer (int64) | ✔ |
| `relationship` | string | ✔ |

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

### PeriodAverageResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `average` | number |  |
| `classId` | integer (int64) |  |
| `componentsEntered` | integer |  |
| `componentsTotal` | integer |  |
| `gradePeriodId` | integer (int64) |  |
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

### QuestionResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `audioUrl` | string |  |
| `choices` | mảng [QuestionChoiceResponse](#questionchoiceresponse) |  |
| `content` | string |  |
| `createdBy` | integer (int64) |  |
| `defaultPoints` | number |  |
| `difficulty` | string |  |
| `explanation` | string |  |
| `id` | integer (int64) |  |
| `imageUrl` | string |  |
| `questionBankId` | integer (int64) |  |
| `questionType` | string |  |
| `referencePassage` | string |  |
| `skill` | string |  |
| `status` | string |  |
| `tags` | mảng string |  |

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

### SortObject

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `ascending` | boolean |  |
| `direction` | string |  |
| `ignoreCase` | boolean |  |
| `nullHandling` | string |  |
| `property` | string |  |

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
| `exerciseAttemptId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `isAutoGradable` | boolean |  |
| `isCorrect` | boolean |  |
| `questionId` | integer (int64) |  |
| `selectedChoiceIds` | mảng integer (int64) |  |

### StudentBatchImportResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `errorSummary` | mảng object |  |
| `failedRows` | integer |  |
| `id` | integer (int64) |  |
| `sourceFileName` | string |  |
| `status` | string |  |
| `successRows` | integer |  |
| `totalRows` | integer |  |

### StudentCommentResponse

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `approvedAt` | string (date-time) |  |
| `approvedBy` | integer (int64) |  |
| `classId` | integer (int64) |  |
| `classSessionId` | integer (int64) |  |
| `commentDate` | string (date) |  |
| `commentType` | string |  |
| `content` | string |  |
| `gradePeriodId` | integer (int64) |  |
| `id` | integer (int64) |  |
| `isWarning` | boolean |  |
| `rejectionReason` | string |  |
| `severity` | string |  |
| `status` | string |  |
| `structuredContent` | object |  |
| `studentFullName` | string |  |
| `studentId` | integer (int64) |  |
| `submittedAt` | string (date-time) |  |
| `teacherId` | integer (int64) |  |
| `visibleToParentAt` | string (date-time) |  |

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

### SubmitGradesRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `gradeEntryIds` | mảng integer (int64) |  |
| `gradePeriodResultIds` | mảng integer (int64) |  |

### SubmitPartnerFeedbackRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string | ✔ |
| `feedbackType` | string | ✔ |
| `priority` | string |  |

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

### UpdateAssignmentStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `comment` | string |  |
| `status` | string | ✔ |

### UpdateClassRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `academicYear` | string |  |
| `endDate` | string (date) |  |
| `maxStudents` | integer | ✔ |
| `minStudents` | integer |  |
| `name` | string | ✔ |
| `semester` | string |  |
| `startDate` | string (date) | ✔ |
| `status` | string | ✔ |

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

### UpdateGradeComponentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `maxScore` | number |  |
| `name` | string | ✔ |
| `passThreshold` | number |  |
| `weightInPeriod` | number | ✔ |

### UpdateGradePeriodRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `displayOrder` | integer |  |
| `endDate` | string (date) |  |
| `name` | string | ✔ |
| `startDate` | string (date) |  |
| `status` | string | ✔ |
| `weightInFinal` | number | ✔ |

### UpdateLeadStatusRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `finalNote` | string |  |
| `outcome` | string |  |
| `status` | string | ✔ |

### UpdateLessonRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `durationMinutes` | integer |  |
| `lessonOrder` | integer |  |
| `status` | string | ✔ |
| `subjectId` | integer (int64) |  |
| `title` | string | ✔ |

### UpdateParentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `address` | string |  |
| `notes` | string |  |
| `occupation` | string |  |
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
| `defaultPoints` | number |  |
| `explanation` | string |  |
| `imageUrl` | string |  |
| `referencePassage` | string |  |
| `status` | string |  |
| `tags` | mảng string |  |

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

### UpdateStudentCommentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `content` | string | ✔ |
| `isWarning` | boolean |  |
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

### WithdrawEnrollmentRequest

| Trường | Kiểu | Bắt buộc |
|---|---|---|
| `reason` | string |  |
| `withdrawnDate` | string (date) | ✔ |

