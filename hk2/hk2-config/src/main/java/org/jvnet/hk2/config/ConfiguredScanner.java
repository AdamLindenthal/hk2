/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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
package org.jvnet.hk2.config;
import com.sun.hk2.component.Holder;
import com.sun.hk2.component.IntrospectionScanner;
import com.sun.hk2.component.LazyInhabitant;
import org.glassfish.hk2.classmodel.reflect.*;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.deprecated.internal.HolderHK2LoaderImpl;

import java.util.*;

/**
 * Scanner for @Configured annotated classes
 *
 * @author Jerome Dochez
 */
@Service
public class ConfiguredScanner implements IntrospectionScanner {

    @Inject
    Habitat habitat;

    ParsingContext context;


    @Override
    public void parse(ParsingContext context, Holder<ClassLoader> loader) {

        this.context = context;
        AnnotationType configured = (AnnotationType) context.getTypes().getBy(Configured.class.getName());
        if (configured==null) return;

        for (AnnotatedElement ae : configured.allAnnotatedTypes()) {
            if (!(ae instanceof ExtensibleType)) {
                continue;
            }
            Map<String, List<String>> metadata = new HashMap<String, List<String>>();
            parse((ExtensibleType<?>) ae, metadata);

            AnnotationModel c = ae.getAnnotation(Configured.class.getName());
            String elementName = (String) c.getValues().get("value");
            if(elementName==null || elementName.length()==0) { // infer default
                elementName = Dom.convertName(ae.getName().substring(ae.getName().lastIndexOf('.')+1));
            }

            addToMetadata(metadata, ConfigMetadata.TARGET, ae.getName());

            // register the injector.
            LazyInhabitant inhabitant = new LazyInhabitant(habitat, new HolderHK2LoaderImpl(loader), NoopConfigInjector.class.getName(), metadata);
            habitat.addIndex(inhabitant, InjectionTarget.class.getName(), ae.getName());
            habitat.addIndex(inhabitant, ConfigInjector.class.getName(), elementName);
        }
    }

    private void parse(ExtensibleType<?> type, Map<String, List<String>> metadata) {
        Stack<ExtensibleType<?>> q = new Stack<ExtensibleType<?>>();
        Set<Type> visited = new HashSet<Type>();
        q.push(type);

        while (!q.isEmpty()) {
            ExtensibleType<?> t = q.pop();
            if (null == t || !visited.add(t)) continue;   // been here already

            if (t instanceof ClassModel) {
                for (FieldModel f : ((ClassModel) t).getFields()) {
                    generate(f);
                }
            }
            
//            System.out.println("type:" + type + "; t=" + t);
            
            Collection<MethodModel> methods = t.getMethods();
            if (null != methods) {
//            System.out.println("type:" + type + "; methods=" + methods);
              for (MethodModel m : methods) {
                  generate(m, metadata);
              }
            }
            
            Collection<InterfaceModel> interfaces = t.getInterfaces();
            if (null != interfaces) {
//            System.out.println("type:" + type + "; ifaces=" + interfaces);
              for (ExtensibleType<?> child : interfaces) {
                  q.add(child);
              }
            }

            if (t.getParent()!=null) {
                q.add(t.getParent());
            }
        }
    }

    private void generate(FieldModel f) {
      // TODO:
//        throw new RuntimeException("Not implemented");
    }

    private void generate(MethodModel m, Map<String, List<String>> metadata) {
        AnnotationModel attribute = m.getAnnotation(Attribute.class.getName());
        AnnotationModel element = m.getAnnotation(Element.class.getName());

        if (attribute != null) {
            generateAttribute(attribute, m, metadata);
            if (element != null)
                throw new RuntimeException("Cannot have both @Element and @Attribute at the same time on method " + m.getName());
        } else {
            if (element != null)
                generateElement(element, m, metadata);
        }
    }

