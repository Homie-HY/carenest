# 基础镜像
FROM openjdk:11.0-jre-buster
# 设定时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
# 注意：阿里云 OSS 凭证（OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET）不写入镜像，
# 由运行时注入（docker-compose.yml environment 或 Jenkins 凭据），避免密钥随镜像层泄露
# 拷贝jar包
COPY carenest-admin.jar /app.jar
# 入口
ENTRYPOINT ["java", "-jar", "/app.jar"]