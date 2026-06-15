package ru.inversion.msmev.js;

import ru.inversion.dataset.IParameters;
import ru.inversion.utils.dco.IDco;

public interface IMIScriptExecutor {
    <R> R execute( int infId, int type, IDco data, IParameters parameters );
}
