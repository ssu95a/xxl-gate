package ru.inversion.msmev.transport;

import ru.inversion.utils.Checks;

/** */
public class PayloadDto {

   final private String mediaType;
   final private Object data;
   final private long   dataSize;

   /** */
   public PayloadDto( String mediaType, byte[] data ) {
      this.mediaType = mediaType;
      this.data      = Checks.Require.bytes( data, "data" );
      this.dataSize  = data.length;
   }

   /** */
   public PayloadDto( String mediaType, Object data, long dataSize ) {
      this.mediaType = mediaType;
      this.data      = Checks.Require.object( data, "data" );
      this.dataSize  = dataSize;
   }

   /** */
   public String mediaType() {
      return mediaType;
   }

   /** */
   public Object data() {
      return data;
   }

   /** */
   public long dataSize() {
      return dataSize;
   }
}