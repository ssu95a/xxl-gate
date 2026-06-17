package ru.inversion.msmev.transport;

import org.springframework.http.MediaType;
import ru.inversion.mi.transport.MiTransportSendMode;
import ru.inversion.msmev.xxi.command.XxiCommandContext;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;

import static ru.inversion.msmev.transport.XxlMiEnvelopeKind.*;

/** DTO конверта запроса XXL -> MI. */
public final class XxlMiEnvelope {

   public static final String DEFAULT_VERSION = "1.0";
   public static final String DEFAULT_SOURCE_NAME = "XXL-gate";
   public static final String DEFAULT_SOURCE_MODULE = "xxi-command";
   public static final String DEFAULT_RESPONSE_QUEUE = "mi-edo.responses";

   private final String version;

   private final OffsetDateTime createdAt;

   private final String  infNamespace;

   private final Ids     ids;

   private final Source  source;
   private final Headers headers;
   private final Payload payload;
   private final Route   route;

   private final MiTransportSendMode sendMode;

   private final XxlMiEnvelopeKind kind;

   private XxlMiEnvelope( Builder builder )
   {
      this.version = DEFAULT_VERSION;
      this.kind    = Checks.Require.object( builder.kind, "kind" );

      this.createdAt
                   = Checks.Require.object( builder.createdAt, "createdAt" );
      this.infNamespace
                   = Checks.Require.text  ( builder.infNamespace, "infNamespace" );

      this.sendMode= Checks.Require.object( builder.sendMode, "sendMode");

      this.ids     = builder.idsBuilder.build();
      this.source  = builder.sourceBuilder.build();
      this.headers = builder.headersBuilder.build();
      this.payload = builder.payloadBuilder.build();
      this.route   = builder.routeBuilder.build();
   }

   public String version() {
      return version;
   }

   public OffsetDateTime createdAt() {
      return createdAt;
   }

   public MiTransportSendMode sendMode() {
      return sendMode;
   }

   public XxlMiEnvelopeKind kind() { return kind; }

   public String infNamespace() {
      return infNamespace;
   }

   public Ids ids() {
      return ids;
   }

   public Source source() {
      return source;
   }

   public Route route() {
      return route;
   }

   public Headers headers() { return headers; }

   public Payload payload() { return payload; }

   /** Основной builder конверта. */
   public static final class Builder
   {
      private OffsetDateTime       createdAt = OffsetDateTime.now();
      private String               infNamespace;

      private final IdsBuilder     idsBuilder     = new IdsBuilder();
      private final SourceBuilder  sourceBuilder  = new SourceBuilder();
      private final HeadersBuilder headersBuilder = new HeadersBuilder();
      private final PayloadBuilder payloadBuilder = new PayloadBuilder();
      private final RouteBuilder   routeBuilder   = new RouteBuilder();

      private MiTransportSendMode  sendMode;
      private XxlMiEnvelopeKind    kind;

      /** */
      private Builder()
      { }

      /** */
      private Builder( XxlMiEnvelopeKind kind )
      {
         this.kind = kind;
      }

      /** */
      public Builder createdAt( OffsetDateTime createdAt ) {
         this.createdAt = createdAt;
         return this;
      }

      public Builder infNamespace( String infNamespace ) {
         this.infNamespace = infNamespace;
         return this;
      }

      public Builder sendMode( MiTransportSendMode v ) {
         this.sendMode = v;
         return this;
      }

      public Builder kind( XxlMiEnvelopeKind v ) {
         this.kind = v;
         return this;
      }

      public Builder ids( Consumer<IdsBuilder> consumer ) {
         consumer.accept(idsBuilder);
         return this;
      }

      public Builder source(Consumer<SourceBuilder> consumer) {
         consumer.accept(sourceBuilder);
         return this;
      }

      public Builder headers(Consumer<HeadersBuilder> consumer) {
         consumer.accept(headersBuilder);
         return this;
      }

      public Builder routeBuilder( Consumer<RouteBuilder> consumer ) {
         consumer.accept(routeBuilder);
         return this;
      }

