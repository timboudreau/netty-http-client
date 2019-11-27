/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.netty.http.client;

import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.thread.Receiver;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.ReferenceCounted;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Returned from launching an HTTP request; attach handlers using the
 * <code>on(Class&lt;EventType&gt;, Receiver&lt;State&lt;T&gt;&gt;))</code>
 * method. Note that it is preferable to attach handlers when constructing the
 * request, unless you can guarantee that the request won't be completed before
 * your handler is attached.
 *
 * @author Tim Boudreau
 */
public final class ResponseFuture implements Comparable<ResponseFuture> {

    AtomicBoolean cancelled;
    final List<HandlerEntry<?>> handlers = new CopyOnWriteArrayList<>();
    final List<Receiver<State<?>>> any = new CopyOnWriteArrayList<>();
    private volatile ChannelFuture future;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final ZonedDateTime start = ZonedDateTime.now();
    private Map<StateType, List<Object>> queuedToSend;
    private final EnumSet<StateType> seenStates = EnumSet.noneOf(StateType.class);

    ResponseFuture(AtomicBoolean cancelled) {
        this.cancelled = cancelled;
    }

    void setFuture(ChannelFuture fut) {
        future = fut;
    }

    void trigger() {
        latch.countDown();
    }

    /**
     * Send some objects through the channel once a given state is reached (if
     * the state already has been, they will be sent immediately if the channel
     * is not closed).
     *
     * @param stateType The state to trigger sending. Must NOT be a state that
     * indicates a closed channel, error or failure condition, or pre-connection
     * state, because there is no channel to use.
     *
     * @param o What to send - must be an object Netty's channel pipeline has a
     * handler for it, such as an HttpContent or ByteBuf or WebSocketFrame (the
     * main use for this method)
     * @throws IllegalArgumentException if the state type is one that has no
     * associated network channel
     * @return this
     */
    public ResponseFuture sendOn(StateType stateType, Object o) {
        if (queuedToSend == null) {
            queuedToSend = new HashMap<>();
        }
        if (stateType.isFailure() || stateType == StateType.Closed || stateType == StateType.Connecting) {
            throw new IllegalArgumentException("Cannot send messages after a "
                    + "failure or close state is reached.  Pick a different "
                    + "state.");
        }
        List<Object> queue = queuedToSend.get(stateType);
        if (queue == null) {
            queue = new ArrayList<>(2);
            queuedToSend.put(stateType, queue);
        }
        queue.add(o);
        sendQueued();
        return this;
    }

    private void sendQueued() {
        if (queuedToSend != null && future != null && future.channel().isWritable()) {
            List<Object> toSend = new LinkedList<>();
            Set<StateType> toRemove = EnumSet.noneOf(StateType.class);
            for (Map.Entry<StateType, List<Object>> e : queuedToSend.entrySet()) {
                if (seenStates.contains(e.getKey()) && !e.getValue().isEmpty()) {
                    toSend.addAll(e.getValue());
                    e.getValue().clear();
                    toRemove.add(e.getKey());
                }
            }
            for (StateType st : toRemove) {
                queuedToSend.remove(st);
            }
            if (!toSend.isEmpty()) {
                new SendObjs(toSend).operationComplete(future.channel().newSucceededFuture());
            }
        }
    }

    final class SendObjs implements ChannelFutureListener {

        private final Iterator<Object> objs;

