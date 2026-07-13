package ru.inversion.msmev.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Attrs
{
   private final Map<String, Object> values = new LinkedHashMap<>();

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

   public Attrs put( String name, Object value )
   {
      values.put(name, value);
      return this;
   }

   /** */
   public Attrs putIfNotNull(String name, Object value)
   {
      if( value != null )
         values.put(name, value);
      return this;
   }

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
}