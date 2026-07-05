package com.sms.service;

import org.springframework.core.io.Resource;

public record StoredFilePayload(
    Resource resource,
    String contentType,
    String originalName,
    long size
) {
}
