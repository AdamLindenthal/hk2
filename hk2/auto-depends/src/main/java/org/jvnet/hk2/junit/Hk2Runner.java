/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
package org.jvnet.hk2.junit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.jvnet.hk2.component.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JUnit runner for hk2 enabled tests. Life-cycle of the test will be managed by
 * this runner which will allow for dependency injection
 *
 * @author Jerome Dochez
 */
public class Hk2Runner extends Runner {

    final Class<?> testClass;
    final Description description;
    final Map<Description, Method> testMethods = 
          new LinkedHashMap<Description, Method>();
    
    private Object instance;

    private final Hk2RunnerOptions options;

    
    public Hk2Runner(Class<?> testClass) {
        this.testClass = testClass;
        this.options = testClass.getAnnotation(Hk2RunnerOptions.class);
        this.description = Description.createSuiteDescription(testClass);
        
        for (Method m : testClass.getDeclaredMethods()) {
            if (m.getAnnotation(Test.class)!=null) {
                Description testDescription = Description.createTestDescription(testClass, m.getName());
                description.addChild(testDescription);
                testMethods.put(testDescription, m);
            }
        }
    }

    @Override
    public Description getDescription() {
        return description;        
    }

    @Override
    public void run(RunNotifier notifier) {
        if (testClass.isAnnotationPresent(Ignore.class)) {
            notifier.fireTestIgnored(getDescription());
            return;
        }

        // Run the @BeforeClass methods.
        for (Method m : testClass.getMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isStatic(mod) && m.getAnnotation(BeforeClass.class)!=null) {
                if (m.getAnnotation(Ignore.class)!=null) continue;
                try {
                    m.invoke(null);
                } catch (IllegalAccessException e) {
                    Failure failure = new Failure(null, e);
                    notifier.fireTestFailure(failure);
                } catch (InvocationTargetException e) {
                    Failure failure = new Failure(null, e);
                    notifier.fireTestFailure(failure);
                }
            }
        }

        
        boolean reinitPerTest = (null != options) ? options.reinitializePerTest() : false;
        try {
            wombInit();
        } catch (ComponentException e) {
            notifier.fireTestFailure(new Failure(getDescription(),e));
            return;
        }
        
        for (Description testDescription : description.getChildren()) {
            Method m = testMethods.get(testDescription);
            if (m.isAnnotationPresent(Ignore.class)) {
                notifier.fireTestIgnored(testDescription);
                continue;
            }
          
            notifier.fireTestStarted(testDescription);
            try {
                m.invoke(instance);
            } catch (IllegalAccessException e) {
                Failure failure = new Failure(testDescription, e);
                notifier.fireTestFailure(failure);
            } catch (InvocationTargetException e) {
                Failure failure = new Failure(testDescription, e.getTargetException());
                notifier.fireTestFailure(failure);
            }
            
            notifier.fireTestFinished(testDescription);

            if (reinitPerTest) {
                try {
                    wombInit();
                } catch (ComponentException e) {
                    notifier.fireTestFailure(new Failure(getDescription(),e));
                    return;
                }
            }
        }

        // Run the @AfterClass methods.
        for (Method m : testClass.getMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isStatic(mod) && m.getAnnotation(AfterClass.class)!=null) {
                if (m.getAnnotation(Ignore.class)!=null) continue;
                try {
                    m.invoke(null);
                } catch (IllegalAccessException e) {
                    Failure failure = new Failure(null, e);
                    notifier.fireTestFailure(failure);
                } catch (InvocationTargetException e) {
                    Failure failure = new Failure(null, e);
                    notifier.fireTestFailure(failure);
                }
            }
        }
    }

    public static Habitat getHabitat() {
        return singleton.getHabitat();
    }

    private void wombInit() {
      singleton = new Hk2TestServices(null == options ? null : options.habitatFactory());
      
      Habitat habitat = singleton.getHabitat();
      // so far we don't support extra meta-data on our tests.
      Womb<?> womb = Wombs.create(testClass, habitat, new MultiMap<String, String>());
      instance = womb.get();
    }
    
    static Hk2TestServices singleton;
}
