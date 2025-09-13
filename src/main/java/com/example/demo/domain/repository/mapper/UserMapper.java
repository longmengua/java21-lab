package com.example.demo.domain.repository.mapper;

import com.example.demo.domain.model.user.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(prefix = "datasource", name = "enabled", havingValue = "true")
public interface UserMapper {

    @Select("SELECT id, username, email, created_at FROM users WHERE id = #{id}")
    Users findById(Long id);

    @Select("SELECT id, username, email, created_at FROM users ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Users> list(@Param("limit") int limit, @Param("offset") int offset);

    @Insert("""
        INSERT INTO users(username, email, created_at) VALUES(#{username}, #{email}, #{createdAt})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Users u);

    @Update("""
        UPDATE users SET username=#{username}, email=#{email} WHERE id=#{id}
    """)
    int update(Users u);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int delete(Long id);
}

