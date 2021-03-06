/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.hk2.extras.interception.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.IndexedFilter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.extras.interception.Intercepted;
import org.glassfish.hk2.extras.interception.Interceptor;
import org.glassfish.hk2.extras.interception.InterceptorOrderingService;

/**
 * A default implementation of the interception service using annotation to
 * denote services that are to be intercepted and other annotations to match
 * methods or constructors to interceptors
 * 
 * @author jwells
 */
@Singleton
public class DefaultInterceptionService implements InterceptionService {
    private final static IndexedFilter METHOD_FILTER = new IndexedFilter() {

        @Override
        public boolean matches(Descriptor d) {
            return d.getQualifiers().contains(Interceptor.class.getName());
        }

        @Override
        public String getAdvertisedContract() {
            return MethodInterceptor.class.getName();
        }

        @Override
        public String getName() {
            return null;
        }
        
    };
    
    private final static IndexedFilter CONSTRUCTOR_FILTER = new IndexedFilter() {

        @Override
        public boolean matches(Descriptor d) {
            return d.getQualifiers().contains(Interceptor.class.getName());
        }

        @Override
        public String getAdvertisedContract() {
            return ConstructorInterceptor.class.getName();
        }

        @Override
        public String getName() {
            return null;
        }
        
    };
    
    @Inject
    private ServiceLocator locator;
    
    @Inject
    private IterableProvider<InterceptorOrderingService> orderers;

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.InterceptionService#getDescriptorFilter()
     */
    @Override
    public Filter getDescriptorFilter() {
        return new Filter() {

            @Override
            public boolean matches(Descriptor d) {
                return d.getQualifiers().contains(Intercepted.class.getName());
            }
            
        };
    }
    
    private List<MethodInterceptor> orderMethods(Method method, List<MethodInterceptor> current) {
        List<MethodInterceptor> retVal = current;
        
        for (InterceptorOrderingService orderer : orderers) {
            List<MethodInterceptor> given = Collections.unmodifiableList(retVal);
            List<MethodInterceptor> returned;
            try {
                returned = orderer.modifyMethodInterceptors(method, given);
            }
            catch (Throwable th) {
                returned = null;
            }
            
            if (returned != null && returned != given) {
                retVal = new ArrayList<MethodInterceptor>(returned);
            }
        }
        
        return retVal;
    }
    
    private List<ConstructorInterceptor> orderConstructors(Constructor<?> constructor, List<ConstructorInterceptor> current) {
        List<ConstructorInterceptor> retVal = current;
        
        for (InterceptorOrderingService orderer : orderers) {
            List<ConstructorInterceptor> given = Collections.unmodifiableList(retVal);
            List<ConstructorInterceptor> returned;
            try {
                returned = orderer.modifyConstructorInterceptors(constructor, given);
            }
            catch (Throwable th) {
                returned = null;
            }
            
            if (returned != null && returned != given) {
                retVal = new ArrayList<ConstructorInterceptor>(returned);
            }
        }
        
        return retVal;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.InterceptionService#getMethodInterceptors(java.lang.reflect.Method)
     */
    @Override
    public List<MethodInterceptor> getMethodInterceptors(Method method) {
        HashSet<String> allBindings = ReflectionUtilities.getAllBindingsFromMethod(method);
        
        List<ServiceHandle<?>> allInterceptors = locator.getAllServiceHandles(METHOD_FILTER);
        
        List<MethodInterceptor> retVal = new ArrayList<MethodInterceptor>(allInterceptors.size());
        
        for (ServiceHandle<?> handle : allInterceptors) {
            ActiveDescriptor<?> ad = handle.getActiveDescriptor();
            if (!ad.isReified()) {
                ad = locator.reifyDescriptor(ad);
            }
            
            Class<?> interceptorClass = ad.getImplementationClass();
            
            HashSet<String> allInterceptorBindings = ReflectionUtilities.getAllBindingsFromClass(interceptorClass);
            
            boolean found = false;
            for (String interceptorBinding : allInterceptorBindings) {
                if (allBindings.contains(interceptorBinding)) {
                    found = true;
                    break;
                }
            }
            if (!found) continue;
            
            MethodInterceptor interceptor = (MethodInterceptor) handle.getService();
            if (interceptor != null) {
                retVal.add(interceptor);
            }
        }
        
        retVal = orderMethods(method, retVal);
        return retVal;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.InterceptionService#getConstructorInterceptors(java.lang.reflect.Constructor)
     */
    @Override
    public List<ConstructorInterceptor> getConstructorInterceptors(
            Constructor<?> constructor) {
        HashSet<String> allBindings = ReflectionUtilities.getAllBindingsFromConstructor(constructor);
        
        List<ServiceHandle<?>> allInterceptors = locator.getAllServiceHandles(CONSTRUCTOR_FILTER);
        
        List<ConstructorInterceptor> retVal = new ArrayList<ConstructorInterceptor>(allInterceptors.size());
        
        for (ServiceHandle<?> handle : allInterceptors) {
            ActiveDescriptor<?> ad = handle.getActiveDescriptor();
            if (!ad.isReified()) {
                ad = locator.reifyDescriptor(ad);
            }
            
            Class<?> interceptorClass = ad.getImplementationClass();
            
            HashSet<String> allInterceptorBindings = ReflectionUtilities.getAllBindingsFromClass(interceptorClass);
            
            boolean found = false;
            for (String interceptorBinding : allInterceptorBindings) {
                if (allBindings.contains(interceptorBinding)) {
                    found = true;
                    break;
                }
            }
            if (!found) continue;
            
            ConstructorInterceptor interceptor = (ConstructorInterceptor) handle.getService();
            if (interceptor != null) {
                retVal.add(interceptor);
            }
        }
        
        retVal = orderConstructors(constructor, retVal);
        return retVal;
    }
}
