/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.jvnet.hk2.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;

import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.Habitat;

/**
 * Transactions is a singleton service that receives transaction notifications and dispatch these
 * notifications asynchronously to listeners.
 *
 * @author Jerome Dochez
 */

@Service
public final class Transactions {

    private static Transactions singleton;
    
    // NOTE: synchronization on the object itself
    List<ListenerInfo<TransactionListener>> listeners;

    ExecutorService executor;
        
    private ListenerInfo<Object> configListeners;
    
    private final class ListenerInfo<T> {
        
        private final T listener;
        
        private final BlockingQueue<Job> pendingJobs = new ArrayBlockingQueue<Job>(50);
        private CountDownLatch latch = new CountDownLatch(1);
        
        public ListenerInfo(T listener) {
            this.listener = listener;
            start();
        }
        
        public void addTransaction(Job job) {
                
            // NOTE that this is put() which blocks, *not* add() which will not block and will
            // throw an IllegalStateException if the queue is full.
            if (latch.getCount()==0) {
                throw new RuntimeException("TransactionListener is inactive, yet jobs are published to it");
            }
            try {
                pendingJobs.put(job);
            } catch (InterruptedException e ) {
                throw new RuntimeException(e);
            }            
            
        }
        
        private void start() {

            executor.submit(new Runnable() {

                public void run() {
                    while (latch.getCount()>0) {
                        try {
                            final Job job  = pendingJobs.take();
                            try {
                                if ( job.mEvents.size() != 0 ) {
                                    job.process(listener);
                               }
                            } finally {
                                job.releaseLatch();
                            }
                        }
                        catch (InterruptedException e) {
                            // do anything here?
                        }
                    }
                }
                
            });
        }

        void stop() {
            latch.countDown();
            // last event to force the close
            pendingJobs.add(new TransactionListenerJob(new ArrayList<PropertyChangeEvent>(), null));
        }
    }
    /**
        A job contains an optional CountdownLatch so that a caller can learn when the
        transaction has "cleared" by blocking until that time.
     */
    private abstract static class Job<T,U> {

        private final CountDownLatch mLatch;
        protected final List<U> mEvents;
        
        public Job(List events, final CountDownLatch latch ) {
            mLatch  = latch;
            mEvents = events;
        }
        
        public void waitForLatch() throws InterruptedException {
            if ( mLatch != null ) {
                mLatch.await();
            }
        }
        
        public void releaseLatch() {
            if ( mLatch != null ) {
                mLatch.countDown();
            }
        }
        
        public abstract void process(T target);
    }
    
    private static class TransactionListenerJob extends Job<TransactionListener, PropertyChangeEvent> {

        public TransactionListenerJob(List<PropertyChangeEvent> events, CountDownLatch latch) {
            super(events,  latch);
        }
        
        @Override
        public void process(TransactionListener listener) {
            try {
                listener.transactionCommited(mEvents);
            } catch(Exception e) {
                e.printStackTrace();
            }            
        }
    }

    private static class UnprocessedEventsJob extends Job<TransactionListener, UnprocessedChangeEvents> {

        public UnprocessedEventsJob(List<UnprocessedChangeEvents> events, CountDownLatch latch) {
            super(events, latch);
        }

