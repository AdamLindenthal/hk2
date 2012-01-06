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
package com.sun.hk2.component;

import org.glassfish.hk2.Scope;
import org.jvnet.hk2.component.Creator;
import org.jvnet.hk2.component.DescriptorImpl;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.tracing.TracingThreadLocal;
import org.jvnet.hk2.tracing.TracingUtilities;

/**
 * Scoped inhabitant.
 *
 * @param <T> scoped inhabitant type.
 * @author Kohsuke Kawaguchi
 */
public class ScopedInhabitant<T> extends AbstractCreatorInhabitantImpl<T> {
    private final Scope scope;

    public ScopedInhabitant(Creator<T> creator, Scope scope) {
        super(DescriptorImpl.createMerged(getDescriptorFor(creator),
                new DescriptorImpl(null, null, null, scope)),
             creator);
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public T get(Inhabitant onBehalfOf) {
        try {
            if (TracingUtilities.isEnabled())
                TracingThreadLocal.get().push(this);

            org.glassfish.hk2.ScopeInstance store = scope.current();
            // scope is extension point, so beware for the broken implementation
            assert store!=null : scope+" returned null";

            if(store.contains(this)) {
                return store.get(this);
            } else {
                synchronized(this) {
                    // to avoid creating multiple objects into the same scope, lock this object
                    // verify no one else created one in the mean time
                    if(store.contains(this)) {
                    return store.get(this);
                    } else {
                        T o = creator.get(onBehalfOf);
                        store.put(this,o);
                        return o;
                    }
                }
            }
        } finally {
            if (TracingUtilities.isEnabled())
                TracingThreadLocal.get().pop();
        }
    }

    @Override
    public boolean isActive() {
        return scope.current().get(this)!=null;
    }

    @Override
    public void release() {
        // noop
    }
}
