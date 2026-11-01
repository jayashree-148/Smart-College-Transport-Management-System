package kalvi.transport.dao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/** A TransportDao that forwards every call to whichever DAO is currently in the reference, so the server can switch from demo files to MySQL while running. */
public final class Switchable {
    private Switchable() { }

    public static TransportDao wrap(AtomicReference<TransportDao> ref) {
        return (TransportDao) Proxy.newProxyInstance(TransportDao.class.getClassLoader(), new Class<?>[]{TransportDao.class},
                (proxy, method, args) -> {
                    try { return method.invoke(ref.get(), args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }
}