        @Override
        public void process(TransactionListener listener) {
            try {
                listener.unprocessedTransactedEvents(mEvents);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private   class ConfigListenerJob extends Job<Object, PropertyChangeEvent> {


        public ConfigListenerJob(List events, CountDownLatch latch) {
            super(events, latch);
        }

        @Override
        public void process(Object target) {
            Set<ConfigListener> notifiedListeners = new HashSet<ConfigListener>();
            List<UnprocessedChangeEvents> unprocessedEvents  = new ArrayList<UnprocessedChangeEvents>();
            for (final PropertyChangeEvent evt : mEvents) {
                final Dom dom = (Dom) ((ConfigView) Proxy.getInvocationHandler(evt.getSource())).getMasterView();
                if (dom.getListeners() != null && dom.getListeners().size() != 0 ) {
                    List<ConfigListener> listeners = new ArrayList<ConfigListener>(dom.getListeners());
                    for (final ConfigListener listener :listeners) {                       
                        if (listener==null) {
                            Logger.getAnonymousLogger().warning("Null config listener is registered to " + dom);
                            continue;
                        }
                        if (!notifiedListeners.contains(listener)) {
                            try {
                                // create a new array each time to avoid any potential array changes?
                                UnprocessedChangeEvents unprocessed = listener.changed(mEvents.toArray(new PropertyChangeEvent[mEvents.size()]));
                                if (unprocessed != null && unprocessed.size() != 0 ) {
                                    unprocessedEvents.add(unprocessed);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        notifiedListeners.add(listener);
                    }
                }
                // we notify the immediate parent.
                // dochez : should we notify the parent chain up to the root or stop at the first parent.
                if (dom.parent()!=null && dom.parent().getListeners()!=null) {
                    List<ConfigListener> parentListeners = new ArrayList<ConfigListener>(dom.parent().getListeners());
                    for (ConfigListener parentListener : parentListeners) {
                        if (parentListener==null) {
                            Logger.getAnonymousLogger().warning("Null config listener is registered to " + dom);
                            continue;
                        }
                        if (!notifiedListeners.contains(parentListener)) {
                            try {
                                // create a new array each time to avoid any potential array changes?
                                UnprocessedChangeEvents unprocessed = parentListener.changed(mEvents.toArray(new PropertyChangeEvent[mEvents.size()]));
                                if (unprocessed != null && unprocessed.size() != 0 ) {
                                    unprocessedEvents.add(unprocessed);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            notifiedListeners.add(parentListener);
                        }
                    }
                }
            }
            // all the config listeners have been notified, let's see if we have
            // some unprocessed events to notifiy the transation listeners.
            if (!unprocessedEvents.isEmpty()) {
                synchronized(listeners) {
                    UnprocessedEventsJob job = new UnprocessedEventsJob(unprocessedEvents, null);
                    for (ListenerInfo listener : listeners) {
                        listener.addTransaction(job);
                    }
                }
            }
        }        
    }

    /**
     * add a new listener to all transaction events.
     *
     * @param listener to be added.
     */
    public void addTransactionsListener(TransactionListener listener) {
        synchronized(listeners) {
            listeners.add(new ListenerInfo(listener));
        }
    }

    /**
     * Removes an existing listener for transaction events
     * @param listener the registered listener
     * @return true if the listener unregistration was successful
     */
    public boolean removeTransactionsListener(TransactionListener listener) {
        synchronized(listeners) {
            for (ListenerInfo info : listeners) {
                if (info.listener==listener) {
                    info.stop();
                    return listeners.remove(info);
                }
            }
        }
        return false;
    }
    
    public List<TransactionListener> currentListeners() {
        synchronized(listeners) {            
            List<TransactionListener> l = new ArrayList<TransactionListener>();
            for (ListenerInfo<TransactionListener> info : listeners) {
                l.add(info.listener);
            }
            return l;
        }
    }


    /** maintains prior semantics of add, and return immediately */
    void addTransaction( final List<PropertyChangeEvent> events) {
        addTransaction(events, false);
    }
        
    /**
     * Notification of a new transation completion
     *
     * @param events accumulated list of changes
     * @param waitTillCleared  synchronous semantics; wait until all change events are sent
     */
    void addTransaction(
        final List<PropertyChangeEvent> events,
        final boolean waitTillCleared ) {
        
        final List<ListenerInfo> listInfos = new ArrayList<ListenerInfo>();
        listInfos.addAll(listeners);
        
        // create a CountDownLatch to implement waiting for events to actually be sent
        final CountDownLatch latch = waitTillCleared ? new CountDownLatch(listInfos.size()+1) : null;
        
        final Job job = new TransactionListenerJob( events, latch );
        
        // NOTE that this is put() which blocks, *not* add() which will not block and will
        // throw an IllegalStateException if the queue is full.
        try {
            for (ListenerInfo listener : listInfos) {
                listener.addTransaction(job);
            }
            // the config listener job
            configListeners.addTransaction(new ConfigListenerJob(events, latch));
            
            job.waitForLatch();
        } catch (InterruptedException e ) {
            throw new RuntimeException(e);
        }
    }

    public void waitForDrain() {
        // insert a dummy Job and block until is has been processed.  This guarantees
        // that all prior jobs have finished
        addTransaction( new ArrayList<PropertyChangeEvent>(), true );
        // at this point all prior transactions are guaranteed to have cleared
    }
    
    private Transactions(ExecutorService executor) {
        this.executor = executor;
        listeners = new ArrayList<ListenerInfo<TransactionListener>>();
        configListeners = new ListenerInfo<Object>(null);
    }

    public static synchronized Transactions get(ExecutorService executor) {
        if (singleton==null) {
            singleton=new Transactions(executor);
        }
        return singleton;
    }
    
    public static final Transactions get() {
        return singleton;
    }
}






