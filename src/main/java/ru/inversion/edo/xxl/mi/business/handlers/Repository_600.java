package ru.inversion.edo.xxl.mi.business.handlers;

import org.springframework.stereotype.Repository;

import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.business.AbstractMiBusinessRepository;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.repo.InfRole;
import ru.inversion.utils.U;

import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Repository
public class Repository_600 extends AbstractMiBusinessRepository
{
   private static final URL DEF_XML =
           Repository_600.class.getResource("plsql/def.xml");

   private static final String APPLY_REQUEST =
           "MI_0600.apply_Request";

   private static final String GET_RECEIVE_DIR =
           "MI_0600.getReceiveDir";

   private static final String RECEIVE_DIR_SQL =
      """
      select MI_prp.get_Inf_Property( ?, 'RECEIVE_DIR', null )
      """;


   /** */
   public Repository_600(XxiRepositoryExecutor db)
   {
      super(db);
   }


   /** */
   public Set<Integer> infIds()
   {
      return Set.of(602, 604);
   }


   /** */
   @Override
   protected URL defXml()
   {
      return DEF_XML;
   }


   /** */
   @Override
   protected String operationName()
   {
      return APPLY_REQUEST;
   }


   /** */
   @Override
   protected String callName()
   {
      return APPLY_REQUEST;
   }


   /**
    * MI_0600 payload является ZIP.
    *
    * Поэтому здесь намеренно НЕ вызывается
    * AbstractMiBusinessRepository.readPayloadText().
    *
    * ZIP обрабатывается Handler_600 / MI_0600_UnzipService.
    */
   @Override
   protected Map<String, Object> prepareParameters(
           MiBusinessRequest request
   )
   {
      if( request == null )
          throw Errors.miBusinessPayloadBadFormat( "MI_0600 business request is null", Map.of() );

      Map<String, Object> parameters = new LinkedHashMap<>();

      parameters.put( "message_uuid", request.messageId() );
      parameters.put( "original_request_uuid", request.requestId() );

      parameters.put( "correlation_id", U.nvl( request.correlationId(), UUID.randomUUID() ) );
      parameters.put( "request_time", request.createdAt() );

      return parameters;
   }


   /**
    * Рабочая директория для входящих файлов данного inf_id.
    */
   public Path getReceiveDir(int infId)
   {
      return db.execute (
              GET_RECEIVE_DIR,
              U.toMap("inf_id", infId),
              tc ->
              {
                 try( PreparedStatement ps = tc.getConnection().prepareStatement(RECEIVE_DIR_SQL) )
                 {
                    ps.setInt(1, infId);
                    try(ResultSet rs = ps.executeQuery())
                    {
                       if( !rs.next() )
                          throw Errors.config( "Не удалось получить RECEIVE_DIR для вида сведений " + infId, U.toMap( "inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR" ) );

                       String value = rs.getString(1);

                       if( value == null || value.isBlank() )
                           throw Errors.config( "Для вида сведений " + infId + " не настроена директория приема", U.toMap( "inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR" ) );

                       try {
                          return Path.of(value.trim());
                       }
                       catch( InvalidPathException e ) {
                          throw Errors.config( "Для вида сведений " + infId + " некорректно настроена директория приема", e, U.toMap( "inf_id", infId, "role", InfRole.Respondent, "property", "RECEIVE_DIR" ) );
                       }
                    }
                 }
              }
      );
   }
}