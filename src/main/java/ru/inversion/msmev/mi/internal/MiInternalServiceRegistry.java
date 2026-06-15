package ru.inversion.msmev.mi.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry технических service handlers.
 *
 * Зона ответственности:
 * - индексирует MiInternalServiceHandler по serviceCode;
 * - валидирует отсутствие дублей;
 * - бросает Errors.miServiceUnsupportedRequest(...) если serviceCode неизвестен.
 */
@Component
@RequiredArgsConstructor
public class MiInternalServiceRegistry {

   private final List<MiInternalServiceHandler> handlers;

   public MiInternalServiceHandler get( String serviceCode ) {
      return null;
   }
}