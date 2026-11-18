# xxl

XXL-Gate — шлюз ЦАБС к `mi`: забирает запросы `mi` из очередей `mi-edo.*`, исполняет их в базе
ЦАБС и возвращает результат; синхронные запросы `mi` принимает по REST.

Java 25 · Spring Boot 3.5 · `mi-transport` · cbsbus-amqp.

## Конфигурация

Настройки контура — [`application-xxlgate.yml`](application-xxlgate.yml), профиль `xxlgate`.
Секреты в файле не хранятся:

| Переменная | Что закрывает |
|---|---|
| `XXL_DB_USER`, `XXL_DB_PASSWORD` | Учётка базы ЦАБС (`DB_USER`, `DB_PASSWORD` в контейнере) |
| `XXL_CBSBUS_PASSWORD` | Пароль `cbsbus` в шине ЦАБС (`SPRING_RABBITMQ_PASSWORD`) |
| `MI_TRANSPORT_PASSWORD` | Учётка `mi-edo` в RabbitMQ и MinIO стенда, общая с `mi` и `mi-infra` |

На кулане они приходят из CI/CD-переменных группы GitLab `edo`, локально — из `.env`
(образец — [`.env.example`](.env.example)).

## Раздельные профили Oracle / PostgreSQL (локальная разработка)

Для выбора БД **и** соответствующего RabbitMQ-контура одновременно используйте два активных Spring-профиля:

| Контур | Активные профили | Обязательные переменные окружения |
|---|---|---|
| Oracle | `xxlgate,oracle` | `ORACLE_DB_URL`, `ORACLE_DB_USER`, `ORACLE_DB_PASSWORD`, `ORACLE_RABBITMQ_ADDRESSES`, `ORACLE_RABBITMQ_USERNAME`, `ORACLE_RABBITMQ_PASSWORD` |
| PostgreSQL | `xxlgate,pg` | `PG_DB_URL`, `PG_DB_USER`, `PG_DB_PASSWORD`, `PG_RABBITMQ_ADDRESSES`, `PG_RABBITMQ_USERNAME`, `PG_RABBITMQ_PASSWORD` |

Профиль контура должен идти **последним**: в нём свойства базы и RabbitMQ перекрывают общую конфигурацию `application-xxlgate.yml`. Укажите в IntelliJ IDEA поле *Active profiles* как `xxlgate,oracle` или `xxlgate,pg` (либо используйте аргумент `--spring.profiles.active=xxlgate,oracle`).

Пример значений без паролей:

```text
ORACLE_DB_URL=jdbc:oracle:thin:@//<host>:1521/<service>
ORACLE_RABBITMQ_ADDRESSES=amqp://<host>:5672/<oracle-vhost>
PG_DB_URL=jdbc:postgresql://<host>:5432/<database>
PG_RABBITMQ_ADDRESSES=amqp://<host>:5672/cbsbus_pg
```

**Не подставляйте автоматически** PG RabbitMQ vhost в Oracle-профиль. Oracle bus/gate/queue должны соответствовать реальной конфигурации мостов Oracle AQ ↔ RabbitMQ. Пароли и адреса стендов в репозиторий не коммитить.

Оба профиля одновременно (`oracle,pg`) не включать: последний перезапишет свойства другого. Профиль `xxlgate` **без overlay** сохранён как прежний вариант конфигурации, чтобы не нарушить существующий деплой.

## Стенд на кулане

Стенд один и собирается с `develop`. Джобы `deploy` и `redeploy` приходят из шаблона
`deploy-app.yml` в `mi-cicd`, порт и тег образа — переменные `.gitlab-ci.yml`.

| Ветка | Проект | Порт | Образ |
|---|---|---|---|
| `develop` | `edo` | 8081 | `xxl-gate:develop` |

В сети `edo_default` включён профиль `docker`: брокер и хранилище `mi-transport` резолвятся как
`rabbitmq` и `minio`, `mi` — по имени `mi`. База и шина ЦАБС — девовые, из `application-xxlgate.yml`.

`xxl-emulator` на стенд с XXL-Gate не поднимать: они слушают одни и те же очереди.

## Локально

```
docker compose up -d --build
```

Сборке нужен Nexus контура (`.mvn/settings.xml`), без VPN она не пройдёт.
