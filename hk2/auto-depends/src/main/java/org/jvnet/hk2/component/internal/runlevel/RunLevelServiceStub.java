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

import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.InhabitantListener;
import org.jvnet.hk2.component.RunLevelService;
import org.jvnet.hk2.component.RunLevelState;

/**
 * This serves as a holder, or proxy, for RunLevelServices that are not
 * initially found in the Habitat.
 * 
 * @author Jeff Trent
 * 
 * @since 3.1
 */
@SuppressWarnings("unchecked")
/*public*/ class RunLevelServiceStub implements RunLevelService, RunLevelState, InhabitantListener {

  private final Habitat h;
  
  private final Class<?> env;
  
  // We are waiting for this guy to come around
  private RunLevelService delegate;
  private InhabitantListener delegateListener;
  
  
  /*public*/ RunLevelServiceStub(Habitat habitat, Class<?> environment) {
    this.h = habitat;
    this.env = environment;
  }

  public RunLevelService getDelegate() {
    return delegate;
  }
  
  /**
   * Called when the habitat backing this RunLevelService has been fully initialized.
   */
  void activate(RunLevelService<?> realRls) {
    delegate = realRls;
    if (InhabitantListener.class.isInstance(delegate)) {
      delegateListener = InhabitantListener.class.cast(delegate);
    }
  }

  Habitat getHabitat() {
    return h;
  }

  @Override
  public RunLevelState getState() {
    return this;
  }

  @Override
  public Integer getCurrentRunLevel() {
    return (null == delegate) ? null : delegate.getState().getCurrentRunLevel();
  }

  @Override
  public Integer getPlannedRunLevel() {
    return (null == delegate) ? null : delegate.getState().getPlannedRunLevel();
  }

  @Override
  public Class<?> getEnvironment() {
    return env;
  }

  @Override
  public void proceedTo(int runLevel) {
    if (null != delegate) {
      delegate.proceedTo(runLevel);
    }

    // should never be here
    throw new IllegalStateException();
  }

  @Override
  public boolean inhabitantChanged(EventType eventType, Inhabitant<?> inhabitant) {
    if (null == delegateListener) {
      if (null == delegate) {
        // we want to keep getting messages for now
        return true;
      } else {
        // our delegate is not a listener, so we don't care anymore
        return false;
      }
    }

    // refer to the delegate
    return delegateListener.inhabitantChanged(eventType, inhabitant);
  }

}
