/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.hk2.component;

import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.MultiMap;
import org.jvnet.hk2.component.Womb;
import org.jvnet.hk2.component.Wombs;

/**
 * @author Kohsuke Kawaguchi
 */
public class LazyInhabitant<T> extends AbstractInhabitantImpl<T> {
    private final String typeName;
    /**
     * Real {@link Inhabitant} object. Lazily created.
     */
    private volatile Inhabitant<T> real;
    /**
     * Lazy reference to {@link ClassLoader}.
     */
    private final Holder<ClassLoader> classLoader;

    protected final Habitat habitat;

    private final MultiMap<String,String> metadata;
    
    private final Inhabitant<?> lead;

    
    public LazyInhabitant(Habitat habitat, Holder<ClassLoader> cl, String typeName, MultiMap<String,String> metadata) {
      this(habitat, cl, typeName, metadata, null);
    }

    public LazyInhabitant(Habitat habitat, Holder<ClassLoader> cl, String typeName, MultiMap<String,String> metadata, Inhabitant<?> lead) {
      assert metadata!=null;
      this.habitat = habitat;
      this.classLoader = cl;
      this.typeName = typeName;
      this.metadata = metadata;
      this.lead = lead;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "-" + System.identityHashCode(this) + 
            "(" + typeName() + ", active: " + isInstantiated() + ")";
    }
    
    @Override
    public Inhabitant<?> lead() {
        return lead;
    }
    
    public String typeName() {
        return typeName;
    }

    public Class<T> type() {
      // fetching is too heavy of an operation because it will activate/write the class
//        fetch();

        Inhabitant<T> real = this.real;
        if (null != real) {
            return real.type();
        } else {
            return loadClass();
        }
    }

    public MultiMap<String,String> metadata() {
        return metadata;
    }

    private synchronized void fetch() {
        if (null == real) {
          Class<T> c = loadClass();
          real = Inhabitants.wrapByScope(c,createWomb(c),habitat);
        }
    }

    public final ClassLoader getClassLoader() {
      return classLoader.get();
    }
    
    @SuppressWarnings("unchecked")
    private Class<T> loadClass() {
      final ClassLoader cl = getClassLoader();
      try {
          Class<T> c = (Class<T>) cl.loadClass(typeName);
          return c;
      } catch (ClassNotFoundException e) {
          throw new ComponentException("Failed to load "+typeName+" from " + cl, e);
      }
    }

    /**
     * Creates {@link Womb} for instantiating objects.
     */
    protected Womb<T> createWomb(Class<T> c) {
        return Wombs.create(c,habitat,metadata);
    }

    @SuppressWarnings("unchecked")
    public T get(Inhabitant onBehalfOf) throws ComponentException {
        fetch();
        return real.get(onBehalfOf);
    }

    public synchronized void release() {
        if (null != real) {
            real.release();
            real = null;
        }
    }

    public boolean isInstantiated() {
        return (real!=null && real.isInstantiated());
    }
}
