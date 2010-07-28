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
package org.jvnet.hk2.component.internal.runlevel;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.hk2.annotations.RunLevel;
import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.HabitatListener;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.InhabitantListener;
import org.jvnet.hk2.component.RunLevelListener;
import org.jvnet.hk2.component.RunLevelService;
import org.jvnet.hk2.component.RunLevelState;
import org.jvnet.hk2.component.ServiceContext;

import com.sun.hk2.component.AbstractInhabitantImpl;
import com.sun.hk2.component.LazyInhabitant;
import com.sun.hk2.component.RunLevelInhabitant;

/**
 * The default environment RunLevelService implementation.
 * 
 * Assume ServiceA, ServiceB, and ServiceC are all in the same RunLevel and the dependencies are:
 *
 *    ServiceA -> ServiceB -> ServiceC
 *
 * Expected start order: ServiceC, ServiceB, ServiceA
 * Expected shutdown / PreDestroy order: ServiceA, ServiceB, ServiceC
 *
 * So, how do we decide when to record events to the recorder?  The answer is hook into
 * (post) PostConstruct activations.
 *
 * Since the model is not in the habitat, we will just need to randomly pick a start service.
 *
 * Case 1: A, B, then C by RLS.
 * get ServiceA (called by RLS)
 *   Start ServiceA:
 *     get ServiceB
 *       Start ServiceB:
 *         get ServiceC
 *           Start ServiceC
 *           wire ServiceC
 *           PostConstruct ServiceC
 *       wire ServiceB
 *       PostConstruct ServiceB
 *   wire ServiceA
 *   PostConstruct ServiceA
 * get ServiceB (called by RLS)
 * get ServiceC (called by RLS)
 *
 * Case 2: B, C, then A by RLS.
 * get ServiceB (called by RLS)
 *   Start ServiceB:
 *     get ServiceC
 *       Start ServiceC
 *       wire ServiceC
 *       PostConstruct ServiceC
 *   wire ServiceB
 *   PostConstruct ServiceB
 * get ServiceC (called by RLS)
 * get ServiceA (called by RLS)
 * Start ServiceA:
 *   get ServiceB
 *   wire ServiceA
 *   PostConstruct ServiceA
 *
 * Case 3: B, A, then C by RLS.
 * get ServiceB (called by RLS)
 *   Start ServiceB:
 *     get ServiceC
 *       Start ServiceC
 *       wire ServiceC
 *       PostConstruct ServiceC
 *   wire ServiceB
 *   PostConstruct ServiceB
 * get ServiceA (called by RLS)
 *   Start ServiceA:
 *     get ServiceB
 *   wire ServiceA
 *   PostConstruct ServiceA
 * get ServiceC (called by RLS)
 *
 * Case 4: C, B, then A by RLS.
 * get ServiceC (called by RLS)
 *   Start ServiceC:
 *   wire ServiceC
 *   PostConstruct ServiceC
 * get ServiceB (called by RLS)
 *   Start ServiceB:
 *     get ServiceC
 *   wire ServiceB
 *   PostConstruct ServiceB
 *   get ServiceA (called by RLS)
 *     Start ServiceA:
 *       get ServiceB
 *     wire ServiceA
 *     PostConstruct ServiceA
 * get ServiceA (called by RLS)
 *
 * ~~~
 * 
 * Note that the implementation performs some level of constraint checking
 * during injection.  For example,
 * 
 *  - It is an error to have a RunLevel-annotated service at RunLevel X to
 *    depend on (i.e., be injected with) a RunLevel-annotated service at
 *    RunLevel Y when Y > X.
 *    
 *  - It is an error to have a non-RunLevel-annotated service to depend on
 *  a RunLevel-annotated service at any RunLevel.
 *  
 * Note that the implementation does not handle Holder and Collection injection
 * constraint validations.
 * 
 * ~~~
 * 
 * The implementation will automatically proceedTo(-1) after the habitat has been initialized.
 * 
 * Note that all RunLevel values less than -1 will be ignored.
 *
 * @author Jeff Trent
 *
 * @since 3.1
 */
