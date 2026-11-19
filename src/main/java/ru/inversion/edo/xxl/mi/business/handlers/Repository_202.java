package ru.inversion.edo.xxl.mi.business.handlers;

import org.springframework.stereotype.Repository;
import ru.inversion.edo.xxl.mi.business.AbstractMiBusinessRepository;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Repository
public class Repository_202 extends AbstractMiBusinessRepository {

   private static final URL DEF_XML = Repository_202.class.getResource("plsql/def.xml");
   private static final DateTimeFormatter ORACLE_REQUEST_TIME =
         DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSSSS xxx", Locale.ROOT);

   protected Repository_202(XxiRepositoryExecutor db ) {
      super(db);
   }

   public Set<Integer> infIds( )
   {
      return Set.of(202);
   }

   @Override
   protected Object bindInput(Object value)
   {
      if (value instanceof OffsetDateTime requestTime) {
         int offsetSeconds = requestTime.getOffset().getTotalSeconds();
         // Смещения со секундами и за пределами обычных часовых поясов передаём в UTC.
         if (offsetSeconds % 60 != 0 || offsetSeconds < -12 * 3600 || offsetSeconds > 14 * 3600)
            requestTime = requestTime.withOffsetSameInstant(ZoneOffset.UTC);
         return ORACLE_REQUEST_TIME.format(requestTime);
      }
      return value instanceof UUID uuid ? uuid.toString() : value;
   }

   @Override
   protected URL defXml() {
      return DEF_XML;
   }

   @Override
   protected String operationName() {
      return "MI_0202.apply_Request";
   }

   @Override
   protected String callName()
   {
      return "MI_0202.apply_Request";
   }
}
