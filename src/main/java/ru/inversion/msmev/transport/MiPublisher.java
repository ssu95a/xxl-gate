package ru.inversion.msmev.transport;

/**
 * Публикация подготовленного request из XXL в MI.
 */
public interface MiPublisher {

   /** */
   MiPublishReceipt publishAsync( XxlMiEnvelope envelope );
}