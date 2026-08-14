# 멀티스테이지 — JDK 와 Gradle 캐시를 최종 이미지에 남기지 않는다. (PRD §9.6)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# 빌드 스크립트를 소스보다 먼저 복사해 Gradle 배포본(130MB) 내려받기를 레이어에 굳힌다.
# 의존성 jar 는 대부분 아래 bootJar 단계에서 받는다 — 이 레이어가 아끼는 건 배포본 쪽이다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar

# 환경변수를 하나 빠뜨렸을 때 열리는 쪽이 아니라 닫히는 쪽으로 떨어뜨린다.
# 기본 프로파일은 local 이고 local 은 테스트 계정이 켜져 있어서, 플랫폼에
# SPRING_PROFILES_ACTIVE 를 안 넣으면 공개 배포에서 /auth/test-login 이 열린다.
# 로그도 헬스체크도 정상이라 아무도 눈치채지 못한다.
# docker run -e 와 플랫폼 환경변수가 이 값을 덮으므로 로컬에서 띄워 보는 것도 그대로 된다.
ENV SPRING_PROFILES_ACTIVE=prod

# 저장 시각은 JpaConfig 가 KST 로 고정한다. 이건 로그 타임스탬프용이다 —
# 시연 중 로그를 보면서 아홉 시간을 암산하지 않으려고 맞춰 둔다.
ENV TZ=Asia/Seoul

# EXPOSE 를 두지 않는다. 앱은 ${PORT} 에 바인딩하는데 EXPOSE 8080 이 남아 있으면
# Railway 가 그걸 프록시 대상 포트로 읽어, 주입한 PORT 와 어긋나면 트래픽이
# 닫힌 포트로 간다. 포트의 출처를 PORT 하나로 둔다.

RUN useradd --system --create-home app && chown -R app /app
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
