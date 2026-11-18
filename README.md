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

## Внешняя конфигурация Oracle / PostgreSQL

Общие настройки Spring находятся внутри JAR: `src/main/resources/application.yml`.
Существующий внешний файл `application-xxlgate.yml` оставлен основой для профиля `xxlgate`.
Два файла **рядом с ним, а не внутри JAR**:

- `application-xxlgate-oracle.yml` — Oracle JDBC и Oracle RabbitMQ;
- `application-xxlgate-pg.yml` — PostgreSQL JDBC и PostgreSQL RabbitMQ.

В конце `application-xxlgate.yml` добавлены Spring Config Data документы с
`spring.config.activate.on-profile` и `spring.config.import`.
Это гарантирует загрузку **только выбранного** файла при активации профиля.
Если файл отсутствует, запуск выбранного контура должен завершиться ошибкой,
а не переключиться тихо на другой брокер.

| Контур | IntelliJ Active profiles | Переменные |
|---|---|---|
| Oracle | `xxlgate,oracle` | `ORACLE_DB_URL`, `ORACLE_DB_USER`, `ORACLE_DB_PASSWORD`, `ORACLE_RABBITMQ_ADDRESSES`, `ORACLE_RABBITMQ_USERNAME`, `ORACLE_RABBITMQ_PASSWORD` |
| PostgreSQL | `xxlgate,pg` | `PG_DB_URL`, `PG_DB_USER`, `PG_DB_PASSWORD`, `PG_RABBITMQ_ADDRESSES`, `PG_RABBITMQ_USERNAME`, `PG_RABBITMQ_PASSWORD` |

Рабочая директория приложения должна содержать `application-xxlgate.yml` и соответствующий файл контура (обычно корень проекта в IntelliJ, `/app` в Docker).

Пример форматов переменных (не значения для реального подключения):

```text
ORACLE_DB_URL=jdbc:oracle:thin:@//<host>:1521/<service>
ORACLE_RABBITMQ_ADDRESSES=amqp://<host>:5672/<oracle-vhost>
PG_DB_URL=jdbc:postgresql://<host>:5432/<database>
PG_RABBITMQ_ADDRESSES=amqp://<host>:5672/cbsbus_pg
```

**Важные ограничения:**
- `oracle` и `pg` нельзя включать одновременно;
- PG-vhost нельзя автоматически подставлять в Oracle-конфигурацию;
- боевые адреса и пароли не коммитить, подставлять через переменные окружения;
- существующий `xxlgate` / `xxlgate,docker` без новых профилей ведёт себя прежним образом. Для Docker-развёртывания отдельного контура нужно отдельно добавить внешний файл в образ/volume и выставить профиль: текущий Dockerfile копирует только `application-xxlgate.yml`.

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
