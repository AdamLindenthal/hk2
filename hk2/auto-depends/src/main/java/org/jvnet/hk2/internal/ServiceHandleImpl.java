/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.jvnet.hk2.internal;

import java.util.LinkedList;
import java.util.List;

import net.sf.cglib.proxy.Enhancer;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Context;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;

/**
 * This handle does the underlying work of getting the service.  Only
 * at the time that the getService call is made is the service gotten
 * from the context.  Once a service has been gotten, it is not looked
 * up again.
 * 
 * @author jwells
 *
 */
public class ServiceHandleImpl<T> implements ServiceHandle<T> {
    private ActiveDescriptor<T> root;
    private final ServiceLocatorImpl locator;
    private final Object lock = new Object();
    
    private boolean serviceDestroyed = false;
    private boolean serviceSet = false;
    private T service;
    
    private final List<ServiceHandleImpl<?>> subHandles = new LinkedList<ServiceHandleImpl<?>>();
    
    /* package */ ServiceHandleImpl(ServiceLocatorImpl locator, ActiveDescriptor<T> root) {
        this.root = root;
        this.locator = locator;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceHandle#getService()
     */
    @SuppressWarnings("unchecked")
    @Override
    public T getService() {
        synchronized (lock) {
            if (serviceDestroyed) throw new IllegalStateException("Service has been disposed");
            
            if (serviceSet) return service;
            
            if (!root.isReified()) {
                root = (ActiveDescriptor<T>) locator.reifyDescriptor(root);
            }
        
            if (Utilities.isProxiableScope(root.getScopeAnnotation())) {
                T proxy = (T) Enhancer.create(root.getImplementationClass(),
                    Utilities.getInterfacesForProxy(root.getContractTypes()),
                    new MethodInterceptorImpl(locator, root, this));
            
                serviceSet = true;
                service = proxy;
                
                return proxy;
            }
        
            Context<?> context = locator.resolveContext(root.getScopeAnnotation());
            service = context.findOrCreate(root, this);
        
            serviceSet = true;
        
            return service;
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceHandle#getActiveDescriptor()
     */
    @Override
    public ActiveDescriptor<T> getActiveDescriptor() {
        return root;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceHandle#isActive()
     */
    @Override
    public boolean isActive() {
        // No lock needed, nothing changes state
        if (serviceDestroyed) return false;
        if (serviceSet) return true;
        
        try {
            Context<?> context = locator.resolveContext(root.getScopeAnnotation());
            return (context.find(root) != null);
        }
        catch (IllegalStateException ise) {
            return false;
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceHandle#destroy()
     */
    @Override
    public void destroy() {
        synchronized (lock) {
            if (serviceDestroyed) return;
            serviceDestroyed = true;
            
            if (!serviceSet) return;
        }
        
        if (root.getScopeAnnotation().equals(PerLookup.class)) {
            // Otherwise it is the scope responsible for the lifecycle
            root.dispose(service);
        }
        
        for (ServiceHandleImpl<?> subHandle : subHandles) {
            subHandle.destroy();
        }
    }
    
    public void addSubHandle(ServiceHandleImpl<?> subHandle) {
        subHandles.add(subHandle);
    }

}
