package ru.inversion.msmev.transport;

import ru.inversion.msmev.mi.IMIEnvelope;


/**
 * Публикация подготовленного request из XXL в MI.
 */
public interface MiPublisher {

   /** */
   MiPublishReceipt publishAsync( IMIEnvelope envelope );
}