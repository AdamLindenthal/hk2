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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.DescriptorType;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.HK2Loader;
import org.glassfish.hk2.api.IndexedFilter;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.InstanceLifecycleEvent;
import org.glassfish.hk2.api.InstanceLifecycleEventType;
import org.glassfish.hk2.api.InstanceLifecycleListener;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ValidationService;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;

/**
 * @author jwells
 * @param <T> The type from the cache
 */
public class SystemDescriptor<T> implements ActiveDescriptor<T> {
    private final Descriptor originalDescriptor;
    private final Descriptor baseDescriptor;
    private final Long id;
    private final ActiveDescriptor<T> activeDescriptor;
    private final Long locatorId;
    private boolean reified;
    
    private final Object cacheLock = new Object();
    private boolean cacheSet = false;
    private T cachedValue;
    
    // These are used when we are doing the reifying ourselves
    private Class<?> implClass;
    private Class<? extends Annotation> scope;
    private Set<Type> contracts;
    private Set<Annotation> qualifiers;
    private Creator<T> creator;
    
    private final HashMap<ValidationService, Boolean> validationServiceCache =
            new HashMap<ValidationService, Boolean>();
    
    private final List<InstanceLifecycleListener> instanceListeners =
            new LinkedList<InstanceLifecycleListener>();
    
    private final Set<IndexedListData> myLists = new HashSet<IndexedListData>();
    
