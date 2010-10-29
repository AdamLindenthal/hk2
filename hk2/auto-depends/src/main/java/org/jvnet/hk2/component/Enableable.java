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

/**
 * Contract used for enabling/disabling a service, and querying whether it
 * is currently enabled.
 * <p/>
 * Implementors are advised to implement this contract if the functionality
 * they are providing can be toggled on/off during run-time.
 * <p/>
 * Suppose a service class implements {@link Runnable} and {@link Enableable}
 * then calls to run() could presumably be disabled by calling
 * {@link #enable(boolean)}.
 * <p/>
 * Implements of this contract are encouraged to throw an 
 * {@link IllegalStateException} when a service is disabled and it is asked to
 * perform the gated operation (as in the case for run() above).
 * 
 * @author Jeff Trent
 * @since 3.1
 */
public interface Enableable {

  /**
   * Toggle the enabled state.
   * <p/>
   * Implementors are encouraged to throw an IllegalStateException
   * if the requested enablement/disablement operation cannot be
   * performed for whatever reason.
   * 
   * @param enabled true to enable, and false to disable
   */
  void enable(boolean enabled) throws IllegalStateException;

  /**
   * @return true if the service is currently enabled
   */
  boolean isEnabled();
  
}
