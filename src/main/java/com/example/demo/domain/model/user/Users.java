package com.example.demo.domain.model.user;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {
    @TableId
    private Long id;
    private String username;
    private String password; // raw 明文，之後可加密
    private String phone;
}
