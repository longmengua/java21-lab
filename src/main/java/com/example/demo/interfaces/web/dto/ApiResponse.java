package com.example.demo.interfaces.web.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = true;
        res.data = data;
        return res;
    }

    public static <T> ApiResponse<T> fail(String msg) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = false;
        res.message = msg;
        return res;
    }
}
