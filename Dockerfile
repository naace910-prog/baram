# Render Free 빌드에서 npm registry 연결이 70~71s 지점에 일정하게 타임아웃 걸리는 문제 회피용.
# 프론트는 로컬에서 미리 `cd frontend && npm run build` 로 dist 생성 후 커밋되어 있음.
# 코드 수정 후엔 로컬 재빌드 + dist 커밋 필수.

# -------- Stage 1: 백엔드 빌드 (커밋된 dist 를 static 에 포함) --------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src
COPY frontend/dist ./backend/src/main/resources/static
RUN cd backend && mvn -B -DskipTests package

# -------- Stage 2: 런타임 (JRE only, 최소 이미지) --------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/backend/target/*.jar /app/app.jar
ENV JAVA_OPTS="-Xmx400m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
