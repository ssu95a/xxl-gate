package ru.inversion.msmev.js;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.inversion.utils.Pair;
import ru.inversion.utils.js.IScriptSupplier;

import java.io.Reader;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
@Slf4j
public class MIScriptSupplier implements IScriptSupplier {

    private final JSRepository jsRepository;

    private Pair<Integer,Integer> scriptId( Object v )
    {
        if( v == null )
            return Pair.makePair(null,null);

        if( v instanceof Pair id )
            return (Pair<Integer,Integer>)id;

        throw new IllegalArgumentException("Bad type fro js ID: " + v.getClass() );
    }

    @Override
    public String makeGlobalId(Object id) {
        final var scriptId = scriptId(id);
        return String.format( "mijs-3.%d.%d", scriptId.first, scriptId.second);
    }

    @Override
    public boolean isOutdated( Object id, LocalDateTime time ) {

        if( id == null )
            return false;

        final var scriptId = scriptId(id);

        LocalDateTime jsDateTime = jsRepository.getJSDateTime(scriptId.first, scriptId.second);
        return time.isAfter(jsDateTime);
    }

    /** */
    @Override
    public Pair<Reader, LocalDateTime> apply( Object id ) {

        if(!( id instanceof Pair<?, ?> ))
            throw new IllegalArgumentException("'id' bad  value");

        final var scriptId = scriptId(id);

        return jsRepository.getJSBody( scriptId.first, scriptId.second );
    }
}
