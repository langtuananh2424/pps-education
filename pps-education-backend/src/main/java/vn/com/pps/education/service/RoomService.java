package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Equipment;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.RoomHistory;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateEquipmentRequest;
import vn.com.pps.education.dto.CreateRoomRequest;
import vn.com.pps.education.dto.EquipmentResponse;
import vn.com.pps.education.dto.RoomResponse;
import vn.com.pps.education.dto.UpdateEquipmentStatusRequest;
import vn.com.pps.education.dto.UpdateRoomRequest;
import vn.com.pps.education.exception.DuplicateEquipmentCodeException;
import vn.com.pps.education.exception.DuplicateRoomCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EquipmentRepository;
import vn.com.pps.education.repository.RoomHistoryRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-37: Quản lý phòng học & thiết bị (FR-FAC-02, FR-FAC-04). Xem
 * docs/uc/phan-he-10-co-so-vat-chat.md.
 *
 * Room đã có entity/bảng từ V2 (dùng tối thiểu qua FK từ class_sessions,
 * FR-FAC-03 kiểm tra trùng phòng) nhưng chưa có CRUD Service/Controller —
 * bổ sung ở đây. Equipment hoàn toàn mới (bảng có sẵn từ V2, chưa có code).
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'facility.room.
 * create/update')") ở RoomController và ('facility.equipment.create/
 * update') ở EquipmentController (Hybrid PBAC — V28/V62), không còn
 * role-check trong Service.
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomHistoryRepository roomHistoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public RoomService(RoomRepository roomRepository,
                        RoomHistoryRepository roomHistoryRepository,
                        EquipmentRepository equipmentRepository,
                        SiteRepository siteRepository,
                        UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomHistoryRepository = roomHistoryRepository;
        this.equipmentRepository = equipmentRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 1-2: khai báo phòng học mới, tùy chọn đánh dấu 'linh hoạt' (loại trừ khỏi FR-FAC-03). */
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, Long actorUserId) {
        Site site = getSiteOrThrow(request.siteId());
        if (roomRepository.existsBySiteIdAndCode(request.siteId(), request.code())) {
            throw new DuplicateRoomCodeException("Mã phòng " + request.code() + " đã tồn tại tại điểm trường id=" + request.siteId());
        }
        User actor = getUserOrThrow(actorUserId);

        Room room = new Room();
        room.setSite(site);
        room.setCode(request.code());
        room.setName(request.name());
        room.setRoomType(Room.RoomType.valueOf(request.roomType()));
        room.setCapacity(request.capacity());
        room.setFlexible(request.flexible());
        room.setManagedByCenter(request.managedByCenter());
        room = roomRepository.save(room);

        writeRoomHistory(room, actor, RoomHistory.Action.CREATED);
        return toResponse(room);
    }

    /** Main Flow bước 4 (cập nhật) + A1 (thiết bị hỏng/bảo trì áp dụng tương tự cho phòng qua status). */
    @Transactional
    public RoomResponse updateRoom(Long roomId, UpdateRoomRequest request, Long actorUserId) {
        Room room = getRoomOrThrow(roomId);
        User actor = getUserOrThrow(actorUserId);

        room.setName(request.name());
        room.setCapacity(request.capacity());
        room.setFlexible(request.flexible());
        room.setManagedByCenter(request.managedByCenter());
        room.setStatus(Room.Status.valueOf(request.status()));
        room.setNotes(request.notes());
        room = roomRepository.save(room);

        writeRoomHistory(room, actor, RoomHistory.Action.UPDATED);
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listBySite(Long siteId) {
        return roomRepository.findBySiteId(siteId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 3: khai báo thiết bị dạy học gắn với phòng. */
    @Transactional
    public EquipmentResponse createEquipment(CreateEquipmentRequest request, Long actorUserId) {
        if (equipmentRepository.existsByCode(request.code())) {
            throw new DuplicateEquipmentCodeException("Mã thiết bị đã tồn tại: " + request.code());
        }
        Room room = request.roomId() == null ? null : getRoomOrThrow(request.roomId());

        Equipment equipment = new Equipment();
        equipment.setRoom(room);
        equipment.setCode(request.code());
        equipment.setName(request.name());
        equipment.setEquipmentType(Equipment.EquipmentType.valueOf(request.equipmentType()));
        equipment = equipmentRepository.save(equipment);
        return toResponse(equipment);
    }

    /** A1: Thiết bị hỏng/bảo trì — chuyển trạng thái, loại khỏi danh sách khả dụng cho tới khi cập nhật lại. */
    @Transactional
    public EquipmentResponse updateEquipmentStatus(Long equipmentId, UpdateEquipmentStatusRequest request, Long actorUserId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị id=" + equipmentId));
        equipment.setStatus(Equipment.Status.valueOf(request.status()));
        if (request.notes() != null) {
            equipment.setNotes(request.notes());
        }
        equipment = equipmentRepository.save(equipment);
        return toResponse(equipment);
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> listByRoom(Long roomId) {
        return equipmentRepository.findByRoomId(roomId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> listEquipmentBySite(Long siteId) {
        return equipmentRepository.findBySiteId(siteId).stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private void writeRoomHistory(Room room, User actor, RoomHistory.Action action) {
        RoomHistory history = new RoomHistory();
        history.setRoom(room);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> details = new HashMap<>();
        details.put("status", room.getStatus().name());
        details.put("capacity", room.getCapacity());
        history.setDetails(details);
        roomHistoryRepository.save(history);
    }

    private Room getRoomOrThrow(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng học id=" + id));
    }

    private Site getSiteOrThrow(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private RoomResponse toResponse(Room r) {
        return new RoomResponse(r.getId(), r.getSite().getId(), r.getCode(), r.getName(), r.getRoomType().name(),
                r.getCapacity(), r.isFlexible(), r.isManagedByCenter(), r.getStatus().name(), r.getNotes());
    }

    private EquipmentResponse toResponse(Equipment e) {
        return new EquipmentResponse(e.getId(), e.getRoom() == null ? null : e.getRoom().getId(), e.getCode(),
                e.getName(), e.getEquipmentType().name(), e.getStatus().name(), e.getNotes());
    }
}