    /* package */ @SuppressWarnings("unchecked")
    SystemDescriptor(Descriptor baseDescriptor, Long locatorId, Long serviceId) {
        this.originalDescriptor = baseDescriptor;
        this.baseDescriptor = BuilderHelper.deepCopyDescriptor(baseDescriptor);
        this.locatorId = locatorId;
        this.id = serviceId;
 
        if (baseDescriptor instanceof ActiveDescriptor) {
            ActiveDescriptor<T> active = (ActiveDescriptor<T>) baseDescriptor;
            if (active.isReified()) {
                activeDescriptor = active;
                reified = true;
            }
            else {
            	activeDescriptor = null;
                reified = false;
            }
        }
        else {
            activeDescriptor = null;
            reified = false;
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getImplementation()
     */
    @Override
    public String getImplementation() {
        return baseDescriptor.getImplementation();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getAdvertisedContracts()
     */
    @Override
    public Set<String> getAdvertisedContracts() {
        return baseDescriptor.getAdvertisedContracts();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getScope()
     */
    @Override
    public String getScope() {
        return baseDescriptor.getScope();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getNames()
     */
    @Override
    public String getName() {
        return baseDescriptor.getName();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getQualifiers()
     */
    @Override
    public Set<String> getQualifiers() {
        return baseDescriptor.getQualifiers();
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getDescriptorType()
     */
    @Override
    public DescriptorType getDescriptorType() {
        return baseDescriptor.getDescriptorType();
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getMetadata()
     */
    @Override
    public Map<String, List<String>> getMetadata() {
        return baseDescriptor.getMetadata();
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getLoader()
     */
    @Override
    public HK2Loader getLoader() {
        return baseDescriptor.getLoader();
    }  

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getRanking()
     */
    @Override
    public int getRanking() {
        return baseDescriptor.getRanking();
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#setRanking(int)
     */
    @Override
    public int setRanking(int ranking) {
        for (IndexedListData myList : myLists) {
            myList.unSort();
        }
        
        return baseDescriptor.setRanking(ranking);
    }
    
    /* package */ void addList(IndexedListData indexedList) {
        myLists.add(indexedList);
    }
    
    /* package */ void removeList(IndexedListData indexedList) {
        myLists.remove(indexedList);
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getBaseDescriptor()
     */
    @Override
    public Descriptor getBaseDescriptor() {
        return originalDescriptor;
    } 

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getServiceId()
     */
    @Override
    public Long getServiceId() {
        return id;
    }
    
    

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#getCache()
     */
    @Override
    public T getCache() {
        return cachedValue;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#isCacheSet()
     */
    @Override
    public boolean isCacheSet() {
        return cacheSet;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#setCache(java.lang.Object)
     */
    @Override
    public void setCache(T cacheMe) {
        synchronized (cacheLock) {
            cachedValue = cacheMe;
            cacheSet = true;
        }
        
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.SingleCache#releaseCache()
     */
    @Override
    public void releaseCache() {
        synchronized (cacheLock) {
            cacheSet = false;
            cachedValue = null;
        }
        
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#isReified()
     */
    @Override
    public boolean isReified() {
        return reified;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getImplementationClass()
     */
    @Override
    public Class<?> getImplementationClass() {
        checkState();
        
        if (activeDescriptor != null) {
            return activeDescriptor.getImplementationClass();
        }
        
        return implClass;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getContractTypes()
     */
    @Override
    public Set<Type> getContractTypes() {
        checkState();
        
        if (activeDescriptor != null) {
            return activeDescriptor.getContractTypes();
        }
        
        return contracts;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getScopeAnnotation()
     */
    @Override
    public Class<? extends Annotation> getScopeAnnotation() {
        checkState();
        
        if (activeDescriptor != null) {
            return activeDescriptor.getScopeAnnotation();
        }
        
        return scope;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getQualifierAnnotations()
     */
    @Override
    public Set<Annotation> getQualifierAnnotations() {
        checkState();
        
        if (activeDescriptor != null) {
            return activeDescriptor.getQualifierAnnotations();
        }
        
        return qualifiers;
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#getInjectees()
     */
    @Override
    public List<Injectee> getInjectees() {
        checkState();
        
        if (activeDescriptor != null) {
            return activeDescriptor.getInjectees();
        }
        
        return creator.getInjectees();
    }
    
    private void invokeInstanceListeners(InstanceLifecycleEvent event) {
        for (InstanceLifecycleListener listener : instanceListeners) {
            listener.lifecycleEvent(event);
        }
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#create(org.glassfish.hk2.api.ServiceHandle)
     */
    @SuppressWarnings("unchecked")
    @Override
    public T create(ServiceHandle<?> root) {
        checkState();
        
        InstanceLifecycleEventImpl event;
        if (activeDescriptor != null) {
            event = new InstanceLifecycleEventImpl(InstanceLifecycleEventType.POST_PRODUCTION,
                    activeDescriptor.create(root));
        }
        else {
            event = creator.create(root);
        }
        
        event.setActiveDescriptor(this);
        
        // invoke the listeners
        invokeInstanceListeners(event);
        
        return (T) event.getLifecycleObject();
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.ActiveDescriptor#dispose(java.lang.Object, org.glassfish.hk2.api.ServiceHandle)
     */
    @Override
    public void dispose(T instance) {
        checkState();
        
        InstanceLifecycleEventImpl event = new InstanceLifecycleEventImpl(
                InstanceLifecycleEventType.PRE_DESTRUCTION,
                instance);
        event.setActiveDescriptor(this);
        
        // invoke listeners BEFORE destroying the instance
        invokeInstanceListeners(event);
        
        if (activeDescriptor != null) {
            activeDescriptor.dispose(instance);
            return;
        }
        
        creator.dispose(instance);
    }
    
    private void checkState() {
        if (!reified) throw new IllegalStateException();
    }
    
    /* package */ void reify(Class<?> implClass, ServiceLocatorImpl locator, Collector collector) {
        this.implClass = implClass;
        
        if (getDescriptorType().equals(DescriptorType.CLASS)) {
            qualifiers = Collections.unmodifiableSet(
                    Utilities.getAllQualifiers(implClass,
                            baseDescriptor.getName(),
                            collector));
            
            creator = new ClazzCreator<T>(locator, implClass, this, collector);
            
            scope = Utilities.getScopeAnnotationType(implClass, collector);
            contracts = Collections.unmodifiableSet(ReflectionHelper.getTypeClosure(implClass,
                    baseDescriptor.getAdvertisedContracts()));
        }
        else {
            Utilities.checkFactoryType(implClass, collector);
            
            // For a factory base stuff off of the method, not the class
            Method provideMethod = Utilities.getFactoryProvideMethod(implClass);
            if (provideMethod == null) {
                collector.addThrowable(new IllegalArgumentException("A non-factory descriptor returned type FACTORY"));
                
                // Do not continue, all is lost
                return;
            }
            
            qualifiers = Collections.unmodifiableSet(
                    Utilities.getAllQualifiers(
                            provideMethod,
                            Utilities.getDefaultNameFromMethod(provideMethod, collector),
                            collector));
            
            creator = new FactoryCreator<T>(locator, implClass);
            
            scope = Utilities.getScopeAnnotationType(provideMethod, collector);
            
            Type factoryProvidedType = provideMethod.getGenericReturnType();
            contracts = Collections.unmodifiableSet(ReflectionHelper.getTypeClosure(factoryProvidedType,
                    baseDescriptor.getAdvertisedContracts()));
        }
        
        // Check the scope
        if ((baseDescriptor.getScope() == null) && (scope == null)) {
            scope = PerLookup.class;
        }
        
        if (baseDescriptor.getScope() != null && scope != null) {
            String scopeName = scope.getName();
            
            if (!scopeName.equals(baseDescriptor.getScope())) {
                collector.addThrowable(new IllegalArgumentException("The scope name given in the descriptor (" +
                        baseDescriptor.getScope() + 
                        ") did not match the scope annotation on the class (" + scope.getName() +
                        ") in class " + Pretty.clazz(implClass)));
                
            }
        }
        
        if (!collector.hasErrors()) {
            reified = true;
        }
        else {
            collector.addThrowable(new IllegalArgumentException("Errors were discovered while reifying " + this));
        }
    }
    
    @Override
    public int hashCode() {
        int low32 = id.intValue();
        int high32 = (int) (id.longValue() >> 32);
        
        int locatorLow32 = locatorId.intValue();
        int locatorHigh32 = (int) (locatorId.longValue() >> 32);
        
        return baseDescriptor.hashCode() ^ low32 ^ high32 ^ locatorLow32 ^ locatorHigh32;
    }
    
    @SuppressWarnings({ "rawtypes" })
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof SystemDescriptor)) return false;
        
        SystemDescriptor sd = (SystemDescriptor) o;
        
        if (!sd.getServiceId().equals(id)) return false;
        
        if (!sd.getLocatorId().equals(locatorId)) return false;
        
        return sd.baseDescriptor.equals(baseDescriptor);
    }
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.api.Descriptor#getLocatorId()
     */
    @Override
    public Long getLocatorId() {
        return locatorId;
    }
    
    /**
     * Gets the decision of the filter from this service.  May use
     * a cache
     * 
     * @param service The service to get the isValidating decision from
     * @return true if this validation service should validate this descriptor
     */
    /* package */ boolean isValidating(ValidationService service) {
        Boolean cachedResult = validationServiceCache.get(service);
        if (cachedResult != null) {
            return cachedResult.booleanValue();
        }
        
        boolean decision = true;
        try {
            decision = service.getLookupFilter().matches(this);
        }
        catch (Throwable th) {
            // If the filter fails we assume the decision is true
        }
        
        if (decision) {
            validationServiceCache.put(service, Boolean.TRUE);
        }
        else {
            validationServiceCache.put(service, Boolean.FALSE);
        }
        
        return decision;
    }
    
    private boolean filterMatches(Filter filter) {
        if (filter == null) return true;
        
        if (filter instanceof IndexedFilter) {
            IndexedFilter indexedFilter = (IndexedFilter) filter;
            
            String indexContract = indexedFilter.getAdvertisedContract();
            if (indexContract != null) {
                if (!baseDescriptor.getAdvertisedContracts().contains(indexContract)) {
                    return false;
                }
            }
            
            String indexName = indexedFilter.getName();
            if (indexName != null) {
                if (baseDescriptor.getName() == null) return false;
                
                if (!indexName.equals(baseDescriptor.getName())) {
                    return false;
                }
            }
            
            // After all that we can run the match method!
        }
        
        return filter.matches(this);
    }
    
    /* package */ void reupInstanceListeners(List<InstanceLifecycleListener> listeners) {
        instanceListeners.clear();
        
        for (InstanceLifecycleListener listener : listeners) {
            Filter filter = listener.getFilter();
            if (filterMatches(filter)) {
                instanceListeners.add(listener);
            }
        }
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer("SystemDescriptor(");
        
        DescriptorImpl.pretty(sb, this);
        
        sb.append("\n\treified=" + reified);
        
        sb.append(")");
        
        return sb.toString();
    }

     
}
