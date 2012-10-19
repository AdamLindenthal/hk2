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
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.utilities.AbstractActiveDescriptor;

/**
 * @author jwells
 * @param <T> The type of the constant
 *
 */
public class ConstantActiveDescriptor<T> extends AbstractActiveDescriptor<T> {
    /**
     * For serialization
     */
    private static final long serialVersionUID = 3663054975929743877L;
    
    private final T theOne;
    private final Long locatorId;
    
    /**
     * Creates a constant active descriptor with the given locator ID
     * 
     * @param theOne the object to create it from
     * @param locatorId The id of the locator this is being created for
     */
    public ConstantActiveDescriptor(T theOne, long locatorId) {
        super(new HashSet<Type>(), PerLookup.class, null, new HashSet<Annotation>(),
                DescriptorType.CLASS, 0, null, null);
        
        this.theOne = theOne;
        this.locatorId = new Long(locatorId);
    }
    
    /**
     * Constructor with more control over the fields of the descriptor
     * 
     * @param theOne
     * @param advertisedContracts
     * @param scope
     * @param name
     * @param qualifiers
     * @param ranking
     * @param locatorId
     */
    public ConstantActiveDescriptor(T theOne,
            Set<Type> advertisedContracts,
            Class<? extends Annotation> scope,
            String name,
            Set<Annotation> qualifiers,
            int ranking,
            Boolean proxy,
            long locatorId,
            Map<String, List<String>> metadata) {
        super(advertisedContracts, scope, name, qualifiers,
                DescriptorType.CLASS, ranking, proxy, metadata);
        if (theOne == null) throw new IllegalArgumentException();
        
        this.theOne = theOne;
        this.locatorId = new Long(locatorId);
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getImplementation()
     */
    @Override
    public String getImplementation() {
        return theOne.getClass().getName();
    }
    
    public Long getLocatorId() {
        return locatorId;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#getCache()
     */
    @Override
    public T getCache() {
        return theOne;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#isCacheSet()
     */
    @Override
    public boolean isCacheSet() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getImplementationClass()
     */
    @Override
    public Class<?> getImplementationClass() {
        return theOne.getClass();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#create(org.glassfish.hk2.api.ServiceHandle)
     */
    @Override
    public T create(ServiceHandle<?> root) {
        return theOne;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#dispose(java.lang.Object, org.glassfish.hk2.api.ServiceHandle)
     */
    @Override
    public void dispose(T instance) {
        // Do nothing
        
    }

}
