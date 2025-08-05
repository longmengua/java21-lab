package com.example.demo.domain.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private String username;
    private String password; // raw 明文，之後可加密
    private String phone;
}
