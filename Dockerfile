# 멀티스테이지 — JDK 와 Gradle 캐시를 최종 이미지에 남기지 않는다. (PRD §9.6)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# 빌드 스크립트를 소스보다 먼저 복사한다. 소스만 고친 재배포에서
# 의존성 내려받기(1분 남짓)를 건너뛴다 — Day 4 첫 배포는 여러 번 다시 올리게 된다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar

# SPRING_PROFILES_ACTIVE 를 여기 고정하지 않는다. prod 로 박아두면 로컬에서
# 이 이미지를 그대로 띄워 확인할 방법이 없어지고, 플랫폼 환경변수로 덮는 것과 중복이다.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
