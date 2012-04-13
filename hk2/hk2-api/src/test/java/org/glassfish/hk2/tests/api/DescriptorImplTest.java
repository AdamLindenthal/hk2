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
package org.glassfish.hk2.tests.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

import junit.framework.Assert;


import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class DescriptorImplTest {
    /**
     * Tests an empty descriptor
     */
    @Test
    public void testEmptyDescriptor() {
        DescriptorImpl desc = new DescriptorImpl();
        
        Assert.assertNull(desc.getImplementation());
        
        Assert.assertNotNull(desc.getAdvertisedContracts());
        Assert.assertTrue(desc.getAdvertisedContracts().isEmpty());
        
        Assert.assertEquals(PerLookup.class.getName(), desc.getScope());
        
        Assert.assertNull(desc.getName());
        
        Assert.assertNotNull(desc.getQualifiers());
        Assert.assertTrue(desc.getQualifiers().isEmpty());
        
        Assert.assertEquals(DescriptorType.CLASS, desc.getDescriptorType());
        
        Assert.assertNotNull(desc.getMetadata());
        Assert.assertTrue(desc.getMetadata().isEmpty());
        
        Assert.assertNull(desc.getLoader());
        
        Assert.assertEquals(0, desc.getRanking());
        
        Assert.assertNull(desc.getBaseDescriptor());
        
        Assert.assertNull(desc.getServiceId());
        
        Assert.assertNull(desc.getLocatorId());
        
        String asString = desc.toString();
        Assert.assertTrue(asString.contains("implementation=null"));
        Assert.assertTrue(asString.contains("contracts={}"));
        Assert.assertTrue(asString.contains("scope=org.glassfish.hk2.api.PerLookup"));
        Assert.assertTrue(asString.contains("metadata=[]"));
        Assert.assertTrue(asString.contains("descriptorType=CLASS"));
    }
    
    private static void testBasicFullDescriptor(DescriptorImpl full) {
        Assert.assertEquals(FullDescriptorImpl.class.getName(), full.getImplementation());
        
        Assert.assertEquals(2, full.getAdvertisedContracts().size());
        Assert.assertTrue(full.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(full.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
        
        Assert.assertEquals(Singleton.class.getName(), full.getScope());
        
        Assert.assertEquals(FullDescriptorImpl.FULL_NAME, full.getName());
        
        Assert.assertEquals(2, full.getQualifiers().size());
        Assert.assertTrue(full.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(full.getQualifiers().contains(Blue.class.getName()));
        
        Assert.assertEquals(DescriptorType.FACTORY, full.getDescriptorType());
        
        Assert.assertNotNull(full.getMetadata());
        Map<String, List<String>> metadata = full.getMetadata();
        
        Assert.assertEquals(2, metadata.size());
        
        List<String> key1Values = metadata.get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNotNull(key1Values);
        Assert.assertEquals(1, key1Values.size());
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE1));
        
        List<String> key2Values = metadata.get(FullDescriptorImpl.FULL_KEY2);
        Assert.assertNotNull(key2Values);
        Assert.assertEquals(2, key2Values.size());
        Assert.assertTrue(key2Values.contains(FullDescriptorImpl.FULL_VALUE1));
        Assert.assertTrue(key2Values.contains(FullDescriptorImpl.FULL_VALUE2));
        
        Assert.assertNotNull(full.getLoader());
        
        Assert.assertEquals(FullDescriptorImpl.FULL_INITIAL_RANK, full.getRanking());
        
        Assert.assertNotNull(full.getBaseDescriptor());
        
        Assert.assertEquals(FullDescriptorImpl.FULL_INITIAL_SID, full.getServiceId());
        
        Assert.assertEquals(FullDescriptorImpl.FULL_INITIAL_LID, full.getLocatorId());
        
        String asString = full.toString();
        Assert.assertTrue(asString.contains("implementation=org.glassfish.hk2.tests.api.FullDescriptorImpl"));
        Assert.assertTrue(asString.contains("contracts={org.glassfish.hk2.tests.api.FullDescriptorImpl,org.glassfish.hk2.tests.api.MarkerInterface}"));
        Assert.assertTrue(asString.contains("scope=javax.inject.Singleton"));
        Assert.assertTrue(asString.contains("name=Full"));
        Assert.assertTrue(asString.contains("descriptorType=FACTORY"));
        
    }
    
    /**
     * Tests an full descriptor
     */
    @Test
    public void testFullDescriptor() {
        testBasicFullDescriptor(new FullDescriptorImpl());
    }
    
    /**
     * Tests copy of a full descriptor
     */
    @Test
    public void testCopyOfFullDescriptor() {
        testBasicFullDescriptor(new DescriptorImpl(new FullDescriptorImpl()));
    }
    
    /**
     * Tests add a contract
     */
    @Test
    public void testAddAContract() {
        DescriptorImpl desc = new FullDescriptorImpl();
        
        desc.addAdvertisedContract(null);
        Assert.assertEquals(2, desc.getAdvertisedContracts().size());
        Assert.assertTrue(desc.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
        
        desc.addAdvertisedContract(MarkerInterface2.class.getName());
        
        Assert.assertEquals(3, desc.getAdvertisedContracts().size());
        Assert.assertTrue(desc.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface2.class.getName()));
    }
    
    /**
     * Tests remove a contract
     */
    @Test
    public void testRemoveAContract() {
        DescriptorImpl desc = new FullDescriptorImpl();
        
        Assert.assertFalse(desc.removeAdvertisedContract(null));
        Assert.assertEquals(2, desc.getAdvertisedContracts().size());
        Assert.assertTrue(desc.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
        
        Assert.assertFalse(desc.removeAdvertisedContract(MarkerInterface2.class.getName()));
        Assert.assertEquals(2, desc.getAdvertisedContracts().size());
        Assert.assertTrue(desc.getAdvertisedContracts().contains(FullDescriptorImpl.class.getName()));
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
        
        Assert.assertTrue(desc.removeAdvertisedContract(FullDescriptorImpl.class.getName()));
        Assert.assertEquals(1, desc.getAdvertisedContracts().size());
        Assert.assertTrue(desc.getAdvertisedContracts().contains(MarkerInterface.class.getName()));
    }
    
    /**
     * Tests add a qualifier
     */
    @Test
    public void testAddAQualifier() {
        DescriptorImpl desc = new FullDescriptorImpl();
        
        desc.addQualifier(null);
        Assert.assertEquals(2, desc.getQualifiers().size());
        Assert.assertTrue(desc.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(desc.getQualifiers().contains(Blue.class.getName()));
        
        desc.addQualifier(Red.class.getName());
        
        Assert.assertEquals(3, desc.getQualifiers().size());
        Assert.assertTrue(desc.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(desc.getQualifiers().contains(Blue.class.getName()));
        Assert.assertTrue(desc.getQualifiers().contains(Red.class.getName()));
    }
    
    /**
     * Tests remove a qualifier
     */
    @Test
    public void testRemoveAQualifier() {
        DescriptorImpl desc = new FullDescriptorImpl();
        
        Assert.assertFalse(desc.removeQualifier(null));
        Assert.assertEquals(2, desc.getQualifiers().size());
        Assert.assertTrue(desc.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(desc.getQualifiers().contains(Blue.class.getName()));
        
        Assert.assertFalse(desc.removeQualifier("purple"));
        Assert.assertEquals(2, desc.getQualifiers().size());
        Assert.assertTrue(desc.getQualifiers().contains(Green.class.getName()));
        Assert.assertTrue(desc.getQualifiers().contains(Blue.class.getName()));
        
        Assert.assertTrue(desc.removeQualifier(Blue.class.getName()));
        Assert.assertEquals(1, desc.getQualifiers().size());
        Assert.assertTrue(desc.getQualifiers().contains(Green.class.getName()));
    }
    
    private final static String ANY = "could.be.Anything";
    
    /**
     * Tests setting the implementation
     */
    @Test
    public void testSetImplementation() {
        DescriptorImpl desc = new DescriptorImpl();
        
        desc.setImplementation(ANY);
        
        Assert.assertEquals(ANY, desc.getImplementation());
    }
    
    /**
     * Tests setting the name
     */
    @Test
    public void testSetName() {
        DescriptorImpl desc = new DescriptorImpl();
        
        desc.setName(ANY);
        
        Assert.assertEquals(ANY, desc.getName());
    }
    
    /**
     * Tests setting the descriptor type
     */
    @Test
    public void testSetDescriptorType() {
        DescriptorImpl desc = new DescriptorImpl();
        
        try {
            desc.setDescriptorType(null);
            Assert.fail("Should not be able to set the descriptor type to null");
        }
        catch (IllegalArgumentException iae) {
        }
        
        desc.setDescriptorType(DescriptorType.FACTORY);
        Assert.assertEquals(DescriptorType.FACTORY, desc.getDescriptorType());
        
        desc.setDescriptorType(DescriptorType.CLASS);
        Assert.assertEquals(DescriptorType.CLASS, desc.getDescriptorType());
    }
    
    /**
     * Tests setting the loader
     */
    @Test
    public void testSetLoader() {
        DescriptorImpl desc = new DescriptorImpl();
        
        HK2LoaderImpl loader = new HK2LoaderImpl();
        desc.setLoader(loader);
        Assert.assertEquals(loader, desc.getLoader());
        
        desc.setLoader(null);
        Assert.assertNull(desc.getLoader());
    }
    
    /**
     * Tests setting the loader
     */
    @Test
    public void testSetRanking() {
        DescriptorImpl desc = new DescriptorImpl();
        
        Assert.assertEquals(0, desc.setRanking(1));
        Assert.assertEquals(1, desc.setRanking(-1));
        Assert.assertEquals(-1, desc.getRanking());
    }
    
    /**
     * Tests setting the base descriptor
     */
    @Test
    public void testSetBase() {
        Descriptor base = new FullDescriptorImpl();
        DescriptorImpl desc = new DescriptorImpl();
        
        desc.setBaseDescriptor(base);
        Assert.assertEquals(base, desc.getBaseDescriptor());
        
        desc.setBaseDescriptor(null);
        Assert.assertNull(desc.getBaseDescriptor());
    }
    
    /**
     * Tests setting the service id
     */
    @Test
    public void testSetServiceId() {
        DescriptorImpl desc = new DescriptorImpl();
        
        desc.setServiceId(1L);
        Assert.assertEquals(new Long(1L), desc.getServiceId());
        
        desc.setServiceId(null);
        Assert.assertNull(desc.getServiceId());
    }
    
    /**
     * Tests setting the locator id
     */
    @Test
    public void testSetLocatorId() {
        DescriptorImpl desc = new DescriptorImpl();
        
        desc.setLocatorId(2L);
        Assert.assertEquals(new Long(2L), desc.getLocatorId());
        
        desc.setLocatorId(null);
        Assert.assertNull(desc.getLocatorId());
    }
    
    /**
     * Tests adding metadata
     */
    @Test
    public void testAddMetadata() {
        FullDescriptorImpl desc = new FullDescriptorImpl();
        testBasicFullDescriptor(desc);
        
        desc.addMetadata(null, null);
        List<String> key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNotNull(key1Values);
        Assert.assertEquals(1, key1Values.size());
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE1));
        
        desc.addMetadata(FullDescriptorImpl.FULL_KEY1, null);
        key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNotNull(key1Values);
        Assert.assertEquals(1, key1Values.size());
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE1));
        
        desc.addMetadata(null, FullDescriptorImpl.FULL_VALUE2);
        key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNotNull(key1Values);
        Assert.assertEquals(1, key1Values.size());
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE1));
        
        desc.addMetadata(FullDescriptorImpl.FULL_KEY1, FullDescriptorImpl.FULL_VALUE2);
        
        key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNotNull(key1Values);
        Assert.assertEquals(2, key1Values.size());
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE1));
        Assert.assertTrue(key1Values.contains(FullDescriptorImpl.FULL_VALUE2));
    }
    
    /**
     * Tests removing metadata
     */
    @Test
    public void testRemoveMetadata() {
        FullDescriptorImpl desc = new FullDescriptorImpl();
        testBasicFullDescriptor(desc);
        
        Assert.assertFalse(desc.removeMetadata(null, null));
        Assert.assertFalse(desc.removeMetadata(FullDescriptorImpl.FULL_KEY1, null));
        Assert.assertFalse(desc.removeMetadata(null, FullDescriptorImpl.FULL_VALUE2));
        
        // Testing from 2 -> 1
        Assert.assertTrue(desc.removeMetadata(FullDescriptorImpl.FULL_KEY2, FullDescriptorImpl.FULL_VALUE2));
        List<String> key2Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY2);
        Assert.assertNotNull(key2Values);
        Assert.assertEquals(1, key2Values.size());
        Assert.assertTrue(key2Values.contains(FullDescriptorImpl.FULL_VALUE1));
        
        Assert.assertFalse(desc.removeMetadata(FullDescriptorImpl.FULL_KEY2, FullDescriptorImpl.FULL_VALUE2));
        
        // Testing from 1 -> 0
        Assert.assertTrue(desc.removeMetadata(FullDescriptorImpl.FULL_KEY1, FullDescriptorImpl.FULL_VALUE1));
        List<String> key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNull(key1Values);
        
        // Testing from 0 -> 0
        Assert.assertFalse(desc.removeMetadata(FullDescriptorImpl.FULL_KEY1, FullDescriptorImpl.FULL_VALUE1));
        key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNull(key1Values);
    }
    
    /**
     * Tests removing all metadata
     */
    @Test
    public void testRemoveAllMetadata() {
        FullDescriptorImpl desc = new FullDescriptorImpl();
        testBasicFullDescriptor(desc);
        
        Assert.assertFalse(desc.removeAllMetadata(null));
        
        // Testing from 2 -> 0
        Assert.assertTrue(desc.removeAllMetadata(FullDescriptorImpl.FULL_KEY2));
        List<String> key2Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY2);
        Assert.assertNull(key2Values);
        
        // Testing from 0 -> 0
        Assert.assertFalse(desc.removeAllMetadata(FullDescriptorImpl.FULL_KEY2));
        
        // Testing from 1 -> 0
        Assert.assertTrue(desc.removeAllMetadata(FullDescriptorImpl.FULL_KEY1));
        List<String> key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNull(key1Values);
        
        // Testing from 0 -> 0
        Assert.assertFalse(desc.removeAllMetadata(FullDescriptorImpl.FULL_KEY1));
        key1Values = desc.getMetadata().get(FullDescriptorImpl.FULL_KEY1);
        Assert.assertNull(key1Values);
    }
    
    /**
     * Tests the read and write external form of DescriptorImpl
     * 
     * @throws IOException
     */
    @Test
    public void testReadAndWriteExternal() throws IOException {
        DescriptorImpl writeA = BuilderHelper.createDescriptorFromClass(WriteServiceA.class);
        DescriptorImpl writeB = BuilderHelper.createDescriptorFromClass(WriteServiceB.class);
        writeB.addMetadata(FullDescriptorImpl.FULL_KEY1, FullDescriptorImpl.FULL_VALUE1);
        writeB.addMetadata(FullDescriptorImpl.FULL_KEY2, FullDescriptorImpl.FULL_VALUE1);
        writeB.addMetadata(FullDescriptorImpl.FULL_KEY2, FullDescriptorImpl.FULL_VALUE2);
        DescriptorImpl writeC = new DescriptorImpl();  // Write out a completely empty one
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        
        writeA.writeObject(pw);
        pw.println();
        writeB.writeObject(pw);
        pw.println();
        writeC.writeObject(pw);
        
        pw.close();
        baos.close();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        BufferedReader br = new BufferedReader(new InputStreamReader(bais));
        
        int lcv = 0;
        while (br.ready()) {
            DescriptorImpl di = new DescriptorImpl();
            
            di.readObject(br);
            
            if (lcv == 0) {
                Assert.assertEquals(writeA, di);
                Assert.assertEquals(writeA.hashCode(), di.hashCode());
            }
            else if (lcv == 1) {
                Assert.assertEquals(writeB, di);
                Assert.assertEquals(writeB.hashCode(), di.hashCode());
            }
            else if (lcv == 2) {
                Assert.assertEquals(writeC, di);
                Assert.assertEquals(writeC.hashCode(), di.hashCode());
            }
            else {
                Assert.fail("More descriptors were read than were written: " + di + " lcv=" + lcv);
            }
            
            lcv++;
        }
        
    }
    
    /**
     * Tests that a bad value cannot come in
     */
    @Test(expected=IllegalArgumentException.class)
    public void testAddBadMetadata() {
        DescriptorImpl di = new DescriptorImpl();
        
        di.addMetadata("innocuousKey", "a key with a bad character :");
        
    }
}
