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

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.HK2Loader;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Creator;
import org.jvnet.hk2.component.Creators;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.deprecated.internal.HolderHK2LoaderImpl;

/**
 * @author Kohsuke Kawaguchi
 */
@Deprecated
public class LazyInhabitant<T> extends EventPublishingInhabitant<T> {
    /**
     * 
     */
    private static final long serialVersionUID = 6404544098462796138L;

    public LazyInhabitant(ServiceLocator serviceLocator, HK2Loader cl, String typeName, Map<String, List<String>> metadata) {
        super(serviceLocator, org.glassfish.hk2.deprecated.utilities.Utilities.createDescriptor(typeName, cl, metadata));

    }
    
    @Override
    public Class<? extends T> type() {
        return type(null);
    }
    
    public Class<? extends T> type(HK2Loader loader) {
      // fetching is too heavy of an operation because it will activate/write the class
//        fetch();

        Inhabitant<T> real = this.real;
        if (null != real) {
            return real.type();
        } else {
            return loadClass(loader);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected synchronized void fetch() {
        if (null == real) {
            ActiveDescriptor<?> reified = getServiceLocator().reifyDescriptor(this);
            
            real = (Inhabitant<T>) reified.getBaseDescriptor();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Class<T> loadClass(HK2Loader loader) {
        String typeName = getImplementation();
        logger.log(Level.FINER, "loading class for: {0}", typeName);
        
        //final ClassLoader cl = getClassLoader();
        try {
            if (loader == null) {
                loader = getLoader();
            }

            Class<T> c = loader == null ?
                        (Class<T>) getClass().getClassLoader().loadClass(typeName) :
                        (Class<T>) loader.loadClass(typeName);
            return c;
        } catch (ClassNotFoundException e) {
            throw new ComponentException("Failed to load "+typeName+" from loader " + loader + " of " +
               this, e);
        } catch (MultiException e) {
            throw new ComponentException("Failed to load "+typeName+" from loader " + getLoader() + " of " +
                this, e);
        }
    }
    
    protected Class<T> loadClass() {
        return loadClass(null);
    }

    @Override
    public synchronized void release() {
        super.release();
        real = null;
    }

    /**
     * Creates {@link Creator} for instantiating objects.
     */
    protected Creator<T> createCreator(Class<T> c) {
        Map<String, List<String>> metadata = getMetadata();
        return Creators.create(c, getServiceLocator(), metadata);
    }

}
