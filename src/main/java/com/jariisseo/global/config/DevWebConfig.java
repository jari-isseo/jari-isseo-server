package com.jariisseo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// dev 전용: 웹 렌더러의 샘플 JSON(/docs/samples/*.json)을 서버에서도 열 수 있게 한다.
//   http://localhost:8080/viewer/index.html?sample=snapshot
// docs/samples 폴더만 연다 (레포 루트 전체를 열면 .env 가 노출되므로 주의)
@Configuration
@Profile("dev")
public class DevWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/docs/samples/**")
                .addResourceLocations("file:docs/samples/");
    }
}
