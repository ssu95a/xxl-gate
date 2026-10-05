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
