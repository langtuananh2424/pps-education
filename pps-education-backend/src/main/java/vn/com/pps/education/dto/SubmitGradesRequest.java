package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** UC-19 Main Flow bước 4: submit từng bản ghi (1 phần tử) hoặc theo lô (nhiều phần tử, sinh chung 1 batch_id). */
public record SubmitGradesRequest(
        @NotEmpty List<Long> gradeEntryIds
) {}
