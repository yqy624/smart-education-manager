package com.sms.dto;

import com.sms.model.CommentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherCommentMemoryResponse {
    private Long id;
    private String commentText;
    private CommentCategory category;
    private long usageCount;
    private LocalDateTime lastUsedAt;
}
