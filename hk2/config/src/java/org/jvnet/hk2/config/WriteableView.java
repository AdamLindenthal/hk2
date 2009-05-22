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
package org.jvnet.hk2.config;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.util.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.Validator;
import javax.validation.ValidatorContext;
import javax.validation.BeanDescriptor;

/**
 * A WriteableView is a view of a ConfigBean object that allow access to the
 * setters of the ConfigBean.
 *
 * @author Jerome Dochez
 */
public class WriteableView implements InvocationHandler, Transactor, ConfigView {

    private final ConfigBean bean;
    private final ConfigBeanProxy defaultView;
    private final Map<String, PropertyChangeEvent> changedAttributes;
    private final Map<String, ProtectedList> changedCollections;
    Transaction currentTx;
    private static Validator beanValidator=null;
    
    public WriteableView(ConfigBeanProxy readView) {
        this.bean = (ConfigBean) ((ConfigView) Proxy.getInvocationHandler(readView)).getMasterView();
        this.defaultView = bean.createProxy();
        changedAttributes = new HashMap<String, PropertyChangeEvent>();
        changedCollections = new HashMap<String, ProtectedList>();
        if (beanValidator == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(null);
                ValidatorFactory validatorFactory =
                    Validation.buildDefaultValidatorFactory();
                ValidatorContext validatorContext = validatorFactory.usingContext();
                beanValidator = validatorContext.getValidator();
            } finally {
                Thread.currentThread().setContextClassLoader(cl);
            }
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if(method.getAnnotation(DuckTyped.class)!=null) {
            return bean.invokeDuckMethod(method,proxy,args);
        }        
        ConfigModel.Property property = bean.model.toProperty(method);
        if(property==null)
            throw new IllegalArgumentException("No corresponding property found for method: "+method);

        if(args==null || args.length==0) {
            // getter, maybe one of our changed properties
            if (changedAttributes.containsKey(property.xmlName())) {
                // serve masked changes.
                return changedAttributes.get(property.xmlName()).getNewValue();
            } else {
                // pass through.
                return getter(property, method.getGenericReturnType());
            }
        } else {
            setter(property, args[0], method.getGenericParameterTypes()[0]);
            return null;
        }
    }

    public String getPropertyValue(String propertyName) {

        ConfigModel.Property prop = this.getProperty(propertyName);
        if (prop!=null) {
            if (changedAttributes.containsKey(prop.xmlName())) {
                // serve masked changes.
                return (String) changedAttributes.get(prop.xmlName()).getNewValue();
            } else {
                return (String) getter(prop, String.class);
            }
        }
        return null;
    }

    public Object getter(ConfigModel.Property property, java.lang.reflect.Type t) {
        Object value =  bean._getter(property, t);
        if (value instanceof List) {
            if (!changedCollections.containsKey(property.xmlName())) {
                // wrap collections so we can record events on that collection mutation.
                changedCollections.put(property.xmlName(),
                        new ProtectedList((List) value, defaultView, property.xmlName()));
            }
            return changedCollections.get(property.xmlName());
        }
        return value;
    }

    public void setter(ConfigModel.Property property,
        Object newValue, java.lang.reflect.Type t)  {
        
        // are we still in a transaction
        if (currentTx==null) {
            throw new IllegalStateException("Not part of a transation");
        }
        try {
            if (newValue != null)
                handleValidation(property, newValue);
        } catch(Exception v) {
            bean.getLock().unlock();
            throw new RuntimeException(v);
        }

        // Following is a check to avoid duplication of elements with same key
        // attribute values. See Issue 7956
        if (property instanceof ConfigModel.AttributeLeaf) {

            // First check if the key leaf attribute is being set
            ConfigModel.AttributeLeaf al =
                (ConfigModel.AttributeLeaf)property;

            ConfigBean master = getMasterView();
            String key = master.model.key;

            // A key attribute may not exist at all if none of the attribs of
            // an element are annotated with key=true. If one exists, make sure
            // that attribute is actually the one being set
            if ((key != null) && (key.substring(1).equals(property.xmlName))) {

                // remove leading @
                key = key.substring(1);
                // Extract the old key value
                String oldKeyValue = getPropertyValue(key);

                // Get the Parent Element which has the key attribute specified
                // through the input paramater 'property'. For e.g. in case of
                // Domain->Resources->ConnectorConnectionPool->name(key attrib)
                // thisview will equal ConnectorConnectionPool
                Dom thisview = Dom.unwrap(defaultView);

                // parent will equal Resources
                Dom parent = thisview.parent();

                // siblings will contain all ConnectorConnectionPools under
                // Resources
                List<Dom> siblings = 
                    parent.domNodeByTypeElements(thisview.getProxyType());

                // Iterate through each sibling element and see if anyone has
                // same key. If true throw an exception after unlocking this
                // element
                for (Dom sibling : siblings) {
                    String siblingKey = sibling.getKey();
                    if (newValue.equals(siblingKey)) {
                        bean.getLock().unlock();
                        throw new IllegalArgumentException(
                            "Keys cannot be duplicate. Old value of this key " +
                            "property, " + oldKeyValue + "will be retained");
                    }
                }
            }
        }

        // setter
        Object oldValue = bean.getter(property, t);
        if (newValue instanceof ConfigBeanProxy) {
            ConfigView bean = (ConfigView)
                Proxy.getInvocationHandler((ConfigBeanProxy) newValue);
            newValue = bean.getMasterView();
        }
        PropertyChangeEvent evt = new PropertyChangeEvent(
            defaultView,property.xmlName(), oldValue, newValue);
        changedAttributes.put(property.xmlName(), evt);
    }

