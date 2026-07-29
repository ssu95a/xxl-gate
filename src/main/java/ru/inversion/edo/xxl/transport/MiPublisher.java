package ru.inversion.edo.xxl.transport;

/**
 * Публикация подготовленного request из XXL в MI.
 */
public interface MiPublisher {

   /** */
   MiPublishReceipt publishAsync( XxlMiEnvelope envelope );
}