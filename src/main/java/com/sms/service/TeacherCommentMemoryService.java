package com.sms.service;

import com.sms.dto.TeacherCommentMemoryResponse;
import com.sms.model.*;
import com.sms.repository.TeacherCommentMemoryRepository;
import com.sms.repository.TeacherCommentUsageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherCommentMemoryService {

    private static final Set<String> ENCOURAGEMENT_KEYWORDS = Set.of(
        "很好", "优秀", "不错", "清晰", "完整", "扎实", "亮点", "认真", "继续保持", "进步"
    );
    private static final Set<String> CORRECTION_KEYWORDS = Set.of(
        "错误", "有误", "不准确", "遗漏", "缺少", "问题", "偏差", "修正", "纠正", "重写"
    );
    private static final Set<String> SCORE_IMPROVEMENT_KEYWORDS = Set.of(
        "建议", "可以", "提升", "加强", "优化", "改进", "补充", "完善", "提分", "下一步"
    );

    private final TeacherCommentMemoryRepository teacherCommentMemoryRepository;
    private final TeacherCommentUsageHistoryRepository teacherCommentUsageHistoryRepository;

    public List<TeacherCommentMemoryResponse> listTeacherComments(User teacher, CommentCategory category) {
        List<TeacherCommentMemory> rows = category == null
            ? teacherCommentMemoryRepository.findByTeacherOrderByUsageCountDescLastUsedAtDesc(teacher)
            : teacherCommentMemoryRepository.findByTeacherAndCategoryOrderByUsageCountDescLastUsedAtDesc(teacher, category);
        return rows.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void recordUsage(User teacher, Submission submission, Double score, String comment, Long quickCommentId) {
        String normalized = normalize(comment);
        if (normalized.isEmpty()) {
            return;
        }

        TeacherCommentMemory memory = null;
        CommentUsageSource sourceType = CommentUsageSource.MANUAL;
        if (quickCommentId != null) {
            memory = teacherCommentMemoryRepository.findByIdAndTeacher(quickCommentId, teacher)
                .orElseThrow(() -> new RuntimeException("快捷评语不存在或无权使用"));
            sourceType = CommentUsageSource.REUSE;
            if (!normalize(memory.getCommentText()).equals(normalized)) {
                throw new RuntimeException("快捷评语内容与提交评语不一致");
            }
        }

        if (memory == null) {
            memory = teacherCommentMemoryRepository.findByTeacherAndNormalizedText(teacher, normalized)
                .orElseGet(() -> TeacherCommentMemory.builder()
                    .teacher(teacher)
                    .commentText(comment.trim())
                    .normalizedText(normalized)
                    .category(resolveCategory(comment, score))
                    .usageCount(0L)
                    .build());
        }

        memory.setCommentText(comment.trim());
        memory.setUsageCount(memory.getUsageCount() + 1);
        memory.setLastUsedAt(LocalDateTime.now());
        TeacherCommentMemory savedMemory = teacherCommentMemoryRepository.save(memory);

        TeacherCommentUsageHistory history = TeacherCommentUsageHistory.builder()
            .teacher(teacher)
            .memory(savedMemory)
            .submission(submission)
            .commentSnapshot(comment.trim())
            .categorySnapshot(savedMemory.getCategory())
            .scoreSnapshot(score)
            .sourceType(sourceType)
            .build();
        teacherCommentUsageHistoryRepository.save(history);
    }

    public CommentCategory resolveCategory(String comment, Double score) {
        String text = normalize(comment);
        if (containsAny(text, CORRECTION_KEYWORDS) || (score != null && score < 70)) {
            return CommentCategory.CORRECTION;
        }
        if (containsAny(text, ENCOURAGEMENT_KEYWORDS) || (score != null && score >= 90)) {
            return CommentCategory.ENCOURAGEMENT;
        }
        if (containsAny(text, SCORE_IMPROVEMENT_KEYWORDS)) {
            return CommentCategory.SCORE_IMPROVEMENT;
        }
        return CommentCategory.SCORE_IMPROVEMENT;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private String normalize(String comment) {
        if (comment == null) {
            return "";
        }
        return comment.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private TeacherCommentMemoryResponse toResponse(TeacherCommentMemory memory) {
        return TeacherCommentMemoryResponse.builder()
            .id(memory.getId())
            .commentText(memory.getCommentText())
            .category(memory.getCategory())
            .usageCount(memory.getUsageCount())
            .lastUsedAt(memory.getLastUsedAt())
            .build();
    }
}
