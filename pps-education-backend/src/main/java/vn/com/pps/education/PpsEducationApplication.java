package vn.com.pps.education;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point của hệ thống PPS Education.
 *
 * Kiến trúc: client-server, tách Backend/Frontend, phân lớp
 * Controller -> Service -> Repository (NFR-TECH-01, NFR-TECH-02).
 *
 * Phase A (nền tảng) triển khai trong package này:
 *  - Phân hệ 1: Đăng nhập & Xác thực (security/, controller/AuthController)
 *  - Phân hệ 2: Quản trị người dùng & Phân quyền (Hybrid PBAC)
 *  - Dữ liệu nền: Điểm trường/Phòng học (Phân hệ 10), Hồ sơ nhân sự (Phân hệ 4)
 */
@SpringBootApplication
public class PpsEducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PpsEducationApplication.class, args);
    }
}
