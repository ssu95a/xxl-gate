# xxl

XXL-Gate — шлюз ЦАБС к `mi`: забирает запросы `mi` из очередей `mi-edo.*`, исполняет их в базе
ЦАБС и возвращает результат; синхронные запросы `mi` принимает по REST.

Java 25 · Spring Boot 3.5 · `mi-transport` · cbsbus-amqp.

## Конфигурация

Общие настройки — [`application-xxlgate.yml`](application-xxlgate.yml), профиль `xxlgate`.
**Файл больше не содержит подключений ни к БД, ни к RabbitMQ.** Конкретный контур обязателен: `pg` или `oracle`.
Секреты в файлах не хранятся:

| Переменная | Что закрывает |
|---|---|
| `XXL_DB_URL`, `XXL_DB_USER`, `XXL_DB_PASSWORD` | Параметры ЦАБС PG; в контейнере отображаются в `PG_DB_*` |
| `XXL_CBSBUS_PASSWORD` | Пароль шины ЦАБС; в контейнере `PG_RABBITMQ_PASSWORD` |
| `MI_TRANSPORT_PASSWORD` | Учётка `mi-edo` в RabbitMQ и MinIO стенда, общая с `mi` и `mi-infra` |

На кулане они приходят из CI/CD-переменных группы GitLab `edo`, локально — из `.env`
(образец — [`.env.example`](.env.example)).

## Внешняя конфигурация Oracle / PostgreSQL

Общие настройки Spring находятся внутри JAR: `src/main/resources/application.yml`.
Существующий внешний файл `application-xxlgate.yml` оставлен основой для профиля `xxlgate`.
Два файла **рядом с ним, а не внутри JAR**:

- `application-xxlgate-oracle.yml` — Oracle JDBC и Oracle RabbitMQ;
- `application-xxlgate-pg.yml` — PostgreSQL JDBC и PostgreSQL RabbitMQ.

В базовом `application-xxlgate.yml` нет `datasource.url/username/password/driver` и `rabbitmq.addresses/username/password`.
Остаются общие Hikari, listener, logging и расписания. В конце файла находятся Spring Config Data документы с
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

### Локальный Oracle без Environment variables в IntelliJ

Oracle-профиль дополнительно импортирует необязательный файл
`application-xxlgate-oracle.local.yml`. Он исключён из Git и загружается
с более высоким приоритетом, чем шаблонные значения `ORACLE_*`.

```powershell
Copy-Item application-xxlgate-oracle.local.yml.example application-xxlgate-oracle.local.yml
```

В локальной копии заполните `spring.datasource.url/username/password` и
`spring.rabbitmq.addresses/username/password` действительными значениями,
без `${ORACLE_*}`. В IntelliJ оставьте только профиль `xxlgate,oracle`,
рабочая директория — корень проекта. Переменные `ORACLE_*` в IDE не нужны,
если все пять параметров указаны в локальном YAML.

**Безопасность:** локальный YAML содержит секреты в открытом виде,
поэтому не отправляйте его в Git, чаты и логи; держите доступ к файлу
ограниченным своей учётной записью. Шаблон `.example` не содержит секретов.
На CI/в Docker локальный YAML отсутствует, поэтому используются
обязательные переменные окружения, заданные в конфигурации контура.

**Важные ограничения:**
- `oracle` и `pg` нельзя включать одновременно;
- PG-vhost нельзя автоматически подставлять в Oracle-конфигурацию;
- боевые адреса и пароли не коммитить, подставлять через переменные окружения;
- **один `xxlgate` без `oracle` или `pg` не является рабочим подключением**: значения БД и шины не заданы;
- Compose развёртывает существующий PG-контур как `xxlgate,pg,docker`. Dockerfile копирует внешний `application-xxlgate-pg.yml` рядом с JAR, но не внутрь него;
- в Compose старые CI-переменные `XXL_DB_*` и `XXL_CBSBUS_*` сопоставляются значениям `PG_DB_*` и `PG_RABBITMQ_*`;
- PG vhost `cbsbus_pg` задан как PG-специфичный fallback **только в compose.yaml**, не в базовом YAML и не в Oracle;
- для отдельного Oracle Docker-контура нужно предоставить `application-xxlgate-oracle.yml` извне (volume или COPY) и настроить `SPRING_PROFILES_ACTIVE=xxlgate,oracle,docker` со своими переменными.

## Стенд на кулане

Стенд один и собирается с `develop`. Джобы `deploy` и `redeploy` приходят из шаблона
`deploy-app.yml` в `mi-cicd`, порт и тег образа — переменные `.gitlab-ci.yml`.

| Ветка | Проект | Порт | Образ |
|---|---|---|---|
| `develop` | `edo` | 8081 | `xxl-gate:develop` |

В сети `edo_default` включён профиль `docker`: брокер и хранилище `mi-transport` резолвятся как
`rabbitmq` и `minio`, `mi` — по имени `mi`. База и шина ЦАБС — девовые, из внешнего `application-xxlgate-pg.yml` и переменных Compose/CI.

`xxl-emulator` на стенд с XXL-Gate не поднимать: они слушают одни и те же очереди.

## Локально

```
docker compose up -d --build
```

Сборке нужен Nexus контура (`.mvn/settings.xml`), без VPN она не пройдёт.
