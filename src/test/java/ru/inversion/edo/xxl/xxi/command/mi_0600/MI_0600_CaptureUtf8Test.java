package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MI_0600_CaptureUtf8Test
{
   @Test
   void streamsUtf8WithoutChangingContent() throws IOException
   {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<root>Привет 🌍</root>";
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      MI_0600_Repository.copyUtf8Xml(new StringReader(xml), output);

      assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), output.toByteArray());
   }

   @Test
   void rejectsMismatchingXmlEncoding()
   {
      String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><root/>";

      assertThrows(IOException.class,
            () -> MI_0600_Repository.copyUtf8Xml(
                  new StringReader(xml), new ByteArrayOutputStream()));
   }

   @Test
   void rejectsMalformedUtf16Surrogate()
   {
      assertThrows(IOException.class,
            () -> MI_0600_Repository.copyUtf8Xml(
                  new StringReader("<r>\uD800</r>"), new ByteArrayOutputStream()));
   }

   @Test
   void rejectsOverlongXmlDeclaration()
   {
      String xml = "<?xml " + " ".repeat(1100) +
            "version=\"1.0\" encoding=\"UTF-8\"?><root/>";

      assertThrows(IOException.class,
            () -> MI_0600_Repository.copyUtf8Xml(
                  new StringReader(xml), new ByteArrayOutputStream()));
   }

   @Test
   void closesOnlyWriterWrapperNotZipTarget() throws IOException
   {
      class TrackingStream extends ByteArrayOutputStream
      {
         boolean closed;
         @Override public void close() throws IOException
         {
            closed = true;
            super.close();
         }
      }
      TrackingStream output = new TrackingStream();
      MI_0600_Repository.copyUtf8Xml(new StringReader("<root/>"), output);
      assertFalse(output.closed);
      output.write(33);
      assertEquals((int)'!', output.toByteArray()[output.size() - 1]);
   }
}
