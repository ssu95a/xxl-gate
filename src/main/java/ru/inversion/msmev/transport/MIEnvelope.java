package ru.inversion.msmev.transport;

import org.springframework.http.MediaType;
import ru.inversion.mi.transport.MiTransportSendMode;
import ru.inversion.msmev.mi.IMIEnvelope;
import ru.inversion.msmev.xxi.command.XxiCommandContext;
import ru.inversion.utils.Checks;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;

/** DTO конверта запроса XXL -> MI. */
public final class MIEnvelope implements IMIEnvelope {

   private final OffsetDateTime createdAt;

   private final String  infNamespace;

   private final Ids     ids;

   private final Source  source;
   private final Headers headers;
   private final Payload payload;
   private final Route   route;

   private MIEnvelope( Builder builder )
   {
      this.createdAt
                   = Checks.Require.object( builder.createdAt, "createdAt" );
      this.infNamespace
                   = Checks.Require.text  ( builder.infNamespace, "infNamespace" );

      this.ids     = builder.idsBuilder.build();
      this.source  = builder.sourceBuilder.build();
      this.headers = builder.headersBuilder.build();
      this.payload = builder.payloadBuilder.build();
      this.route   = builder.routeBuilder.build();
   }

   /** */
   public static Builder builder( ) {
      return new Builder( );
   }

   @Override
   public String version() {
      return "";
   }

   @Override
   public OffsetDateTime createdAt() {
      return createdAt;
   }

   @Override
   public MiTransportSendMode sendMode() {
      return null;
   }

   @Override
   public String infNamespace() {
      return infNamespace;
   }

   @Override
   public Ids ids() {
      return ids;
   }

   @Override
   public Source source() {
      return source;
   }

   @Override
   public Route route() {
      return route;
   }

   @Override
   public Headers headers() {
      return headers;
   }

   @Override
   public Payload payload() {
      return payload;
   }

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

      private Builder()
      { }

      /** */
      public Builder createdAt( OffsetDateTime createdAt ) {
         this.createdAt = createdAt;
         return this;
      }

      public Builder infNamespace( String infNamespace ) {
         this.infNamespace = infNamespace;
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

      public MIEnvelope build( ) {
         return new MIEnvelope(this);
      }
   }

   /**  Builder блока ids. */
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

      public IdsBuilder correlationId( UUID v ) {
         this.correlationId = v;
         return this;
      }

      public IdsBuilder infId(int infId, int wspid ) {
         this.infId = infId;
         this.wspId = wspid;
         return this;
      }

      public IdsBuilder reqId(long reqId) {
         this.reqId = reqId;
         return this;
      }

      private Ids build( ) {
         return new Ids (
           Checks.Require.object( externalRequestUuid, "ids.externalRequestUuid" ),
           Checks.Require.object( correlationId, "ids.correlationId"),
           infId,
           wspId,
           reqId,
           null,
           null,
           Checks.Require.object( originalRequestUuid, "ids.originalRequestUuid")
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
         return new Source( Checks.Require.text( name, "source.name" ), Checks.Require.text( module, "source.module" ), instance );
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
         Checks.Require.text(header, "header");

         if( value != null )
             values.put(header, value);

         return this;
      }

      public HeadersBuilder putAll(Map<String, Object> headers) {
         if( headers != null )
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

      public PayloadBuilder data(Object data) {
         this.data = data;
         if (data != null && dataClass == null) {
            this.dataClass = data.getClass();
         }
         if (dataSize == null) {
            this.dataSize = detectSize(data);
         }
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
         this.data = json;
         this.dataClass = String.class;
         this.dataSize = detectSize(json);
         return this;
      }

      public PayloadBuilder bytes( String contentType, byte[] bytes ) {
         this.contentType = contentType;
         this.data = bytes;
         this.dataClass = byte[].class;
         this.dataSize = bytes == null ? -1L : bytes.length;
         return this;
      }

      private Payload build() {
         return new Payload(
           Checks.Require.object( contentType, "payload.mediaType" ),
           Checks.Require.object( data, "payload.data" ),
           dataSize == null ? -1L : dataSize
         );
      }
   }


   /** Реализация IXXLEnvelope.Ids. */
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
      implements IMIEnvelope.Ids
   { }


   /** Реализация IXXLEnvelope.ISource. */
   public record Source(
      String name,
      String module,
      String instance
   )
      implements IMIEnvelope.Source
   { }


   /** Реализация IXXLEnvelope.IHeaders. */
   public record Headers( Map<String, Object> asMap )
      implements IMIEnvelope.Headers
   {
      public Headers {
         asMap = asMap == null ? Map.of() : Map.copyOf(asMap);
      }
      @Override
      public Optional<Object> get(String header)
      {
         return Optional.ofNullable( asMap.get(header) );
      }
      @Override
      public boolean contains(String header) {
         return asMap.containsKey(header);
      }
   }


   /** Реализация IXXLEnvelope.IPayload. */
   public record Payload(
      String contentType,
      Object data,
      long   dataSize
   )
      implements IMIEnvelope.Payload
   {
      public Class<?> dataClass() {
         return data.getClass();
      }
   }


   /** Реализация IXXLEnvelope.Route. */
   public record Route (
      String requestQueue,
      String responseQueue,
      Long   ttlMs
   )
      implements IMIEnvelope.Route
   { }


   /** */
   private static long detectSize( Object data ) {

      if( data == null )
          return -1L;

      if( data instanceof byte[] bytes )
          return bytes.length;

      if( data instanceof CharSequence text )
          return text.toString().getBytes(StandardCharsets.UTF_8).length;

      return -1L;
   }


   /** */
   public static Builder builder( XxiCommandContext xcc ) {

      if( xcc == null )
         return new Builder( );

      final Builder bld = new Builder();

      bld.infNamespace( xcc.inf().getNamespace() );

      bld.ids( new Consumer<MIEnvelope.IdsBuilder>() {
         @Override
         public void accept( MIEnvelope.IdsBuilder b ) {
            b.correlationId( xcc.command().getCorrelationId() )
             .externalRequestUuid( xcc.command().getExternalUuid()  )
             .reqId( xcc.reqId() )
             .infId( xcc.infId(), xcc.inf().getWspId() );
         }
      })
      .source(new Consumer<SourceBuilder>() {
         @Override
         public void accept(SourceBuilder b) {
            b.name("XXL-gate");
         }
      })
      .routeBuilder(new Consumer<RouteBuilder>() {
         @Override
         public void accept(RouteBuilder b) {
            b.requestQueue ( xcc.inf().requestQueue() )
             .responseQueue( xcc.inf().responseQueue());
         }
      })
      ;
      return bld;
   }

}