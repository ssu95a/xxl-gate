package ru.inversion.msmev.mi.response;

import org.springframework.stereotype.Component;
import ru.inversion.msmev.error.Errors;
import ru.inversion.utils.U;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MiItemResultDispatcher {

   private final Map<String, MiItemResultRepository> repositories;

   public MiItemResultDispatcher(
           List<MiItemResultRepository> repositories
   ) {
      this.repositories = buildIndex(repositories);
   }

   public void dispatch(MiAsyncResponse response)
   {
      String namespace =
              normalize(response.infNamespace());

      MiItemResultRepository repository =
              repositories.get(namespace);

      if (repository == null) {
         throw Errors.config(
                 "ITEM_RESULT repository not found",
                 U.toMap(
                         "inf_namespace",
                         response.infNamespace(),
                         "available_namespaces",
                         repositories.keySet()
                 )
         );
      }

      repository.apply(response);
   }

   private Map<String, MiItemResultRepository> buildIndex(
           List<MiItemResultRepository> source
   ) {
      Map<String, MiItemResultRepository> result =
              new LinkedHashMap<>();

      for (MiItemResultRepository repository : source) {
         String namespace =
                 normalize(repository.infNamespace());

         if (namespace == null) {
            throw Errors.config(
                    "Empty infNamespace in ITEM_RESULT repository",
                    U.toMap(
                            "repository",
                            repository.getClass().getName()
                    )
            );
         }

         MiItemResultRepository previous =
                 result.put(namespace, repository);

         if (previous != null) {
            throw Errors.config(
                    "Duplicate ITEM_RESULT repository",
                    U.toMap(
                            "inf_namespace",
                            namespace,
                            "repository_1",
                            previous.getClass().getName(),
                            "repository_2",
                            repository.getClass().getName()
                    )
            );
         }
      }

      return result;
   }

   private String normalize(String value)
   {
      if (value == null)
         return null;

      String normalized =
              value.trim().toLowerCase(Locale.ROOT);

      return normalized.isEmpty()
              ? null
              : normalized;
   }
}