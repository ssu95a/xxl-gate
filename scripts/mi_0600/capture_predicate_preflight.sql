-- MI_0600: предварительная проверка CAPTURE_PREDICATE.
-- Запускать в той же PostgreSQL Pro БД и под той же ролью, что использует XXL-Gate.
-- Никаких изменений в NI_OUTFILES, MI_REQ, MI_0600 или MI_PRP скрипт не делает.
--
-- SQL-предикат берётся непосредственно из MI_prp для каждого inf_id.
-- Допустимые алиасы: o = xxi.ni_outfiles, b = xxi."NI_BASES".
-- Предикат НЕ проверяется и НЕ переписывается: PostgreSQL выполняет его как есть.
--
-- ВАЖНО: бизнес-соответствие 601 / 603 / 611 пока не установлено.
-- Не задавать широкие предикаты до согласования маршрутизации и даты начала
-- захвата. Желательно включать ограничение по o.cre_date в самом предикате.
--
-- После согласования правила задаются отдельно, вне этого read-only скрипта:
-- CALL MI_prp.set_Inf_Property(<inf_id>, 'CAPTURE_PREDICATE', <SQL_выражение>);
-- Не включать реальную отправку, пока выборки ниже не проверены.

BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL client_min_messages = NOTICE;

DO $preflight$
DECLARE
   v_inf_ids       integer[] := ARRAY[601, 603, 611];
   v_predicates    text[] := ARRAY[NULL::text, NULL::text, NULL::text];
   v_inf_id        integer;
   v_exists        boolean;
   v_enabled       text;
   v_predicate     text;
   v_sql           text;
   v_candidates    bigint;
   v_oldest        timestamp;
   v_newest        timestamp;
   v_state0_in_mi  bigint;
   v_state1_no_mi  bigint;
   v_overlap       bigint;
   v_row           record;
BEGIN
   FOR i IN 1..array_length(v_inf_ids, 1) LOOP
      v_inf_id := v_inf_ids[i];

      SELECT EXISTS (
         SELECT 1
           FROM xxi.mi_inf m
          WHERE m.inf_id = v_inf_id AND m.wsp_id = 600
      ) INTO v_exists;

      IF NOT v_exists THEN
         RAISE WARNING 'MI_0600: inf_id=% отсутствует в MI_INF/WSP=600', v_inf_id;
         CONTINUE;
      END IF;

      SELECT MI_prp.get_Inf_Property(v_inf_id, 'ENABLED', '1'),
             MI_prp.get_Inf_Property(v_inf_id, 'CAPTURE_PREDICATE', NULL)
        INTO v_enabled, v_predicate;

      IF v_enabled IS DISTINCT FROM '1' THEN
         RAISE NOTICE 'MI_0600: inf_id=% отключён (ENABLED=%)', v_inf_id, v_enabled;
         CONTINUE;
      END IF;

      IF v_predicate IS NULL OR btrim(v_predicate) = '' THEN
         RAISE NOTICE 'MI_0600: inf_id=% CAPTURE_PREDICATE не задан, захват выключен',
            v_inf_id;
         CONTINUE;
      END IF;

      v_predicates[i] := v_predicate;
      RAISE NOTICE 'МИ_0600: inf_id=%, CAPTURE_PREDICATE: %',
         v_inf_id, v_predicate;

      -- Точная область выборки Java-репозитория, без FOR UPDATE и LIMIT.
      v_sql := format($query$
         SELECT count(*)::bigint, min(o.cre_date), max(o.cre_date)
           FROM xxi.ni_outfiles o
           JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
          WHERE o.rec_state = '0'
            AND (%s)
            AND NOT EXISTS (
               SELECT 1 FROM xxi.mi_0600_f f
                WHERE f.czip_file_name = o.filename
            )
      $query$, v_predicate);

      EXECUTE v_sql INTO v_candidates, v_oldest, v_newest;

      RAISE NOTICE 'inf_id=%: ожидают захвата=%, первый cre_date=%, последний cre_date=%',
         v_inf_id, v_candidates, v_oldest, v_newest;

      -- Несогласованные статусы НЕ исправляем, только показываем количество.
      v_sql := format($query$
         SELECT count(*) FILTER (
                  WHERE o.rec_state = '0'
                    AND EXISTS (
                       SELECT 1 FROM xxi.mi_0600_f f
                        WHERE f.czip_file_name = o.filename
                    )
                ),
                count(*) FILTER (
                  WHERE o.rec_state = '1'
                    AND NOT EXISTS (
                       SELECT 1 FROM xxi.mi_0600_f f
                        WHERE f.czip_file_name = o.filename
                    )
                )
           FROM xxi.ni_outfiles o
           JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
          WHERE (%s)
      $query$, v_predicate);

      EXECUTE v_sql INTO v_state0_in_mi, v_state1_no_mi;

      RAISE NOTICE 'inf_id=%: rec_state=0 уже в MI: %; rec_state=1 без MI: %',
         v_inf_id, v_state0_in_mi, v_state1_no_mi;

      -- Небольшой срез именно тех XML, которые выберет обработчик.
      v_sql := format($query$
         SELECT o.int_id, o.filename, o.cre_date, o.file_type, b.mod_id
           FROM xxi.ni_outfiles o
           JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
          WHERE o.rec_state = '0'
            AND (%s)
            AND NOT EXISTS (
               SELECT 1 FROM xxi.mi_0600_f f
                WHERE f.czip_file_name = o.filename
            )
          ORDER BY o.int_id
          LIMIT 10
      $query$, v_predicate);

      FOR v_row IN EXECUTE v_sql LOOP
         RAISE NOTICE '  XML: int_id=%, filename=%, cre_date=%, file_type=%, mod_id=%',
            v_row.int_id, v_row.filename, v_row.cre_date,
            v_row.file_type, v_row.mod_id;
      END LOOP;
   END LOOP;

   -- Если один XML подходит двум маршрутам, фактический получатель зависит
   -- от порядка захвата. Такие пересечения нужно устранить в настройках.
   FOR i IN 1..(array_length(v_inf_ids, 1) - 1) LOOP
      IF v_predicates[i] IS NULL THEN
         CONTINUE;
      END IF;

      FOR j IN (i + 1)..array_length(v_inf_ids, 1) LOOP
         IF v_predicates[j] IS NULL THEN
            CONTINUE;
         END IF;

         v_sql := format($query$
            SELECT count(*)::bigint
              FROM xxi.ni_outfiles o
              JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
             WHERE o.rec_state = '0'
               AND (%s)
               AND (%s)
               AND NOT EXISTS (
                  SELECT 1 FROM xxi.mi_0600_f f
                   WHERE f.czip_file_name = o.filename
               )
         $query$, v_predicates[i], v_predicates[j]);

         EXECUTE v_sql INTO v_overlap;

         IF v_overlap > 0 THEN
            RAISE WARNING 'MI_0600: пересечение маршрутов % и %: % XML',
               v_inf_ids[i], v_inf_ids[j], v_overlap;
         ELSE
            RAISE NOTICE 'MI_0600: маршруты % и % не пересекаются',
               v_inf_ids[i], v_inf_ids[j];
         END IF;
      END LOOP;
   END LOOP;
END;
$preflight$;

ROLLBACK;
