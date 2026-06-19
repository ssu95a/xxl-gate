package ru.inversion.msmev.js;

import org.springframework.stereotype.Component;
import ru.inversion.dataset.IParameters;
import ru.inversion.utils.Pair;
import ru.inversion.utils.dco.IDco;
import ru.inversion.utils.js.ScriptMan;

@Component
public class ScriptExecutor implements IMIScriptExecutor, AutoCloseable {

    private final MIScriptSupplier scriptSupplier; // внедряем через конструктор
    private ScriptMan scriptMan;

    public ScriptExecutor(MIScriptSupplier scriptSupplier) {
        this.scriptSupplier = scriptSupplier;
    }

    private ScriptMan scriptMan() {
        if (scriptMan == null)
            scriptMan = ScriptMan.create(scriptSupplier);
        return scriptMan;
    }

    @Override
    public <R> R execute( int infId, int type, IDco dco,  IParameters parameters )
    {
        try {
            parameters.set("dco", dco);
            return scriptMan().execute( Pair.makePair(infId, type), parameters );

        } catch( JSException exception ) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new JSException( "JS execution failed", infId, type, exception );
        }
    }
    @Override
    public void close()  {
        if( scriptMan != null )
            scriptMan.close();
    }
}