    public ConfigModel.Property getProperty(String xmlName) {
        return bean.model.findIgnoreCase(xmlName);
    }

    /**
     * Enter a new Transaction, this method should return false if this object
     * is already enlisted in another transaction, or cannot be enlisted with
     * the passed transaction. If the object returns true, the object
     * is enlisted in the passed transaction and cannot be enlisted in another
     * transaction until either commit or abort has been issued.
     *
     * @param t the transaction to enlist with
     * @return true if the enlisting with the passed transaction was accepted,
     *         false otherwise
     */
    public synchronized boolean join(Transaction t) {
        if (currentTx==null) {
            currentTx = t;
            t.addParticipant(this);
            return true;
        }
        return false;
    }

    /**
     * Returns true of this Transaction can be committed on this object
     *
     * @param t is the transaction to commit, should be the same as the
     *          one passed during the join(Transaction t) call.
     * @return true if the trsaction commiting would be successful
     */
    public synchronized boolean canCommit(Transaction t) {
        return currentTx==t;
    }

    /**
     * Commit this Transaction.
     *
     * @param t the transaction commiting.
     * @throws TransactionFailure
     *          if the transaction commit failed
     */
    public synchronized List<PropertyChangeEvent> commit(Transaction t) throws TransactionFailure {
        if (currentTx==t) {
            currentTx=null;
        }
        try {
            List<PropertyChangeEvent> appliedChanges = new ArrayList<PropertyChangeEvent>();
            for (PropertyChangeEvent event : changedAttributes.values()) {
                ConfigModel.Property property = bean.model.findIgnoreCase(event.getPropertyName());
                ConfigBeanInterceptor interceptor  = bean.getOptionalFeature(ConfigBeanInterceptor.class);
                try {
                    if (interceptor!=null) {
                        interceptor.beforeChange(event);
                    }
                } catch (PropertyVetoException e) {
                    throw new TransactionFailure(e.getMessage(), e);
                }
                property.set(bean, event.getNewValue());
                if (interceptor!=null) {
                    interceptor.afterChange(event, System.currentTimeMillis());
                }
                appliedChanges.add(event);
            }
            for (ProtectedList entry :  changedCollections.values())  {
                List originalList = entry.readOnly;
                for (PropertyChangeEvent event : (List<PropertyChangeEvent>) entry.changeEvents) {
                    if (event.getOldValue()==null) {
                        originalList.add(event.getNewValue());
                    } else {
                        final Object toBeRemovedObj = event.getOldValue();
                        if ( toBeRemovedObj instanceof ConfigBeanProxy ) {
                            final Dom toBeRemoved = Dom.unwrap((ConfigBeanProxy)toBeRemovedObj);
                            for (int index=0;index<originalList.size();index++) {
                                Object element = originalList.get(index);
                                Dom dom = Dom.unwrap((ConfigBeanProxy) element);
                                if (dom==toBeRemoved) {
                                    originalList.remove(index);
                                }
                            }
                        }
                        else if ( toBeRemovedObj instanceof String ) {
                            final String toBeRemoved = (String)toBeRemovedObj;
                            for (int index=0;index<originalList.size();index++) {
                                final String item = (String)originalList.get(index);
                                if (item.equals(toBeRemoved)) {
                                    originalList.remove(index);
                                }
                            }
                        }
                        else {
                            throw new IllegalArgumentException();
                        }
                    }
                    appliedChanges.add(event);
                }
            }
            changedAttributes.clear();
            return appliedChanges;
        } catch(TransactionFailure e) {
            throw e;
        } catch(Exception e) {
            throw new TransactionFailure(e.getMessage(), e);
        } finally {
            bean.getLock().unlock();
        }

    }

