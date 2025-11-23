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
src/
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
│       └── application.properties
└── test/
```

## Настройка

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

4. **Настройте подключение к БД в `src/main/resources/application.properties`:**
   ```properties
   spring.datasource.url=jdbc:postgresql://your_host:5432/your_database?currentSchema=solution
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

5. **Настройте JWT секрет (обязательно измените в продакшене!):**
   ```properties
   jwt.secret=your-secret-key-change-in-production-min-256-bits
   ```

6. **Запуск приложения:**
   ```bash
   mvn spring-boot:run
   ```

7. **Приложение будет доступно по адресу:** `http://localhost:8080/api`

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

