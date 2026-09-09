package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ImportJob;
import vn.com.pps.education.repository.ImportJobRepository;

/**
 * Commit 1 ImportJob trong transaction RIÊNG (REQUIRES_NEW), độc lập với
 * transaction ambient của caller — dùng để tạo bản ghi import_jobs TRƯỚC
 * khi xử lý từng dòng, vì StudentBatchImportRowService.importRow() (và các
 * *RowService khác dùng chung pattern) chạy REQUIRES_NEW và ghi FK
 * import_job_id trỏ tới đúng import_jobs.id: nếu job chưa thật sự COMMIT
 * (VD do caller đang chạy trong 1 @Transactional khác — StudentBatchImportService
 * .importStudents() không tự mở transaction cho chính nó, NHƯNG khi được
 * gọi từ 1 nơi khác đã có sẵn transaction, như test @Transactional, thì vẫn
 * có ambient transaction), transaction REQUIRES_NEW của dòng sẽ không thấy
 * được row job vừa tạo → vi phạm FK.
 */
@Service
public class ImportJobCommitService {

    private final ImportJobRepository importJobRepository;

    public ImportJobCommitService(ImportJobRepository importJobRepository) {
        this.importJobRepository = importJobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportJob save(ImportJob job) {
        return importJobRepository.save(job);
    }
}
