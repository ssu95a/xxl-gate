package ru.inversion.msmev.transport;

import org.springframework.http.MediaType;
import ru.inversion.utils.Checks;

/** */
public record PayloadDto (
   String mediaType,
   Object data,
   long   dataSize
)
{
   public PayloadDto
   {
      data      = Checks.Require.object( data, "data" );
      mediaType = Checks.Require.text  ( mediaType, "mediaType" );
   }

   /** */
   public static PayloadDto bytea( String mediaType, byte[] data )
   {
      return new PayloadDto ( mediaType, data, data.length );
   }

   /** */
   public static PayloadDto xml( Object data )
   {
      if( data instanceof byte[] bytes )
          return bytea( MediaType.APPLICATION_XML_VALUE, bytes );
      if( data instanceof String str )
         return new PayloadDto( MediaType.APPLICATION_XML_VALUE, str, str.length() );

      return new PayloadDto ( MediaType.APPLICATION_XML_VALUE, data, -1L );
   }

   /** */
   public static PayloadDto json( Object data )
   {
      if( data instanceof byte[] bytes )
         return bytea( MediaType.APPLICATION_JSON_VALUE, bytes );
      if( data instanceof String str )
         return new PayloadDto( MediaType.APPLICATION_JSON_VALUE, str, str.length() );

      return new PayloadDto ( MediaType.APPLICATION_JSON_VALUE, data, -1 );
   }

}