        SendObjs(List<Object> objs) {
            this.objs = objs.iterator();
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                if (objs.hasNext()) {
                    Object o = objs.next();
                    future = future.channel().writeAndFlush(o);
                    if (objs.hasNext()) {
                        future.addListener(this);
                    }
                }
            } else {
                event(new State.Error(future.cause()));
            }
        }
    }

    /**
     * Wait for the channel to be closed. Dangerous without a timeout!
     * <p/>
     * Note - blocking while waiting for a response defeats the purpose of using
     * an asynchronous HTTP client; this sort of thing is sometimes useful in
     * unit tests, but should not be done in production code. Where possible,
     * find a way to attach a callback and finish work there, rather than use
     * this method.
     *
     * @throws InterruptedException
     */
    public ResponseFuture await() throws InterruptedException {
        latch.await();
        return this;
    }

    /**
     * Wait for a timeout for the request to be complleted. This is realy for
     * use in unit tests - normal users of this library should use callbacks.
     * <p/>
     * Note - blocking while waiting for a response defeats the purpose of using
     * an asynchronous HTTP client; this sort of thing is sometimes useful in
     * unit tests, but should not be done in production code. Where possible,
     * find a way to attach a callback and finish work there, rather than use
     * this method.
     *
     * @param l A number of time units
     * @param tu Time units
     * @return follows the contract of CountDownLatch.await()
     * @throws InterruptedException
     */
    public ResponseFuture await(long l, TimeUnit tu) throws InterruptedException {
        Checks.notNull("tu", tu);
        Checks.nonNegative("l", l);
        latch.await(l, tu);
        return this;
    }

    void onTimeout(Duration dur) {
//        System.out.println("onTimeout");
        cancel(dur);
    }

    /**
     * Cancel the associated request. This will make a best-effort, but cannot
     * guarantee, that no state changes will be fired after the final Cancelled.
     *
     * @return true if it succeeded, false if it was already canceled
     */
    public boolean cancel() {
        return cancel(null);
    }

    boolean cancel(Duration forTimeout) {
        // We need to send the timeout event before setting the cancelled flag
        if (forTimeout != null && !cancelled.get()) {
            event(new State.Timeout(forTimeout));
        }
        boolean result = cancelled.compareAndSet(false, true);
        if (result) {
            try {
                ChannelFuture fut = future;
                if (fut != null) {
                    fut.cancel(true);
                }
                if (fut != null && fut.channel() != null && fut.channel().isOpen()) {
                    fut.channel().close();
                }
            } finally {
                if (forTimeout == null) {
                    event(new State.Cancelled());
                }
            }
            latch.countDown();
        }
        return result;
    }

    private volatile Throwable error;

    /**
     * If an error was encountered, throw it
     *
     * @return this
     * @throws Throwable a throwable
     */
    public ResponseFuture throwIfError() throws Throwable {
        if (error != null) {
            throw error;
        }
        return this;
    }

    public final StateType lastState() {
        return lastState.get();
    }

    private final AtomicReference<StateType> lastState = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    <T> void event(State<T> state) {
        if (state.stateType().isFailure()) {
            queuedToSend = null;
        }
        Checks.notNull("state", state);
        seenStates.add(state.stateType());
        lastState.set(state.stateType());
        if (state.get() instanceof ReferenceCounted) {
            ((ReferenceCounted) state.get()).touch("response-future-state-" + state.name());
        }
        try {
            if ((state instanceof State.Error && cancelled.get()) || (state instanceof State.Timeout && cancelled.get())) {
                if (!(state.get() instanceof RedirectException)) {
//                    System.err.println("Suppressing error after cancel");
                    return;
                } else if (state.get() instanceof RedirectException && ((RedirectException) state.get()).kind() == RedirectException.Kind.INVALID_REDIRECT_URL) {
                    return;
                }
            }
            if (state instanceof State.Error) {
                error = ((State.Error) state).get();
            }
            for (HandlerEntry<?> h : handlers) {
                if (h.state.isInstance(state)) {
                    HandlerEntry<T> hh = (HandlerEntry<T>) h;
                    hh.onEvent(state);
                }
            }
            for (Receiver<State<?>> r : any) {
                r.receive(state);
            }
        } finally {
            if (state instanceof State.Closed) {
                latch.countDown();
            }
            sendQueued();
        }
    }

    /**
     * Add a listener which is notified of all state changes (there are many!).
     *
     * @param r
     * @return
     */
    public ResponseFuture onAnyEvent(Receiver<State<?>> r) {
        any.add(r);
        return this;
    }

    boolean has(Class<? extends State<?>> state) {
        if (!any.isEmpty()) {
            return true;
        }
        for (HandlerEntry<?> h : handlers) {
            if (state == h.state) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a listener for a particular type of event
     */
    @SuppressWarnings("unchecked")
    public <T> ResponseFuture on(StateType state, Receiver<T> receiver) {
        StateType s = this.lastState.get();
        if (s == StateType.Closed && state == StateType.Closed) {
            receiver.receive(null);
        }
        Class<? extends State<T>> type = (Class<? extends State<T>>) state.type();
        return on(type, (Receiver<T>) state.wrapperReceiver(receiver));
    }

    @SuppressWarnings("unchecked")
    public <T> ResponseFuture on(Class<? extends State<T>> state, Receiver<T> receiver) {
        HandlerEntry<T> handler = null;
        for (HandlerEntry<?> h : handlers) {
            if (state.equals(h.state)) {
                handler = (HandlerEntry<T>) h;
                break;
            }
        }
        if (handler == null) {
            handler = new HandlerEntry<>(state);
            handlers.add(handler);
        }
        handler.add(receiver);
        return this;
    }

    @Override
    public int compareTo(ResponseFuture t) {
        ZonedDateTime mine = this.start;
        ZonedDateTime other = t.start;
        return mine.compareTo(other);
    }
}
