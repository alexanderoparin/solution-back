#!/bin/sh
# При монтировании томов ./logs и ./uploads с хоста каталоги часто с владельцем root.
# Даём права пользователю spring, чтобы приложение могло писать логи и загрузки.
chown -R spring:spring /app/logs /app/uploads 2>/dev/null || true
exec su-exec spring:spring java $JAVA_OPTS -jar app.jar
