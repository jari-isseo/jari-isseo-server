package com.jariisseo.customer;

import com.jariisseo.store.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 손님 링크 /s/{publicId} → 웹 렌더러(static/viewer/index.html)를 내려준다 (API 정의서 1장·6장)
//   파일을 그대로 넘기는 게 아니라 문자열로 읽어서 내려주므로,
//   나중에(8-4) 여기서 매장 이름을 og 태그로 넣을 수 있다 (카톡 링크 미리보기)
@Controller
public class ViewerPageController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final StoreRepository storeRepository;
    private final ResourceLoader resourceLoader;
    private final String indexLocation;

    // 웹 파일 위치는 Spring 의 static 위치 설정을 따른다
    //   dev: file:src/main/resources/static/ (#10, 수정하면 바로 반영) / 배포: classpath:/static/
    public ViewerPageController(StoreRepository storeRepository, ResourceLoader resourceLoader,
                                @Value("${spring.web.resources.static-locations:classpath:/static/}")
                                String[] staticLocations) {
        this.storeRepository = storeRepository;
        this.resourceLoader = resourceLoader;
        String base = staticLocations[0].endsWith("/") ? staticLocations[0] : staticLocations[0] + "/";
        this.indexLocation = base + "viewer/index.html";
    }

    @GetMapping("/s/{publicId}")
    public ResponseEntity<String> page(@PathVariable String publicId) throws IOException {
        String html = resourceLoader.getResource(indexLocation).getContentAsString(StandardCharsets.UTF_8);

        // index.html 은 style.css·renderer.js 를 상대 경로로 부른다.
        // 주소가 /s/{publicId} 라서 그대로 두면 /s/style.css 를 찾게 됨 → 기준 경로를 /viewer/ 로 지정
        html = html.replaceFirst("<head>", "<head>\n  <base href=\"/viewer/\">");
        // og 태그(매장 이름·미리보기)는 8-4 에서 여기에 넣는다

        // 없는 매장이어도 페이지는 내려주고(화면에서 '매장을 찾을 수 없어요' 표시), 상태 코드만 404
        HttpStatus status = storeRepository.findByPublicId(publicId).isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).contentType(HTML_UTF8).body(html);
    }
}
