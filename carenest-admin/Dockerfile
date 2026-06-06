# 基础镜像
FROM openjdk:11.0-jre-buster
# 设定时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
ENV OSS_ACCESS_KEY_ID ${OSS_ACCESS_KEY_ID}
ENV OSS_ACCESS_KEY_SECRET ${OSS_ACCESS_KEY_SECRET}
# 拷贝jar包
COPY carenest-admin.jar /app.jar
# 入口
ENTRYPOINT ["java", "-jar", "/app.jar"]