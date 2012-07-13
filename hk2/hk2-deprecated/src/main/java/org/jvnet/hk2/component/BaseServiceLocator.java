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
package org.jvnet.hk2.component;

import java.util.Collection;

import org.jvnet.hk2.annotations.Contract;

/**
 * Provide a simple abstraction for getting services by contract or type.
 *
 * @author Mason Taube
 */
@Deprecated
@Contract
public interface BaseServiceLocator {

    /**
     * Loads a component that implements the given contract and has the given
     * name.
     * 
     * @param name
     *            can be null, in which case it'll only match to the unnamed
     *            component.
     * @return null if no such service exists.
     */
    <T> T getComponent(Class<T> contract, String name) throws ComponentException;

    /**
     * Analogous to the following:
     * <pre>
     * getComponent(contractClass.getName(), name);
     * </pre>

     * @param fullQualifiedName the contract class name
     * @param name
     *            can be null, in which case it'll only match to the unnamed
     *            component.
     * @return null if no such service exists.
     */
    <T> T getComponent(String fullQualifiedName, String name);
    
    public <T> T getComponent(Class<T> clazz) throws ComponentException;
    
    /**
     * Gets the object of the given type.
     * 
     * @return null if not found.
     */
    <T> T getByType(Class<T> implType);

    /**
     * Gets the object of the given type.
     * 
     * @return null if not found.
     */
    <T> T getByType(String implType);

    /**
     * Gets the object that has the given contract.
     * <p/>
     * <p/>
     * If there are more than one of them, this method arbitrarily return one of
     * them.
     */
    <T> T getByContract(Class<T> contractType);

    <T> T getByContract(String contractType);
    
    /**
     * Gets all the inhabitants registered under the given {@link Contract}.
     * This is an example of heterogeneous type-safe container.
     *
     * @return can be empty but never null.
     */
    <T> Collection<T> getAllByContract(Class<T> contractType);

    <T> Collection<T> getAllByContract(String contractType);
    
}
