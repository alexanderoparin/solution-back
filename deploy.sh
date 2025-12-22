#!/bin/bash

set -e

echo "=== Деплой приложения ==="
echo ""

# Переходим в директорию проекта
cd "$(dirname "$0")"

echo "1. Обновление кода из Git..."
echo "   Обновление бэкенда..."
git pull

echo ""
echo "   Обновление фронтенда..."
if [ -d "../solution-front" ]; then
    cd ../solution-front
    git pull
    cd ../solution-back
else
    echo "   Директория ../solution-front не найдена, пропускаем"
fi

echo ""
echo "2. Очистка старых build cache (освобождение места)..."
docker builder prune -f --filter "until=24h"

echo ""
echo "3. Сборка и запуск контейнеров..."
docker-compose up -d --build

echo ""
echo "4. Проверка статуса контейнеров..."
docker-compose ps

echo ""
echo "=== Деплой завершен ==="