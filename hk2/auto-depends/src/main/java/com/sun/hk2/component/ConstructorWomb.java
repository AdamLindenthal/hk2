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
package com.sun.hk2.component;

import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.MultiMap;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.component.Scope;
import org.jvnet.hk2.component.Singleton;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Creates an object from its constructor.
 * @author Kohsuke Kawaguchi
 */
public class ConstructorWomb<T> extends AbstractWombImpl<T> {
    private final Habitat habitat;
    private final ScopeInstance singletonScope;


    public ConstructorWomb(Class<T> type, Habitat habitat, MultiMap<String,String> metadata) {
        super(type,metadata);
        this.habitat = habitat;
        singletonScope = habitat.singletonScope;
    }

    public T create(Inhabitant onBehalfOf) throws ComponentException {
        try {
            return type.newInstance();
        } catch (InstantiationException e) {
            throw new ComponentException("Failed to create "+type,e);
        } catch (IllegalAccessException e) {
            throw new ComponentException("Failed to create "+type,e);
        } catch (LinkageError e) {
            throw new ComponentException("Failed to create "+type,e);
        } catch (RuntimeException e) {
            throw new ComponentException("Failed to create "+type,e);
        }
    }

    public void initialize(final T t, final Inhabitant onBehalfOf) throws ComponentException {
        Scoped scoped = t.getClass().getAnnotation(Scoped.class);
        ScopeInstance si = (scoped == null ? singletonScope : getScope(scoped));
        if (System.getSecurityManager() != null) {
          AccessController.doPrivileged(new PrivilegedAction() {
              //doprivileged required for running with SecurityManager ON
              public java.lang.Object run() {
                  inject(habitat, t, onBehalfOf);
                  return null;
              }
          });
        } else {
          inject(habitat, t, onBehalfOf);
        }
    }

    /**
     * Determines the {@link ScopeInstance} that stores the component.
     *
     * @return
     *      null for prototype scope. (Note that in {@link Scope#current()}
     *      null return value is an error.)
     */
    private ScopeInstance getScope(Scoped svc) throws ComponentException {
        Class<? extends Scope> s = svc.value();
        // for performance reason and to avoid infinite recursion,
        // recognize these two fundamental built-in scopes and process them differently.
        if(s==Singleton.class)
            return singletonScope;
        if(s==PerLookup.class)
            return null;

        // for all the other scopes, including user-defined ones.
        Scope scope = habitat.getByType(s);
        ScopeInstance si = scope.current();
        if(si==null) // scope is an extension point, so beware for broken implementations
            throw new ComponentException(scope+" returned null from the current() method");
        return si;
    }
    
}
