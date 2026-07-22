package vn.com.pps.education.controller;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractControllerTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-35: xác nhận student.profile.import (tách từ student.manage ở V44) chặn/cho phép đúng qua HTTP
 * thật. Regression test cho lỗ hổng đã vá: trước đây
 * StudentBatchImportController hoàn toàn không có @PreAuthorize (class lẫn
 * method) — bất kỳ tài khoản đã đăng nhập nào (kể cả STUDENT/PARENT) đều
 * gọi được POST /api/student-imports để tạo tài khoản + ghi danh học sinh
 * hàng loạt vào bất kỳ lớp nào.
 */
@Transactional
class StudentBatchImportControllerTest extends AbstractControllerTest {

    @Test
    void importStudents_deniedForRoleWithoutStudentManage_returns403() throws Exception {
        var student = userWithRole("student.import.noaccess", "STUDENT");
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/student-imports")
                        .file(file)
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    @Test
    void importStudents_allowedForStaff_returns200() throws Exception {
        var staff = userWithRole("staff.import.access", "STAFF");
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", headerOnlyWorkbook());

        mockMvc.perform(multipart("/api/student-imports")
                        .file(file)
                        .header("Authorization", bearerToken(staff, "STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getJob_deniedForRoleWithoutStudentManage_returns403() throws Exception {
        var student = userWithRole("student.getjob.noaccess", "STUDENT");

        mockMvc.perform(get("/api/student-imports/1")
                        .header("Authorization", bearerToken(student, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản không có quyền thực hiện thao tác này."));
    }

    private byte[] headerOnlyWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Họ và tên");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
