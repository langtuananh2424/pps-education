package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeEntryHistory;

public interface GradeEntryHistoryRepository extends JpaRepository<GradeEntryHistory, Long> {

    /**
     * UC-19 (xoá điểm nháp, bổ sung ngoài SDD gốc): grade_entry_id ở
     * grade_entries_history là FK NOT NULL không CASCADE — phải xoá hết
     * history trước khi xoá cứng grade_entries, không có bản ghi
     * "DELETED" nào được lưu lại (chưa công bố nên không có giá trị
     * pháp lý cần giữ audit, xem CLAUDE.md quy ước soft-delete).
     */
    void deleteByGradeEntryId(Long gradeEntryId);
}
