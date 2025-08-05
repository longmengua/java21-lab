package com.example.demo.interfaces.web.exception;

import com.example.demo.interfaces.web.dto.ApiResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException e) {
        var firstError = e.getBindingResult().getFieldError();
        return ApiResponse.fail(firstError != null ? firstError.getDefaultMessage() : "參數錯誤");
    }

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<?> handleRuntime(RuntimeException e) {
        return ApiResponse.fail(e.getMessage());
    }
}
