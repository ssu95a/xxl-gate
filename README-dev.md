# CBSBUS/RabbitMQ-сервис xxl-gate

## Сборка

Для сборки требуются:

- openjdk 11
- maven 3

Сборка:

```
mvn clean package
```

В каталоге `target` будут jar и дистрибутив.

Выпуск релиза:

```
mvn release:prepare
mvn release:perform
```

## Формат сообщений

Пример запроса:
```
<request>
    <text>...</text>
</request>  
```

Пример ответа при успешной обработке запроса:
```
<response>
    <success>true</success>
</response>
```

Пример ответа при ошибке:
```
<response>
    <success>false</success>
    <errorMessage><![CDATA[Текст ошибки]]></errorMessage>
</response>
```

В файле `sql/plsql-example.sql` показан пример вызова из PL/SQL.
