package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DemoReportGenerationTest {

    @Test
    public void generateSampleReport() throws Exception {
        // 1. Prepare HTML Input
        String htmlInput = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <style>\n" +
                "        body { font-family: 'PPSNotoSans', sans-serif; font-size: 14px; }\n" +
                "        .header { text-align: center; color: #2c3e50; }\n" +
                "        .report-card { border: 1px solid #ccc; padding: 20px; border-radius: 8px; }\n" +
                "        .score { font-weight: bold; color: #e74c3c; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"report-card\">\n" +
                "        <h1 class=\"header\">KẾT QUẢ HỌC TẬP</h1>\n" +
                "        <p>Học sinh: <strong>[STUDENT_NAME]</strong></p>\n" +
                "        <p>Lớp: <strong>[CLASS_NAME]</strong></p>\n" +
                "        <hr/>\n" +
                "        <p>Điểm Nghe: <span class=\"score\">[LISTENING]</span></p>\n" +
                "        <p>Điểm Nói: <span class=\"score\">[SPEAKING]</span></p>\n" +
                "        <p>Điểm Đọc: <span class=\"score\">[READING]</span></p>\n" +
                "        <p>Điểm Viết: <span class=\"score\">[WRITING]</span></p>\n" +
                "        <p>Điểm Trung Bình: <span class=\"score\">[[[LISTENING]+[SPEAKING]+[READING]+[WRITING]]/4]</span></p>\n" +
                "        <br/>\n" +
                "        <p>Giáo viên nhận xét: <em>[TEACHER_COMMENT]</em></p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        // 2. Prepare Sample Data
        Map<String, Object> data = Map.of(
                "STUDENT_NAME", "Nguyễn Văn A",
                "CLASS_NAME", "IELTS 6.5 - K99",
                "LISTENING", 7.5,
                "SPEAKING", 6.5,
                "READING", 8.0,
                "WRITING", 6.0,
                "TEACHER_COMMENT", "Học sinh rất tiến bộ trong kỹ năng Đọc và Nghe."
        );
        
        // 3. Setup Mappings
        ReportTemplateFieldMapping m1 = new ReportTemplateFieldMapping();
        m1.setPlaceholderKey("[STUDENT_NAME]");
        m1.setDataPath("STUDENT_NAME");
        m1.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        
        ReportTemplateFieldMapping m2 = new ReportTemplateFieldMapping();
        m2.setPlaceholderKey("[CLASS_NAME]");
        m2.setDataPath("CLASS_NAME");
        m2.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);

        ReportTemplateFieldMapping m3 = new ReportTemplateFieldMapping();
        m3.setPlaceholderKey("[LISTENING]");
        m3.setDataPath("LISTENING");
        m3.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);

        ReportTemplateFieldMapping m4 = new ReportTemplateFieldMapping();
        m4.setPlaceholderKey("[SPEAKING]");
        m4.setDataPath("SPEAKING");
        m4.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        
        ReportTemplateFieldMapping m5 = new ReportTemplateFieldMapping();
        m5.setPlaceholderKey("[READING]");
        m5.setDataPath("READING");
        m5.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        
        ReportTemplateFieldMapping m6 = new ReportTemplateFieldMapping();
        m6.setPlaceholderKey("[WRITING]");
        m6.setDataPath("WRITING");
        m6.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);
        
        ReportTemplateFieldMapping m7 = new ReportTemplateFieldMapping();
        m7.setPlaceholderKey("[[[LISTENING]+[SPEAKING]+[READING]+[WRITING]]/4]");
        m7.setFieldType(ReportTemplateFieldMapping.FieldType.FORMULA);
        
        ReportTemplateFieldMapping m8 = new ReportTemplateFieldMapping();
        m8.setPlaceholderKey("[TEACHER_COMMENT]");
        m8.setDataPath("TEACHER_COMMENT");
        m8.setFieldType(ReportTemplateFieldMapping.FieldType.FIELD);

        List<ReportTemplateFieldMapping> mappings = List.of(m1, m2, m3, m4, m5, m6, m7, m8);

        // 4. Initialize Engine and Merge
        HtmlMergeEngine htmlMergeEngine = new HtmlMergeEngine();
        byte[] pdfOutput = htmlMergeEngine.merge(htmlInput.getBytes(StandardCharsets.UTF_8), mappings, data);

        assertNotNull(pdfOutput);

        // Save to file to show user
        try (FileOutputStream fos = new FileOutputStream("test_sample_output.pdf")) {
            fos.write(pdfOutput);
        }
        try (FileOutputStream fos2 = new FileOutputStream("test_sample_input.html")) {
            fos2.write(htmlInput.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("GENERATED PDF SUCCESSFULLY!");
    }
}
