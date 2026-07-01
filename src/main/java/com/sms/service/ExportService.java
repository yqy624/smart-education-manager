package com.sms.service;

import com.sms.model.*;
import com.sms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导出服务（CSV 格式）。
 * 【新增文件 - 模块5：数据导出】
 *
 * 说明：项目未引入 Apache POI，这里输出带 UTF-8 BOM 的 CSV，
 * 用 Excel / WPS 双击即可正确打开为表格（BOM 解决中文乱码）。
 * 如需原生 .xlsx，可后续引入 poi-ooxml 依赖再加一个导出实现。
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 导出全部用户列表为 CSV 字节数组 */
    public byte[] exportUsersCsv() {
        List<User> users = userRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(UTF8_BOM);
            Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writeRow(w, "ID", "用户名", "显示名", "邮箱", "角色", "状态", "创建时间", "最后登录");
            for (User u : users) {
                writeRow(w,
                    String.valueOf(u.getId()),
                    u.getUsername(),
                    nz(u.getDisplayName()),
                    nz(u.getEmail()),
                    u.getRole() == null ? "" : u.getRole().name(),
                    u.isEnabled() ? "启用" : "禁用",
                    u.getCreatedAt() == null ? "" : u.getCreatedAt().toString(),
                    u.getLastLogin() == null ? "" : u.getLastLogin().toString());
            }
            w.flush();
        } catch (IOException e) {
            throw new RuntimeException("导出用户失败: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /** 导出某课程的成绩单为 CSV 字节数组 */
    public byte[] exportCourseGradesCsv(Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        List<Enrollment> enrollments = enrollmentRepository.findByCourse(course);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(UTF8_BOM);
            Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writeRow(w, "课程", course.getName());
            writeRow(w, "学号(ID)", "用户名", "姓名", "成绩", "状态");
            for (Enrollment e : enrollments) {
                User s = e.getStudent();
                boolean graded = e.getScore() >= 0;
                writeRow(w,
                    String.valueOf(s.getId()),
                    s.getUsername(),
                    nz(s.getDisplayName()),
                    graded ? String.valueOf(e.getScore()) : "",
                    graded ? "已评分" : "未评分");
            }
            w.flush();
        } catch (IOException e) {
            throw new RuntimeException("导出成绩单失败: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    // ===== CSV 工具方法 =====
    private void writeRow(Writer w, String... cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(cells[i]));
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    /** CSV 转义：含逗号/引号/换行时用双引号包裹，内部引号翻倍 */
    private String escapeCsv(String value) {
        if (value == null) return "";
        boolean needQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String v = value.replace("\"", "\"\"");
        return needQuote ? "\"" + v + "\"" : v;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
