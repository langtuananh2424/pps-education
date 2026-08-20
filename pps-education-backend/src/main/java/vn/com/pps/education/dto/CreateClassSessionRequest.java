package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-48: Xếp lịch buổi học (FR-ACA-05) — xem Javadoc ClassSessionService.
 * Đảo ngược quyết định 2026-08-13 (xác nhận lại 2026-08-19): không còn
 * nhập startTime/endTime tự do (đổi sang chọn tiết theo cấu hình
 * site_period_templates của điểm trường lớp này), và không còn tự động
 * suy ra giáo viên phụ trách — chọn tay primaryTeacherId (bắt buộc) +
 * assistantTeacherId/cmTeacherId (tuỳ chọn), gán riêng theo buổi.
 * dayPart (V129, xác nhận 2026-08-20): buổi Sáng/Chiều/Tối — mỗi buổi
 * đánh số tiết riêng, periodNumbers chỉ có nghĩa trong phạm vi dayPart này.
 */
public record CreateClassSessionRequest(
        @NotNull LocalDate sessionDate,
        @NotBlank String dayPart,
        /** Danh sách periodNumber (theo site_period_templates cùng dayPart, của điểm trường lớp này) buổi học này chiếm — VD [1,2]. */
        @NotEmpty List<Integer> periodNumbers,
        Long roomId,
        @NotBlank String sessionType,
        /** Loại giáo viên (VIETNAMESE/FOREIGN) — bắt buộc, chỉ để hiển thị cho HS/PH, không ràng buộc primaryTeacherId. */
        @NotBlank String teacherType,
        @NotNull Long primaryTeacherId,
        Long assistantTeacherId,
        Long cmTeacherId,
        /** Bắt buộc khi sessionType=MAKEUP (buổi này bù cho buổi nào); phải để trống với loại khác. */
        Long makeupForSessionId
) {}
