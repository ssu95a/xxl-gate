-- MI_0600: инвентаризация исходящих XML для согласования CAPTURE_PREDICATE.
-- Выполнять в PostgreSQL Pro, в той же БД, что XXL-Gate.
-- Скрипт не изменяет данные и не задаёт настройки MI_prp.
-- Оригинальные маршруты 601/603/611 пока не восстановлены из бизнес-логики.
--
-- Основные поля для будущего правила:
--   o = xxi.ni_outfiles (file_type, cre_date, filename, id_base, int_id, rec_state);
--   b = xxi."NI_BASES" (mod_id, file_type, id_base).
--
-- Для первого включения особенно важна нижняя граница o.cre_date:
-- без неё в захват могут попасть исторические записи с rec_state='0'.

BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '60s';

-- 1. Как распределены необработанные файлы по исходным группам.
-- Колонки last_7_days/last_30_days позволяют отличить старый архив
-- от текущего потока. Они не влияют на захват в Java.
SELECT b.mod_id,
       b.file_type AS base_file_type,
       o.file_type AS xml_file_type,
       count(*) AS pending_total,
       count(*) FILTER (
          WHERE o.cre_date >= CURRENT_TIMESTAMP - INTERVAL '7 days'
       ) AS last_7_days,
       count(*) FILTER (
          WHERE o.cre_date >= CURRENT_TIMESTAMP - INTERVAL '30 days'
       ) AS last_30_days,
       min(o.cre_date) AS oldest_xml,
       max(o.cre_date) AS newest_xml
  FROM xxi.ni_outfiles o
  JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
 WHERE o.rec_state = '0'
 GROUP BY b.mod_id, b.file_type, o.file_type
 ORDER BY pending_total DESC, b.mod_id, xml_file_type;

-- 2. Последние XML, ещё не отмеченные как обработанные.
-- При выборе CAPTURE_PREDICATE проверяем не только file_type,
-- но и filename, mod_id, дату и связанный базовый документ.
SELECT o.int_id,
       o.id_base,
       o.filename,
       o.cre_date,
       o.file_type AS xml_file_type,
       b.mod_id,
       b.file_type AS base_file_type
  FROM xxi.ni_outfiles o
  JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
 WHERE o.rec_state = '0'
 ORDER BY o.cre_date DESC, o.int_id DESC
 LIMIT 25;

-- 3. Сколько записей имеют каждый исходный статус.
-- '1' может означать экспорт старым JavaFX-клиентом;
-- его нельзя автоматически считать регистрацией в MI.
SELECT o.rec_state,
       count(*) AS total,
       min(o.cre_date) AS oldest_xml,
       max(o.cre_date) AS newest_xml
  FROM xxi.ni_outfiles o
 GROUP BY o.rec_state
 ORDER BY o.rec_state NULLS LAST;

ROLLBACK;
