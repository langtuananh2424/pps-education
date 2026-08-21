package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.ShiftHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateShiftRequest;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.UpdateShiftRequest;
import vn.com.pps.education.exception.DuplicateShiftCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ShiftHistoryRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * UC-70: Quản lý Ca làm việc (shifts) — bổ sung HOÀN TOÀN ngoài SDD/SRS gốc,
 * đã xác nhận với người dùng 2026-08-13. shifts/employee_shifts/work_calendar
 * (V7) trước đây chỉ được AttendanceService (UC-09) đọc, chưa từng có
 * Controller/UI nào tạo dữ liệu — không ca nào được gán khiến mọi lượt chấm
 * công thật bị từ chối "không phải ngày làm việc". Xem
 * docs/uc/phan-he-04-nhan-su.md (ghi chú dưới UC-09).
 */
@Service
public class ShiftService {

    // 1=T2...7=CN (khớp Shift.appliesToWeekdays/AttendanceService.matchesShiftPattern), phân cách dấu
    // phẩy, không khoảng trắng, không trùng số -- AttendanceService không tự validate format này khi
    // đọc lúc chấm công, lỗi định dạng phải bị chặn ngay lúc tạo/sửa Shift.
    private static final Pattern WEEKDAYS_PATTERN = Pattern.compile("^[1-7](,[1-7])*$");

    private final ShiftRepository shiftRepository;
    private final ShiftHistoryRepository shiftHistoryRepository;
    private final UserRepository userRepository;

    public ShiftService(ShiftRepository shiftRepository, ShiftHistoryRepository shiftHistoryRepository,
                         UserRepository userRepository) {
        this.shiftRepository = shiftRepository;
        this.shiftHistoryRepository = shiftHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listShifts() {
        return shiftRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShiftResponse createShift(CreateShiftRequest request, Long actorUserId) {
        if (shiftRepository.existsByCode(request.code())) {
            throw new DuplicateShiftCodeException("error.duplicateShiftCode.default",
                    new Object[]{request.code()}, "Mã ca làm việc đã tồn tại: " + request.code());
        }
        String weekdays = validateAndNormalizeWeekdays(request.appliesToWeekdays());
        Shift.WeekParity weekParity = parseWeekParity(request.weekParity());

        Shift shift = new Shift();
        shift.setCode(request.code());
        shift.setName(request.name());
        shift.setCheckInTime(request.checkInTime());
        shift.setCheckOutTime(request.checkOutTime());
        applyWindowMinutes(shift, request.checkInWindowBeforeMinutes(), request.checkInWindowAfterMinutes(),
                request.checkOutWindowBeforeMinutes(), request.checkOutWindowAfterMinutes());
        shift.setAppliesToWeekdays(weekdays);
        shift.setWeekParity(weekParity);
        shift = shiftRepository.save(shift);

        writeHistory(shift, getUserOrThrow(actorUserId), ShiftHistory.Action.CREATED);
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse updateShift(Long id, UpdateShiftRequest request, Long actorUserId) {
        Shift shift = getShiftOrThrow(id);
        String weekdays = validateAndNormalizeWeekdays(request.appliesToWeekdays());
        Shift.WeekParity weekParity = parseWeekParity(request.weekParity());

        shift.setName(request.name());
        shift.setCheckInTime(request.checkInTime());
        shift.setCheckOutTime(request.checkOutTime());
        applyWindowMinutes(shift, request.checkInWindowBeforeMinutes(), request.checkInWindowAfterMinutes(),
                request.checkOutWindowBeforeMinutes(), request.checkOutWindowAfterMinutes());
        shift.setAppliesToWeekdays(weekdays);
        shift.setWeekParity(weekParity);
        shift = shiftRepository.save(shift);

        writeHistory(shift, getUserOrThrow(actorUserId), ShiftHistory.Action.UPDATED);
        return toResponse(shift);
    }

    /**
     * Soft-delete -- AttendanceService.matchesShiftPattern() đã tự loại ca
     * is_active=false khỏi mọi kết quả khớp, không cần (và không nên) xoá
     * cứng vì employee_shifts/work_calendar có thể đang tham chiếu.
     */
    @Transactional
    public ShiftResponse deactivateShift(Long id, Long actorUserId) {
        Shift shift = getShiftOrThrow(id);
        shift.setActive(false);
        shift = shiftRepository.save(shift);
        writeHistory(shift, getUserOrThrow(actorUserId), ShiftHistory.Action.UPDATED);
        return toResponse(shift);
    }

    private String validateAndNormalizeWeekdays(String raw) {
        String weekdays = (raw == null || raw.isBlank()) ? "1,2,3,4,5,6" : raw.trim();
        if (!WEEKDAYS_PATTERN.matcher(weekdays).matches()) {
            throw new IllegalArgumentException(
                    "appliesToWeekdays không hợp lệ (chỉ chấp nhận số 1-7 phân cách bởi dấu phẩy, VD \"1,2,3,4,5,6\"): " + raw);
        }
        String[] parts = weekdays.split(",");
        if (java.util.Set.of(parts).size() != parts.length) {
            throw new IllegalArgumentException("appliesToWeekdays không được chứa số trùng lặp: " + raw);
        }
        return weekdays;
    }

    private Shift.WeekParity parseWeekParity(String raw) {
        if (raw == null || raw.isBlank()) {
            return Shift.WeekParity.ALL;
        }
        try {
            return Shift.WeekParity.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("weekParity không hợp lệ (ALL/ODD/EVEN): " + raw);
        }
    }

    private void applyWindowMinutes(Shift shift, Integer inBefore, Integer inAfter, Integer outBefore, Integer outAfter) {
        shift.setCheckInWindowBeforeMinutes(inBefore != null ? inBefore : 30);
        shift.setCheckInWindowAfterMinutes(inAfter != null ? inAfter : 30);
        shift.setCheckOutWindowBeforeMinutes(outBefore != null ? outBefore : 30);
        shift.setCheckOutWindowAfterMinutes(outAfter != null ? outAfter : 60);
    }

    private void writeHistory(Shift shift, User actor, ShiftHistory.Action action) {
        ShiftHistory history = new ShiftHistory();
        history.setShift(shift);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(snapshot(shift));
        shiftHistoryRepository.save(history);
    }

    private Map<String, Object> snapshot(Shift s) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", s.getCode());
        details.put("name", s.getName());
        details.put("checkInTime", String.valueOf(s.getCheckInTime()));
        details.put("checkOutTime", String.valueOf(s.getCheckOutTime()));
        details.put("appliesToWeekdays", s.getAppliesToWeekdays());
        details.put("weekParity", s.getWeekParity().name());
        details.put("active", s.isActive());
        return details;
    }

    private ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(
                s.getId(), s.getCode(), s.getName(), s.getCheckInTime(), s.getCheckOutTime(),
                s.getCheckInWindowBeforeMinutes(), s.getCheckInWindowAfterMinutes(),
                s.getCheckOutWindowBeforeMinutes(), s.getCheckOutWindowAfterMinutes(),
                s.getAppliesToWeekdays(), s.getWeekParity().name(), s.isActive());
    }

    private Shift getShiftOrThrow(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.shift.notFoundById", new Object[]{id}, "Không tìm thấy ca làm việc id=" + id));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.shift.userNotFound", new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId));
    }
}
