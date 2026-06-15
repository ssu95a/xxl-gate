package ru.inversion.msmev.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.msmev.dto.XXLRequest;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.xxi.repo.InfRepository;
import ru.inversion.msmev.xxi.repo.PInf;
import ru.inversion.msmev.xxi.repo.PReq;
import ru.inversion.msmev.xxi.repo.ReqRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * <h5>Собирает runtime-контекст команды XXI -> MI.</h5>
 *
 * Задачи - только чтение:
 * <ul>
 * <li>Загружает PReq из XXI;</li>
 * <li>Валидация данных запроса;</li>
 * <li>загружает PInf;</li>
 * <li>создаёт XxiCommandContext.</li>
 *</ul>
 * Не вызывает:
 *    <pre>take_For_Proc; to_Sent; to_Error</pre>
 */
@Component
@RequiredArgsConstructor
public class XxiCommandContextFactory {

   private final ReqRepository reqRepository;
   private final InfRepository infRepository;

   public XxiCommandContext create( XXLRequest xxlRequest )
   {
      PReq req = reqRepository.getRequest(xxlRequest.getRequestId());
      verifyRequestIdentity( xxlRequest, req );

      PInf inf = infRepository.getInf( req.getInfId() );

      return new XxiCommandContext( xxlRequest, req, inf );
   }

   /** Проверка толи пришло из XXI
    *  На тот случай если маршрут собьется
    */
   private void verifyRequestIdentity( XXLRequest request, PReq req ) {

      Map<String, Object> mismatch = new LinkedHashMap<>();

      if( !Objects.equals( req.getRequestId(), request.getRequestId()) )
      {
         mismatch.put("xml_req_id", request.getRequestId() );
         mismatch.put("xxi_req_id", req.getRequestId() );
      }

      if (!Objects.equals(req.getExternalUuid(), request.getExternalUuid())) {
         mismatch.put("xml_external_uuid", request.getExternalUuid());
         mismatch.put("xxi_external_uuid", req.getExternalUuid());
      }

      if (!Objects.equals(req.getInfId(), request.getInfId())) {
         mismatch.put("xml_inf_id", request.getInfId());
         mismatch.put("xxi_inf_id", req.getInfId());
      }

      if (!Objects.equals(req.getCorrelationId(), request.getCorrelationId())) {
         mismatch.put("xml_correlation_id", request.getCorrelationId());
         mismatch.put("xxi_correlation_id", req.getCorrelationId());
      }

      if (!mismatch.isEmpty()) {
         mismatch.put("req_id", request.getRequestId());
         mismatch.put("call_uuid", request.getCallUuid());

         throw Errors.requestMismatch(request.getRequestId(), mismatch);
      }
   }
}