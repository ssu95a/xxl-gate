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
import java.time.LocalDateTime;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class MI_0007_ResponseRepository implements MiItemResultRepository
{
   private static final String INF_NAMESPACE =
           "urn://mvd/gismu/RFP_ACTUAL_BANK/1.0.1";

   public static final URL defXml =
           MI_0007_ResponseRepository.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;
   private final ObjectMapper objectMapper;

   @Override
   public String infNamespace()
   {
      return INF_NAMESPACE;
   }

   @Override
   public MiItemApplyResult applyItem(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int itemIndex
   )
   {
      MI_0007_ResponseRow row =
              prepareRow(response, item, itemIndex);

      return db.execute(
              "MI_0007.applyItemResponse",
              response.itemParameters(item, itemIndex),
              tc -> {
                 MiItemApplyResult result =
                         applyItemImpl(tc, row);

                 tc.commit();
                 return result;
              }
      );
   }

   private MI_0007_ResponseRow prepareRow(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int index
   )
   {
      if( item.payload() == null )
      {
         throw Errors.miResponseBadFormat(
                 "MI_0007 item payload is null",
                 response.itemParameters(item, index)
         );
      }

      MediaType mediaType;

      try
      {
         mediaType =
                 MediaType.parseMediaType(
                         item.payload().contentType()
                 );
      }
      catch( InvalidMediaTypeException exception )
      {
         throw Errors.miResponseBadFormat(
                 "Bad MI_0007 item payload media type",
                 exception,
                 response.itemParameters(item, index)
         );
      }

      if( !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType) )
      {
         throw Errors.miResponseBadFormat(
                 "Unsupported MI_0007 item payload media type",
                 response.itemParameters(item, index)
         );
      }

      try( InputStream stream = item.payload().openStream() )
      {
         MI_0007_ResponseRow row =
                 objectMapper.readValue(
                         stream,
                         MI_0007_ResponseRow.class
                 );

         row.setItemUuid(item.itemExternalUuid());
         row.setMessageUuid(response.messageId());
         row.setRequestUuid(response.originalRequestId());
         row.setItemIndex(index);

         return row;
      }
      catch( Exception exception )
      {
         throw Errors.miResponseBadFormat(
                 "Error reading MI_0007 item JSON payload",
                 exception,
                 response.itemParameters(item, index)
         );
      }
   }

  /*
      :request_uuid,
      :message_uuid,
      :item_uuid,
      :ires_code,
      :tres_time,
      :cres_info,
      :_return,
      :res_info
 */

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
                        case "tres_time"    -> LocalDateTime.now();
                        case "cres_info"    -> row.getComment();
                        default -> throw new IllegalStateException("Unexpected value: " + name);
                    };
                 }
              })
                  .build()
              .execute();

      final int    retVal = call_Apply.<Integer>getReturnValue();
      final String retInf = call_Apply.get("ret_info");

      final MiItemApplyResult.Status status = MiItemApplyResult.Status.ofInt(retVal);

      return new MiItemApplyResult(
         row.getItemIndex(), row.getItemUuid(), status, Integer.toString(retVal), retInf
      );
   }

}