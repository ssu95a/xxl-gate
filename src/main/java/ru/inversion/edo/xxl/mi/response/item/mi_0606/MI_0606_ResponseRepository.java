package ru.inversion.edo.xxl.mi.response.item.mi_0606;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.mi.response.MiAsyncResponse;
import ru.inversion.edo.xxl.mi.response.item.MiItemApplyResult;
import ru.inversion.edo.xxl.mi.response.item.MiItemResultRepository;
import ru.inversion.edo.xxl.util.Attrs;
import ru.inversion.edo.xxl.xxi.command.XxiCommandStatus;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;
import ru.inversion.mi.transport.async.model.MiAsyncItemResult;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class MI_0606_ResponseRepository implements MiItemResultRepository
{
   private static final int INF_ID = 606;
   private static final String APPLY_DOCUMENT_SUBMITTED = "apply_Document_Submitted";
   private static final String OPERATION = "MI_0606_Api." + APPLY_DOCUMENT_SUBMITTED;
   private static final URL DEF_XML = MI_0606_ResponseRepository.class.getResource("plsql/def.xml");

   private static final int RET_OK = 0;
   private static final int RET_BAD_STATE = 2;
   private static final int RET_ALREADY_APPLIED = 5;

   private static final Set<String> DOCUMENT_SUBMITTED_STATUSES = Set.of("SUCCESS", "ERROR", "VALIDATION_ERROR");
   private static final Pattern DOC_KEY = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
   private static final DateTimeFormatter ORACLE_RESPONSE_TIME =
         DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSSSS xxx", Locale.ROOT);

   private final XxiRepositoryExecutor db;
   private final ReqRepository reqRepository;
   private final ObjectMapper objectMapper;

   @Override
   public Set<Integer> infIds()
   {
      return Set.of(INF_ID);
   }

   @Override
   public MiItemApplyResult applyItem(MiAsyncResponse response, MiAsyncItemResult item, int itemIndex)
   {
      Map<String, Object> itemParameters = response.itemParameters(item, itemIndex);
      JsonNode submitted = readDocumentSubmitted(item, itemParameters);

      String docKey = submitted.path("docKey").asText("");
      if (!DOC_KEY.matcher(docKey).matches())
         throw Errors.miResponseBadFormat("DOCUMENT_SUBMITTED.docKey must be a UUID", itemParameters);

      String status = submitted.path("status").asText("");
      if (!DOCUMENT_SUBMITTED_STATUSES.contains(status))
         throw Errors.miResponseBadFormat("DOCUMENT_SUBMITTED.status is missing or unsupported", itemParameters);

      Map<String, Object> parameters = Map.of(
            "request_uuid", response.originalRequestId().toString(),
            "message_uuid", response.messageId().toString(),
            "doc_key", docKey,
            "status", status,
            "response_time", ORACLE_RESPONSE_TIME.format(item.occurredAt())
      );

      ApplyResult result = db.execute(OPERATION, parameters, tc -> {
         if (!tc.isOracle())
            throw Errors.config("DOCUMENT_SUBMITTED requires Oracle MI_0606_Api", itemParameters);

         ApplyResult applied;
         try (IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name(APPLY_DOCUMENT_SUBMITTED)
               .callBackParameters(ParametersByName.of(parameters)).build())
         {
            call.execute();
            applied = new ApplyResult(call.getReturnValue(), call.get("ret_info"), call.get("req_id"));
         }

         if (applied.accepted())
            tc.commit();
         else
            tc.rollback();

         return applied;
      });

      if (result.accepted())
      {
         MiItemApplyResult.Status applyStatus = Integer.valueOf(RET_OK).equals(result.code())
               ? MiItemApplyResult.Status.APPLIED : MiItemApplyResult.Status.ALREADY_APPLIED;
         return new MiItemApplyResult(itemIndex, item.itemExternalUuid(), applyStatus,
               result.code().toString(), result.info());
      }

      Map<String, Object> failureParameters = Attrs.merge(itemParameters, parameters,
            U.toMap("req_id", result.reqId(), "ret_code", result.code(), "ret_info", result.info()));

      if (Integer.valueOf(RET_BAD_STATE).equals(result.code()) && result.reqId() != null)
      {
         // MI can deliver the ACK between publication and the commit of to_Sent.
         XxiCommandStatus requestStatus = XxiCommandStatus.of(reqRepository.getRequest(result.reqId()).getStatus());
         if (requestStatus == XxiCommandStatus.STATUS_IN_WORK || requestStatus == XxiCommandStatus.STATUS_SENT
               || requestStatus == XxiCommandStatus.STATUS_DONE)
            throw Errors.technicalBreak("Retry DOCUMENT_SUBMITTED after the SEND_DOCUMENT status transition",
                  null, failureParameters);
      }

      throw Errors.miResponseApplyFailed("XXI failed to apply DOCUMENT_SUBMITTED", null, failureParameters);
   }

   private JsonNode readDocumentSubmitted(MiAsyncItemResult item, Map<String, Object> parameters)
   {
      var payload = item.payload();
      if (payload == null)
         throw Errors.miResponseBadFormat("DOCUMENT_SUBMITTED item payload is missing", parameters);

      try (InputStream stream = payload.openStream())
      {
         JsonNode submitted = objectMapper.readTree(stream);
         if (submitted == null || !submitted.isObject())
            throw Errors.miResponseBadFormat("DOCUMENT_SUBMITTED payload must be a JSON object", parameters);
         return submitted;
      }
      catch (IOException failure)
      {
         throw Errors.miResponseBadFormat("Cannot read DOCUMENT_SUBMITTED JSON", failure, parameters);
      }
   }

   private record ApplyResult(Integer code, String info, Long reqId)
   {
      boolean accepted()
      {
         return Integer.valueOf(RET_OK).equals(code) || Integer.valueOf(RET_ALREADY_APPLIED).equals(code);
      }
   }
}
