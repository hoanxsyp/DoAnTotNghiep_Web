# =====================================================================
#  Backend - Spring Boot 3.3.5 / Java 21
#  Multi-stage build: khong can cai Java/Maven tren may host.
# =====================================================================

# ------------------------------ Stage 1: build -----------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy rieng pom.xml truoc de Docker cache lop dependency. Chi khi pom.xml
# doi thi lop nay moi phai chay lai, doi source thi khong.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests \
    && java -Djarmode=layertools -jar target/backend-webtro.jar extract --destination target/extracted

# ----------------------------- Stage 2: runtime ----------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Chay bang user thuong, khong phai root (nguyen tac dac quyen toi thieu).
RUN groupadd --system --gid 1001 webtro \
    && useradd --system --uid 1001 --gid webtro --create-home webtro \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Thu muc luu anh upload - nam NGOAI webroot, duoc mount ra volume.
RUN mkdir -p /app/storage && chown -R webtro:webtro /app

# Copy theo tung lop cua Spring Boot layertools -> doi code ung dung chi
# lam moi lop cuoi, cac lop thu vien van duoc cache.
COPY --from=builder --chown=webtro:webtro /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=webtro:webtro /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=webtro:webtro /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=webtro:webtro /build/target/extracted/application/ ./

USER webtro

EXPOSE 8080

ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8 -Duser.timezone=UTC"

# Dung startup probe cua Actuator: container chi duoc coi la healthy khi
# Flyway migrate xong va datasource + redis song.
# start-period rộng (180s) để chịu được lần khởi động nguội đầu tiên trên máy đang tải nặng
# (JIT + nạp ~570 class + Flyway validate). Lần chạy thường app sẵn sàng trong ~15-25s.
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
