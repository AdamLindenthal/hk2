/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.hk2.utilities;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.IndexedFilter;
import org.glassfish.hk2.api.Metadata;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.internal.ConstantActiveDescriptor;
import org.glassfish.hk2.internal.DescriptorBuilderImpl;
import org.glassfish.hk2.internal.IndexedFilterImpl;
import org.glassfish.hk2.internal.StarFilter;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.jvnet.hk2.annotations.Contract;

/**
 * This class is used to generate DescriptorBuilders to be used
 * as a simple mechanism to create a Filter or Descriptor.
 */
public class BuilderHelper {
    /**
     * Returns an indexed filter that will return all descriptors that
     * have contract as an advertised contract
     * 
     * @param contract The advertised contract to look for
     * @return The indexed filter that can be used to calls to ServiceLocator methods
     */
    public static IndexedFilter createContractFilter(String contract) {
        return new IndexedFilterImpl(contract, null);
    }
    
    /**
     * Returns an indexed filter that will return all descriptors that
     * have the given name
     * 
     * @param name The name to look for
     * @return The indexed filter that can be used to calls to ServiceLocator methods
     */
    public static IndexedFilter createNameFilter(String name) {
        return new IndexedFilterImpl(null, name);
    }
    
    /**
     * Returns an indexed filter that will return all descriptors that
     * have the given name and given contract
     * 
     * @param contract The advertised contract to look for
     * @param name The name to look for
     * @return The indexed filter that can be used to calls to ServiceLocator methods
     */
    public static IndexedFilter createNameAndContractFilter(String contract, String name) {
        return new IndexedFilterImpl(contract, name);
    }
    
    /**
     * Returns a filter of type Descriptor that matches
     * all descriptors
     * 
     * @return A filter that matches all descriptors
     */
    public static Filter allFilter() {
      return StarFilter.getDescriptorFilter();
    }
	
	/**
     * This method links an implementation class with a {@link DescriptorBuilder}, to
     * be used to further build the {@link Descriptor}.
     * 
     * @param implementationClass The fully qualified name of the implementation
     * class to be associated with the DescriptorBuilder.
     * @param addToContracts if true, this implementation class will be added to the
     * list of contracts
     * 
     * @return A {@link DescriptorBuilder} that can be used to further build up the
     * {@link Descriptor}
     * @throws IllegalArgumentException if implementationClass is null
     */
    public static DescriptorBuilder link(String implementationClass, boolean addToContracts) throws IllegalArgumentException {
        if (implementationClass == null) throw new IllegalArgumentException();
        
        return new DescriptorBuilderImpl(implementationClass, addToContracts);
    }
	
	/**
	 * This method links an implementation class with a {@link DescriptorBuilder}, to
	 * be used to further build the {@link Descriptor}.  This method will automatically
	 * put the implementationClass into the list of advertised contracts.
	 * 
	 * @param implementationClass The fully qualified name of the implementation
	 * class to be associated with the PredicateBuilder.
	 * 
	 * @return A {@link DescriptorBuilder} that can be used to further build up the
	 * {@link Descriptor}
	 * @throws IllegalArgumentException if implementationClass is null
	 */
	public static DescriptorBuilder link(String implementationClass) throws IllegalArgumentException {
	    return link(implementationClass, true);
	}
	
	/**
     * This method links an implementation class with a {@link DescriptorBuilder}, to
     * be used to further build the {@link Descriptor}
     * 
     * @param implementationClass The implementation class to be associated
     * with the {@link DescriptorBuilder}.
     * @param addToContracts true if this impl class should be automatically added to
     * the list of contracts
     * @return A {@link DescriptorBuilder} that can be used to further build up the
     * {@link Descriptor}
     * @throws IllegalArgumentException if implementationClass is null
     */
    public static DescriptorBuilder link(Class<?> implementationClass, boolean addToContracts) throws IllegalArgumentException {
        if (implementationClass == null) throw new IllegalArgumentException();
        
        DescriptorBuilder builder = link(implementationClass.getName(), addToContracts);
        
        return builder;
    }
	
	/**
	 * This method links an implementation class with a {@link DescriptorBuilder}, to
	 * be used to further build the {@link Descriptor}.
	 * 
	 * @param implementationClass The implementation class to be associated
	 * with the {@link DescriptorBuilder}.
	 * @return A {@link DescriptorBuilder} that can be used to further build up the
	 * {@link Descriptor}
	 * @throws IllegalArgumentException if implementationClass is null
	 */
	public static DescriptorBuilder link(Class<?> implementationClass) throws IllegalArgumentException {
	    if (implementationClass == null) throw new IllegalArgumentException();
	    
	    boolean isFactory = (Factory.class.isAssignableFrom(implementationClass));
	    
	    DescriptorBuilder db = link(implementationClass, !isFactory);
	    
	    return db;
	}
    
