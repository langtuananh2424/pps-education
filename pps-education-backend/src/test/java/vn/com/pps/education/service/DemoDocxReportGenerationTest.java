package vn.com.pps.education.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.ReportTemplateFieldMapping;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DemoDocxReportGenerationTest {

    @Test
    public void generateSampleDocxReport() throws Exception {
        // 1. Create Sample Docx Template using POI
        XWPFDocument templateDoc = new XWPFDocument();
        XWPFParagraph p1 = templateDoc.createParagraph();
        XWPFRun r1 = p1.createRun();
        r1.setText("KẾT QUẢ HỌC TẬP");
        r1.setBold(true);
        r1.setFontSize(20);

        XWPFParagraph p2 = templateDoc.createParagraph();
        XWPFRun r2 = p2.createRun();
        r2.setText("Học sinh: [STUDENT_NAME]");
        
        XWPFParagraph p3 = templateDoc.createParagraph();
        XWPFRun r3 = p3.createRun();
        r3.setText("Lớp: [CLASS_NAME]");

        XWPFParagraph p4 = templateDoc.createParagraph();
        XWPFRun r4 = p4.createRun();
        r4.setText("Điểm Nghe: [LISTENING]");
        
        XWPFParagraph p5 = templateDoc.createParagraph();
        XWPFRun r5 = p5.createRun();
        r5.setText("Điểm Nói: [SPEAKING]");
        
        XWPFParagraph p6 = templateDoc.createParagraph();
        XWPFRun r6 = p6.createRun();
        r6.setText("Điểm Đọc: [READING]");

        XWPFParagraph p7 = templateDoc.createParagraph();
        XWPFRun r7 = p7.createRun();
        r7.setText("Điểm Viết: [WRITING]");

        XWPFParagraph p8 = templateDoc.createParagraph();
        XWPFRun r8 = p8.createRun();
        r8.setText("Điểm Trung Bình: [[[LISTENING]+[SPEAKING]+[READING]+[WRITING]]/4]");

        XWPFParagraph p9 = templateDoc.createParagraph();
        XWPFRun r9 = p9.createRun();
        r9.setText("Giáo viên nhận xét: [TEACHER_COMMENT]");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        templateDoc.write(baos);
        byte[] docxInputBytes = baos.toByteArray();
        templateDoc.close();

        // 2. Prepare Sample Data
        Map<String, Object> data = Map.of(
                "STUDENT_NAME", "Trần Thị B",
                "CLASS_NAME", "IELTS 7.0 - K100",
                "LISTENING", 8.0,
                "SPEAKING", 7.0,
                "READING", 8.5,
                "WRITING", 6.5,
                "TEACHER_COMMENT", "Học sinh có từ vựng phong phú, cố gắng phát huy."
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
        DocxMergeEngine docxMergeEngine = new DocxMergeEngine();
        byte[] docxOutput = docxMergeEngine.merge(docxInputBytes, mappings, data);

        assertNotNull(docxOutput);

        // Save to file to show user
        try (FileOutputStream fos = new FileOutputStream("test_sample_output.docx")) {
            fos.write(docxOutput);
        }
        try (FileOutputStream fos2 = new FileOutputStream("test_sample_input.docx")) {
            fos2.write(docxInputBytes);
        }
        System.out.println("GENERATED DOCX SUCCESSFULLY!");
    }
}
