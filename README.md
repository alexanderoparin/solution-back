# Solution Backend

REST API и фоновая логика Clicki: кабинеты WB и Ozon, аналитика, реклама, очереди событий маркетплейсов, подписки, оплата Точка Банк.

Обзор репозитория — в [корневом README](../README.md).

## Технологии

- Java 21, Maven
- Spring Boot **3.3.5** (Web, Data JPA, Security, Validation, Mail)
- PostgreSQL, Hibernate (`ddl-auto: validate`)
- JWT (jjwt), BCrypt
- ShedLock — блокировки `@Scheduled`
- Lombok

Автотестов бэкенда в репозитории нет (не добавлять в `src/test/`, если явно не попросили). Проверка — `mvn compile`, логи, ручной прогон.

## Пакеты `src/main/java/ru/oparin/solution/`

| Пакет | Содержимое |
|--------|------------|
| `config` | Security, CORS, свойства WB/Ozon/Точка, планировщик, ShedLock |
| `controller` | REST |
| `dto` | Запросы/ответы |
| `exception` | Ошибки API |
| `model` | JPA-сущности |
| `repository` | Spring Data JPA, `spec` |
| `security` | JWT, фильтры |
| `service` | Бизнес-логика: `analytics` (wb/ozon), `events`, `sync`, `wb`, `ozon`, `campaign`, `abtest`, `tochka` |
| `scheduler` | `@Scheduled` (ночная синхронизация, retry очередей, fallback оплаты) |

SQL: `src/main/resources/sql/`. Применять **все файлы по имени по возрастанию**. Номера 116 и 117 встречаются дважды — это разные скрипты.

Конфиг: `src/main/resources/application.yaml` (порт **8080**, context-path **`/api`**).

## Запуск локально

JDK 21, Maven 3.9+, PostgreSQL со схемой `solution` и накатанными SQL.

```bash
cd solution_back
# DB_*, JWT_SECRET; при необходимости MAIL_PASSWORD, TOCHKA_*, CORS_ALLOWED_ORIGINS
mvn spring-boot:run
```

- База API: `http://localhost:8080/api`
- Health: `GET http://localhost:8080/api/health`

## Docker Compose

В этом каталоге `docker-compose.yml`: `backend` + `frontend` (Nginx-образ фронта).

`.env` рядом с compose. Минимум: **`DB_HOST`**, **`DB_PASSWORD`**, **`JWT_SECRET`**. Оплата: [docs/TOCHKA_SETUP.md](../docs/TOCHKA_SETUP.md).

Контекст сборки фронта в compose: **`../solution-front`**. Если клон называется `solution_front` — поправьте `context` или симлинк.

В runtime-образ импортируются сертификаты НУЦ Минцифры (`docker/certs/`) в JVM `cacerts`. Без них вызовы `enter.tochka.com` падают с `PKIX path building failed`.

```bash
cd solution_back
docker compose up -d --build
```

Логи: контейнер `/app/logs/application.log`, хост `./logs`.

## REST (префиксы относительно `/api`)

| Префикс | Назначение |
|---------|------------|
| `/auth` | Регистрация, вход, сброс пароля, подтверждение email |
| `/health` | Liveness |
| `/public` | Лендинг (заявка на аудит и т.п.), без JWT |
| `/public/invitations` | Принятие приглашения в кабинет |
| `/user` | Профиль, пароль, статус доступа, удаление аккаунта |
| `/cabinets` | Кабинеты WB/Ozon, ключи, доступы, work context |
| `/analytics` | Сводка, товары, карточка артикула |
| `/analytics/article/{nmId}/notes` | Заметки к артикулу |
| `/advertising` | РК, синхронизация промо |
| `/advertising/campaigns/{id}/notes` | Заметки к кампании |
| `/advertising/campaigns/{advertId}/manage` | Биддер, расписание, бюджет (WB) |
| `/advertising/ab-tests` | А/Б-тесты фото (WB) |
| `/subscription` | Тарифы, подписка «Управление РК», оплата |
| `/webhooks/tochka` | Webhook эквайринга (публичный) |
| `/admin` | Планы, подписки, очереди WB/Ozon, ручной sync |
| `/admin/promo-codes` | Промокоды |
| `/users` | Пользователи и доступы к кабинетам |
| `/wb-api` | Сервисные вызовы WB |

Методы — в `*Controller.java`. Отдельной OpenAPI-спеки нет.

## Роли и кабинеты

- `Role`: **ADMIN**, **USER**.
- `AccountType`: **SELLER**, **AGENCY**, **EMPLOYEE** (отображение и статистика).
- Доступ к разделам кабинета — гранты. Кабинет = один маркетплейс (`MarketplaceType.WB` / `OZON`).

## Интеграции маркетплейсов

Очереди **WB** (`WbApiEvent*`) и **Ozon** (`OzonApiEvent*`): карточки, цены, остатки, аналитика, реклама. Планировщик ставит пачки событий; диспетчер соблюдает rate-limit API.

Документация: [WB](https://dev.wildberries.ru/), [Ozon Seller](https://docs.ozon.ru/api/seller/). Модель продукта: [docs/ozon-wb-product-model.md](../docs/ozon-wb-product-model.md).

## Логирование

`application.yaml` → `logging.*`. В Docker по умолчанию `/app/logs/application.log`.
