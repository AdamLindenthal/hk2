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

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import javax.inject.Singleton;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Context;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.reflection.Logger;

/**
 * @author jwells
 *
 */
@Singleton
public class SingletonContext implements Context<Singleton> {
    private int generationNumber = Integer.MIN_VALUE;
    private final ServiceLocatorImpl locator;
    
    /* package */ SingletonContext(ServiceLocatorImpl impl) {
        locator = impl;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#getScope()
     */
    @Override
    public Class<? extends Annotation> getScope() {
        return Singleton.class;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#findOrCreate(org.glassfish.hk2.api.ActiveDescriptor, org.glassfish.hk2.api.ServiceHandle)
     */
    @Override
    public <T> T findOrCreate(ActiveDescriptor<T> activeDescriptor,
            ServiceHandle<?> root) {
        if (activeDescriptor.isCacheSet()) return activeDescriptor.getCache();
        
        synchronized (activeDescriptor) {
            if (activeDescriptor.isCacheSet()) return activeDescriptor.getCache();
            
            T t = activeDescriptor.create(root);
            activeDescriptor.setCache(t);
        
            if (activeDescriptor instanceof SystemDescriptor) {
                ((SystemDescriptor<T>) activeDescriptor).setSingletonGeneration(generationNumber++);
            }
            
            return t;
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#find(org.glassfish.hk2.api.Descriptor)
     */
    @Override
    public boolean containsKey(ActiveDescriptor<?> descriptor) {
        return descriptor.isCacheSet();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#isActive()
     */
    @Override
    public boolean isActive() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#supportsNullCreation()
     */
    @Override
    public boolean supportsNullCreation() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Context#supportsNullCreation()
     */
    @SuppressWarnings("unchecked")
    @Override
    public void shutdown() {
        List<ActiveDescriptor<?>> all = locator.getDescriptors(BuilderHelper.allFilter());
        
        long myLocatorId = locator.getLocatorId();
        
        TreeSet<SystemDescriptor<Object>> singlesOnly = new TreeSet<SystemDescriptor<Object>>(
                new GenerationComparator());
        for (ActiveDescriptor<?> one : all) {
            if (one.getScope() == null || !one.getScope().equals(Singleton.class.getName())) continue;
            
            if (!one.isCacheSet()) continue;
            
            if (one.getLocatorId() == null || one.getLocatorId().longValue() != myLocatorId) continue;
            
            SystemDescriptor<Object> oneAsObject = (SystemDescriptor<Object>) one;
            
            singlesOnly.add(oneAsObject);
        }
        
        for (SystemDescriptor<Object> one : singlesOnly) {
            destroyOne(one);
        }
    }
    
    /**
     * Release one system descriptor
     * 
     * @param one The descriptor to release (may not be null).  Further, the cache MUST be set
     */
    @SuppressWarnings("unchecked")
    public void destroyOne(ActiveDescriptor<?> one) {
        if (!one.isCacheSet()) return;
        
        Object value = one.getCache();
        one.releaseCache();
        
        try {
            ((ActiveDescriptor<Object>) one).dispose(value);
        }
        catch (Throwable th) {
            Logger.getLogger().debug("SingletonContext", "releaseOne", th);
        }
        
    }
    
    private static class GenerationComparator implements Comparator<SystemDescriptor<Object>> {

        @Override
        public int compare(SystemDescriptor<Object> o1,
                SystemDescriptor<Object> o2) {
            if (o1.getSingletonGeneration() > o2.getSingletonGeneration()) {
                return -1;
            }
            if (o1.getSingletonGeneration() == o2.getSingletonGeneration()) {
                return 0;
            }
            
            return 1;
        }
        
    }
}
