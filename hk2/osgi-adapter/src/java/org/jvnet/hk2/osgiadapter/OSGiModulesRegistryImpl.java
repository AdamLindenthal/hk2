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


package org.jvnet.hk2.osgiadapter;

import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;
import static org.jvnet.hk2.osgiadapter.Logger.logger;
import com.sun.enterprise.module.*;
import com.sun.enterprise.module.common_impl.CompositeEnumeration;
import com.sun.hk2.component.InhabitantsParser;

import java.io.IOException;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.logging.*;
import java.net.URL;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.net.URISyntaxException;

/**
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class OSGiModulesRegistryImpl
        extends com.sun.enterprise.module.common_impl.AbstractModulesRegistryImpl {

    /**
     * OSGi BundleContext - used to install/uninstall, start/stop bundles
     */
    BundleContext bctx;
    private PackageAdmin pa;
    private Map<ModuleChangeListener, BundleListener> moduleChangeListeners =
            new HashMap<ModuleChangeListener, BundleListener>();
    private Map<ModuleLifecycleListener, BundleListener> moduleLifecycleListeners =
            new HashMap<ModuleLifecycleListener, BundleListener>();

    /*package*/ OSGiModulesRegistryImpl(BundleContext bctx) {
        super(null);
        this.bctx = bctx;
        // Populate registry with pre-installed bundles
        for (final Bundle b : bctx.getBundles()) {
            if (b.getLocation().equals (Constants.SYSTEM_BUNDLE_LOCATION)) {
                continue;
            }
            ModuleDefinition md;
            try {
                md = new OSGiModuleDefinition(b);
            } catch (Exception e) {
                logger.logp(Level.WARNING, "OSGiModulesRegistryImpl",
                        "OSGiModulesRegistryImpl",
                        "Not able convert bundle [{0}] having location [{1}] " +
                                "to module because of exception: {2}",
                        new Object[]{b, b.getLocation(), e});
                continue;
            }
            Module m = new OSGiModuleImpl(this, b, md);
            add(m); // call add as it processes provider names
        }
        ServiceReference ref = bctx.getServiceReference(PackageAdmin.class.getName());
        pa = PackageAdmin.class.cast(bctx.getService(ref));
    }

    protected Module newModule(ModuleDefinition moduleDef) {
        String location = moduleDef.getLocations()[0].toString();
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.logp(Level.FINE, "OSGiModulesRegistryImpl", "add",
                    "location = {0}", location);
            }
            File l = new File(moduleDef.getLocations()[0]);
            if (l.isDirectory()) {
                location = "reference:" + location;
            }
            Bundle bundle = bctx.installBundle(location);
            // wrap Bundle by a Module object
            return new OSGiModuleImpl(this, bundle, moduleDef);
        } catch (BundleException e) {
            logger.logp(Level.WARNING, "OSGiModulesRegistryImpl", "add",
                    "Exception {0} while adding location = {1}", new Object[]{e, location});
//            throw new RuntimeException(e); // continue
        }
        return null;
    }

    public void parseInhabitants(
            Module module, String name, InhabitantsParser inhabitantsParser)
            throws IOException {
        OSGiModuleImpl.class.cast(module).parseInhabitants(name, inhabitantsParser);
    }

    public ModulesRegistry createChild() {
        throw new UnsupportedOperationException("Not Yet Implemented"); // TODO(Sahoo)
    }

    public synchronized void detachAll() {
        for (Module m : modules.values()) {
            m.detach();
        }
    }

    public synchronized void shutdown() {
        for (Module m : modules.values()) {
            // Only stop modules that were started after ModulesRegistry
            // came into existence.
            if (OSGiModuleImpl.class.cast(m).isTransientlyActive()) {
                 m.stop();
            }
        }
        modules.clear();

        for (Repository repo : repositories.values()) {
            try {
                repo.shutdown();
            } catch(Exception e) {
                java.util.logging.Logger.getAnonymousLogger().log(Level.SEVERE, "Error while closing repository " + repo, e);
                // swallows
            }
        }
        // don't try to stop the system bundle, as we may be embedded inside
        // something like Eclipse.
    }

    /**
     * Sets the classloader parenting the class loaders created by the modules
     * associated with this registry.
     * @param parent parent class loader
     */
    public void setParentClassLoader(ClassLoader parent) {
        throw new UnsupportedOperationException("This method can't be implemented in OSGi environment");
    }

    /**
     * Returns the parent class loader parenting the class loaders created
     * by modules associated with this registry.
     * @return the parent classloader
     */
    public ClassLoader getParentClassLoader() {
        return Bundle.class.getClassLoader();
    }

    
    /**
     * Returns a ClassLoader capable of loading classes from a set of modules identified
     * by their module definition and also load new urls.
     *
     * @param parent the parent class loader for the returned class loader instance
     * @param mds module definitions for all modules this classloader should be
     *        capable of loading
     * @param urls urls to be added to the module classloader
     * @return class loader instance
     * @throws com.sun.enterprise.module.ResolveError if one of the provided module
     *         definition cannot be resolved
     */
    public ClassLoader getModulesClassLoader(final ClassLoader parent,
                                             Collection<ModuleDefinition> mds,
                                             URL[] urls) throws ResolveError {
        final List<ClassLoader> delegateCLs = new ArrayList<ClassLoader>();
        final List<Module> delegateModules = new ArrayList<Module>();
        for (ModuleDefinition md : mds) {
            Module m = makeModuleFor(md.getName(), md.getVersion());
            delegateModules.add(m);
            delegateCLs.add(m.getClassLoader());
        }
        return new URLClassLoader(urls!=null?urls:new URL[0], parent) {
            /*
             * This is a delegating class loader.
             * This extends URLClassLoader, because web layer (Jasper to be specific)
             * expects it to be a URLClassLoader so that it can extract Classpath information
             * used for javac.
             * It always delegates to a chain of OSGi bundle's class loader.
             * ClassLoader.defineClass() is never called in the context of this class.
             * There will never be a class for which getClassLoader()
             * would return this class loader.
             */
            @Override
            public URL[] getURLs() {
                List<URL> result = new ArrayList<URL>();
                if (parent instanceof URLClassLoader) {
                    URL[] parentURLs = URLClassLoader.class.cast(parent).getURLs();
                    result.addAll(Arrays.asList(parentURLs));
                }
                for (Module m : delegateModules) {
                    ModuleDefinition md = m.getModuleDefinition();
                    URI[] uris = md.getLocations();
                    URL[] urls = new URL[uris.length];
                    for (int i = 0; i < uris.length; ++i) {
                        try {
                            urls[i] = uris[i].toURL();
                        } catch (MalformedURLException e) {
                            logger.warning("Exception " + e + " while converting " + uris[i] + " to URL");
                        }
                    }
                    result.addAll(Arrays.asList(urls));
                }
                return result.toArray(new URL[0]);
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                for (ClassLoader delegate : delegateCLs) {
                    try {
                        return delegate.loadClass(name);
                    } catch(ClassNotFoundException e) {
                        // This is expected, so ignore
                    }
                }
                throw new ClassNotFoundException(name);
            }

            @Override
            public URL findResource(String name) {
                URL resource = null;
                for (ClassLoader delegate : delegateCLs) {
                    resource = delegate.getResource(name);
                    if (resource != null) {
                        return resource;
                    }
                }
                return resource;
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                List<Enumeration<URL>> enumerators = new ArrayList<Enumeration<URL>>();
                for (ClassLoader delegate : delegateCLs) {
                    Enumeration<URL> enumerator = delegate.getResources(name);
                    enumerators.add(enumerator);
                }
                return new CompositeEnumeration(enumerators);
            }

        };
    }


    /**
     * Returns a ClassLoader capable of loading classes from a set of modules identified
     * by their module definition
     *
     * @param parent the parent class loader for the returned class loader instance
     * @param defs module definitions for all modules this classloader should be
     *        capable of loading classes from
     * @return class loader instance
     * @throws com.sun.enterprise.module.ResolveError if one of the provided module
     *         definition cannot be resolved
     */
    public ClassLoader getModulesClassLoader(ClassLoader parent,
                                             Collection<ModuleDefinition> defs)
        throws ResolveError {
        return getModulesClassLoader(parent, defs, null);
    }

    
    public Module find(Class clazz) {
        Bundle b = pa.getBundle(clazz);
        if (b!=null) {
            return modules.get(b.getSymbolicName());
        }
        return null;
    }

    public PackageAdmin getPackageAdmin() {
        return pa;
    }

    public void addModuleChangeListener(final ModuleChangeListener listener, final OSGiModuleImpl module) {
        SynchronousBundleListener bundleListener = new SynchronousBundleListener() {
            public void bundleChanged(BundleEvent event) {
                if ((event.getBundle() == module.getBundle()) &&
                        ((event.getType() & BundleEvent.UPDATED) == BundleEvent.UPDATED)) {
                    listener.changed(module);
                }
            }
        };
        bctx.addBundleListener(bundleListener);
        moduleChangeListeners.put(listener, bundleListener);
    }

    public boolean removeModuleChangeListener(ModuleChangeListener listener) {
        BundleListener bundleListener = moduleChangeListeners.remove(listener);
        if (bundleListener!= null) {
            bctx.removeBundleListener(bundleListener);
            return true;
        }
        return false;
    }

    public void register(final ModuleLifecycleListener listener) {
        SynchronousBundleListener bundleListener = new SynchronousBundleListener() {
            public void bundleChanged(BundleEvent event) {
                if ((event.getType() & BundleEvent.STARTED) == BundleEvent.STARTED) {
                    listener.moduleStarted(getModule(event.getBundle()));
                }
            }
        };
        bctx.addBundleListener(bundleListener);
        moduleLifecycleListeners.put(listener,  bundleListener);
    }


    public void unregister(ModuleLifecycleListener listener) {
        BundleListener bundleListener = moduleLifecycleListeners.remove(listener);
        if (bundleListener!=null) {
            bctx.removeBundleListener(bundleListener);
        }
    }

    /**
     * This method is needed because {@link super#getModules()} always goes thru'
     * all the repositories and installs new modules. When such a side effect is not
     * necessary, this method can be called. The returned collection does not include
     * modules known to ancestors of this registry.
     * @return a readonly map of module name to module.
     */
    /*package*/Map<String, Module> getExistingModules() {
        return Collections.unmodifiableMap(modules);
    }

    /*package*/ Module getModule(Bundle bundle) {
        return modules.get(bundle.getHeaders().get(ManifestConstants.BUNDLE_NAME));
    }

    private static class DummyModuleDefinition implements ModuleDefinition {
        private final Bundle b;
        private URI location;
        public DummyModuleDefinition(Bundle b) throws URISyntaxException {
            this.b = b;
            location = new URI(b.getLocation());
        }

        public String getName() {
            return b.getSymbolicName();
        }

        public String[] getPublicInterfaces() {
            return new String[0];
        }

        public ModuleDependency[] getDependencies() {
            return new ModuleDependency[0];
        }

        public URI[] getLocations() {
            return new URI[]{location};
        }

        public String getVersion() {
            return String.class.cast(b.getHeaders().get(Constants.BUNDLE_VERSION));
        }

        public String getImportPolicyClassName() {
            return null;
        }

        public String getLifecyclePolicyClassName() {
            return null;
        }

        public Manifest getManifest() {
            return null;
        }

        public ModuleMetadata getMetadata() {
            return new ModuleMetadata();
        }
    }
}