      public Builder payload( Consumer<PayloadBuilder> consumer ) {
         consumer.accept(payloadBuilder);
         return this;
      }

      public XxlMiEnvelope build( ) {
         return new XxlMiEnvelope(this);
      }

   }


   /**  Builder блока ids - идентификаторы */
   public static final class IdsBuilder {

      private UUID externalRequestUuid;
      private UUID correlationId;
      private UUID originalRequestUuid;
      private int  infId;
      private int  wspId;
      private Long reqId;

      private IdsBuilder() {
      }

      public IdsBuilder externalRequestUuid( UUID v ) {
         this.externalRequestUuid = v;
         return this;
      }

      public IdsBuilder originalRequestUuid( UUID v ) {
         this.originalRequestUuid = v;
         return this;
      }

      /** */
      public IdsBuilder correlationId( UUID v ) {
         this.correlationId = v;
         return this;
      }

      /** */
      public IdsBuilder infId( int infId, int wspId ) {
         this.infId = infId;
         this.wspId = wspId;
         return this;
      }

      /** */
      public IdsBuilder reqId(long reqId) {
         this.reqId = reqId;
         return this;
      }

      private Ids build( ) {
         return new Ids (
           Checks.Require.object( externalRequestUuid, "ids.externalRequestUuid" ),
           Checks.Require.object( correlationId, "ids.correlationId" ),
           infId,
           wspId,
           reqId,
           null,
           null,
           originalRequestUuid
         );
      }
   }


   /** Builder блока source. */
   public static final class SourceBuilder {

      private String name;
      private String module;
      private String instance;

      private SourceBuilder() {
      }

      public SourceBuilder name(String name) {
         this.name = name;
         return this;
      }

      public SourceBuilder module(String module) {
         this.module = module;
         return this;
      }

      public SourceBuilder instance(String instance) {
         this.instance = instance;
         return this;
      }

      private Source build() {
         return new Source( Checks.Require.text( name, "source.name" ),
                            Checks.Require.text( module, "source.module" ), instance );
      }
   }

   /** Builder блока headers. */
   public static final class HeadersBuilder
   {
      private final Map<String, Object> values = new LinkedHashMap<>();

      private HeadersBuilder()
      { }

      public HeadersBuilder put(String header, Object value)
      {
         Checks.Require.text( header, "header" );

         if( value != null )
             values.put( header, value );

         return this;
      }

      public HeadersBuilder putAll(Map<String, Object> headers) {
         if( headers != null && !headers.isEmpty() )
             headers.forEach(this::put);
         return this;
      }
      /** */
      private Headers build() {
         return new Headers(values);
      }
   }

   /** Builder блока headers. */
   public static final class RouteBuilder
   {
      String requestQueue,
             responseQueue;
        long ttlMs = 0L;

      private RouteBuilder()
      { }

      public RouteBuilder responseQueue( String v )
      {
         responseQueue = v;
         return this;
      }

      /** */
      public RouteBuilder requestQueue( String v) {
         requestQueue = v;
         return this;
      }

      /** */
      public RouteBuilder ttlMs( long v) {
         ttlMs = v;
         return this;
      }

      private Route build() {
         return new Route(requestQueue, responseQueue, ttlMs );
      }
   }


   /**
    * Builder блока payload.
    */
   public static final class PayloadBuilder {

      private String contentType;
      private Object data;
      private Class<?> dataClass;
      private Long dataSize;

      private PayloadBuilder() {
      }

      public PayloadBuilder contentType(String contentType) {
         this.contentType = contentType;
         return this;
      }

      public PayloadBuilder data( Object data )
      {
         this.data = data;
         if( data != null && dataClass == null)
             this.dataClass = data.getClass();

         if( dataSize == null )
            this.dataSize = detectSize(data);

         return this;
      }

      public PayloadBuilder dataClass(Class<?> dataClass) {
         this.dataClass = dataClass;
         return this;
      }

      public PayloadBuilder dataSize( long dataSize ) {
         this.dataSize = dataSize;
         return this;
      }

