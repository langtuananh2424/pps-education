package vn.com.pps.education.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex) {
        return error(HttpStatus.LOCKED, ex.getMessage());
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<Object> handleAccountInactive(AccountInactiveException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<Object> handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(GoogleAccountNotProvisionedException.class)
    public ResponseEntity<Object> handleGoogleAccountNotProvisioned(GoogleAccountNotProvisionedException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Object> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicateEmployeeCodeException.class, DuplicateContractNumberException.class,
            ActiveContractAlreadyExistsException.class, EmployeeAlreadyExistsException.class,
            StudentAlreadyExistsException.class, ParentAlreadyExistsException.class,
            ParentStudentLinkAlreadyExistsException.class, StudentContactRoleConflictException.class,
            DuplicateCurriculumCodeException.class, DuplicateClassCodeException.class,
            ClassEnrollmentAlreadyActiveException.class, RoomConflictException.class})
    public ResponseEntity<Object> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({CurriculumUpdateConfirmationRequiredException.class, ApprovalAlreadyDecidedException.class,
            GradePeriodWeightExceededException.class, GradeComponentLockedException.class})
    public ResponseEntity<Object> handleConfirmationRequired(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({CurriculumNotActiveException.class, LinkedClassRequiresPartnerSiteException.class,
            CurriculumNotEditableException.class, CurriculumNotAvailableForSiteException.class,
            InvalidGradeScoreException.class, GradeEntryNotEditableException.class,
            AttendanceSessionNotEditableException.class})
    public ResponseEntity<Object> handleClassSetupRejected(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler({NotHeadAcademicException.class, NotAuthorizedForClassManagementException.class,
            NotAuthorizedForPortalAccessException.class, NotSiteManagerForSiteException.class,
            NotAssignedTeacherForClassException.class, NotAssignedTeacherForSessionException.class})
    public ResponseEntity<Object> handleAcademicAuthorization(RuntimeException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidStudentStatusTransitionException.class)
    public ResponseEntity<Object> handleInvalidStudentStatusTransition(InvalidStudentStatusTransitionException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ManagementExemptFromAttendanceException.class)
    public ResponseEntity<Object> handleManagementExempt(ManagementExemptFromAttendanceException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({OutsideAttendanceWindowException.class, OutsideGpsRadiusException.class,
            BiometricVerificationFailedException.class, NotAWorkingDayException.class,
            AttendanceMethodNotAvailableException.class})
    public ResponseEntity<Object> handleAttendanceRejected(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ExecutiveExemptFromLeaveRequestException.class)
    public ResponseEntity<Object> handleExecutiveExempt(ExecutiveExemptFromLeaveRequestException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NotCurrentApproverException.class)
    public ResponseEntity<Object> handleNotCurrentApprover(NotCurrentApproverException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LeaveRequestAlreadyFinalizedException.class)
    public ResponseEntity<Object> handleLeaveRequestFinalized(LeaveRequestAlreadyFinalizedException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotHrManagerException.class)
    public ResponseEntity<Object> handleNotHrManager(NotHrManagerException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NotAuthorizedForStudentStatusException.class)
    public ResponseEntity<Object> handleNotAuthorizedForStudentStatus(NotAuthorizedForStudentStatusException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", status.value(),
                "message", message
        ));
    }
}