    /**
     * Aborts this Transaction, reverting the state
     *
     * @param t the aborting transaction
     */
    public synchronized void abort(Transaction t) {
        currentTx=null;
        bean.getLock().unlock();
        changedAttributes.clear();

    }

    /**
     * Allocate a new ConfigBean object as part of the Transaction
     * associated with this configuration object. This will eventually
     * be moved to a factory.
     *
     * @param type the request configuration object type
     * @return the propertly constructed configuration object
     * @throws TransactionFailure if the allocation failed 
     */

    public <T extends ConfigBeanProxy> T allocateProxy(Class<T> type) throws TransactionFailure {
        if (currentTx==null) {
            throw new TransactionFailure("Not part of a transaction", null);
        }
        ConfigBean newBean = bean.allocate(type);
        WriteableView writeableView = bean.getHabitat().getComponent(ConfigSupport.class).getWriteableView(newBean.getProxy(type), newBean);
        writeableView.join(currentTx);

        return writeableView.getProxy(type);
   }

    public ConfigBean getMasterView() {
        return bean;
    }

    public void setMasterView(ConfigView view) {

    }

    public <T extends ConfigBeanProxy> Class<T> getProxyType() {
        return bean.getProxyType();
    }

    @SuppressWarnings("unchecked")    
    public <T extends ConfigBeanProxy> T getProxy(Class<T> type) {
        final ConfigBean sourceBean = getMasterView();
        if (!(type.getName().equals(sourceBean.model.targetTypeName))) {
            throw new IllegalArgumentException("This config bean interface is " + sourceBean.model.targetTypeName
                    + " not "  + type.getName());
        }
        Class[] interfacesClasses = { type };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), interfacesClasses, this);
    }

/**
 * A Protected List is a @Link java.util.List implementation which mutable
 * operations are constrained by the owner of the list.
 *
 * @author Jerome Dochez
 */
private class ProtectedList extends AbstractList {

    final ConfigBeanProxy readView;
    final List readOnly;
    final String id;
    final List<PropertyChangeEvent> changeEvents = new ArrayList<PropertyChangeEvent>();
    final List proxied;

    ProtectedList(List readOnly, ConfigBeanProxy parent, String id) {
        proxied = Collections.synchronizedList(new ArrayList(readOnly));
        this.readView = parent;
        this.readOnly = readOnly;
        this.id = id;
    }

    /**
     * Returns the number of elements in this collection.  If the collection
     * contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of elements in this collection.
     */
    public int size() {
        return proxied.size();
    }

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index index of element to return.
     * @return the element at the specified position in this list.
     * @throws IndexOutOfBoundsException if the given index is out of range
     *                                   (<tt>index &lt; 0 || index &gt;= size()</tt>).
     */
    public Object get(int index) {
        return proxied.get(index);
    }

    @Override
    public synchronized boolean add(Object object) {
        Object param = object;
        Object handler = null;
        try {
            handler = Proxy.getInvocationHandler(object);
        } catch(IllegalArgumentException e) {
            // ignore, this is a leaf
        }
        if (handler!=null && handler instanceof WriteableView) {
            ConfigBean master = ((WriteableView) handler).getMasterView();
            String key = master.model.key;
            if (key!=null) {
                // remove leading @
                key = key.substring(1);
                // check that we are not adding a duplicate key element
                String keyValue = ((WriteableView) handler).getPropertyValue(key);
                for (Object o : proxied) {
                    // the proxied object can be a read-only or a writeable view, we need
                    // to be careful
                    // ToDo : we need to encasulate this test.
                    String value;
                    if (Proxy.getInvocationHandler(o) instanceof WriteableView) {
                        value = ((WriteableView) Proxy.getInvocationHandler(o)).getPropertyValue(key);
                    }  else {
                        Dom cbo = Dom.unwrap((ConfigBeanProxy) o);
                        value = cbo.attribute(key);
                    }
                    if (keyValue!=null && keyValue.equals(value)) {
                        Dom parent = Dom.unwrap(readView);
                        throw new IllegalArgumentException("A " + master.getProxyType().getSimpleName() +
                                " with the same key \"" + keyValue + "\" already exists in " +
                                parent.getProxyType().getSimpleName() + " " + parent.getKey()) ;

                    }
                }
            }
            param = ((WriteableView) handler).getMasterView().createProxy(master.type());

        }
        changeEvents.add(new PropertyChangeEvent(defaultView, id, null, param));
        return proxied.add(object);
    }

    @Override
    public synchronized void clear() {
        // make a temporary list, iterating while removing doesn't work
        final List allItems = new ArrayList( proxied );
        for( final Object item : allItems ) {
            remove( item );
        }
    }
    
