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

/**
 * Quản lý danh mục ca làm việc chuẩn — bổ sung 2026-08-13, xem
 * docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09/FR-HRM-02).
 */
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final ShiftHistoryRepository shiftHistoryRepository;
    private final UserRepository userRepository;

    public ShiftService(ShiftRepository shiftRepository,
                         ShiftHistoryRepository shiftHistoryRepository,
                         UserRepository userRepository) {
        this.shiftRepository = shiftRepository;
        this.shiftHistoryRepository = shiftHistoryRepository;
        this.userRepository = userRepository;
    }

    public List<ShiftResponse> list() {
        return shiftRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ShiftResponse getById(Long id) {
        return toResponse(getShiftOrThrow(id));
    }

    @Transactional
    public ShiftResponse create(CreateShiftRequest request, Long actorUserId) {
        if (shiftRepository.existsByCode(request.code())) {
            throw new DuplicateShiftCodeException("Mã ca \"" + request.code() + "\" đã tồn tại.");
        }
        Shift shift = new Shift();
        shift.setCode(request.code());
        applyRequest(shift, request.name(), request.checkInTime(), request.checkOutTime(),
                request.checkInWindowBeforeMinutes(), request.checkInWindowAfterMinutes(),
                request.checkOutWindowBeforeMinutes(), request.checkOutWindowAfterMinutes(),
                request.appliesToWeekdays(), request.weekParity());
        shift = shiftRepository.save(shift);
        writeShiftHistory(shift, actorUserId, ShiftHistory.Action.CREATED);
        return toResponse(shift);
    }

    @Transactional
    public ShiftResponse update(Long id, UpdateShiftRequest request, Long actorUserId) {
        Shift shift = getShiftOrThrow(id);
        applyRequest(shift, request.name(), request.checkInTime(), request.checkOutTime(),
                request.checkInWindowBeforeMinutes(), request.checkInWindowAfterMinutes(),
                request.checkOutWindowBeforeMinutes(), request.checkOutWindowAfterMinutes(),
                request.appliesToWeekdays(), request.weekParity());
        shift.setActive(request.active());
        shift = shiftRepository.save(shift);
        writeShiftHistory(shift, actorUserId, ShiftHistory.Action.UPDATED);
        return toResponse(shift);
    }

    /** Tắt ca (is_active = FALSE) thay vì xóa cứng — employee_shifts/work_calendar tham chiếu qua FK. */
    @Transactional
    public void deactivate(Long id, Long actorUserId) {
        Shift shift = getShiftOrThrow(id);
        shift.setActive(false);
        shift = shiftRepository.save(shift);
        writeShiftHistory(shift, actorUserId, ShiftHistory.Action.UPDATED);
    }

    Shift getShiftOrThrow(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc id=" + id));
    }

    private void applyRequest(Shift shift, String name, java.time.LocalTime checkInTime, java.time.LocalTime checkOutTime,
                               Integer checkInBefore, Integer checkInAfter, Integer checkOutBefore, Integer checkOutAfter,
                               String appliesToWeekdays, Shift.WeekParity weekParity) {
        shift.setName(name);
        shift.setCheckInTime(checkInTime);
        shift.setCheckOutTime(checkOutTime);
        if (checkInBefore != null) shift.setCheckInWindowBeforeMinutes(checkInBefore);
        if (checkInAfter != null) shift.setCheckInWindowAfterMinutes(checkInAfter);
        if (checkOutBefore != null) shift.setCheckOutWindowBeforeMinutes(checkOutBefore);
        if (checkOutAfter != null) shift.setCheckOutWindowAfterMinutes(checkOutAfter);
        if (appliesToWeekdays != null && !appliesToWeekdays.isBlank()) shift.setAppliesToWeekdays(appliesToWeekdays);
        if (weekParity != null) shift.setWeekParity(weekParity);
    }

    private void writeShiftHistory(Shift shift, Long actorUserId, ShiftHistory.Action action) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));
        ShiftHistory history = new ShiftHistory();
        history.setShift(shift);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(shiftSnapshot(shift));
        shiftHistoryRepository.save(history);
    }

    private Map<String, Object> shiftSnapshot(Shift s) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", s.getCode());
        snapshot.put("name", s.getName());
        snapshot.put("checkInTime", String.valueOf(s.getCheckInTime()));
        snapshot.put("checkOutTime", String.valueOf(s.getCheckOutTime()));
        snapshot.put("appliesToWeekdays", s.getAppliesToWeekdays());
        snapshot.put("weekParity", s.getWeekParity().name());
        snapshot.put("active", s.isActive());
        return snapshot;
    }

    private ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getCheckInTime(),
                s.getCheckOutTime(),
                s.getCheckInWindowBeforeMinutes(),
                s.getCheckInWindowAfterMinutes(),
                s.getCheckOutWindowBeforeMinutes(),
                s.getCheckOutWindowAfterMinutes(),
                s.getAppliesToWeekdays(),
                s.getWeekParity(),
                s.isActive()
        );
    }
}
