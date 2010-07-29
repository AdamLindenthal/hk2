/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package org.jvnet.hk2.component;

import com.sun.hk2.component.InjectionResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * InjectionManager is responsible for injecting resources into a component.
 * Injection targets are identified by the injection resolver type attribute.
 *
 * @author Jerome Dochez
 */
@SuppressWarnings("unchecked")
public class InjectionManager {

    private final Habitat habitat;
    
    public InjectionManager(Habitat habitat) {
        this.habitat = habitat;
    }
  
   /**
     * Initializes the component by performing injection.
     *
     * @param component component instance to inject
     * @param onBehalfOf the inhabitant to do injection on behalf of
     * @throws ComponentException
     *      if injection failed for some reason.
     */    
    public void inject(Object component, Inhabitant<?> onBehalfOf, Collection<InjectionResolver> targets) {
        inject(component, onBehalfOf, component.getClass(), targets);
    }
    
    /**
      * Initializes the component by performing injection.
      *
      * @param component component instance to inject
      * @param onBehalfOf the inhabitant to do injection on behalf of
      * @param type component class
      * @throws ComponentException
      *      if injection failed for some reason.
      */
    public void inject(Object component,
                Inhabitant<?> onBehalfOf,
                Class type,
                Collection<InjectionResolver> targets) {

        try {
            assert component!=null;

            // TODO: faster implementation needed.

            Class currentClass = type;
            while (currentClass!=null && !currentClass.equals(Object.class)) {
                // get the list of the instances variable
                for (Field field : currentClass.getDeclaredFields()) {

                    for (InjectionResolver target : targets) {
                        Annotation inject = field.getAnnotation(target.type);
                        if (inject == null)     continue;

                        Class fieldType = field.getType();
                        try {
                            Object value = target.getValue(habitat, component, onBehalfOf, field, fieldType);
                            if (value != null) {
                                field.setAccessible(true);
                                field.set(component, value);
                                Injectable injectable;
                                try {
                                    injectable = Injectable.class.cast(value);
                                    if (injectable!=null) {
                                        injectable.injectedInto(component);
                                    }
                                } catch (Exception e) {
                                }

                            } else {
                                if(!target.isOptional(field, inject)) {
                                    Logger.getAnonymousLogger().info("Cannot inject " + field + " into component " + component);
                                    throw new UnsatisfiedDependencyException(field);
                                }
                            }
                        } catch (ComponentException e) {
                            if (!target.isOptional(field, inject)) {
                                throw new UnsatisfiedDependencyException(field,e);
                            }
                        } catch (IllegalAccessException e) {
                            throw new ComponentException("Injection failed on " + field.toGenericString(), e);
                        } catch (RuntimeException e) {
                            throw new ComponentException("Injection failed on " + field.toGenericString(), e);
                        }
                    }
                }
                for (Method method : currentClass.getDeclaredMethods()) {

                    for (InjectionResolver target : targets) {

                        Annotation inject = method.getAnnotation(target.type);
                        if (inject == null)     continue;

                        Method setter = target.getSetterMethod(method, inject);

                        if (setter.getReturnType() != void.class) {
                            if (Collection.class.isAssignableFrom(setter.getReturnType())) {
                                injectCollection(component, setter, 
                                    target.getValue(habitat, component, onBehalfOf, method, setter.getReturnType()));
                                continue;
                            }
                            throw new ComponentException("Injection failed on %s : setter method is not declared with a void return type",method.toGenericString());
                        }

                        Class<?>[] paramTypes = setter.getParameterTypes();

                        if (paramTypes.length > 1) {
                            throw new ComponentException("injection failed on %s : setter method takes more than 1 parameter",method.toGenericString());
                        }
                        if (paramTypes.length == 0) {
                            throw new ComponentException("injection failed on %s : setter method does not take a parameter",method.toGenericString());
                        }

                        try {
                            Object value = target.getValue(habitat, component, onBehalfOf, method, paramTypes[0]);
                            if (value != null) {
                                setter.setAccessible(true);
                                setter.invoke(component, value);
                                try {
                                    Injectable injectable = Injectable.class.cast(value);
                                    if (injectable!=null) {
                                        injectable.injectedInto(component);
                                    }
                                } catch (Exception e) {
                                }
                            } else {
                                if (!target.isOptional(method, inject))
                                    throw new UnsatisfiedDependencyException(method);
                            }
                        } catch (IllegalAccessException e) {
                            throw new ComponentException("Injection failed on " + setter.toGenericString(), e);
                        } catch (InvocationTargetException e) {
                            throw new ComponentException("Injection failed on " + setter.toGenericString(), e);
                        } catch (RuntimeException e) {
                            throw new ComponentException("Injection failed on " + setter.toGenericString(), e);
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (LinkageError e) {
            // reflection could trigger additional classloading and resolution, so it can cause linkage error.
            // report more information to assist diagnosis.
            // can't trust component.toString() as the object could be in an inconsistent state.
            Class<?> cls = type;
            LinkageError x = new LinkageError("Failed to inject " + cls +" from "+cls.getClassLoader());
            x.initCause(e);
            throw x;
        }


    }

    private void injectCollection(Object component, Method method, Object value) {
        if (value==null) {
            return;
        }
        Collection c = Collection.class.cast(value);
        Collection target = null;
        try {
            target = Collection.class.cast(method.invoke(component));
        } catch (IllegalAccessException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        } catch (InvocationTargetException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }
        target.addAll(c);
    }
    
    /**
     * Initializes the component by performing injection.
     *
     * @param component component instance to inject
     * @throws ComponentException
     *      if injection failed for some reason.
     */
/*     public void inject(Object component, Class<T extends Annotation> type) throws ComponentException {
        try {
            assert component!=null;

            // TODO: faster implementation needed.

            Class currentClass = component.getClass();
            while (!currentClass.equals(Object.class)) {
                // get the list of the instances variable
                for (Field field : currentClass.getDeclaredFields()) {

                    T inject = field.getAnnotation(type);
                    if (inject == null)     continue;

                    Class fieldType = field.getType();
                    try {
                        Object value = getValue(component, field, fieldType);
                        if (value != null) {
                            field.setAccessible(true);
                            field.set(component, value);
                            Injectable injectable;
                            try {
                                injectable = Injectable.class.cast(value);
                                if (injectable!=null) {
                                    injectable.injectedInto(component);
                                }
                            } catch (Exception e) {
                            }

                        } else {
                            if(!isOptional(inject)) {
                                Logger.getAnonymousLogger().info("Cannot inject " + field + " in component" + component);   
                                throw new UnsatisfiedDepedencyException(field);
                            }
                        }
                    } catch (ComponentException e) {
                        if (!isOptional(inject)) {
                            throw new UnsatisfiedDepedencyException(field,e);
                        }
                    } catch (IllegalAccessException e) {
                        throw new ComponentException("Injection failed on " + field.toGenericString(), e);
                    } catch (RuntimeException e) {
                        throw new ComponentException("Injection failed on " + field.toGenericString(), e);                        
                    }
                }
                for (Method method : currentClass.getDeclaredMethods()) {
                    T inject = method.getAnnotation(type);
                    if (inject == null)     continue;

                    if (method.getReturnType() != void.class) {
                        throw new ComponentException("Injection failed on %s : setter method is not declared with a void return type",method.toGenericString());
                    }

                    Class<?>[] paramTypes = method.getParameterTypes();

                    if (paramTypes.length > 1) {
                        throw new ComponentException("injection failed on %s : setter method takes more than 1 parameter",method.toGenericString());
                    }
                    if (paramTypes.length == 0) {
                        throw new ComponentException("injection failed on %s : setter method does not take a parameter",method.toGenericString());
                    }

                    try {
                        Object value = getValue(component, method, paramTypes[0]);
                        if (value != null) {
                            method.setAccessible(true);
                            method.invoke(component, value);
                            try {
                                Injectable injectable = Injectable.class.cast(value);
                                if (injectable!=null) {
                                    injectable.injectedInto(component);
                                }
                            } catch (Exception e) {
                            }
                        } else {
                            if (!isOptional(inject))
                                throw new UnsatisfiedDepedencyException(method);
                        }
                    } catch (IllegalAccessException e) {
                        throw new ComponentException("Injection failed on " + method.toGenericString(), e);
                    } catch (InvocationTargetException e) {
                        throw new ComponentException("Injection failed on " + method.toGenericString(), e);
                    } catch (RuntimeException e) {
                        throw new ComponentException("Injection failed on " + method.toGenericString(), e);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (LinkageError e) {
            // reflection could trigger additional classloading and resolution, so it can cause linkage error.
            // report more information to assist diagnosis.
            // can't trust component.toString() as the object could be in an inconsistent state.
            Class<?> cls = component.getClass();
            LinkageError x = new LinkageError("Failed to inject " + cls +" from "+cls.getClassLoader());
            x.initCause(e);
            throw x;
        }
    }
    */
}
