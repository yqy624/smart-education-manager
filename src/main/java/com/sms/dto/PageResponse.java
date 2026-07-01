package com.sms.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应 DTO。
 * 【新增文件 - 模块2：服务端分页+多条件搜索】
 * 把 Spring Data 的 Page 转成前端友好的结构，避免直接暴露 Page 的冗余字段。
 */
@Data
public class PageResponse<T> {
    private List<T> content;   // 当前页数据
    private int page;          // 当前页码（从 0 开始）
    private int size;          // 每页条数
    private long totalElements; // 总记录数
    private int totalPages;    // 总页数
    private boolean first;
    private boolean last;

    public static <E, T> PageResponse<T> from(Page<E> p, Function<E, T> mapper) {
        PageResponse<T> r = new PageResponse<>();
        r.content = p.getContent().stream().map(mapper).toList();
        r.page = p.getNumber();
        r.size = p.getSize();
        r.totalElements = p.getTotalElements();
        r.totalPages = p.getTotalPages();
        r.first = p.isFirst();
        r.last = p.isLast();
        return r;
    }

    public static <T> PageResponse<T> from(Page<T> p) {
        return from(p, Function.identity());
    }
}
