package vn.com.pps.education.exception;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** @PreAuthorize("hasPermission(...)") từ chối — Hybrid PBAC (UC-02..05), giữ format lỗi nhất quán với các Not*Exception khác. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Object> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return errorByKey(HttpStatus.FORBIDDEN, "error.auth.accessDenied", "Tài khoản không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex) {
        return error(HttpStatus.LOCKED, ex);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<Object> handleAccountInactive(AccountInactiveException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<Object> handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(GoogleAccountNotProvisionedException.class)
    public ResponseEntity<Object> handleGoogleAccountNotProvisioned(GoogleAccountNotProvisionedException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Object> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler({DuplicateEmployeeCodeException.class, DuplicateContractNumberException.class,
            ActiveContractAlreadyExistsException.class, EmployeeAlreadyExistsException.class,
            DuplicateStudentCodeException.class,
            StudentAlreadyExistsException.class, ParentAlreadyExistsException.class,
            ParentStudentLinkAlreadyExistsException.class, StudentContactRoleConflictException.class,
            DuplicateCurriculumCodeException.class, DuplicateClassCodeException.class,
            ClassEnrollmentAlreadyActiveException.class, RoomConflictException.class,
            TeacherScheduleConflictException.class, ClassScheduleConflictException.class,
            ClassSessionOutsideClassPeriodException.class,
            DuplicateSiteCodeException.class, DuplicateLeadPhoneException.class,
            DuplicatePartnerContractNumberException.class, ActivePartnerContractAlreadyExistsException.class,
            DuplicateRoomCodeException.class, DuplicateEquipmentCodeException.class,
            DuplicateUserAccountException.class, DuplicateRoleCodeException.class,
            SiteTeacherAlreadyAssignedException.class, DuplicateDepartmentCodeException.class,
            DuplicatePositionCodeException.class, DuplicateSkillCodeException.class,
            TaskAssigneeAlreadyAssignedException.class, MakeupSessionAlreadyLinkedException.class,
            DuplicateQuestionContentException.class, DuplicateAcademicYearCodeException.class,
            DuplicateShiftCodeException.class,
            WorkCalendarOverrideAlreadyExistsException.class, ShiftAssignmentOverlapException.class,
            EmployeeShiftAlreadyEndedException.class, DuplicateSitePeriodNumberException.class})
    public ResponseEntity<Object> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({CurriculumUpdateConfirmationRequiredException.class, ApprovalAlreadyDecidedException.class,
            GradeComponentSetupScaleMismatchException.class, GradeComponentLockedException.class,
            GradeAlreadyPublishedException.class, EntranceComponentLockedException.class})
    public ResponseEntity<Object> handleConfirmationRequired(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({CurriculumNotActiveException.class, LinkedClassRequiresPartnerSiteException.class,
            CurriculumNotEditableException.class, CurriculumNotAvailableForSiteException.class,
            InvalidGradeScoreException.class, GradeNotEditableException.class,
            AttendanceSessionNotEditableException.class, StudentCommentNotEditableException.class,
            InvalidCommentContextException.class, QuestionLockedException.class,
            ExerciseNotAvailableException.class, RetakeNotAllowedException.class,
            SubmissionPastDeadlineException.class, AttemptNotEditableException.class,
            AnswerNotManuallyGradableException.class, InvalidTeachingPlanPeriodException.class,
            InvalidTaskStatusTransitionException.class, IncompleteLeadDataException.class,
            LeadNotQualifiedException.class, InvalidLeadStatusTransitionException.class,
            InvalidFeedbackStatusTransitionException.class, InvalidClassSessionStatusTransitionException.class,
            OperatingExpenseAlreadyDecidedException.class, PartnerContractNotDeletableException.class,
            TuitionPlanNotActiveException.class, RoleNotDeletableException.class,
            DepartmentNotDeletableException.class, PositionNotDeletableException.class,
            GradeComponentNotDeletableException.class, GradeComponentSetupNotDeletableException.class,
            MissingLessonContentException.class, MissingCommentContentException.class, HomeworkNextConflictException.class,
            NoUpcomingClassSessionException.class, VideoNotYetQualifiedException.class,
            QuizAlreadyCompletedException.class, ListeningHintNotUnlockedException.class,
            ClassSessionNotCheckableException.class, SitePeriodTemplateNotDeletableException.class,
            EntranceAssessmentNotDeletableException.class})
    public ResponseEntity<Object> handleClassSetupRejected(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler({NotAuthorizedForPortalAccessException.class, NotSiteManagerForSiteException.class,
            NotAssignedTeacherForClassException.class, NotAssignedTeacherForSessionException.class,
            AssigneeOutsideDepartmentException.class,
            NotTaskParticipantException.class, NotTaskCreatorException.class, NotAuthorizedForFeedbackException.class,
            NotAuthorizedForTaskOverviewException.class})
    public ResponseEntity<Object> handleAcademicAuthorization(RuntimeException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(InvalidStudentStatusTransitionException.class)
    public ResponseEntity<Object> handleInvalidStudentStatusTransition(InvalidStudentStatusTransitionException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ManagementExemptFromAttendanceException.class)
    public ResponseEntity<Object> handleManagementExempt(ManagementExemptFromAttendanceException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler({OutsideAttendanceWindowException.class, OutsideGpsRadiusException.class,
            BiometricVerificationFailedException.class, NotAWorkingDayException.class,
            AttendanceMethodNotAvailableException.class, AlreadyCheckedInException.class,
            AlreadyCheckedOutException.class})
    public ResponseEntity<Object> handleAttendanceRejected(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ExecutiveExemptFromLeaveRequestException.class)
    public ResponseEntity<Object> handleExecutiveExempt(ExecutiveExemptFromLeaveRequestException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(NotCurrentApproverException.class)
    public ResponseEntity<Object> handleNotCurrentApprover(NotCurrentApproverException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(LeaveRequestAlreadyFinalizedException.class)
    public ResponseEntity<Object> handleLeaveRequestFinalized(LeaveRequestAlreadyFinalizedException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    /** UC-53 A1 — cột Excel không khớp cấu hình kỳ đánh giá, dừng toàn bộ import. */
    @ExceptionHandler(GradeImportColumnMismatchException.class)
    public ResponseEntity<Object> handleGradeImportColumnMismatch(GradeImportColumnMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    /** UC-67 A3 — biểu thức công thức placeholder trong mẫu báo cáo sai cú pháp. */
    @ExceptionHandler(InvalidTemplatePlaceholderException.class)
    public ResponseEntity<Object> handleInvalidTemplatePlaceholder(InvalidTemplatePlaceholderException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    /** UC-68 A1 — thiếu dữ liệu cho 1 placeholder khi xuất báo cáo. */
    @ExceptionHandler(MissingReportDataException.class)
    public ResponseEntity<Object> handleMissingReportData(MissingReportDataException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(NotHrManagerException.class)
    public ResponseEntity<Object> handleNotHrManager(NotHrManagerException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(InvalidWebhookSecretException.class)
    public ResponseEntity<Object> handleInvalidWebhookSecret(InvalidWebhookSecretException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    /** UC-47 / A2 — không thể tự khóa tài khoản của chính mình. */
    @ExceptionHandler(SelfAccountLockException.class)
    public ResponseEntity<Object> handleSelfAccountLock(SelfAccountLockException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    /**
     * Bean Validation (@Valid) từ chối — trước đây KHÔNG có handler riêng nên rơi vào shape lỗi mặc
     * định của Spring Boot (khác hẳn {timestamp,status,message} dùng ở mọi handler khác trong class
     * này), FE không đọc được message thống nhất. Gộp field error đầu tiên (đủ dùng cho form 1 lỗi
     * tại 1 thời điểm, khớp cách hiển thị lỗi hiện có ở FE) làm message chính.
     *
     * message song ngữ: Spring Boot tự wire bean {@code messageSource} (I18nConfig) vào
     * LocalValidatorFactoryBean mặc định, nên {@code FieldError.getDefaultMessage()} đã được
     * interpolate đúng locale TRƯỚC khi tới đây — chỉ cần khai báo key chuẩn Hibernate Validator
     * (VD {@code jakarta.validation.constraints.NotBlank.message}) trong messages_{vi,en}.properties,
     * không cần file ValidationMessages riêng.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(null);
        if (message == null) {
            return errorByKey(HttpStatus.BAD_REQUEST, "error.validation.invalidData", "Dữ liệu không hợp lệ.");
        }
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status.value(),
                "message", message
        ));
    }

    /**
     * Overload dịch song ngữ — chỉ áp dụng cho exception đã migrate sang LocalizedMessage (messageKey
     * != null); exception chưa migrate rơi vào nhánh else, giữ NGUYÊN hành vi cũ (ex.getMessage()
     * tiếng Việt cứng), không đổi gì cho tới khi chủ động migrate từng cái (xem kế hoạch đồng bộ song
     * ngữ). An toàn để áp dụng cho MỌI exception (kể cả JDK như IllegalArgumentException) — nhánh
     * `instanceof LocalizedMessage` chỉ đơn giản là false với exception chưa/không migrate được.
     */
    private ResponseEntity<Object> error(HttpStatus status, RuntimeException ex) {
        String message = ex.getMessage();
        if (ex instanceof LocalizedMessage lm && lm.messageKey() != null) {
            message = messageSource.getMessage(lm.messageKey(), lm.messageArgs(), message, LocaleContextHolder.getLocale());
        }
        return error(status, message);
    }

    /** Dịch trực tiếp qua message key — dùng cho lỗi KHÔNG gắn với 1 exception cụ thể (literal cũ trong handler, không phải ex.getMessage()). */
    private ResponseEntity<Object> errorByKey(HttpStatus status, String messageKey, String fallbackVi) {
        String message = messageSource.getMessage(messageKey, null, fallbackVi, LocaleContextHolder.getLocale());
        return error(status, message);
    }
}
