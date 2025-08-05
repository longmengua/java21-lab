package com.example.demo.interfaces.web.dto;

import com.example.demo.interfaces.web.validator.Mobile;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @Mobile
    private String phone;
}

