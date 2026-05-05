# Build Stage
FROM clojure:lein-alpine AS builder

WORKDIR /app

# 依存関係をキャッシュ
COPY project.clj ./
RUN lein deps

# ソースコードをコピーして Uberjar を作成
COPY . .
RUN lein uberjar

# Run Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Builder からスタンドアロン JAR をコピー
# (ビルドされるバージョンに関わらず standalone のみコピー)
COPY --from=builder /app/target/*-standalone.jar ./app.jar

EXPOSE 3030

# サーバーモードで実行
CMD ["java", "-jar", "app.jar", "server"]
