# CBSBUS/RabbitMQ-сервис xxl-gate

## Системные требования

- Рекомендуется использовать GNU/Linux с поддержкой systemd.
- openjdk 11 - установите пакет ОС openjdk-11-jre-headless или аналогичный в случае GNU/Linux, иначе
  скачайте https://www.oracle.com/java/technologies/downloads/#java11

## Конфигурирование

Конфигурация находится в файле `application-xxlgate.yml`. Файл имеет кодировку UTF-8, отступы имеют значение.

До запуска создайте каталог `log`, либо поменяйте в конфигурации путь к файлу лога на свой.

## Запуск в командной строке

Используйте прилагаемый файл `xxlgate.sh` или `xxlgate.bat`, в зависимости от ОС, предварительно заменив
путь к java.

## Установка в виде сервиса systemd

Перед установкой в качестве сервиса systemd поменяйте пути к каталогу сервиса и к java в
файле `cbsbus-xxl-gate.service`.

Пример установки systemd-сервиса, если базовый каталог `/opt/cbsbus/xxl-gate`:

```
sudo useradd -r -s /bin/false cbsbus
sudo chown -R cbsbus:root /opt/cbsbus/xxl-gate
sudo cp cbsbus-xxl-gate.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable cbsbus-xxl-gate 
sudo systemctl start cbsbus-xxl-gate 
```

Состояние сервиса можно проверить следующим образом:

```
sudo systemctl status cbsbus-xxl-gate
```

Если сервис не стартует и в его логах пусто, посмотрите лог его старта в systemd:

```
sudo journalctl -eu cbsbus-xxl-gate 
```

## Регистрация в CBSBUS

Добавьте в `cbs_bus.xml`:
```
  <rabbitgate id="r-xxl-gate">
    <routing-key>xxl-gate</routing-key>
  </rabbitgate>
```
После этого перезапустите CBSBUS.
