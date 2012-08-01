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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.InstanceLifecycleEventType;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.utilities.reflection.Logger;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;

/**
 * @author jwells
 * @param <T> The type of object this creator creates
 *
 */
public class ClazzCreator<T> implements Creator<T> {
    private final ServiceLocatorImpl locator;
    private final Class<?> implClass;
    private final Set<ResolutionInfo> myInitializers = new HashSet<ResolutionInfo>();
    private final Set<ResolutionInfo> myFields = new HashSet<ResolutionInfo>();
    private final ActiveDescriptor<?> selfDescriptor;
    
    private final ResolutionInfo myConstructor;
    private List<Injectee> allInjectees;
    
    private Method postConstructMethod;
    private Method preDestroyMethod;
    
    /* package */ ClazzCreator(ServiceLocatorImpl locator, Class<?> implClass, ActiveDescriptor<?> selfDescriptor, Collector collector) {
        this.locator = locator;
        this.implClass = implClass;
        this.selfDescriptor = selfDescriptor;
        
        List<Injectee> baseAllInjectees = new LinkedList<Injectee>();
        
        AnnotatedElement element;
        List<Injectee> injectees;
        
        element = Utilities.findProducerConstructor(implClass, locator, collector);
        if (element == null) {
            myConstructor = null;
            return;
        }
        
        injectees = Utilities.getConstructorInjectees((Constructor<?>) element);
        if (injectees == null) {
            myConstructor = null;
            return;
        }
        
        baseAllInjectees.addAll(injectees);
        
        myConstructor = new ResolutionInfo(element, injectees);
        
        Set<Method> initMethods = Utilities.findInitializerMethods(implClass, locator, collector);
        for (Method initMethod : initMethods) {
            element = initMethod;
            
            injectees = Utilities.getMethodInjectees(initMethod);
            if (injectees == null) return;
            
            baseAllInjectees.addAll(injectees);
            
            myInitializers.add(new ResolutionInfo(element, injectees));
        }
        
        Set<Field> fields = Utilities.findInitializerFields(implClass, locator, collector);
        for (Field field : fields) {
            element = field;
            
            injectees = Utilities.getFieldInjectees(field);
            if (injectees == null) return;
            
            baseAllInjectees.addAll(injectees);
            
            myFields.add(new ResolutionInfo(element, injectees));
        }
        
        postConstructMethod = Utilities.findPostConstruct(implClass, collector);
        preDestroyMethod = Utilities.findPreDestroy(implClass, collector);
        
        allInjectees = Collections.unmodifiableList(baseAllInjectees);
        
        Utilities.validateSelfInjectees(selfDescriptor, allInjectees, collector);
    }
    
    private void resolve(Map<Injectee, Object> addToMe,
            InjectionResolver<?> resolver,
            Injectee injectee,
            ServiceHandle<?> root,
            Collector errorCollection) {
        if (injectee.isSelf()) {
            addToMe.put(injectee, selfDescriptor);
            return;
        }
        
        Object addIn = null;
        try {
            addIn = resolver.resolve(injectee, root);
        }
        catch (Throwable th) {
            errorCollection.addThrowable(th);
        }
        
        if (addIn != null) {
            addToMe.put(injectee, addIn);
        }
    }
    
    private Map<Injectee, Object> resolveAllDependencies(ServiceHandle<?> root) throws IllegalStateException {
        Collector errorCollector = new Collector();
        
        Map<Injectee, Object> retVal = new HashMap<Injectee, Object>();
        
        InjectionResolver<?> resolver = Utilities.getInjectionResolver(locator, myConstructor.baseElement);
        for (Injectee injectee : myConstructor.injectees) {
            resolve(retVal, resolver, injectee, root, errorCollector);
        }
        
        for (ResolutionInfo fieldRI : myFields) {
            resolver = Utilities.getInjectionResolver(locator, fieldRI.baseElement);
            for (Injectee injectee : fieldRI.injectees) {
                resolve(retVal, resolver, injectee, root, errorCollector);
            }
        }
        
        for (ResolutionInfo methodRI : myInitializers) {
            resolver = Utilities.getInjectionResolver(locator, methodRI.baseElement);
            for (Injectee injectee : methodRI.injectees) {
                resolve(retVal, resolver, injectee, root, errorCollector);
            }
        }
        
        if (errorCollector.hasErrors()) {
            errorCollector.addThrowable(new IllegalArgumentException("While attempting to resolve the dependencies of " +
              implClass.getName() + " errors were found"));
            
            errorCollector.throwIfErrors();
        }
        
        return retVal;
    }
    
