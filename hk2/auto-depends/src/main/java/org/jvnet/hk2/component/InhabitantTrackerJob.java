/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package org.jvnet.hk2.component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jvnet.hk2.component.InhabitantTracker.Callback;

/**
 * @see Habitat#trackFuture(InhabitantTrackerContext)
 * 
 * @author Jeff Trent
 * 
 * @since 3.1
 */
/*public*/class InhabitantTrackerJob implements Future<InhabitantTracker>,
    Callable<InhabitantTracker>, Callback {
//  private final boolean REUSE_HABITAT_EXECUTOR = true;
//  private static ExecutorService trackerLevelExecutorService;
  
  private final Habitat h;
  private final ExecutorService exec;
  volatile InhabitantTrackerImpl it;
  private final InhabitantTrackerContext itc;
  private Long timeout;

  /*public*/ InhabitantTrackerJob(Habitat h, InhabitantTrackerContext itc) {
    this.h = h;
//    if (REUSE_HABITAT_EXECUTOR) {
      this.exec = h.exec;
//    } else {
//      if (null == trackerLevelExecutorService) {
//        trackerLevelExecutorService = 
//          Executors.newCachedThreadPool(new ThreadFactory() {
//            @Override
//            public Thread newThread(Runnable runnable) {
//              Thread t = Executors.defaultThreadFactory().newThread(runnable);
//              t.setDaemon(true);
//              return t;
//            }
//        });
//      }
//      this.exec = trackerLevelExecutorService;
//    }
    this.itc = itc;
  }
  
  @Override
  public String toString() {
    // need to call isDone() to create IT
    isDone();
    return getClass().getSimpleName() + "-" +
        System.identityHashCode(this) + "(" + it + ")";
  }

  @Override
  public boolean cancel(boolean arg) {
    if (null != it) {
      synchronized (it) {
        timeout = 0L;
        it.notify();
      }
      it.release();
    }
    return true;
  }

  @Override
  public InhabitantTracker get() throws InterruptedException,
      ExecutionException {
    if (!isDone()) {
      timeout = null;
      this.exec.submit(this).get();
    }
    return isDone() ? it : null;
  }

  @Override
  public InhabitantTracker get(long timeout, TimeUnit units)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!isDone()) {
      this.timeout = units.convert(timeout, TimeUnit.MILLISECONDS);
      if (0 == timeout) {
        return null;
      }
      this.exec.submit(this).get();
    }
    return isDone() ? it : null;
  }

  @Override
  public boolean isCancelled() {
    return true;
  }

  @Override
  public boolean isDone() {
    if (null == it) {
      it = new InhabitantTrackerImpl(h, itc, this);
    }
    return it.isDone();
  }

  @Override
  public void updated(InhabitantTracker t, Habitat h, boolean initial) {
    if (null != it) {
      synchronized (it) {
        it.notify();
      }
    }
  }

  @Override
  public InhabitantTracker call() throws Exception {
    if (null != it) {
      synchronized (it) {
        if (!isDone()) {
          if (null == timeout) {
            it.wait();
          } else {
            it.wait(timeout);
          }
        }
      }
    }
    return it;
  }
}
