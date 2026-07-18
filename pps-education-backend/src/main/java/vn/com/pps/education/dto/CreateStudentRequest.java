package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * UC-13 Main Flow bước 1-3: khởi tạo hồ sơ học sinh mới. Cung cấp ĐÚNG 1
 * trong 2 (kiểm tra ở StudentService):
 * - userId: gắn hồ sơ vào tài khoản đã có sẵn.
 * - newAccount: học sinh chưa có tài khoản — tạo tài khoản (gán role
 *   STUDENT) kèm hồ sơ trong cùng 1 transaction.
 * studentCode do người dùng tự nhập (duy nhất — StudentService kiểm tra
 * trùng, ném DuplicateStudentCodeException), không tự sinh.
 */
public record CreateStudentRequest(
        Long userId,
        @Valid CreateUserRequest newAccount,
        @NotBlank String studentCode,
        @NotNull @Past LocalDate dateOfBirth,
        String gender,
        String portraitUrl,
        Long primarySiteId,
        String originalSchool,
        String originalClass,
        @NotNull LocalDate enrollmentDate,
        String notes
) {}
