package ru.inversion.edo.xxl.transport;

public interface MiBusinessResponsePublisher
{
   MiPublishReceipt publishAsync(XxlMiEnvelope response);
}