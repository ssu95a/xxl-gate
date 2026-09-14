package ru.inversion.edo.xxl.xxi.command.mi_0600;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.ParametersByName;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.tc.TaskContext;
import ru.inversion.utils.U;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MI_0600_Repository
{
   private static final URL DEF_XML = MI_0600_Repository.class.getResource("plsql/def.xml");

   private final XxiRepositoryExecutor db;

   /** */
   public long createItem( Path zipPath, List<String> fileNames )
   {
      if( zipPath == null || !Files.isRegularFile(zipPath) )
          throw new IllegalArgumentException("ZIP file does not exist: " + zipPath);

      if( fileNames == null || fileNames.isEmpty() )
          throw new IllegalArgumentException("File list is empty");

      final long zipSize;

      try {
         zipSize = Files.size(zipPath);
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Не удалось определить размер ZIP", e, U.toMap("zip_file", zipPath.toString()) );
      }

      final int zipSizeInt;

      try {
         zipSizeInt = Math.toIntExact(zipSize);
      }
      catch( ArithmeticException e ) {
         throw Errors.payloadBuildFailed( "Размер ZIP превышает допустимый размер INTEGER", e, U.toMap( "zip_file", zipPath.toString(), "zip_size", zipSize ) );
      }

      try( InputStream zipData = Files.newInputStream(zipPath) )
      {
         Map<String, Object> parameters = new LinkedHashMap<>();

         parameters.put("zip_name", zipPath.getFileName().toString());
         parameters.put("zip_data", zipData );
         parameters.put("zip_size", zipSizeInt );
         parameters.put("zip_files_count", fileNames.size());
         parameters.put("file_names", fileNames.toArray(String[]::new));
         parameters.put("create_type", 0);

         return db.execute(
                 "MI_0600.create_item",
                 parameters,
                 tc -> callCreateItem(tc, parameters)
         );
      }
      catch( IOException e ) {
         throw Errors.payloadBuildFailed( "Не удалось открыть ZIP для сохранения в XXI", e, U.toMap( "zip_file", zipPath.toString(), "zip_size", zipSize ) );
      }
   }


   /** */
   private long callCreateItem( TaskContext tc, Map<String, Object> parameters ) throws Exception
   {
      try
      {
         try( IDataCall call = SQLCallBuilder.NEW(tc).url(DEF_XML).name("MI_0600.create_item").callBackParameters( ParametersByName.of(parameters) ).build().execute() )
         {
            Integer retCode = call.getReturnValue();
            String  retInfo = call.get("ret_info");
            Number  itmId   = call.get("itm_id");

            if( retCode == null || retCode != 0 )
                throw Errors.xxiCallFailed( "MI_0600.create_item", 0L, U.nvl(retCode, -1), retInfo, null );

            if( itmId == null )
                throw Errors.xxiCallFailed( "MI_0600.create_item", 0L, retCode, "out parameter 'itm_id' is null", null );

            tc.commit();

            return itmId.longValue();
         }
      }
      catch( Exception e ) {
         tc.rollback();
         throw e;
      }
   }
}