      public PayloadBuilder xml( String xml ) {
         this.contentType = MediaType.APPLICATION_XML_VALUE;
         this.data        = xml;
         this.dataClass   = String.class;
         this.dataSize    = detectSize(xml);
         return this;
      }

      public PayloadBuilder json( String json ) {
         this.contentType = MediaType.APPLICATION_JSON_VALUE;
         this.data        = json;
         this.dataClass   = String.class;
         this.dataSize    = detectSize(json);
         return this;
      }

      public PayloadBuilder bytes( String contentType, byte[] bytes ) {
         this.contentType = contentType;
         this.data        = bytes;
         this.dataClass   = byte[].class;
         this.dataSize    = bytes == null ? -1L : bytes.length;
         return this;
      }

      private Payload build() {
         return new Payload(
           Checks.Require.text  ( contentType, "payload.mediaType" ),
           Checks.Require.object( data,        "payload.data" ),
           dataSize == null ? -1L : dataSize
         );
      }
   }


   /** Данные Ids. */
   public record Ids(
        UUID externalRequestUuid,
        UUID correlationId,
        int  infId,
        int  wspId,
        long reqId,
        UUID messageId,
        UUID callUuid,
        UUID originalRequestUuid
   )
   { }


   /** Данные Source. */
   public record Source(
      String name,
      String module,
      String instance
   )
   { }


   /** Данные Headers. */
   public record Headers( Map<String, Object> asMap )
   {
      public Headers {
         asMap = asMap == null ? Map.of() : Map.copyOf(asMap);
      }
      
      public Optional<Object> get(String header)
      {
         return Optional.ofNullable( asMap.get(header) );
      }
      
      public boolean contains(String header) {
         return asMap.containsKey(header);
      }
   }


   /** Данные Payload. */
   public record Payload(
      String contentType,
      Object data,
      long   dataSize
   )
   {
      public Class<?> dataClass() {
         return data.getClass();
      }
   }


   /** Данные Route. */
   public record Route (
      String requestQueue,
      String responseQueue,
      Long   ttlMs
   )
   { }


   /** */
   private static long detectSize( Object data ) {

      return switch (data) {
         case byte[] bytes -> bytes.length;
         case CharSequence text -> text.toString().getBytes(StandardCharsets.UTF_8).length;
         default -> -1L;
      };

   }


   /** */
   public static Builder builder( XxiCommandContext xcc ) {

      if( xcc == null )
          return new Builder( );

      final Builder bld = new Builder();

      bld.infNamespace( xcc.inf().getNamespace() )
          .sendMode ( MiTransportSendMode.ASYNC )
          .createdAt( OffsetDateTime.now() )
          .kind     ( XXI_REQUEST );

      bld.ids( new Consumer<IdsBuilder>() {
         
         public void accept( XxlMiEnvelope.IdsBuilder b ) {
            b.correlationId( xcc.command().getCorrelationId() )
             .externalRequestUuid( xcc.command().getExternalUuid()  )
             .reqId( xcc.reqId() )
             .infId( xcc.infId(), xcc.inf().getWspId() );
         }
      })
      .source(new Consumer<SourceBuilder>() {
         
         public void accept(SourceBuilder b) {
            b.name  (DEFAULT_SOURCE_NAME)
             .module(DEFAULT_SOURCE_MODULE);
         }
      })
      .routeBuilder(new Consumer<RouteBuilder>() {
         
         public void accept(RouteBuilder b) {
            b.requestQueue ( xcc.inf().requestQueue() )
             .responseQueue(S.isNullOrEmpty( xcc.inf().responseQueue() ) ? DEFAULT_RESPONSE_QUEUE : xcc.inf().responseQueue() );
         }
      })
      ;
      return bld;
   }

   /** */
   public static Builder builder( ) {
      return new Builder( );
   }

   public static Builder xxiRequest( ) {
      return new Builder( XXI_REQUEST );
   }

   public static Builder businessResponse( ) {
      return new Builder( MI_BUSINESS_RESPONSE );
   }

   public static Builder internalResponse( ) {
      return new Builder( MI_INTERNAL_RESPONSE );
   }
}