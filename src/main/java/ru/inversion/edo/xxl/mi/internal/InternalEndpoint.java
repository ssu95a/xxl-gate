package ru.inversion.edo.xxl.mi.internal;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Синхронная точка входа для внутренних запросов к XXL.
 * <p>
 * Endpoint отвечает только за HTTP transport.
 * Маршрутизация и обработка запроса выполняются ниже.
 */
@RestController
@RequestMapping( value = "/internal", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public final class InternalEndpoint
{
   private final InternalRequestDispatcher dispatcher;

   @PostMapping( consumes = MediaType.APPLICATION_JSON_VALUE)
   public InternalResult handle( @RequestBody InternalRequest request)
   {
      return dispatcher.dispatch(request);
   }
}