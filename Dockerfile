# -------- Stage 1: 프론트 빌드 --------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
# node:20-alpine 내장 npm 10.8.2 의 "Exit handler never called!" 버그 회피 (Render 빌드 71s 지점 실패)
RUN npm install -g npm@11.0.0
ENV NODE_OPTIONS=--max-old-space-size=512
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# -------- Stage 2: 백엔드 빌드 (프론트 dist 를 static 에 포함) --------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src
COPY --from=frontend /app/frontend/dist ./backend/src/main/resources/static
RUN cd backend && mvn -B -DskipTests package

# -------- Stage 3: 런타임 (JRE only, ~200MB image) --------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/backend/target/*.jar /app/app.jar
ENV JAVA_OPTS="-Xmx400m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
