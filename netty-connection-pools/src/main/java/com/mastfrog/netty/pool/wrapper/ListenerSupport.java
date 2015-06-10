package com.mastfrog.netty.pool.wrapper;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Common logic for supporting listeners in wrapper Channels/Promises/Futures.
 *
 * @author Tim Boudreau
 */
public final class ListenerSupport<T> {
    private final List<GenericFutureListener<? extends Future<? super T>>> listeners = new LinkedList<>();
    private Future<? super T> object;
    private boolean notified;

    void fireOne(GenericFutureListener<? extends Future<? super T>> to) throws Exception {
        // XXX what is up with the uninvokable generic signature here?  Netty itself hacks this
        // by using the raw type.
        genericInvoke(to);
    }
    
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    private <R extends Future<? super T>> void genericInvoke(GenericFutureListener<R> g) throws Exception {
        g.operationComplete((R) object);
    }

    public void operationComplete(Future<? super T> object) {
        System.out.println("ListenerSupport onDone with " + object + " with " + object.getNow());
        this.object = object;
        notified = true;
        RuntimeException ex = null;
        for (GenericFutureListener<? extends Future<? super T>> listener : listeners) {
            try {
                fireOne(listener);
            } catch (Exception e) {
                if (ex == null) {
                    ex = new RuntimeException("Exceptions notifying listeners");
                }
                ex.addSuppressed(e);
            }
        }
        if (ex != null) {
            throw ex;
        }
    }
    
    @SafeVarargs
    public final void addListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        RuntimeException ex = null;
        for (GenericFutureListener<? extends Future<? super T>> listener : listeners) {
            if (notified) {
                try {
                    fireOne(listener);
                } catch (Exception e) {
                    if (ex == null) {
                        ex = new RuntimeException("Exceptions notifying listeners");
                    }
                    ex.addSuppressed(e);
                }
            } else {
                this.listeners.add(listener);
            }
        }
        if (ex != null) {
            throw ex;
        }
    }

    @SafeVarargs
    public final void removeListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        this.listeners.removeAll(Arrays.<GenericFutureListener<? extends Future<? super T>>>asList(listeners));
    }
}
