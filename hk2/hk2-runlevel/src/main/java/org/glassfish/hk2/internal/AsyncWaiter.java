/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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
package org.glassfish.hk2.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.hk2.AsyncPostConstruct;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.ServiceHandle;

/**
 * Helper class that will manage all {@link AsyncPostConstruct} services
 * and Futures for completion.
 *
 * <p/>
 * Once a service or Future is found to be completed, it is dropped.
 *
 * TODO : this can go away if we get rid of the synchronous proceedTo
 *
 * @author Jeff Trent
 */
public class AsyncWaiter {

    private static Logger logger = Logger.getLogger(AsyncWaiter.class.getName());
    private static Level LEVEL = Level.FINE;

    private Collection<AsyncPostConstruct> watches;

    private AsyncPostConstruct workingOn;


    /**
     * Clear the collection of watches, regardless of state.
     */
    public synchronized void clear() {
        watches = null;
        workingOn = null;
    }

    /**
     * Watches an service handle if the service implements {@link AsyncPostConstruct}.
     *
     * @param serviceHandle  the service handle
     */
    public synchronized void watchIfNecessary(ServiceHandle<?> serviceHandle) {
        Object service = serviceHandle.getService();
        if (AsyncPostConstruct.class.isInstance(service)) {
            if (!AsyncPostConstruct.class.cast(service).isDone()) {
                if (null == watches) {
                    watches = new ArrayList<AsyncPostConstruct>();
                }
                AsyncServiceHandle watch = new AsyncServiceHandle(serviceHandle);
                logger.log(LEVEL, "Adding watch on {0}", watch);

                logger.log(LEVEL, " watch done = {0}", watch.isDone());

                watches.add(watch);
            }
        }
    }

    /**
     * Watches a Future for completion.
     *
     * @param f  the future
     */
    public synchronized void watchIfNecessary(Future<?> f) {
        if (!f.isDone()) {
            if (null == watches) {
                watches = new ArrayList<AsyncPostConstruct>();
            }
            AsyncFuture watch = new AsyncFuture(f);
            logger.log(LEVEL, "Adding watch on {0}", watch);
            watches.add(watch);
        }
    }

    /**
     * Waits for all watches to be done. This might be a blocking operation.
     *
     * @throws ExecutionException   if the computation threw an exception
     * @throws TimeoutException     if the wait timed out
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    public synchronized void waitForDone() throws ExecutionException, TimeoutException, InterruptedException {
        if (null != watches) {
            Iterator<AsyncPostConstruct> iter = watches.iterator();
            while (iter.hasNext()) {
                workingOn = iter.next();
                workingOn.waitForDone();
                iter.remove();
            }
        }

        workingOn = null;
    }

    /**
     * Waits for all service handles being watched to be done, giving each up
     * to timeout/unit's to be done. If there are any {@link TimeoutException}s
     * the result will be false.
     *
     * @param timeout  the timeout value
     * @param unit     the time unit
     *
     * @return if no exceptions are encountered while waiting
     *
     * @throws ExecutionException   throws
     * @throws TimeoutException     throws
     * @throws InterruptedException throws
     */
    public boolean waitForDone(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException, InterruptedException {
        long start = System.currentTimeMillis();
        logger.log(LEVEL, "entering; watches = {0}", (null == watches ? -1 : watches.size()));

        boolean done = true;

        if (null != watches) {
            Iterator<AsyncPostConstruct> iter = watches.iterator();
            while (iter.hasNext()) {
                workingOn = iter.next();
                if (workingOn.waitForDone(timeout, unit)) {
                    iter.remove();
                } else {
                    logger.log(LEVEL, "gave up waiting on {0}", workingOn);
                    done = false;
                }
            }
        }

        workingOn = null;

        logger.log(LEVEL, "exiting - {0} ms", System.currentTimeMillis()-start);
        logger.log(LEVEL, " watches = {0}", (null == watches ? -1 : watches.size()));
        logger.log(LEVEL, " done is {0}", done);

        return done;
    }

    public synchronized int getWatches() {
        return (null == watches) ? 0 : watches.size();
    }

    /**
     * A non-blocking call that returns true when we are done waiting.
     *
     * @return true if we are done waiting
     */
    public synchronized boolean isDone() {
        boolean done = true;
        if (null != watches) {
            Iterator<AsyncPostConstruct> iter = watches.iterator();
            while (iter.hasNext()) {
                workingOn = iter.next();
                if (workingOn.isDone()) {
                    iter.remove();
                }
            }

            workingOn = null;

            done = watches.isEmpty();
        }

        return done;
    }

    /**
     * Returns the last service handle that was being worked on, provided that we are not in a "done" state.
     *
     * @return returns the last service handle that was being worked on
     */
    public synchronized ActiveDescriptor<?> getLastDescriptorWorkingOn() {
        return workingOn instanceof AsyncServiceHandle ? ((AsyncServiceHandle)workingOn).serviceHandle.getActiveDescriptor() : null;
    }


    private static class AsyncFuture implements AsyncPostConstruct {
        private final Future<?> future;

        private AsyncFuture(Future<?> future) {
            this.future = future;
        }

        @Override
        public void postConstruct() {
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public void waitForDone() throws ExecutionException, InterruptedException{
            future.get();
        }

        @Override
        public boolean waitForDone(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException {
            try {
                future.get(timeout, unit);
                return true;
            } catch (TimeoutException e) {
                return false;
            }
        }
    }


    private static class AsyncServiceHandle implements AsyncPostConstruct {
        private final ServiceHandle<?> serviceHandle;
        private final AsyncPostConstruct service;

        private AsyncServiceHandle(ServiceHandle<?> serviceHandle) {
            this.serviceHandle = serviceHandle;
            this.service = (AsyncPostConstruct) serviceHandle.getService();
        }

        @Override
        public void postConstruct() {
        }

        @Override
        public boolean isDone() {
            return service.isDone();
        }

        @Override
        public void waitForDone() throws ExecutionException, TimeoutException, InterruptedException {
            service.waitForDone();
        }

        @Override
        public boolean waitForDone(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException {
            return service.waitForDone(timeout, unit);
        }
    }

}
