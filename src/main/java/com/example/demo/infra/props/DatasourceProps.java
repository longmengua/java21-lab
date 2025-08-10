package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "spring.datasource")
public class DatasourceProps {
    /** spring.datasource.enabled（自定義旗標） */
    private boolean enabled = false;

    /** spring.datasource.mysql.* */
    private Mysql mysql = new Mysql();

    @Getter @Setter
    public static class Mysql {
        /** spring.datasource.mysql.url */
        private String url;

        /** spring.datasource.mysql.username */
        private String username;

        /** spring.datasource.mysql.password */
        private String password;

        /** spring.datasource.mysql.driver-class-name */
        private String driverClassName;
    }
}

