# 자리있어 서버 실행 이미지 (#25)
#   jar 는 서버가 아니라 내 PC 에서 빌드해서 올린다 (deploy/deploy.sh) → 2GB 서버에서 Gradle 빌드를 하지 않음
#   이 이미지는 올라온 deploy/app.jar 를 JRE 위에 얹기만 한다 (몇 초면 끝남)
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 가 아닌 전용 사용자로 실행 (컨테이너가 뚫려도 피해를 줄이기 위해)
RUN useradd --system --uid 10001 --no-create-home app
COPY app.jar app.jar
USER app

# 메모리 2GB 서버: 자바 힙 최대 512MB (docker-compose.yml 에서 JAVA_OPTS 로 바꿀 수 있음)
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
