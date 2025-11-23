# Этап 1: Сборка приложения
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем pom.xml для кеширования зависимостей
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY src ./src

# Собираем JAR файл
RUN mvn clean package -DskipTests

# Этап 2: Запуск приложения
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Устанавливаем curl для healthcheck
RUN apk add --no-cache curl

# Создаем пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring

# Создаем директорию для логов с правильными правами
RUN mkdir -p /app/logs && chown spring:spring /app/logs

# Копируем собранный JAR из этапа сборки
COPY --from=build /app/target/solution_back-1.0.0.jar app.jar

# Меняем владельца JAR файла
RUN chown spring:spring app.jar

# Переключаемся на пользователя spring
USER spring:spring

# Настройка JVM (можно переопределить через переменные окружения)
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport"

# Порт приложения
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

