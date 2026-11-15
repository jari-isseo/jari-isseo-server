# 자리있어 서버 이미지 (#23)
#   1단계(build): 소스를 받아 Gradle 로 실행 가능한 jar 를 만든다 (테스트는 로컬·PR 에서 이미 돌렸으므로 생략)
#   2단계(run):  jar 만 JRE 이미지로 옮긴다 → JDK·Gradle·소스가 빠져서 이미지가 작고 안전하다

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 목록이 그대로면 이 층을 재사용 → 코드만 바꿔 다시 빌드할 때 라이브러리를 또 받지 않는다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

# root 가 아닌 전용 사용자로 실행 (컨테이너가 뚫려도 피해를 줄이기 위해)
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

# 메모리 2GB 서버: 자바 힙 최대 512MB (docker-compose.yml 에서 JAVA_OPTS 로 바꿀 수 있음)
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
