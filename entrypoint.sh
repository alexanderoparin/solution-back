#!/bin/sh
# При монтировании томов ./logs и ./uploads с хоста каталоги часто с владельцем root.
# Даём права пользователю spring, чтобы приложение могло писать логи и загрузки.
chown -R spring:spring /app/logs /app/uploads 2>/dev/null || true
# BusyBox su из базового образа — без отдельного apk (su-exec), чтобы деплой не зависел от DNS Alpine.
exec su -s /bin/sh spring -c "exec java $JAVA_OPTS -jar /app/app.jar"
