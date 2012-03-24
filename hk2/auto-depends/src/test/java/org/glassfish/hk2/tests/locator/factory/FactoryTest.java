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
package org.glassfish.hk2.tests.locator.factory;

import java.util.Date;

import junit.framework.Assert;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.tests.locator.utilities.LocatorHelper;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class FactoryTest {
    private final static String TEST_NAME = "FactoryTest";
    private final static ServiceLocator locator = LocatorHelper.create(TEST_NAME, new FactoryModule());
    
    /**
     * A very simple factory test
     */
    @Test
    public void testSimpleFactory() {
        Date date = locator.getService(Date.class);
        Assert.assertNotNull(date);
    }
    
    /**
     * Factory injected with Provider
     */
    @Test
    public void testFactoryProvided() {
        DateInjectee dateInjectee = locator.getService(DateInjectee.class);
        Assert.assertNotNull(dateInjectee);
        
        Date rawDate = dateInjectee.getRawInject();
        Assert.assertNotNull(rawDate);
        
        Date providedDate1 = dateInjectee.getProvidedInject();
        Assert.assertNotNull(providedDate1);
        
        Date providedDate2 = dateInjectee.getProvidedInject();
        Assert.assertNotNull(providedDate2);
        
        Assert.assertNotSame(rawDate, providedDate1);
        Assert.assertNotSame(rawDate, providedDate2);
        Assert.assertNotSame(providedDate1, providedDate2);
    }
    
    /**
     * Factory into a custom scope
     */
    @Test
    public void testFactoryProducingIntoCustomScope() {
        FruitContext fruitContext = locator.getService(FruitContext.class);
        Assert.assertNotNull(fruitContext);
        
        // Nothing here yet, haven't asked for an apple
        Assert.assertTrue(fruitContext.getContextStoredFruits().isEmpty());
        
        Apple apple = locator.getService(Apple.class);
        Assert.assertNotNull(apple);
        
        Assert.assertEquals("Expected 1 apple but got " + fruitContext.getContextStoredFruits().size(),
                1, fruitContext.getContextStoredFruits().size());
        
        Assert.assertTrue(fruitContext.getContextStoredFruits().values().contains(apple));
        
        // Ask again, expect the same result back
        Apple apple2 = locator.getService(Apple.class);
        Assert.assertEquals(apple, apple2);
        
        Assert.assertEquals("Expected 1 apple but got " + fruitContext.getContextStoredFruits().size(),
                1, fruitContext.getContextStoredFruits().size());
    }
}
