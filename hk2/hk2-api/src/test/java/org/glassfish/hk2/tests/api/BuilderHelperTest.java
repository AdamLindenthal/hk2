/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.hk2.tests.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.IndexedFilter;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.tests.contracts.AnotherContract;
import org.glassfish.hk2.tests.contracts.SomeContract;
import org.glassfish.hk2.tests.services.AnotherService;
import org.glassfish.hk2.utilities.AbstractActiveDescriptor;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class BuilderHelperTest {
	private final static String NAME = "hello";
	
	/**
	 * This predicate will only have an implementation and a contract
	 */
	@Test
	public void testSimpleFilter() {
		Descriptor predicate = BuilderHelper.link(BuilderHelperTest.class).to(SomeContract.class).build();
		
		Assert.assertNotNull(predicate);
		
		Assert.assertNotNull(predicate.getImplementation());
		Assert.assertEquals(predicate.getImplementation(), BuilderHelperTest.class.getName());
		
		Assert.assertNotNull(predicate.getAdvertisedContracts());
		Assert.assertTrue(predicate.getAdvertisedContracts().size() == 2);
		
		Assert.assertNotNull(predicate.getMetadata());
		Assert.assertTrue(predicate.getMetadata().size() == 0);
		
		Assert.assertNotNull(predicate.getQualifiers());
		Assert.assertTrue(predicate.getQualifiers().size() == 0);
		
		Assert.assertNull(predicate.getName());
		
		Assert.assertNull(predicate.getScope());
	}
	
	private final static String KEY_A = "keya";
	private final static String KEY_B = "keyb";
	private final static String VALUE_A = "valuea";
	private final static String VALUE_B1 = "valueb1";
	private final static String VALUE_B2 = "valueb2";
	
	/**
	 * This predicate will have two of those things which allow multiples and
	 * one thing of all other things
	 */
	@Test
	public void testFullFilter() {
		LinkedList<String> multiValue = new LinkedList<String>();
		multiValue.add(VALUE_B1);
		multiValue.add(VALUE_B2);
		
		
		Descriptor predicate = BuilderHelper.link(AnotherService.class.getName()).
				to(SomeContract.class).
				to(AnotherContract.class.getName()).
				in(Singleton.class.getName()).
				named(NAME).
				has(KEY_A, VALUE_A).
				has(KEY_B, multiValue).
				qualifiedBy(Red.class.getName()).
				build();
		
		Assert.assertNotNull(predicate);
		
		Assert.assertNotNull(predicate.getImplementation());
		Assert.assertEquals(predicate.getImplementation(), AnotherService.class.getName());
		
		HashSet<String> correctSet = new HashSet<String>();
		correctSet.add(SomeContract.class.getName());
		correctSet.add(AnotherContract.class.getName());
		correctSet.add(AnotherService.class.getName());
		
		Assert.assertNotNull(predicate.getAdvertisedContracts());
		Assert.assertTrue(predicate.getAdvertisedContracts().size() == 3);
		Assert.assertTrue(correctSet.containsAll(predicate.getAdvertisedContracts()));
		
		correctSet.clear();
		correctSet.add(Red.class.getName());
		correctSet.add(Named.class.getName());
		
		Assert.assertNotNull(predicate.getQualifiers());
		Assert.assertTrue(predicate.getQualifiers().size() == 2);  // One for @Named
		Assert.assertTrue(correctSet.containsAll(predicate.getQualifiers()));
		
		Assert.assertEquals(NAME, predicate.getName());
		
		Assert.assertNotNull(predicate.getScope());
		Assert.assertEquals(Singleton.class.getName(), predicate.getScope());
		
		Assert.assertNotNull(predicate.getMetadata());
		Assert.assertTrue(predicate.getMetadata().size() == 2);
		
		Map<String, List<String>> metadata = predicate.getMetadata();
		Set<String> keySet = metadata.keySet();
		
		correctSet.clear();
		correctSet.add(KEY_A);
		correctSet.add(KEY_B);
		
		Assert.assertTrue(correctSet.containsAll(keySet));
		
		List<String> aValue = metadata.get(KEY_A);
		Assert.assertNotNull(aValue);
		Assert.assertTrue(aValue.size() == 1);
		Assert.assertEquals(aValue.get(0), VALUE_A);
		
		List<String> bValue = metadata.get(KEY_B);
		Assert.assertNotNull(bValue);
		Assert.assertTrue(bValue.size() == 2);
		Assert.assertEquals(bValue.get(0), VALUE_B1);
		Assert.assertEquals(bValue.get(1), VALUE_B2);
	}
	
	/**
	 * Tests the all descriptor filter
	 */
	@Test
    public void testAllDescriptorFilter() {
        Descriptor predicate = BuilderHelper.link("empty").build();
    
        Filter allFilter = BuilderHelper.allFilter();
    
        Assert.assertTrue(allFilter.matches(predicate));
    }
	
	/**
     * Tests the all descriptor filter
     */
    @Test
    public void testConstantFilter() {
        FullDescriptorImpl c = new FullDescriptorImpl();
        ActiveDescriptor<FullDescriptorImpl> cDesc = BuilderHelper.createConstantDescriptor(c);
        Assert.assertNotNull(cDesc);
        
        Assert.assertEquals(FullDescriptorImpl.class.getName(), cDesc.getImplementation());
        Assert.assertEquals(FullDescriptorImpl.class, cDesc.getImplementationClass());
        
        Assert.assertEquals(2, cDesc.getAdvertisedContracts().size());
        Assert.assertTrue(cDesc.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(cDesc.getAdvertisedContracts().contains(MarkerInterface2.class.getName()));
        
        Assert.assertEquals(2, cDesc.getContractTypes().size());
        Assert.assertTrue(cDesc.getContractTypes().contains(FullDescriptorImpl.class));
        Assert.assertTrue(cDesc.getContractTypes().contains(MarkerInterface2.class));
        
        Assert.assertNull(cDesc.getName());
        
        Assert.assertEquals(PerLookup.class.getName(), cDesc.getScope());
        Assert.assertEquals(PerLookup.class, cDesc.getScopeAnnotation());
        
        Assert.assertEquals(3, cDesc.getQualifiers().size());
        Assert.assertTrue(cDesc.getQualifiers().contains(Red.class.getName()));
        Assert.assertTrue(cDesc.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(cDesc.getQualifiers().contains(Blue.class.getName()));
        
        Assert.assertEquals(3, cDesc.getQualifierAnnotations().size());
        boolean red = false;
        boolean green = false;
        boolean blue = false;
        
        for (Annotation anno : cDesc.getQualifierAnnotations()) {
            if (Red.class.equals(anno.annotationType())) red = true;
            if (Green.class.equals(anno.annotationType())) green = true;
            if (Blue.class.equals(anno.annotationType())) blue = true;
        }
        
        Assert.assertTrue(red);
        Assert.assertTrue(green);
        Assert.assertTrue(blue);
        
        Assert.assertEquals(DescriptorType.CLASS, cDesc.getDescriptorType());
        Assert.assertTrue(cDesc.getMetadata().isEmpty());
        Assert.assertNull(cDesc.getLoader());
        Assert.assertEquals(0, cDesc.getRanking());
        Assert.assertNull(cDesc.getBaseDescriptor());
        Assert.assertNull(cDesc.getServiceId());
        Assert.assertNull(cDesc.getLocatorId());
        Assert.assertTrue(cDesc.isReified());
        Assert.assertTrue(cDesc.getInjectees().isEmpty());
        
        Assert.assertEquals(c, cDesc.create(null));
        
        // Call the destroy, though it should do nothing
        cDesc.dispose(c);
        
        // Check the cache
        Assert.assertEquals(c, cDesc.getCache());
        Assert.assertTrue(cDesc.isCacheSet());
        
        String asString = cDesc.toString();
        Assert.assertTrue(asString.contains("implementation=org.glassfish.hk2.tests.api.FullDescriptorImpl"));
    }
    
    /**
     * Tests the contract filter
     */
    @Test
    public void testCreateContractFilter() {
        IndexedFilter iff = BuilderHelper.createContractFilter(Object.class.getName());
        
        Assert.assertEquals(Object.class.getName(), iff.getAdvertisedContract());
        Assert.assertNull(iff.getName());
        Assert.assertTrue(iff.matches(new DescriptorImpl()));
    }
    
    /**
     * Tests the contract filter
     */
    @Test
    public void testCreateNameFilter() {
        IndexedFilter iff = BuilderHelper.createNameFilter(NAME);
        
        Assert.assertEquals(NAME, iff.getName());
        Assert.assertNull(iff.getAdvertisedContract());
        Assert.assertTrue(iff.matches(new DescriptorImpl()));
    }
    
    /**
     * Tests the contract filter
     */
    @Test
    public void testCreateNameAndContractFilter() {
        IndexedFilter iff = BuilderHelper.createNameAndContractFilter(Object.class.getName(), NAME);
        
        Assert.assertEquals(NAME, iff.getName());
        Assert.assertEquals(Object.class.getName(), iff.getAdvertisedContract());
        Assert.assertTrue(iff.matches(new DescriptorImpl()));
    }
    
    /**
     * Tests the contract filter
     */
    @Test
    public void testDeepCopy() {
        DescriptorImpl a = new DescriptorImpl();
        a.addMetadata(KEY_A, VALUE_A);
        
        DescriptorImpl b = BuilderHelper.deepCopyDescriptor(a);
        
        Assert.assertEquals(a, b);
        
        // This is a bit tricky, make sure we have separated the metadata values!
        b.addMetadata(KEY_A, VALUE_B1);
        
        Assert.assertFalse(a.equals(b));
        
        Map<String, List<String>> aMeta= a.getMetadata();
        List<String> keyAValues = aMeta.get(KEY_A);
        Assert.assertEquals(1, keyAValues.size());
        
        Assert.assertEquals(VALUE_A, keyAValues.get(0));
    }
    
    /**
     * Tests that a class with a complex hierarchy is analyzed properly
     */
    @Test
    public void testComplexHierarchyClass() {
        Descriptor d = BuilderHelper.createDescriptorFromClass(ComplexHierarchy.class);
        
        Set<String> contracts = d.getAdvertisedContracts();
        
        Assert.assertTrue(contracts.contains(ComplexHierarchy.class.getName()));
        Assert.assertTrue(contracts.contains(MarkerInterfaceImpl.class.getName()));
        Assert.assertTrue(contracts.contains(MarkerInterface2.class.getName()));
        Assert.assertTrue(contracts.contains(ParameterizedInterface.class.getName()));
        
        Assert.assertEquals(4, contracts.size());
    }
    
    /**
     * Tests that an object with a complex hierarchy is analyzed properly
     */
    @Test
    public void testComplexHierarchyObject() {
        AbstractActiveDescriptor<ComplexHierarchy> d = BuilderHelper.createConstantDescriptor(new ComplexHierarchy());
        
        Set<String> contracts = d.getAdvertisedContracts();
        
        Assert.assertTrue(contracts.contains(ComplexHierarchy.class.getName()));
        Assert.assertTrue(contracts.contains(MarkerInterfaceImpl.class.getName()));
        Assert.assertTrue(contracts.contains(MarkerInterface2.class.getName()));
        Assert.assertTrue(contracts.contains(ParameterizedInterface.class.getName()));
        
        Assert.assertEquals(4, contracts.size());
        
        Set<Type> contractsAsTypes = d.getContractTypes();
        
        int lcv = 0;
        for (Type contractAsType : contractsAsTypes) {
            // This tests that this is an ordered iterator
            switch(lcv) {
            case 0:
                Assert.assertEquals(ComplexHierarchy.class, contractAsType);
                break;
            case 1:
                Assert.assertEquals(MarkerInterfaceImpl.class, contractAsType);
                break;
            case 2:
                Assert.assertEquals(MarkerInterface2.class, contractAsType);
                break;
            case 3:
                ParameterizedType pt = (ParameterizedType) contractAsType;
                
                Assert.assertEquals(ParameterizedInterface.class, pt.getRawType());
                Assert.assertEquals(String.class, pt.getActualTypeArguments()[0]);
                break;
            default:
                Assert.fail("Too many types: " + contractAsType);
            }
            
            lcv++;
        }
    }
}
