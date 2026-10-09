FROM public.ecr.aws/docker/library/eclipse-temurin:17.0.16_8-jre-jammy@sha256:e90fd2b084488c0ffdd22610d44c1579e63b585c7fb83489fd815c0dc5b4e1f7
# 中文字体：流程图/验证码 PNG 渲染需要 CJK 字形，否则中文渲染成方块（乱码）
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-noto-cjk \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN groupadd --gid 10001 agentoa && useradd --uid 10001 --gid agentoa --no-create-home agentoa && mkdir /app/logs && chown agentoa:agentoa /app/logs
COPY agentoa-backend/ruoyi-admin/target/ruoyi-admin.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
