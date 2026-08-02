package vn.com.pps.education.service.integrity;

import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;

/**
 * Kết quả resolve 1 attempt đa hình về đúng học sinh/lớp — dùng chung cho
 * mọi AttemptIntegrityContextResolver. Mang thẳng entity (không chỉ id)
 * vì resolver nào cũng đã có sẵn entity trong tay, tránh truy vấn lại lần
 * nữa ở AttemptIntegrityService. schoolClass có thể null (VD chưa xác
 * định được lớp — xem Javadoc từng resolver cụ thể).
 */
public record AttemptContext(Student student, SchoolClass schoolClass, String attemptLabel) {}