    private Object createMe(Map<Injectee, Object> resolved) throws Throwable {
        Constructor<?> c = (Constructor<?>) myConstructor.baseElement;
        List<Injectee> injectees = myConstructor.injectees;
        
        Object args[] = new Object[injectees.size()];
        for (Injectee injectee : injectees) {
            args[injectee.getPosition()] = resolved.get(injectee);
        }
        
        Utilities.setAccessible(c);
        
        return Utilities.makeMe(c, args);
    }
    
    private void fieldMe(Map<Injectee, Object> resolved, T t) throws Throwable {
        for (ResolutionInfo ri : myFields) {
            Field field = (Field) ri.baseElement;
            List<Injectee> injectees = ri.injectees;  // Should be only one injectee, itself!
            
            Injectee fieldInjectee = null;
            for (Injectee candidate : injectees) {
                fieldInjectee = candidate;
            }
            
            Object putMeIn = resolved.get(fieldInjectee);
            
            Utilities.setAccessible(field);
            
            field.set(t, putMeIn);
        }
    }
    
    private void methodMe(Map<Injectee, Object> resolved, T t) throws Throwable {
        for (ResolutionInfo ri : myInitializers) {
            Method m = (Method) ri.baseElement;
            List<Injectee> injectees = ri.injectees;
            
            Object args[] = new Object[injectees.size()];
            for (Injectee injectee : injectees) {
                args[injectee.getPosition()] = resolved.get(injectee);
            }
            
            Utilities.setAccessible(m);
            
            ReflectionHelper.invoke(t, m, args);
        }
    }
    
    private void postConstructMe(T t) throws Throwable {
        if (t == null) return;
        
        if (t instanceof PostConstruct) {
            ((PostConstruct) t).postConstruct();
            return;
        }
        
        if (postConstructMethod == null) return;
        
        Utilities.setAccessible(postConstructMethod);
        ReflectionHelper.invoke(t, postConstructMethod, new Object[0]);
    }
    
    private void preDestroyMe(T t) throws Throwable {
        if (t == null) return;
        
        if (t instanceof PreDestroy) {
            ((PreDestroy) t).preDestroy();
            return;
        }
        
        if (preDestroyMethod == null) return;
        
        Utilities.setAccessible(preDestroyMethod);
        ReflectionHelper.invoke(t, preDestroyMethod, new Object[0]);
    }

    /* (non-Javadoc)
     * @see org.jvnet.hk2.internal.Creator#create()
     */
    @SuppressWarnings("unchecked")
    @Override
    public InstanceLifecycleEventImpl create(ServiceHandle<?> root) {
        try {
            Map<Injectee, Object> allResolved = resolveAllDependencies(root);
            
            T retVal = (T) createMe(allResolved);
            
            fieldMe(allResolved, retVal);
            
            methodMe(allResolved, retVal);
            
            postConstructMe(retVal);
            
            return new InstanceLifecycleEventImpl(InstanceLifecycleEventType.POST_PRODUCTION,
                    retVal, allResolved);
        }
        catch (Throwable th) {
            if (th instanceof MultiException) {
                MultiException me = (MultiException) th;
                
                me.addError(new IllegalStateException("Unable to create or inject " + implClass.getName()));
                
                throw me;
            }
            
            MultiException me = new MultiException(th);
            me.addError(new IllegalStateException("Unable to create or inject " + implClass.getName()));
            
            throw me;
        }
    }

    /* (non-Javadoc)
     * @see org.jvnet.hk2.internal.Creator#dispose(java.lang.Object)
     */
    @Override
    public void dispose(T instance) {
        try {
            preDestroyMe(instance);
        }
        catch (Throwable th) {
            Logger.getLogger().debug("ClazzCreator", "dispose", th);
        }

    }
    
    /* (non-Javadoc)
     * @see org.jvnet.hk2.internal.Creator#getInjectees()
     */
    @Override
    public List<Injectee> getInjectees() {
        return allInjectees;
    }
    
    private static class ResolutionInfo {
        private final AnnotatedElement baseElement;
        private final List<Injectee> injectees = new LinkedList<Injectee>();
        
        private ResolutionInfo(AnnotatedElement baseElement, List<Injectee> injectees) {
            this.baseElement = baseElement;
            this.injectees.addAll(injectees);
        }
    }
}
