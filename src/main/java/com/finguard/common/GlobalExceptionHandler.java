package com.finguard.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 拦截所有 Controller 抛出的异常，统一返回 JSON 格式的错误响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        log.error("系统异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "请求参数有误", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用", e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIO(IOException e) {
        log.error("IO异常: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "文件读写失败", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, String detail) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        body.put("detail", detail);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
