package ru.inversion.msmev.mi.internal.license;


import org.springframework.stereotype.Component;
import ru.inversion.msmev.mi.internal.MiInternalServiceHandler;

/**
 * Проверка лицензии для MI.
 *
 * Зона ответственности:
 * - принимает технический запрос CHECK_LICENSE;
 * - получает/проверяет данные лицензии;
 * - возвращает MiInternalResponse.
 */
@Component
public class CheckLicenseServiceHandler implements MiInternalServiceHandler {

   @Override
   public String serviceCode() {
      return "CHECK_LICENSE";
   }

//   @Override
//   public MiInternalResponse handle(MiInternalRequest request) {
//      return null;
//   }
}