    @Override
    public synchronized boolean retainAll( final Collection keepers ) {
        final List toRemoveList = new ArrayList();
        for( final Object iffy : proxied ) {
            if ( ! keepers.contains(iffy) ) {
                toRemoveList.add(iffy);
            }
        }
        final boolean changed = removeAll(toRemoveList);
        
        return changed;
    }
    
    @Override
    public synchronized boolean removeAll( final Collection goners ) {
        boolean listChanged = false;
        for( final Object goner : goners ) {
            if ( remove(goner) ) {
                listChanged = true;
            }
        }

        return listChanged;
    }

    @Override
    public synchronized boolean remove(Object object) {
        changeEvents.add(new PropertyChangeEvent(defaultView, id, object, null));

        try {
            ConfigView handler = ((ConfigView) Proxy.getInvocationHandler(object)).getMasterView();
            for (int index = 0 ; index<proxied.size() ; index++) {
                Object target = proxied.get(index);
                try {
                    ConfigView targetHandler = ((ConfigView) Proxy.getInvocationHandler(target)).getMasterView();
                    if (targetHandler==handler) {
                        return (proxied.remove(index)!=null);
                    }
                } catch(IllegalArgumentException ex) {
                    // ignore
                }
            }
        } catch(IllegalArgumentException e) {
            // ignore, this is a leaf
            return proxied.remove(object);

        }
        return false;
    }

}

    private String toCamelCase(String xmlName) {
        StringTokenizer st =  new StringTokenizer(xmlName, "-");
        StringBuffer camelCaseName = null;
        if (st.hasMoreTokens()) {
            camelCaseName = new StringBuffer(st.nextToken());
        }
        StringBuffer sb = null;
        while (st.hasMoreTokens()) {
            sb = new StringBuffer(st.nextToken());
            char startChar = sb.charAt(0);
            sb.setCharAt(0,Character.toUpperCase(startChar));
            camelCaseName.append(sb);
        }
        return camelCaseName.toString();
    }
    
    private void handleValidation(ConfigModel.Property property, Object value)
    throws ValidationException { 

        // First check for dataType constraints -- as was done for v3 Prelude
        // see PrimitiveDataType.java
        // These validations could be transformed into BV custom annotations
        // such as AssertBoolean, AssertInteger etc. But since GUI and other
        // config clients such as AMX need dataType key in @Attribute it's been
        // decided to validate using existing annotation information
        if (property instanceof ConfigModel.AttributeLeaf) {
            ConfigModel.AttributeLeaf al = (ConfigModel.AttributeLeaf)property;
            validateDataType(al, value.toString());
        }

        // Next check for other non-dataType annotations expressed as
        // BV annotations
        BeanDescriptor bd =
            beanValidator.getConstraintsForClass(bean.getProxyType());

        Set constraintViolations = beanValidator.validateValue(
            bean.getProxyType(), toCamelCase(property.xmlName()), value);

        if (constraintViolations != null) {
            Iterator it = constraintViolations.iterator();
            boolean violated = false;
            String msg = "Constraints for this bean " +
                   "violated. \n Message = ";
            while (it.hasNext()) {
                violated = true;
                ConstraintViolation cv = (ConstraintViolation) it.next();
                msg = msg + cv.getPropertyPath() + " " + cv.getMessage();
            }
            if (violated) {
                System.out.println(msg); // todo: needs to be removed
                throw new ValidationException(msg);
            }
        }               
    }

    private void validateDataType(ConfigModel.AttributeLeaf al, String value)
    throws ValidationException {

        if (value.startsWith("${") && value.endsWith("}"))
          return;

        boolean isValid = String.class.getName().equals(al.dataType);
        if ("int".equals(al.dataType) ||
            "java.lang.Integer".equals(al.dataType))
            isValid = representsInteger(value);
        else if ("boolean".equals(al.dataType) ||
                 "java.lang.Boolean".endsWith(al.dataType))
            isValid = representsBoolean(value);
        else if ("char".equals(al.dataType) ||
                 "java.lang.Character".equals(al.dataType))
            isValid = representsChar(value);        
        if (!isValid) {
            String msg = "Validation Failed: " +
                         value + " is not of data type: " + al.dataType;
            throw new ValidationException(msg);            
        }
    }
    
    private boolean representsBoolean(String value) {
        boolean isBoolean = 
           "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        return (isBoolean);
    }

    private boolean representsChar(String value) {
            if (value.length() == 1)
                return true;
            return false;
    }

    private boolean representsInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch(NumberFormatException ne) {
            return false;
        }
    }

}