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
package com.sun.hk2.component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.InhabitantListener;
import org.jvnet.hk2.component.MultiMap;

/**
 * An inhabitant that implements InhabitantEventPublisher, and maintains a list
 * of listeners to notify for interesting changes of the underlying delegate.
 * 
 * @author Jeff Trent
 * 
 * @since 3.1
 */
/* public */class EventPublishingInhabitant<T> extends AbstractInhabitantImpl<T> {

  /**
   * Real {@link Inhabitant} object.
   */
  protected volatile Inhabitant<T> real;

  /**
   * Those that will receive events
   */
  private HashSet<InhabitantListener> listeners;

  
  /* public */EventPublishingInhabitant() {
    this.real = null;
  }
  
  /* public */EventPublishingInhabitant(Inhabitant<?> delegate) {
    this(delegate, null);
  }

  @SuppressWarnings("unchecked")
  /* public */EventPublishingInhabitant(Inhabitant<?> delegate, InhabitantListener listener) {
    this.real = (Inhabitant<T>) delegate;
    if (null != listener) {
      addInhabitantListener(listener);
    }
  }
  
  @Override
  public String toString() {
      return getClass().getSimpleName() + "-" + System.identityHashCode(this) + 
          "(" + typeName() + ", active: " + real + ")";
  }

  @Override
  public String typeName() {
    return (null == real) ? null : real.typeName();
  }

  @Override
  public MultiMap<String, String> metadata() {
    return (null == real) ? null : real.metadata();
  }

  @Override
  public void release() {
    final boolean wasActive = isInstantiated();
    if (null != real) {
      real.release();
    }
    if (wasActive && !isInstantiated()) {
      notify(InhabitantListener.EventType.INHABITANT_RELEASED);
    }
  }

  @Override
  public boolean isInstantiated() {
    return (null != real && real.isInstantiated());
  }

  @Override
  public Class<T> type() {
    if (null == real) throw new IllegalStateException();
    final boolean wasActive = real.isInstantiated();
    Class<T> t = real.type();
    if (!wasActive && real.isInstantiated()) {
      notify(InhabitantListener.EventType.INHABITANT_ACTIVATED);
    }
    return t;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(Inhabitant onBehalfOf) {
    if (null == real) {
      fetch();
    }
    
    final boolean wasActive = real.isInstantiated();
    T result = real.get(onBehalfOf);
    if (!wasActive && real.isInstantiated()) {
      notify(InhabitantListener.EventType.INHABITANT_ACTIVATED);
    }
    
    return result;
  }

  protected void fetch() {
    throw new IllegalStateException();  // responsibility on derived classes
  }

//@Override // for EventPublishingInhabitant
  public synchronized void addInhabitantListener(InhabitantListener listener) {
    if (null == listener) throw new IllegalArgumentException();
    if (null == listeners) {
      listeners = new HashSet<InhabitantListener>();
    }
    listeners.add(listener);
  }

//  @Override // for EventPublishingInhabitant
  public boolean removeInhabitantListener(InhabitantListener listener) {
    return (null == listeners) ? false : listeners.remove(listener);
  }

  protected synchronized void notify(InhabitantListener.EventType eventType) {
    if (null != listeners) {
      Iterator<InhabitantListener> iter = listeners.iterator();
      while (iter.hasNext()) {
        InhabitantListener listener = iter.next();
        try {
          boolean keepListening = listener.inhabitantChanged(eventType, this);
          if (!keepListening) {
            iter.remove();
          }
        } catch (Exception e) {
          // don't percolate the exception since it may negatively impact processing
          Logger.getAnonymousLogger().log(Level.WARNING, "exception caught from listener:", e);
        }
      }
    }
  }
}
