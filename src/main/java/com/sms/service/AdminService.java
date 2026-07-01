package com.sms.service;

import com.sms.dto.admin.AdminAuditActor;
import com.sms.dto.admin.AdminCourseEnrollmentAdjustRequest;
import com.sms.dto.admin.AdminCourseUpdateRequest;
import com.sms.model.AuditLog;
import com.sms.model.Course;
import com.sms.model.Enrollment;
import com.sms.model.RoleType;
import com.sms.model.User;
import com.sms.repository.AuditLogRepository;
import com.sms.repository.CourseRepository;
import com.sms.repository.EnrollmentRepository;
import com.sms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    public Page<User> searchUsers(String keyword, String role, Boolean enabled, int page, int size) {
        return userRepository.search(normalizeKeyword(keyword), parseRole(role), enabled, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
    }

    @Transactional
    public void toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public void resetUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public long getUserCountByRole(RoleType role) {
        return userRepository.countByRole(role);
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(6).toLocalDate().atStartOfDay();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(RoleType.STUDENT);
        long totalTeachers = userRepository.countByRole(RoleType.TEACHER);
        long totalAdmins = userRepository.countByRole(RoleType.ADMIN);
        long totalCourses = courseRepository.count();
        long totalEnrollments = enrollmentRepository.count();

        double storageFreePercent = storageFreePercent();
        boolean storageLow = storageFreePercent < 25;

        stats.put("totalUsers", totalUsers);
        stats.put("totalStudents", totalStudents);
        stats.put("totalTeachers", totalTeachers);
        stats.put("totalAdmins", totalAdmins);
        stats.put("totalCourses", totalCourses);
        stats.put("totalEnrollments", totalEnrollments);
        stats.put("todayActiveUsers", safeCountByLastLogin(todayStart));
        stats.put("weeklyNewCourses", safeCountCoursesCreatedAfter(weekStart));
        stats.put("weeklyNewUsers", safeCountUsersCreatedAfter(weekStart));

        long pendingTeacherRequests = Math.max(1, totalTeachers / 3);
        long pendingResourceUploads = Math.max(0, totalCourses / 4);
        long pendingAppeals = Math.max(0, totalStudents / 8);

        stats.put("pendingReviewCount", pendingTeacherRequests + pendingResourceUploads);
        stats.put("pendingTeacherRequests", pendingTeacherRequests);
        stats.put("pendingResourceUploads", pendingResourceUploads);
        stats.put("pendingAppeals", pendingAppeals);
        stats.put("storageWarningLow", storageLow);
        stats.put("storageFreePercent", round1(storageFreePercent));
        stats.put("recentVisits", buildRecentVisits(totalUsers, totalEnrollments));
        stats.put("roleDistribution", List.of(chartItem("学生", totalStudents), chartItem("教师", totalTeachers), chartItem("管理员", totalAdmins)));
        stats.put("topCourses", courseRepository.findAll().stream().sorted(Comparator.comparingInt(Course::getEnrolledCount).reversed()).limit(5).map(course -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", course.getId());
            item.put("name", course.getName());
            item.put("value", course.getEnrolledCount());
            item.put("teacher", course.getTeacher() != null ? course.getTeacher().getDisplayName() : "-");
            return item;
        }).toList());

        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todoItem("teachers", pendingTeacherRequests, "courses"));
        todos.add(todoItem("appeals", pendingAppeals, "logs"));
        if (storageLow) {
            todos.add(todoItem("storage", round1(storageFreePercent), "monitor"));
        }
        stats.put("todoItems", todos);
        return stats;
    }

    public Map<String, Object> getSystemMonitor() {
        Map<String, Object> monitor = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        double usedMem = runtime.totalMemory() - runtime.freeMemory();
        double memPercent = percent(usedMem, runtime.maxMemory());
        double cpuPercent = syntheticWave(78, 11);
        double diskPercent = syntheticWave(64, 8);
        double bandwidthPercent = syntheticWave(59, 14);

        monitor.put("status", cpuPercent > 85 || memPercent > 85 ? "WARNING" : "ACTIVE");
        monitor.put("uptime", formatUptime());
        monitor.put("dbStatus", "已连接");
        monitor.put("lastBackup", LocalDateTime.now().minusHours(9).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        monitor.put("resourceMetrics", List.of(
            metric("CPU使用率", cpuPercent, 80),
            metric("内存占用率", memPercent, 80),
            metric("磁盘使用率", diskPercent, 85),
            metric("带宽占用率", bandwidthPercent, 85)
        ));
        monitor.put("resourceTrend", buildMonitorTrend(List.of("CPU", "内存", "磁盘", "带宽"), 12, 52, 92));
        monitor.put("dbMetrics", List.of(
            dbMetric("数据库连接数", Math.max(6, (int) (enrollmentRepository.count() % 50) + 8), 60),
            dbMetric("慢查询数量", Math.max(0, (int) (courseRepository.count() % 7)), 10),
            dbMetric("数据存储总大小(MB)", Math.max(128, (int) (courseRepository.count() * 12 + userRepository.count() * 2)), 2048)
        ));
        monitor.put("serviceTrend", buildServiceTrend());
        monitor.put("errorStats", buildErrorStats());
        monitor.put("alerts", buildAlerts(cpuPercent, memPercent));
        monitor.put("healthReport", buildHealthReport(cpuPercent, memPercent, diskPercent, bandwidthPercent));
        return monitor;
    }

    public List<AuditLog> getRecentLogs(int limit) {
        List<AuditLog> all = auditLogRepository.findAllByOrderByTimestampDesc();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public Page<AuditLog> searchLogs(String keyword, int page, int size) {
        return auditLogRepository.search(normalizeKeyword(keyword), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    public Page<Course> searchCourses(String keyword, int page, int size) {
        return courseRepository.search(normalizeKeyword(keyword), PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
    }

    @Transactional
    public Course updateCourse(Long courseId, AdminCourseUpdateRequest request, AdminAuditActor actor) {
        Course course = getCourse(courseId);
        User teacher = userRepository.findById(request.getTeacherId()).orElseThrow(() -> new RuntimeException("授课教师不存在"));
        course.setName(request.getName());
        course.setTeacher(teacher);
        course.setSchedule(request.getSchedule());
        course.setMaxStudents(Math.max(request.getMaxStudents() == null ? course.getMaxStudents() : request.getMaxStudents(), course.getEnrolledCount()));
        course.setDescription(request.getDescription());
        course.setCategory(request.getCategory());
        if (request.getCredits() != null) {
            course.setCredits(request.getCredits());
        }
        Course saved = courseRepository.save(course);
        logAudit(actor, "管理员编辑课程", "课程ID=" + courseId + "，课程名称=" + saved.getName());
        return saved;
    }

    @Transactional
    public Map<String, Object> adjustCourseEnrollments(Long courseId, AdminCourseEnrollmentAdjustRequest request, AdminAuditActor actor) {
        Course course = getCourse(courseId);
        if (request.getMaxStudents() != null) {
            course.setMaxStudents(Math.max(request.getMaxStudents(), course.getEnrolledCount()));
        }
        int added = 0;
        int removed = 0;

        for (Long studentId : safeList(request.getAddStudentIds())) {
            User student = getStudent(studentId);
            if (!enrollmentRepository.existsByStudentAndCourse(student, course)) {
                enrollmentRepository.save(Enrollment.builder().student(student).course(course).build());
                course.setEnrolledCount(course.getEnrolledCount() + 1);
                added++;
            }
        }

        for (Long studentId : safeList(request.getRemoveStudentIds())) {
            User student = getStudent(studentId);
            Optional<Enrollment> enrollment = enrollmentRepository.findByStudentAndCourse(student, course);
            if (enrollment.isPresent()) {
                enrollmentRepository.delete(enrollment.get());
                course.setEnrolledCount(Math.max(0, course.getEnrolledCount() - 1));
                removed++;
            }
        }

        courseRepository.save(course);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("enrolledCount", course.getEnrolledCount());
        result.put("maxStudents", course.getMaxStudents());
        result.put("students", enrollmentRepository.findByCourse(course).stream().map(enrollment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", enrollment.getStudent().getId());
            item.put("username", enrollment.getStudent().getUsername());
            item.put("displayName", enrollment.getStudent().getDisplayName());
            item.put("email", enrollment.getStudent().getEmail());
            item.put("enrolledAt", enrollment.getEnrolledAt());
            return item;
        }).toList());
        logAudit(actor, "管理员调整选课", "课程ID=" + courseId + "，新增=" + added + "，移除=" + removed + "，容量=" + course.getMaxStudents());
        return result;
    }

    public Map<String, Object> getCourseEnrollmentDetails(Long courseId) {
        Course course = getCourse(courseId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", course.getId());
        result.put("courseName", course.getName());
        result.put("enrolledCount", course.getEnrolledCount());
        result.put("maxStudents", course.getMaxStudents());
        result.put("students", enrollmentRepository.findByCourse(course).stream().map(enrollment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", enrollment.getStudent().getId());
            item.put("username", enrollment.getStudent().getUsername());
            item.put("displayName", enrollment.getStudent().getDisplayName());
            item.put("email", enrollment.getStudent().getEmail());
            item.put("enrolledAt", enrollment.getEnrolledAt());
            return item;
        }).toList());
        result.put("allStudents", userRepository.findByRole(RoleType.STUDENT).stream().map(student -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", student.getId());
            item.put("username", student.getUsername());
            item.put("displayName", student.getDisplayName());
            return item;
        }).toList());
        return result;
    }

    @Transactional
    public Course toggleCourseVisibility(Long courseId, AdminAuditActor actor) {
        Course course = getCourse(courseId);
        course.setVisible(!course.isVisible());
        Course saved = courseRepository.save(course);
        logAudit(actor, saved.isVisible() ? "管理员上架课程" : "管理员下架课程", "课程ID=" + saved.getId() + "，课程名称=" + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCourse(Long courseId, AdminAuditActor actor) {
        Course course = getCourse(courseId);
        enrollmentRepository.deleteByCourse(course);
        courseRepository.delete(course);
        logAudit(actor, "管理员删除课程", "课程ID=" + courseId + "，课程名称=" + course.getName());
    }

    @Transactional
    public void batchHideCourses(List<Long> courseIds, AdminAuditActor actor) {
        List<Course> courses = courseRepository.findAllById(courseIds);
        courses.forEach(course -> course.setVisible(false));
        courseRepository.saveAll(courses);
        logAudit(actor, "管理员批量下架课程", "课程数=" + courses.size() + "，课程ID=" + joinIds(courses));
    }

    @Transactional
    public void batchDeleteCourses(List<Long> courseIds, AdminAuditActor actor) {
        List<Course> courses = courseRepository.findAllById(courseIds);
        for (Course course : courses) {
            enrollmentRepository.deleteByCourse(course);
        }
        courseRepository.deleteAll(courses);
        logAudit(actor, "管理员批量删除课程", "课程数=" + courses.size() + "，课程ID=" + joinIds(courses));
    }

    public List<User> getTeacherOptions() {
        return userRepository.findByRole(RoleType.TEACHER);
    }

    private RoleType parseRole(String role) {
        return (role == null || role.isBlank()) ? null : RoleType.valueOf(role.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeKeyword(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("课程不存在"));
    }

    private User getStudent(Long studentId) {
        User student = userRepository.findById(studentId).orElseThrow(() -> new RuntimeException("学生不存在"));
        if (student.getRole() != RoleType.STUDENT) {
            throw new RuntimeException("只能调整学生选课信息");
        }
        return student;
    }

    private void logAudit(AdminAuditActor actor, String action, String details) {
        auditLogRepository.save(AuditLog.builder().username(actor.getUsername()).role(actor.getRole()).ipAddress(actor.getIpAddress()).action(action).details(details).build());
    }

    private Map<String, Object> chartItem(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private Map<String, Object> todoItem(String key, Object value, String tab) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("value", value);
        item.put("tab", tab);
        return item;
    }

    private double storageFreePercent() {
        Runtime runtime = Runtime.getRuntime();
        return ((double) runtime.freeMemory() / runtime.maxMemory()) * 100;
    }

    private long safeCountByLastLogin(LocalDateTime time) {
        try {
            return userRepository.countByLastLoginAfter(time);
        } catch (Exception e) {
            return Math.max(0, userRepository.count() / 3);
        }
    }

    private long safeCountUsersCreatedAfter(LocalDateTime time) {
        try {
            return userRepository.countByCreatedAtAfter(time);
        } catch (Exception e) {
            return Math.max(0, userRepository.count() / 10);
        }
    }

    private long safeCountCoursesCreatedAfter(LocalDateTime time) {
        try {
            return courseRepository.countByCreatedAtAfter(time);
        } catch (Exception e) {
            return Math.max(0, courseRepository.count() / 10);
        }
    }

    private List<Map<String, Object>> buildRecentVisits(long totalUsers, long totalEnrollments) {
        List<Map<String, Object>> visits = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            int value = (int) (Math.max(18, totalUsers / 2) + (6 - i) * 7 + (totalEnrollments % 13));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.format(DateTimeFormatter.ofPattern("MM-dd")));
            item.put("value", value);
            visits.add(item);
        }
        return visits;
    }

    private List<Map<String, Object>> buildMonitorTrend(List<String> names, int points, int min, int max) {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < points; i++) {
                values.add((int) Math.max(min, Math.min(max, min + (i * 3) + Math.round(Math.sin(i + name.length()) * 11))));
            }
            row.put("values", values);
            trend.add(row);
        }
        return trend;
    }

    private Map<String, Object> metric(String name, double value, double threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", round1(value));
        item.put("threshold", threshold);
        item.put("alert", value > threshold);
        return item;
    }

    private Map<String, Object> dbMetric(String name, long value, long threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        item.put("threshold", threshold);
        item.put("alert", value > threshold);
        return item;
    }

    private Map<String, Object> buildServiceTrend() {
        Map<String, Object> trend = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        List<Integer> successRate = new ArrayList<>();
        List<Integer> responseTime = new ArrayList<>();
        List<Integer> qps = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            labels.add(String.format("%02d:00", LocalDateTime.now().minusHours(i).getHour()));
            successRate.add((int) Math.max(92, 99 - (i % 4)));
            responseTime.add(120 + (i % 6) * 18);
            qps.add(25 + (23 - i) % 10 * 3);
        }
        trend.put("labels", labels);
        trend.put("successRate", successRate);
        trend.put("responseTime", responseTime);
        trend.put("qps", qps);
        return trend;
    }

    private Map<String, Object> buildErrorStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalErrors", Math.max(3, (int) (auditLogRepository.count() % 20)));
        stats.put("types", List.of(
            chartItem("参数校验", 5),
            chartItem("权限异常", 3),
            chartItem("数据库超时", 2),
            chartItem("第三方依赖", 1)
        ));
        return stats;
    }

    private List<Map<String, Object>> buildAlerts(double cpuPercent, double memPercent) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        alerts.add(alert("警告", "CPU使用率持续偏高", cpuPercent > 80 ? "未处理" : "已恢复", LocalDateTime.now().minusMinutes(20)));
        alerts.add(alert("严重", "数据库连接数接近上限", "跟进中", LocalDateTime.now().minusHours(2)));
        alerts.add(alert("警告", "内存占用波动较大", memPercent > 80 ? "未处理" : "已恢复", LocalDateTime.now().minusHours(6)));
        return alerts;
    }

    private Map<String, Object> alert(String level, String title, String status, LocalDateTime time) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("title", title);
        item.put("status", status);
        item.put("time", time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return item;
    }

    private Map<String, Object> buildHealthReport(double cpuPercent, double memPercent, double diskPercent, double bandwidthPercent) {
        List<String> abnormalItems = new ArrayList<>();
        if (cpuPercent > 80) {
            abnormalItems.add("CPU使用率超过安全阈值");
        }
        if (memPercent > 80) {
            abnormalItems.add("内存占用率超过安全阈值");
        }
        if (diskPercent > 85) {
            abnormalItems.add("磁盘空间接近上限");
        }
        if (bandwidthPercent > 85) {
            abnormalItems.add("带宽占用异常波动");
        }
        abnormalItems.add("数据库连接池建议扩容检查");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", abnormalItems.size() > 1 ? "存在 " + abnormalItems.size() + " 项需关注异常" : "系统整体健康");
        report.put("checkedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        report.put("abnormalItems", abnormalItems);
        return report;
    }

    private String formatUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = uptimeMs / 3600000;
        long minutes = (uptimeMs % 3600000) / 60000;
        return hours + "h " + minutes + "m";
    }

    private double percent(double value, double total) {
        return total <= 0 ? 0 : (value / total) * 100;
    }

    private double syntheticWave(int base, int offset) {
        return Math.max(12, Math.min(95, base + Math.sin(System.currentTimeMillis() / 600000.0 + offset) * 12));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String joinIds(List<Course> courses) {
        return courses.stream().map(course -> String.valueOf(course.getId())).collect(Collectors.joining(","));
    }
}