public class DefaultRunLevelService
  implements RunLevelService<Void>, RunLevelState<Void>, InhabitantListener, HabitatListener {

  static final boolean ASYNC_ENABLED = false;
  
  private final boolean asyncMode;
  private ExecutorService exec;

  private final Habitat habitat;

  private RunLevelState<Void> delegate;
  
  private Integer current;
  private Integer planned;
  
  private final HashMap<Integer, Recorder> recorders;
  private volatile Recorder activeRecorder;
  private volatile Integer activeRunLevel;
  private volatile Boolean upSide;
  private volatile Future<?> activeProceedToOp;

  private enum ListenerEvent {
    PROGRESS,
    CANCEL,
    ERROR,
  }
  
  public DefaultRunLevelService(Habitat habitat) {
    this(habitat, ASYNC_ENABLED, new LinkedHashMap<Integer, Recorder>());
  }

  DefaultRunLevelService(Habitat habitat, boolean async,
      HashMap<Integer, Recorder> recorders) {
    this.habitat = habitat;
    assert(null != habitat);
    this.asyncMode = async;
    if (asyncMode) {
      // TODO: after jdk16 is the std, use cache policy to clear even the single thread after idle time
      exec = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
          Thread t = new RunLevelServiceThread(runnable);
          return t;
        }
      });
    }
    this.recorders = recorders;
    
    habitat.addHabitatListener(this);
  }
  
  @Override
  public String toString() {
    return getClass().getSimpleName() + "-" + System.identityHashCode(this)
        + "(" + current + ", " + planned + ", " + delegate + ")";
  }
  
  @Override
  public synchronized void proceedTo(final int runLevel) {
    if (runLevel < -1) {
      throw new IllegalArgumentException();
    }
    
    if (null != activeProceedToOp) {
      Logger.getAnonymousLogger().log(Level.INFO, 
          "Cancelling activation to runLevel {0} and instead proceeding to {1}",
          new Object[] {planned, runLevel});
      activeProceedToOp.cancel(true);
      reset();
    }

    assert(null == activeRunLevel);
    assert(null == planned);
    
    // set planned immediately
    this.planned = runLevel;
    
    if (null != exec) {
      activeProceedToOp = exec.submit(new Runnable() {
        @Override
        public void run() {
          proceedToWorker(runLevel);
          if (Thread.currentThread().isInterrupted()) {
            DefaultRunLevelService.this.notify(ListenerEvent.CANCEL, null, null);
          } else {
            synchronized (DefaultRunLevelService.this) {
              DefaultRunLevelService.this.notifyAll();
            }
          }
        }
      });
    } else {
      proceedToWorker(runLevel);
    }
  }

  private void proceedToWorker(int runLevel) {
    int current = (null == this.current) ? -2 : this.current;
    if (runLevel > current) {
      for (int rl = current + 1; rl <= runLevel; rl++) {
        upActiveRecorder(rl);
      }
    } else if (runLevel < current) {
      for (int rl = current; rl >= runLevel; rl--) {
        downActiveRecorder(rl);
      }
    } else {
      this.current = current;
    }
    
    // remember to null these for proper constraint checking
    reset();
  }

  private void reset() {
    this.activeRecorder = null;
    this.upSide = null;
    this.activeRunLevel = null;
    this.planned = null;
    this.activeProceedToOp = null;
  }
  
  private synchronized void upActiveRecorder(int runLevel) {
    upSide = true;
    activeRunLevel = runLevel;

    // We do not create a Recorder here, and instead wait for notification to come in

    // create demand for RunLevel (runLevel) components
    activateRunLevel();

    // don't set current until we've actually reached it
    current = runLevel;
    activeRunLevel = null;

    if (planned == current) {
      // needed for the chained case
      reset();
    } else {
      activeRecorder = null;
    }
    
    // notify listeners that we are complete
    notify(ListenerEvent.PROGRESS, null, null);
  }

  protected void activateRunLevel() {
    // TODO: we could cache this in top-level proceedTo()
    Collection<Inhabitant<?>> runLevelInhabitants = habitat.getAllInhabitantsByContract(RunLevel.class.getName());
    for (Inhabitant<?> i : runLevelInhabitants) {
      AbstractInhabitantImpl<?> ai = AbstractInhabitantImpl.class.cast(i);
      RunLevel rl = ai.getAnnotation(RunLevel.class);

      if (rl.value() == activeRunLevel) {
//        assert(!i.isInstantiated()); -- avoid check since we could have cancelled a previous op
        Logger.getAnonymousLogger().log(Level.FINE, "activating {0}", i);
        try {
          i.get();
          assert(i.isInstantiated());
          if (!RunLevelInhabitant.class.isInstance(ai)) {
            Logger.getAnonymousLogger().log(Level.WARNING, 
                "{0} is not a RunLevelInhabitant and therefore will not be released during shutdown", ai);
          }
        } catch (Exception e) {
          // don't percolate the exception since it may negatively impact processing
          Logger.getAnonymousLogger().log(Level.WARNING, "exception caught from activation:", e);

          notify(ListenerEvent.ERROR, serviceContext(e, ai), e);
        }
//      } else if (rl.value() < activeRunLevel) {
          // should have been already, but we don't try here because it likely caused an exception earlier, or was cancelled
//      } else {  // future runLevel
//        assert(!i.isInstantiated()); -- avoid check since we could have cancelled a previous op
      }
    }
  }

  private synchronized void downActiveRecorder(int runLevel) {
    upSide = false;
    activeRunLevel = runLevel;

    // activeRecorder should really just be used on upSide
    Recorder downRecorder = recorders.remove(runLevel);
    if (null != downRecorder) {
      downRecorder.release();
    }
    
    // don't set current until we've actually reached it
    current = runLevel;

    if (planned == current) {
      // needed for the chained case
      reset();
    } else {
      activeRunLevel = null;
    }
    
    // notify listeners that we are complete
    notify(ListenerEvent.PROGRESS, null, null);
  }

  private void notify(ListenerEvent event, ServiceContext context, Throwable error) {
    Collection<RunLevelListener> activeListeners = habitat.getAllByContract(RunLevelListener.class);
    for (RunLevelListener listener : activeListeners) {
      try {
        if (ListenerEvent.PROGRESS == event) {
          listener.onProgress(this);
        } else if (ListenerEvent.CANCEL == event) {
          listener.onCancelled(this, current);
        } else {
          listener.onError(this, context, error, true);
        }
      } catch (Exception e) {
        // don't percolate the exception since it may negatively impact processing
        Logger.getAnonymousLogger().log(Level.WARNING, "exception caught from listener:", e);
      }
    }
  }

  private ServiceContext serviceContext(Exception e, final AbstractInhabitantImpl<?> ai) {
    ServiceContext ctx = null;

    if (e instanceof ComponentException) {
      ctx = ((ComponentException)e).getFailureContext();
    }
    
    if (null == ctx) {
      ctx = new ServiceContext() {
        @Override
        public ClassLoader getClassLoader() {
          ClassLoader cl;
          if (LazyInhabitant.class.isInstance(ai)) { 
            cl = ((LazyInhabitant<?>)ai).getClassLoader();
          } else {
            cl = ai.getClass().getClassLoader();
          }
          return cl;
        }

        @Override
        public Inhabitant<?> getInhabitant() {
          return ai;
        }

        @Override
        public String getType() {
          return ai.typeName();
        }
        
        @Override
        public String toString() {
          return ai.toString();
        }
      };
    }
    
    return ctx;
  }

  void setDelegate(RunLevelState<Void> stateProvider) {
    assert(this != stateProvider);
    assert(getEnvironment() == stateProvider.getEnvironment());
    this.delegate = stateProvider;
  }
  
  @Override
  public RunLevelState<Void> getState() {
    return (null == delegate) ? this : delegate;
  }

  @Override
  public Class<Void> getEnvironment() {
    return (null == delegate) ? Void.class : delegate.getEnvironment();
  }

  @Override
  public Integer getCurrentRunLevel() {
    return (null == delegate) ? current : delegate.getCurrentRunLevel();
  }

  @Override
  public Integer getPlannedRunLevel() {
    return (null == delegate) ? planned : delegate.getPlannedRunLevel();
  }

  @Override
  public boolean inhabitantChanged(InhabitantListener.EventType eventType, Inhabitant<?> inhabitant) {
    if (InhabitantListener.class.isInstance(delegate)) {
      return InhabitantListener.class.cast(delegate).inhabitantChanged(eventType, inhabitant);
    }

    if (null == activeRunLevel) {
      // its unclear if this is possible, but definitely indicates a problem of some kind.
      // If the recorder is not active, then we are not in a proceedTo() call.
      throw new ComponentException("problem: " + inhabitant);
    }

    if ((upSide && InhabitantListener.EventType.INHABITANT_ACTIVATED != eventType) ||
        (!upSide && InhabitantListener.EventType.INHABITANT_RELEASED != eventType)) {
      throw new ComponentException("problem: " + inhabitant);
    }

    // forward to the active recorder
    if (upSide) {
      synchronized (this) {
        if (null == activeRecorder) {
          activeRecorder = new Recorder(activeRunLevel);
          if (null != recorders.put(activeRunLevel, activeRecorder)) {
            throw new AssertionError("bad state");
          }
        }
      }
    }

    // if we have an active recorder, redirect the event
    // (we don't really need to do this for release events)
    if (null != activeRecorder) {
      activeRecorder.inhabitantChanged(eventType, inhabitant);
    }
    
    // we always want to maintain our subscription
    return true;
  }
  
  @Override
  public boolean inhabitantChanged(
      HabitatListener.EventType eventType,
      Habitat habitat, Inhabitant<?> inhabitant) {
    if (org.jvnet.hk2.component.HabitatListener.EventType.HABITAT_INITIALIZED == eventType) {
      proceedTo(-1);
    }
    return !habitat.isInitialized();
  }

  @Override
  public boolean inhabitantIndexChanged(
      HabitatListener.EventType eventType,
      Habitat habitat, Inhabitant<?> inhabitant, String index, String name,
      Object service) {
    return true;
  }

  
  private static class RunLevelServiceThread extends Thread {
    
    private RunLevelServiceThread(Runnable r) {
      super(r);
      setDaemon(true);
      setName(getClass().getSimpleName() + "-" + System.identityHashCode(this));
    }

  }


}
