package ru.inversion.msmev.error;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Value
public class XXLException extends RuntimeException {

   public enum Layer
   {
      DB,

      XXI,
      XXL,

      MI_TRANSPORT,
      MI_ASYNC_RESPONSE,
      MI_INTERNAL,
      MI_BUSINESS,

      INTERNAL
   }

   public enum Subject
   {
      REQUEST,
      RESPONSE,
      CALL,
      CONFIG,
      PAYLOAD,
      VALIDATION
   }


   public enum Namespace {

      /**
       * DB
       */
      DB_CALL( Layer.DB, Subject.CALL ),

      DB_CONFIG( Layer.DB, Subject.CONFIG ),

      /**
       * XXI
       */
      XXI_REQUEST( Layer.XXI, Subject.REQUEST ),

      XXI_RESPONSE( Layer.XXI, Subject.RESPONSE ),

      XXI_CALL( Layer.XXI, Subject.CALL ),

      XXI_VALIDATION( Layer.XXI, Subject.VALIDATION ),

      /*
       * XXL
       */
      XXL_CONFIG( Layer.XXL, Subject.CONFIG ),

      XXL_PAYLOAD( Layer.XXL, Subject.PAYLOAD ),

      XXL_VALIDATION( Layer.XXL, Subject.VALIDATION ),

      XXL_CALL( Layer.XXL, Subject.CALL ),

      /*
       * MI transport, то есть техническая доставка через mi-transport.
       * Не только XXL -> MI, а любой publish/send через transport.
       */
      MI_TRANSPORT_REQUEST( Layer.MI_TRANSPORT, Subject.REQUEST ),

      MI_TRANSPORT_RESPONSE( Layer.MI_TRANSPORT, Subject.RESPONSE ),

      /*
       * Async response от MI/S:
       * S / MI -> XXL -> XXI.
       */
      MI_ASYNC_RESPONSE( Layer.MI_ASYNC_RESPONSE, Subject.RESPONSE ),

      MI_ASYNC_PAYLOAD( Layer.MI_ASYNC_RESPONSE, Subject.PAYLOAD ),

      MI_ASYNC_VALIDATION( Layer.MI_ASYNC_RESPONSE, Subject.VALIDATION ),

      MI_ASYNC_CALL( Layer.MI_ASYNC_RESPONSE, Subject.CALL ),

      /*
       * Internal/service query:
       * MI -> XXL, например DATABASE_CONNECTION_INFO.
       */
      MI_INTERNAL_REQUEST( Layer.MI_INTERNAL, Subject.REQUEST ),

      MI_INTERNAL_RESPONSE( Layer.MI_INTERNAL, Subject.RESPONSE ),

      MI_INTERNAL_CALL( Layer.MI_INTERNAL, Subject.CALL ),

      MI_INTERNAL_PAYLOAD( Layer.MI_INTERNAL, Subject.PAYLOAD ),

      MI_INTERNAL_VALIDATION( Layer.MI_INTERNAL, Subject.VALIDATION ),

      /*
       * Business flow, где инициатор MI/S, а XXL ходит в XXI.
       */
      MI_BUSINESS_REQUEST( Layer.MI_BUSINESS, Subject.REQUEST ),

      MI_BUSINESS_RESPONSE( Layer.MI_BUSINESS, Subject.RESPONSE ),

      MI_BUSINESS_CALL( Layer.MI_BUSINESS, Subject.CALL ),

      MI_BUSINESS_PAYLOAD( Layer.MI_BUSINESS, Subject.PAYLOAD ),

      MI_BUSINESS_VALIDATION( Layer.MI_BUSINESS, Subject.VALIDATION ),

      /*
       * Внутренний сбой XXL, который нельзя нормально привязать
       * к DB / XXI / MI / payload / config.
       */
      INTERNAL( Layer.INTERNAL, Subject.CALL );
      private final Layer layer;
      private final Subject subject;

      Namespace( Layer layer, Subject subject )
      {
         this.layer = layer;
         this.subject = subject;
      }

      public Layer layer()
      {
         return layer;
      }

      public Subject subject()
      {
         return subject;
      }
   }

   Namespace namespace;
   String resultCode;
   Errors.LogPolicy logPolicy;
   Map<String, Object> attributes;

   public XXLException(
      Namespace namespace,
      String resultCode,
      String message,
      Throwable cause,
      Errors.LogPolicy logPolicy,
      Map<String, Object> attributes
   )
   {
      super(message, cause);
      this.namespace  = namespace  == null ? Namespace.INTERNAL : namespace;
      this.resultCode = resultCode == null ? Errors.ResultCode.XXL_INTERNAL_ERROR : resultCode;
      this.logPolicy  = logPolicy  == null ? Errors.LogPolicy.ERROR_WITH_STACK : logPolicy;
      this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
   }
}