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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.extension.ServiceLocatorGenerator;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;

/**
 * The implementation of the {@link ServiceLocatorFactory} that looks
 * in the OSGi service registry or the META-INF/services for the implementation
 * to use.  Failing those things, it uses the standard default locator
 * generator, which is found in auto-depends, which is the 99.9% case
 * 
 * @author jwells
 */
public class ServiceLocatorFactoryImpl extends ServiceLocatorFactory {
  private final ServiceLocatorGenerator defaultGenerator;
  private final Object lock = new Object();
  private final HashMap<String, ServiceLocator> serviceLocators = new HashMap<String, ServiceLocator>();
  
  /**
   * This will create a new set of name to locator mappings
   */
  public ServiceLocatorFactoryImpl() {
      defaultGenerator = AccessController.doPrivileged(new PrivilegedAction<ServiceLocatorGenerator>() {

        @Override
        public ServiceLocatorGenerator run() {
            try {
                return getGenerator();
            }
            catch (Throwable th) {
                Logger.getLogger(ServiceLocatorFactoryImpl.class.getName()).severe("Error finding implementation of hk2: " + th.getMessage());
                Thread.dumpStack();
                return null;
            }
        }
          
      });
  }
  
  private static ServiceLocatorGenerator getGenerator() {
      Iterable<? extends ServiceLocatorGenerator> generators = ServiceLoader.lookupProviderInstances(ServiceLocatorGenerator.class);
      if (generators !=null) {
          for (ServiceLocatorGenerator generator : generators) {
              if (generator != null) return generator;
        }
      }
        
      Iterator<ServiceLocatorGenerator> providers = java.util.ServiceLoader.load(ServiceLocatorGenerator.class).iterator();
      if (providers.hasNext()) {
          return providers.next();
      }
    
      Logger.getLogger(ServiceLocatorFactoryImpl.class.getName()).info("Cannot find a default implementation of the HK2 ServiceLocatorGenerator");
      return null;
  }

  /* (non-Javadoc)
   * @see org.glassfish.hk2.api.ServiceLocatorFactory#create(java.lang.String, org.glassfish.hk2.api.Module)
   */
  @Override
  public ServiceLocator create(String name) {
      return create(name, null, null);
  }

  /* (non-Javadoc)
   * @see org.glassfish.hk2.api.ServiceLocatorFactory#find(java.lang.String)
   */
  @Override
  public ServiceLocator find(String name) {
    synchronized (lock) {
      return serviceLocators.get(name);
    }
  }

  /* (non-Javadoc)
   * @see org.glassfish.hk2.api.ServiceLocatorFactory#destroy(java.lang.String)
   */
  @Override
  public void destroy(String name) {
      ServiceLocator killMe = null;
    
      synchronized (lock) {
          killMe = serviceLocators.remove(name);
      }
    
      if (killMe != null) {
          killMe.shutdown();
      }
  }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceLocatorFactory#create(java.lang.String, org.glassfish.hk2.api.Module, org.glassfish.hk2.api.ServiceLocator)
     */
    @Override
    public ServiceLocator create(String name,
            ServiceLocator parent) {
        return create(name, parent, null);
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ServiceLocatorFactory#create(java.lang.String, org.glassfish.hk2.api.ServiceLocator, org.glassfish.hk2.extension.ServiceLocatorGenerator)
     */
    @Override
    public ServiceLocator create(String name, ServiceLocator parent,
            ServiceLocatorGenerator generator) {
 
        synchronized (lock) {
            ServiceLocator retVal = serviceLocators.get(name);
            if (retVal != null) return retVal;
 
            if (generator == null) {
                if (defaultGenerator == null) {
                    throw new IllegalStateException("No generator was provided and there is no default generator registered");
                }
                
                generator = defaultGenerator;
            }
            
            retVal = generator.create(name, parent);
            
            serviceLocators.put(name, retVal);
            
            return retVal;
        }
    }

}
