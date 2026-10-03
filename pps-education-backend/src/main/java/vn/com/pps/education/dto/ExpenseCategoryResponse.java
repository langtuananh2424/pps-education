package vn.com.pps.education.dto;

/** Danh mục loại chi vận hành (expense_categories) cho form ghi nhận chi UC-31 bước 2. */
public record ExpenseCategoryResponse(
        Long id,
        String code,
        String name,
        String categoryGroup
) {}
