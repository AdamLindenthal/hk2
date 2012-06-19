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
package org.glassfish.hk2.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.HK2Loader;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.utilities.AbstractActiveDescriptor;
import org.glassfish.hk2.utilities.ActiveDescriptorBuilder;

/**
 * @author jwells
 *
 */
public class ActiveDescriptorBuilderImpl implements ActiveDescriptorBuilder {
    private String name;
    private final HashSet<Type> contracts = new HashSet<Type>();
    private Class<? extends Annotation> scope = PerLookup.class;
    private final HashSet<Annotation> qualifiers = new HashSet<Annotation>();
    private final HashMap<String, List<String>> metadatas = new HashMap<String, List<String>>();
    private final Class<?> implementation;
    private HK2Loader loader = null;
    private int rank = 0;
    
    public ActiveDescriptorBuilderImpl(Class<?> implementation) {
        this.implementation = implementation;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#named(java.lang.String)
     */
    @Override
    public ActiveDescriptorBuilder named(String name) throws IllegalArgumentException {
        this.name = name;
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#to(java.lang.reflect.Type)
     */
    @Override
    public ActiveDescriptorBuilder to(Type contract) throws IllegalArgumentException {
        if (contract != null) contracts.add(contract);
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#in(java.lang.Class)
     */
    @Override
    public ActiveDescriptorBuilder in(Class<? extends Annotation> scope)
            throws IllegalArgumentException {
        this.scope = scope;
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#qualifiedBy(java.lang.annotation.Annotation)
     */
    @Override
    public ActiveDescriptorBuilder qualifiedBy(Annotation annotation)
            throws IllegalArgumentException {
        if (annotation != null) qualifiers.add(annotation);
        
        return this;
    }
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#has(java.lang.String, java.lang.String)
     */
    @Override
    public ActiveDescriptorBuilder has(String key, String value)
            throws IllegalArgumentException {
        return has(key, Collections.singletonList(value));
    }
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#has(java.lang.String, java.util.List)
     */
    @Override
    public ActiveDescriptorBuilder has(String key, List<String> values)
            throws IllegalArgumentException {
        if (key == null || values == null || values.size() <= 0) {
            throw new IllegalArgumentException();
        }
        
        metadatas.put(key, values);
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#ofRank(int)
     */
    @Override
    public ActiveDescriptorBuilder ofRank(int rank) {
        this.rank = rank;
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#andLoadWith(org.glassfish.hk2.api.HK2Loader)
     */
    @Override
    public ActiveDescriptorBuilder andLoadWith(HK2Loader loader)
            throws IllegalArgumentException {
        this.loader = loader;
        
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#build()
     */
    @Override
    public AbstractActiveDescriptor<?> build() throws IllegalArgumentException {
        return new BuiltActiveDescriptor<Object>(
                implementation,
                contracts,
                scope,
                name,
                qualifiers,
                DescriptorType.CLASS,
                rank,
                metadatas,
                loader);
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.utilities.ActiveDescriptorBuilder#buildFactory()
     */
    @Override
    public AbstractActiveDescriptor<?> buildFactory() throws IllegalArgumentException {
        return new BuiltActiveDescriptor<Object>(
                implementation,
                contracts,
                scope,
                name,
                qualifiers,
                DescriptorType.PROVIDE_METHOD,
                rank,
                metadatas,
                loader);
    }
    
    private static class BuiltActiveDescriptor<T> extends AbstractActiveDescriptor<T> {
        /**
         * For serialization
         */
        private static final long serialVersionUID = 2434137639270026082L;
        
        private final Class<?> implementationClass;
        
        private BuiltActiveDescriptor(Class<?> implementationClass,
                Set<Type> advertisedContracts,
                Class<? extends Annotation> scope,
                String name,
                Set<Annotation> qualifiers,
                DescriptorType descriptorType,
                int ranking,
                Map<String, List<String>> metadata,
                HK2Loader loader) {
            super(advertisedContracts,
                    scope,
                    name,
                    qualifiers,
                    descriptorType,
                    ranking,
                    metadata);
            
            super.setReified(false);
            super.setLoader(loader);
            
            this.implementationClass = implementationClass;
            super.setImplementation(implementationClass.getName());
        }
        
        /* (non-Javadoc)
         * @see org.glassfish.hk2.api.ActiveDescriptor#getImplementationClass()
         */
        @Override
        public Class<?> getImplementationClass() {
            return implementationClass;
        }

        /* (non-Javadoc)
         * @see org.glassfish.hk2.api.ActiveDescriptor#create(org.glassfish.hk2.api.ServiceHandle)
         */
        @Override
        public T create(ServiceHandle<?> root) {
            throw new AssertionError("Should not be called directly");
        }
        
    }
    

}
