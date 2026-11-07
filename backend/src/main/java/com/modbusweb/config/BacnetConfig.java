package com.modbusweb.config;

import com.modbusweb.service.bacnet.BacnetValueAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BacnetConfig {

    @Bean
    @ConditionalOnMissingBean(BacnetValueAdapter.class)
    public BacnetValueAdapter bacnetValueAdapter() {
        return BacnetValueAdapter.defaultAdapter();
    }
}
