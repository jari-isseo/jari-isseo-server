package com.jariisseo.customer;

import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// 손님 링크 /s/{publicId} → 웹 렌더러(static/viewer/index.html)를 내려준다 (API 정의서 1장·6장)
//   파일을 그대로 넘기는 게 아니라 문자열로 읽어서 내려주므로, 매장 이름을 og 태그로 넣을 수 있다 (#28)
//   카카오톡 등 링크 미리보기는 JS 를 실행하지 않고 HTML 의 og 태그만 읽기 때문에 서버에서 넣어야 한다
@Controller
public class ViewerPageController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final StoreRepository storeRepository;
    private final ResourceLoader resourceLoader;
    private final List<String> indexCandidates;
    private final String publicBaseUrl;

    // 웹 파일 위치는 Spring 의 static 위치 설정을 따른다
    //   dev: file:src/main/resources/static/ (#10, 수정하면 바로 반영) / 배포: classpath:/static/
    //   위치가 여러 개면 앞에서부터 viewer/index.html 이 있는 곳을 쓴다 (Spring 의 정적 파일 찾는 순서와 같음)
    public ViewerPageController(StoreRepository storeRepository, ResourceLoader resourceLoader,
                                @Value("${spring.web.resources.static-locations:classpath:/static/}")
                                String[] staticLocations,
                                @Value("${app.public-base-url}") String publicBaseUrl) {
        this.storeRepository = storeRepository;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.resourceLoader = resourceLoader;
        this.indexCandidates = Arrays.stream(staticLocations)
                .map(String::trim)
                .map(location -> (location.endsWith("/") ? location : location + "/") + "viewer/index.html")
                .toList();
    }

    @GetMapping("/s/{publicId}")
    public ResponseEntity<String> page(@PathVariable String publicId) throws IOException {
        String html = findIndex().getContentAsString(StandardCharsets.UTF_8);

        // index.html 은 style.css·renderer.js 를 상대 경로로 부른다.
        // 주소가 /s/{publicId} 라서 그대로 두면 /s/style.css 를 찾게 됨 → 기준 경로를 /viewer/ 로 지정
        Optional<Store> store = storeRepository.findByPublicId(publicId);
        html = insertAfterHead(html, "\n  <base href=\"/viewer/\">" + metaTags(publicId, store));
        // 탭 제목도 매장 이름으로 (렌더러 공유 제목과 같은 형식)
        html = html.replace("<title>자리있어</title>", "<title>" + escape(title(store)) + "</title>");

        // 없는 매장이어도 페이지는 내려주고(화면에서 '매장을 찾을 수 없어요' 표시), 상태 코드만 404
        HttpStatus status = store.isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).contentType(HTML_UTF8).body(html);
    }

    // 링크 미리보기용 태그 (#28)
    //   매장 이름은 사장님이 입력한 값이라 HTML 이스케이프 필수 (따옴표·꺾쇠로 태그를 깨지 못하게)
    //   og:image·og:url 은 미리보기 서버가 따로 접속하므로 전체 주소(PUBLIC_BASE_URL)로 쓴다
    private String metaTags(String publicId, Optional<Store> store) {
        String title = escape(title(store));
        String description = escape(store.isPresent()
                ? "지금 빈자리를 실시간으로 확인하세요. 앱 설치 없이 바로 열려요."
                : "매장을 찾을 수 없어요. 링크를 다시 확인해 주세요.");
        String url = escape(publicBaseUrl + "/s/" + publicId);
        String image = escape(publicBaseUrl + "/viewer/og.png");
        List<String> tags = List.of(
                "<meta name=\"description\" content=\"" + description + "\">",
                "<meta property=\"og:type\" content=\"website\">",
                "<meta property=\"og:site_name\" content=\"자리있어?!\">",
                "<meta property=\"og:locale\" content=\"ko_KR\">",
                "<meta property=\"og:title\" content=\"" + title + "\">",
                "<meta property=\"og:description\" content=\"" + description + "\">",
                "<meta property=\"og:url\" content=\"" + url + "\">",
                "<meta property=\"og:image\" content=\"" + image + "\">",
                "<meta property=\"og:image:width\" content=\"1200\">",
                "<meta property=\"og:image:height\" content=\"630\">",
                "<meta property=\"og:image:alt\" content=\"자리있어?! 실시간 빈자리\">",
                "<meta name=\"twitter:card\" content=\"summary_large_image\">");
        return tags.stream().map(tag -> "\n  " + tag).reduce("", String::concat);
    }

    private static String title(Optional<Store> store) {
        return store.map(s -> s.getName() + " 빈자리").orElse("자리있어?!");
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    // replaceFirst 는 넣는 문자열의 $·\ 를 특수 문자로 읽음 (매장 이름에 $ 가 있으면 깨짐) → 위치를 찾아서 붙인다
    private static String insertAfterHead(String html, String text) {
        int at = html.indexOf("<head>");
        if (at < 0) throw new IllegalStateException("viewer/index.html 에 <head> 가 없어요");
        at += "<head>".length();
        return html.substring(0, at) + text + html.substring(at);
    }

    // 요청마다 찾는다 → dev 에서 파일을 고치거나 위치 설정이 바뀌어도 바로 반영
    private Resource findIndex() {
        return indexCandidates.stream()
                .map(resourceLoader::getResource)
                .filter(Resource::isReadable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("viewer/index.html 을 찾을 수 없어요: " + indexCandidates));
    }
}
