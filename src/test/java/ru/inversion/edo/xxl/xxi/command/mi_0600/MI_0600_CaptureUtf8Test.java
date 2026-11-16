package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MI_0600_CaptureUtf8Test
{
   private static byte[] writeXml(String xml) throws IOException
   {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      MI_0600_Repository.copyXml(new StringReader(xml), output);
      return output.toByteArray();
   }

   @Test
   void streamsUtf8WithoutChangingContent() throws IOException
   {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<root>Привет 🌍</root>";
      assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), writeXml(xml));
   }

   @Test
   void honorsWindows1251WithoutChangingDeclaration() throws IOException
   {
      String xml = "<?xml version='1.0' encoding='WINDOWS-1251'?><root>Привет, Ёж!</root>";
      assertArrayEquals(xml.getBytes(Charset.forName("windows-1251")), writeXml(xml));
   }

   @Test
   void acceptsCp1251Alias() throws IOException
   {
      String xml = "<?xml version=\"1.0\" encoding=\"Cp1251\"?><root>Тест</root>";
      assertArrayEquals(xml.getBytes(Charset.forName("windows-1251")), writeXml(xml));
   }

   @Test
   void defaultsToUtf8WithNoDeclaration() throws IOException
   {
      String xml = "<root>Привет 🌍</root>";
      assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), writeXml(xml));
   }

   @Test
   void defaultsToUtf8WhenDeclarationOmitsEncoding() throws IOException
   {
      String xml = "<?xml version=\"1.0\"?><root>Привет 🌍</root>";
      assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), writeXml(xml));
   }

   @Test
   void rejectsUnsupportedEncoding()
   {
      String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-5\"?><root/>";
      IOException failure = assertThrows(IOException.class, () -> writeXml(xml));
      assertTrue(failure.getMessage().contains("Unsupported XML declaration encoding"));
   }

   @Test
   void rejectsUnmappableCharactersInWindows1251()
   {
      String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><root>🌍</root>";
      assertThrows(IOException.class, () -> writeXml(xml));
   }

   @Test
   void rejectsMalformedUtf16Surrogate()
   {
      assertThrows(IOException.class, () -> writeXml("<r>\uD800</r>"));
   }

   @Test
   void rejectsOverlongXmlDeclaration()
   {
      String xml = "<?xml " + " ".repeat(1100) +
            "version=\"1.0\" encoding=\"UTF-8\"?><root/>";
      assertThrows(IOException.class, () -> writeXml(xml));
   }

   @Test
   void rejectsConflictingBom()
   {
      String xml = "\uFEFF<?xml version=\"1.0\" encoding=\"windows-1251\"?><root/>";
      assertThrows(IOException.class, () -> writeXml(xml));
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
      MI_0600_Repository.copyXml(new StringReader("<root/>"), output);
      assertFalse(output.closed);
      output.write(33);
      assertEquals((int)'!', output.toByteArray()[output.size() - 1]);
   }
}
