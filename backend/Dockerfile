FROM maven:3.9.14-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行（使用精简 JRE 镜像）
FROM eclipse-temurin:21-jre-ubi9-minimal

WORKDIR /app

# 设置时区（基础镜像已包含 tzdata，只需创建符号链接和环境变量）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段复制 jar 包
COPY --from=builder /build/target/chatsystem-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]