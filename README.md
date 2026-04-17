# Solution Backend

REST API и фоновая логика платформы Solution для продавцов Wildberries: аналитика, реклама, кабинеты, подписки, очереди событий WB.

Общий обзор монорепозитория — в [корневом README](../README.md).

## Технологии

- Java 21, Maven
- Spring Boot 3.3.x (Web, Data JPA, Security, Validation, Mail)
- PostgreSQL, Hibernate (`ddl-auto: validate`)
- JWT (jjwt), BCrypt
- **ShedLock** — блокировки для распределённых задач
- Lombok

## Структура `src/main/java/ru/oparin/solution/`

| Пакет | Содержимое |
|--------|------------|
| `config` | Security, CORS, свойства WB, планировщик, ShedLock |
| `controller` | REST-контроллеры |
| `dto` | Запросы/ответы API |
| `exception` | Обработка ошибок |
| `model` | JPA-сущности |
| `repository` | Spring Data JPA, при необходимости `spec` |
| `security` | JWT, фильтры |
| `service` | Бизнес-логика; подпакеты `sync`, `events`, `wb`, `analytics` и др. |
| `scheduler` | `@Scheduled`-задачи |

SQL-скрипты схемы: `src/main/resources/sql/` (файлы `001_…` … — применять **по порядку номера**).

Конфигурация: `src/main/resources/application.yaml` (порт **8080**, контекст **`/api`**).

## Запуск локально

Требования: JDK 21, Maven 3.9+, PostgreSQL со схемой `solution` и применёнными SQL-скриптами.

```bash
cd solution_back
# задайте переменные окружения: DB_*, JWT_SECRET, при необходимости MAIL_PASSWORD, ROBOKASSA_*, CORS_ALLOWED_ORIGINS
mvn spring-boot:run
```

- База API: `http://localhost:8080/api`
- Health: `GET http://localhost:8080/api/health`

## Docker Compose

В этом каталоге лежит **`docker-compose.yml`**: сервисы `backend` и `frontend` (сборка Nginx-образа фронта).

Создайте файл **`.env`** рядом с compose (шаблон `.env.example` в репозитории может отсутствовать — ориентируйтесь на блок `environment` в compose и на `application.yaml`). Минимум: **`DB_HOST`**, **`DB_PASSWORD`**, **`JWT_SECRET`**.

Сборка фронта в compose использует контекст **`../solution-front`**. Если в монорепозитории папка называется **`../solution_front`**, поправьте `context` в `docker-compose.yml` или используйте симлинк.

```bash
cd solution_back
docker compose up -d --build
```

Логи бэкенда: в контейнере `/app/logs/application.log`, на хосте — `./logs` при смонтированном volume.

## REST: префиксы контроллеров

Все пути ниже относительно **`/api`** (context-path).

| Префикс | Назначение |
|---------|------------|
| `/auth` | Регистрация, вход, восстановление пароля, подтверждение email |
| `/health` | Проверка живости |
| `/user` | Профиль, пароль, ключи, статус доступа |
| `/cabinets` | Кабинеты WB, привязка ключей, work context для MANAGER |
| `/analytics` | Сводка, товары, карточка артикула, цели по РК в артикуле |
| `/advertising` | Список РК, детали, синхронизация промо со WB |
| `/advertising/campaigns/{id}/notes` | Заметки и файлы к кампании |
| `/analytics/article/{nmId}/notes` | Заметки к артикулу |
| `/subscription` | Тарифы, подписка, Robokassa |
| `/admin` | Админские операции (тарифы, события WB и т.д.) |
| `/users` | Управление пользователями (роли, кабинеты) |
| `/wb-api` | Сервисные вызовы, связанные с WB API |

Полный перечень методов — в исходниках классов `*Controller.java`. Отдельной OpenAPI-спеки в проекте нет.

## Роли

- **SELLER** — владелец кабинета, API-ключи, сотрудники
- **WORKER** — сотрудник селлера
- **MANAGER** — доступ к кабинетам клиентов (work context)
- **ADMIN** — администрирование системы

## Интеграция с Wildberries

Базовые URL и задержки синхронизации задаются в `application.yaml` (блоки `wb.api`, `wb.*` sync delays). Документация WB: https://dev.wildberries.ru/

В приложении используется очередь/диспетчер **событий WB** (`WbApiEvent*`), планировщики и исполнители синхронизации карточек, цен, остатков, рекламы, отзывов и т.д.

## Логирование

Уровни и файл — в `application.yaml` (`logging.*`). В Docker по умолчанию пишется в `/app/logs/application.log`.
