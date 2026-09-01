# Этап 1: Сборка приложения
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем pom.xml для кеширования зависимостей
COPY pom.xml .
RUN mvn -B dependency:go-offline dependency:resolve dependency:resolve-plugins

# Копируем исходный код
COPY src ./src

# Сборка offline: зависимости уже в слое выше; не зависит от DNS Docker bridge
RUN mvn -B -o package -DskipTests

# Этап 2: Запуск приложения
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# TLS Минцифры (Russian Trusted CA): enter.tochka.com уже на этом корне.
# Источник: https://developers.tochka.com/docs/tochka-api/certificate
#   https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt
#   https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt
COPY docker/certs/russian_trusted_root_ca.crt /usr/local/share/ca-certificates/russian_trusted_root_ca.crt
COPY docker/certs/russian_trusted_sub_ca.crt /usr/local/share/ca-certificates/russian_trusted_sub_ca.crt
RUN apk add --no-cache curl ca-certificates \
    && update-ca-certificates \
    && "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
         -alias russian-trusted-root \
         -file /usr/local/share/ca-certificates/russian_trusted_root_ca.crt \
         -keystore "$JAVA_HOME/lib/security/cacerts" \
         -storepass changeit \
    && "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
         -alias russian-trusted-sub \
         -file /usr/local/share/ca-certificates/russian_trusted_sub_ca.crt \
         -keystore "$JAVA_HOME/lib/security/cacerts" \
         -storepass changeit

# Создаем пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring

# Создаем директории для логов и загрузок с правильными правами
RUN mkdir -p /app/logs && chown spring:spring /app/logs
RUN mkdir -p /app/uploads && chown spring:spring /app/uploads

# Копируем собранный JAR из этапа сборки
COPY --from=build /app/target/solution_back-1.0.0.jar app.jar

# Меняем владельца JAR файла
RUN chown spring:spring app.jar

# Настройка JVM (можно переопределить через переменные окружения)
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport"

# Порт приложения
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Entrypoint: при монтировании томов каталоги на хосте часто с владельцем root — даём права spring.
# Переключение на spring через BusyBox su (без apk su-exec — меньше зависимость от DNS Alpine).
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && chown root:root /entrypoint.sh

USER root
ENTRYPOINT ["/entrypoint.sh"]
