package ru.inversion.edo.xxl.util;

import ru.inversion.dataset.ParametersByName;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Attrs
{
   private final Map<String, Object> values = new LinkedHashMap<>();
   private final Set<String> sensitive = new HashSet<>();

   private Attrs()
   {
   }

   private Attrs(Map<String, Object> m)
   {
      if( m != null && !m.isEmpty() )
          values.putAll( m );
   }

   public static Attrs create( )
   {
      return new Attrs( );
   }

   public static Attrs create( Map<String, Object> m )
   {
      return new Attrs(m);
   }

   /** */
   public static Attrs of( String name, Object value )
   {
      return create().put(name, value);
   }

   /**
    * <h6>Объединяет несколько мап в одну новую.</h6>
    * <ul>
    * <li>Все переданные мапы копируются в новую.
    * <li>При совпадении ключей значение из мапы, переданной позже, перезаписывает предыдущее.
    * <li>Мапы, равные {@code null} или пустые, игнорируются.
    * <li>Возвращается новая {@link LinkedHashMap}, сохраняющая порядок вставки.
    * </ul>
    *
    * @param maps мапы для объединения
    * @return новая мапа, содержащая все записи из переданных мап
    */
   @SafeVarargs
   public static Map<String, Object> merge( Map<String, Object>... maps )
   {
      final Map<String, Object> result = new LinkedHashMap<>();

      if( maps == null || maps.length == 0 )
         return result;

      for( Map<String, Object> map : maps )
      {
         if( map != null && !map.isEmpty() )
             result.putAll(map);
      }

      return result;
   }

   @SafeVarargs
   public static Map<String, Object> mergeTo( Map<String, Object> mapTo, Map<String, Object>... maps )
   {
      if( maps == null || maps.length == 0 )
         return mapTo;

      for( Map<String, Object> map : maps )
      {
         if( map != null && !map.isEmpty() )
            mapTo.putAll(map);
      }

      return mapTo;
   }

   public Attrs put( String name, Object value )
   {
      values.put(name, value);
      sensitive.remove(name);
      return this;
   }

   /** */
   public Attrs putIfNotNull(String name, Object value)
   {
      if( value != null )
          put( name, value );
      return this;
   }

   /** */
   public Attrs putSensitive(String name, Object value)
   {
      values.put(name, value);
      sensitive.add(name);
      return this;
   }


   /** */
   public Attrs merge(Map<String, ?> source)
   {
      if( source != null && !source.isEmpty() )
          values.putAll(source);

      return this;
   }

   public Attrs merge(Attrs source)
   {
      if( source != null )
         merge(source.values);
      return this;
   }

   /** */
   public Map<String, Object> toMap()
   {
      return values;
   }

   public Map<String, Object> toSafeMap()
   {
      return values.entrySet().stream().filter(e->!sensitive.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
   }

   public ParametersByName toParameters( )
   {
      return ParametersByName.of(values);
   }
}