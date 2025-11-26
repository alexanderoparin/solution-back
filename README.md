# Solution Backend

Backend приложение для управления рекламными кампаниями Wildberries.

## Технологии

- Java 21
- Spring Boot 3.3.5
- Spring Data JPA
- Spring Security + JWT
- PostgreSQL
- Maven
- BCrypt для хеширования паролей

## Структура проекта

```
solution_back/
├── Dockerfile              # Образ для Docker
├── docker-compose.yml      # Конфигурация Docker Compose (включает фронтенд)
├── .env.example            # Шаблон переменных окружения
├── pom.xml
└── src/
    ├── main/
    │   ├── java/ru/oparin/solution/
    │   │   ├── config/          # Конфигурации (SecurityConfig)
    │   │   ├── controller/      # REST контроллеры
    │   │   ├── dto/             # Data Transfer Objects
    │   │   ├── exception/       # Обработка исключений
    │   │   ├── model/           # JPA сущности
    │   │   ├── repository/      # Репозитории JPA
    │   │   ├── security/        # JWT и Security компоненты
    │   │   └── service/         # Бизнес-логика
    │   └── resources/
    │       ├── sql/             # SQL скрипты для создания таблиц
    │       └── application.yaml
    └── test/
```

**Примечание:** `docker-compose.yml` находится в этом репозитории и управляет как бэкендом, так и фронтендом (когда он будет создан). Фронтенд должен быть клонирован в соседнюю директорию `../solution-front`.

## Настройка

### Вариант 1: Запуск через Docker (рекомендуется)

1. **Убедитесь, что у вас установлены:**
   - Docker и Docker Compose
   - PostgreSQL на отдельном сервере

2. **Создайте схему в БД:**
   ```sql
   CREATE SCHEMA IF NOT EXISTS solution;
   ```

3. **Выполните SQL скрипты:**
   - `src/main/resources/sql/001_create_users_table.sql`
   - `src/main/resources/sql/002_create_wb_api_keys_table.sql`

4. **Скопируйте файл с переменными окружения:**
   ```bash
   cp .env.example .env
   ```

5. **Отредактируйте `.env` файл:**
   - Укажите данные для подключения к PostgreSQL
   - Установите JWT_SECRET (обязательно измените в продакшене!)

6. **Запустите приложение:**
   ```bash
   docker-compose up -d --build
   ```

7. **Проверьте статус:**
   ```bash
   docker-compose ps
   docker-compose logs -f backend
   ```

8. **Приложение будет доступно по адресу:** `http://localhost:8080/api`

### Вариант 2: Запуск без Docker

1. **Убедитесь, что у вас установлены:**
   - Java 21 или выше
   - Maven 3.6+
   - PostgreSQL

2. **Создайте схему в БД:**
   ```sql
   CREATE SCHEMA IF NOT EXISTS solution;
   ```

3. **Выполните SQL скрипты:**
   - `src/main/resources/sql/001_create_users_table.sql`
   - `src/main/resources/sql/002_create_wb_api_keys_table.sql`

4. **Настройте подключение к БД через переменные окружения или `application.properties`**

5. **Запуск приложения:**
   ```bash
   mvn spring-boot:run
   ```

6. **Приложение будет доступно по адресу:** `http://localhost:8080/api`

## API Endpoints

### Публичные эндпоинты

- `POST /api/auth/register` - Регистрация SELLER'а
  ```json
  {
    "email": "seller@example.com",
    "password": "password123",
    "wbApiKey": "your-wb-api-key"
  }
  ```

- `POST /api/auth/login` - Авторизация
  ```json
  {
    "email": "seller@example.com",
    "password": "password123"
  }
  ```
  Ответ:
  ```json
  {
    "token": "jwt-token",
    "email": "seller@example.com",
    "role": "SELLER",
    "userId": 1
  }
  ```

- `GET /api/health` - Проверка работоспособности

### Защищенные эндпоинты (требуют JWT токен в заголовке Authorization: Bearer <token>)

- `PUT /api/user/api-key` - Обновление WB API ключа (только для SELLER)
  ```json
  {
    "wbApiKey": "new-wb-api-key"
  }
  ```

- `GET /api/user/profile` - Получение профиля пользователя

## Роли пользователей

- **ADMIN** - Администратор системы
- **SELLER** - Продавец Wildberries (имеет WB API ключ)
- **WORKER** - Сотрудник SELLER'а (права будут реализованы позже)

## Особенности

- WB API ключ валидируется при первом использовании планировщиком
- Пароли хранятся в зашифрованном виде (BCrypt)
- JWT токены для аутентификации
- CORS настроен для работы с фронтендом на localhost:5173

## Документация Wildberries API

- **Документация WB API**: https://dev.wildberries.ru/
- **Базовый URL API**: `https://statistics-api.wildberries.ru`

## Планировщик

Планировщик задач включен (`@EnableScheduling`). Для реализации ночной загрузки данных создайте компонент с `@Scheduled` аннотацией.

Пример:
```java
@Component
@RequiredArgsConstructor
public class DataScheduler {
    private final WbApiKeyService wbApiKeyService;
    private final WbApiClient wbApiClient;
    
    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2:00
    public void loadData() {
        // Логика загрузки данных
    }
}
```

## Деплой на сервер

### Структура на сервере:
```
/opt/
├── solution-back/      # git clone solution-back
│   ├── docker-compose.yml
│   └── ...
└── solution-front/     # git clone solution-front (когда будет создан)
    └── ...
```

### Обновление:
```bash
# Обновить только бэкенд
cd /opt/solution-back
git pull
docker-compose up -d --build backend

# Обновить только фронтенд (когда будет создан)
cd /opt/solution-front
git pull
cd /opt/solution-back
docker-compose up -d --build frontend

# Обновить все
cd /opt/solution-back
git pull
cd /opt/solution-front
git pull
cd /opt/solution-back
docker-compose up -d --build
```

## Логирование

Логи сохраняются в:
- Контейнер: `/app/logs/application.log`
- Хост: `./logs/application.log`

Просмотр логов:
```bash
# Логи из контейнера
docker-compose logs -f backend

# Логи из файла на хосте
tail -f logs/application.log
```