    private void generateAttribute(AnnotationModel attribute, MethodModel m, Map<String, List<String>> metadata) {
        String name = Dom.convertName(m.getName());
        String xmlTokenName = '@' + name;
        boolean isRequired = Boolean.parseBoolean((String) attribute.getValues().get("isRequired"));
        addToMetadata(metadata, xmlTokenName, isRequired ? "required" : "optional");
        String defaultValue = (String) attribute.getValues().get("defaultValue");
        if (defaultValue!=null && !defaultValue.isEmpty()) {
            if (defaultValue.indexOf(',')!=-1) {
                addToMetadata(metadata, xmlTokenName, '"' + "default:" + defaultValue + '"');
            } else {
                addToMetadata(metadata, xmlTokenName, "default:" + defaultValue);                        
            }
        }
        String signature = m.getSignature();
        String[] arguments = m.getArgumentTypes();
        String refTypeAsString;
        if (arguments.length==0) {
            refTypeAsString = m.getReturnType();
        } else {
            if (arguments.length!=1) {
                throw new RuntimeException("@Attribute method cannot have more than 1 argument " + m.getSignature());
            }
            refTypeAsString = arguments[0];
        }
        // we need to handle collection types
        boolean isCollection = refTypeAsString.startsWith("java.util.List<L");
        if (isCollection) {
            refTypeAsString = refTypeAsString.substring("java.util.List<L".length());
        }
        addToMetadata(metadata, xmlTokenName, "datatype:" + refTypeAsString);
        Type refType = context.getTypes().getBy(refTypeAsString);
        Boolean isReference = (Boolean) attribute.getValues().get("reference");
        if (refType==null || (isReference!=null &&isReference)) {
            // leaf
            addToMetadata(metadata, xmlTokenName, makeCollectionIfNecessary(isCollection, "leaf"));
            if (isReference!=null && isReference) {
                addToMetadata(metadata, xmlTokenName, "reference");
            }
        } else {
            // node
            addToMetadata(metadata, xmlTokenName, makeCollectionIfNecessary(isCollection, refTypeAsString));
        }
        Boolean isKey = (Boolean) attribute.getValues().get("key");
        if (isKey!=null && isKey) {
            addToMetadata(metadata, ConfigMetadata.KEY, xmlTokenName);
            addToMetadata(metadata, ConfigMetadata.KEYED_AS, m.getDeclaringType().getName());
        }
    }

    private String makeCollectionIfNecessary(boolean isCollection, String value) {
        if (isCollection) {
            return "collection:" + value;
        } else {
            return value;
        }
    }

    private void generateElement(AnnotationModel element, MethodModel m, Map<String, List<String>> metadata) {
        String name = Dom.convertName(m.getName());
        String xmlTokenName = "<" + name + ">";
        String[] arguments = m.getArgumentTypes();
        String refTypeAsString;
        if (arguments.length==0) {
            refTypeAsString = m.getReturnType();
        } else {
            if (arguments.length!=1) {
                throw new RuntimeException("@Element method cannot have more than 1 argument " + m.getSignature());
            }
            refTypeAsString = arguments[0];
        }
        boolean isCollection = refTypeAsString.startsWith("java.util.List<L");
        if (isCollection) {
            refTypeAsString = refTypeAsString.substring("java.util.List<L".length());
        }
        Boolean isReference = (Boolean) element.getValues().get("reference");
        Type refType = context.getTypes().getBy(refTypeAsString);
        if (refType==null || (isReference!=null && isReference)) {
            // leaf
            addToMetadata(metadata, xmlTokenName, makeCollectionIfNecessary(isCollection, "leaf"));
            if (isReference!=null && isReference) {
                addToMetadata(metadata, xmlTokenName, "reference");
            }
        } else {
            // node
            addToMetadata(metadata, xmlTokenName, makeCollectionIfNecessary(isCollection, refTypeAsString));
        }
    }
    
    private static final void addToMetadata(Map<String, List<String>> map, String key, String value) {
        List<String> list = map.get(key);
        if (list == null) {
            list = new LinkedList<String>();
        }

        list.add(value);
    }

}
