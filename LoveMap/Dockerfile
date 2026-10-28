# 阶段1：Maven 构建（Java 25）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# 国内网络下直连 Maven Central 易失败，改用阿里云公共仓库镜像
COPY maven-settings.xml /build/maven-settings.xml
COPY pom.xml .
RUN mvn -B -q -s /build/maven-settings.xml dependency:go-offline || true
COPY src ./src
RUN mvn -B -s /build/maven-settings.xml package -DskipTests

# 阶段2：JRE 运行
FROM eclipse-temurin:25-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/LoveMap-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