    /**
     * This creates a descriptor that will always return the given object.  The
     * set of types in the advertised contracts will contain the class of the
     * constant along with:<UL>
     * <LI>Any superclass of the constant marked with {@link Contract}</LI>
     * <LI>Any interface of the constant marked with {@link Contract}</LI>
     * </UL>
     * 
     * @param constant The non-null constant that should always be returned from
     * the create method of this ActiveDescriptor.  
     * @return The descriptor returned can be used in calls to
     * DynamicConfiguration.addActiveDescriptor
     * @throws IllegalArgumentException if constant is null
     */
    public static <T> AbstractActiveDescriptor<T> createConstantDescriptor(T constant) {
        if (constant == null) throw new IllegalArgumentException();
        
        Annotation scope =
                ReflectionHelper.getScopeAnnotationFromObject(constant);
        Class<? extends Annotation> scopeClass = (scope == null) ? PerLookup.class :
            scope.annotationType();
        
        Set<Annotation> qualifiers =
                ReflectionHelper.getQualifiersFromObject(constant);
        
        Map<String, List<String>> metadata = new HashMap<String, List<String>>();
        if (scope != null) {
            getMetadataValues(scope, metadata);
        }
        
        for (Annotation qualifier : qualifiers) {
            getMetadataValues(qualifier, metadata);
        }
        
        return new ConstantActiveDescriptor<T>(
                constant,
                ReflectionHelper.getAdvertisedTypesFromObject(constant, Contract.class),
                scopeClass,
                ReflectionHelper.getName(constant.getClass()),
                qualifiers,
                metadata);
    }
    
    /**
     * This returns a DescriptorImpl based on the given class.  The returned
     * descriptor will include the class itself as an advertised contract and
     * all implemented interfaces that are marked &#64;Contract
     * 
     * @param clazz The class to analyze
     * @return The DescriptorImpl corresponding to this class
     */
    public static DescriptorImpl createDescriptorFromClass(Class<?> clazz) {
        if (clazz == null) return new DescriptorImpl();
        
        Set<String> contracts = ReflectionHelper.getContractsFromClass(clazz, Contract.class);
        String name = ReflectionHelper.getName(clazz);
        String scope = ReflectionHelper.getScopeFromClass(clazz, PerLookup.class).getName();
        Set<String> qualifiers = ReflectionHelper.getQualifiersFromClass(clazz);
        DescriptorType type = DescriptorType.CLASS;
        if (Factory.class.isAssignableFrom(clazz)) {
            type = DescriptorType.FACTORY;
        }
        
        // TODO:  Can we get metadata from @Service?
        return new DescriptorImpl(
                contracts,
                name,
                scope,
                clazz.getName(),
                new HashMap<String, List<String>>(),
                qualifiers,
                type,
                null,
                0,
                null,
                null,
                null);
    }
	
    /**
     * Makes a deep copy of the incoming descriptor
     * 
     * @param copyMe The descriptor to copy
     * @return A new descriptor with all fields copied
     */
	public static DescriptorImpl deepCopyDescriptor(Descriptor copyMe) {
	    return new DescriptorImpl(copyMe);
	}
	
	/**
	 * This is a helper method that gets the metadata values from the
	 * {@link Metadata} annotations found in an annotation.
	 *  
	 * @param annotation The annotation to find {@link Metadata} values
	 * from.  May not be null.
	 * @param metadata A non-null metadata map.  The values found in the
	 * annotation will be added to this metadata map
	 * @throws IllegalArgumentException if annotation or metadata is null
	 * @throws MultiException if there was an error invoking the methods of the annotation
	 */
	public static void getMetadataValues(Annotation annotation, Map<String, List<String>> metadata) {
	    if (annotation == null || metadata == null) {
	        throw new IllegalArgumentException();
	    }
	    
	    Class<? extends Annotation> annotationClass = annotation.annotationType();
	    Method annotationMethods[] = annotationClass.getDeclaredMethods();
	    for (Method annotationMethod : annotationMethods) {
	        Metadata metadataAnno = annotationMethod.getAnnotation(Metadata.class);
	        if (metadataAnno == null) continue;
	        
	        String key = metadataAnno.value();
	        
	        Object addMe;
	        try {
	            addMe = ReflectionHelper.invoke(annotation, annotationMethod, new Object[0]);
	        }
	        catch (Throwable th) {
	            throw new MultiException(th);
	        }
	        
	        if (addMe == null) continue;
	        
	        String addMeString;
	        if (addMe instanceof Class) {
	            addMeString = ((Class<?>) addMe).getName();
	        }
	        else {
	            addMeString = addMe.toString();
	        }
	        
	        ReflectionHelper.addMetadata(metadata, key, addMeString);
	    }
	}
}
