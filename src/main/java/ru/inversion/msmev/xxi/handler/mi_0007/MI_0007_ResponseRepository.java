package ru.inversion.msmev.xxi.handler.mi_0007;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.CallStatement;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.item.MiItemApplyResult;
import ru.inversion.msmev.mi.response.item.MiItemResultRepository;
import ru.inversion.msmev.xxi.repo.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.InputStream;
import java.net.URL;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class MI_0007_ResponseRepository implements MiItemResultRepository
{
   /** */
   private static final String INF_NAMESPACE = "urn://mvd/gismu/RFP_ACTUAL_BANK/1.0.1";

   /** */
   public static final URL defXml = MI_0007_ResponseRepository.class.getResource( "plsql/def.xml" );

   /** */
   private final XxiRepositoryExecutor db;

   /** */
   @Override
   public String infNamespace()
   {
      return INF_NAMESPACE;
   }

   @Override
   public MiItemApplyResult applyItem( MiAsyncResponse response, MiAsyncItemResult item, int itemIndex )
   {
      final MI_0007_ResponseRow row = prepareRow(response, item, itemIndex);

      return db.execute( "MI_0007.applyItemResponse", response.itemParameters( item, itemIndex ),
        tc -> {
           MiItemApplyResult result = applyItemImpl(tc, row );
           tc.commit();
           return result;
        }
      );
   }

   /** */
   private MI_0007_ResponseRow prepareRow( MiAsyncResponse response, MiAsyncItemResult item, int index )
   {
      final MediaType mediaType;

      try {
         mediaType = MediaType.parseMediaType( item.payload().contentType() );
      }
      catch ( InvalidMediaTypeException imte) {
         throw Errors.miResponseBadFormat( "Bad MediaType payload", imte, U.toMap("mediaType",  item.payload().contentType() ) );
      }

      final MI_0007_ResponseRow row;

      try( InputStream is = item.payload().openStream() ) {

         if( mediaType == MediaType.APPLICATION_JSON )
         {
            ObjectMapper mapper = new ObjectMapper();
            row = mapper.readValue( is, MI_0007_ResponseRow.class );
         }
         else
            throw new IllegalStateException("MediaType '" + mediaType + "' is not supported in 'MI_0007_ResponseRepository'");
      }
      catch ( Exception e ) {
         throw Errors.miResponseBadFormat( "Error on read pojo from JSON payload", e, U.toMap( "mediaType",  item.payload().contentType() ) );
      }

      row.setItemUuid   ( item.itemExternalUuid()     );
      row.setMessageUuid( response.messageId()        );
      row.setRequestUuid( response.originalRequestId());
      row.setItemIndex  ( index );

      return row;
   }

   /** */
   private MiItemApplyResult applyItemImpl( TaskContext tc, MI_0007_ResponseRow row )
   {
      final IDataCall call_Apply
            = SQLCallBuilder.NEW(tc).url(defXml).name("apply_Item_Result")
              .callBackParameters(new ParametersByName() {
                 @Override
                 public Object getParameter(String name) {
                    return switch (name) {
                        case "request_uuid" -> row.getRequestUuid();
                        case "message_uuid" -> row.getMessageUuid();
                        case "item_uuid"    -> row.getItemUuid();
                        case "ires_code"    -> row.getDocStatus();
                        case "cres_code"    -> row.getComment();
                        default -> throw new IllegalStateException("Unexpected value: " + name);
                    };
                 }
              })
                  .build()
              .execute();

      final int    retVal = call_Apply.<Integer>getReturnValue();
      final String retInf = call_Apply.get("res_info");

      final MiItemApplyResult.Status status = MiItemApplyResult.Status.ofInt(retVal);

      if( status == MiItemApplyResult.Status.APPLIED )
          tc.commit();
      else
          tc.rollback();

      return new MiItemApplyResult(
         row.getItemIndex(), row.getItemUuid(), status, Integer.toString(retVal), retInf
      );
   }

}