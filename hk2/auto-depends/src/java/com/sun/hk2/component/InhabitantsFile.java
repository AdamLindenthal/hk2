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

import org.jvnet.hk2.annotations.InhabitantAnnotation;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.component.CageBuilder;

/**
 * Inhabitants file.
 *
 * <p>
 * Inhabitants file describe inhabitants (objects) that are to be placed into the habitat.
 * This file is generated by the APT processor, which is integrated transparently into
 * the build process by the HK2 maven plugin.
 *
 * <p>
 * The file is by convention placed into <tt>/{@value #PATH}/xyz</tt> where 'xyz'
 * portion is {@link InhabitantAnnotation#value() the identifier of the habitat}.
 * This allows multiple different habitats to be created over the same set of classes.
 * For example, there can be one habitat for the whole GF, then there are smaller habitats
 * for each JAX-WS deployment.
 *
 * <h2>Format of the inhabitants file</h2>
 * <p>
 * The file is a UTF-8 encoded text file, and processing is line-based. A line
 * that starts with '#' is treated as a comment and ignored.
 * Other lines are assumed to be in <tt>key=value,key=value,...</tt> format.
 * <tt>=value</tt> portion is optional, and this can be used to design keys
 * that are conceptually boolean. The same key can appear multiple times.s
 *
 * <p>
 * The following keys are defined:
 *
 * <table>
 * <tr>
 * <td>Key</td>
 * <td>Value</td>
 * </tr>
 *
 * <tr>
 * <td>{@value #CLASS_KEY}</td>
 * <td>
 * The fully qualified class name of the inhabitant.
 * </td>
 * </tr>
 *
 * <tr>
 * <td>{@value #INDEX_KEY}</td>
 * <td>
 * The index under which the inhabitant is registered.
 * Multiple values allowed. A value is of the form:
 * {@code PRIMARYNAME[:SUBNAME]}.
 *
 * This is used for all kinds of indexing needs, including
 * {@link Contract} (where PRIMARYNAME is the FQCN of the contract name
 * and SUBNAME is the component name.)
 * </td>
 * </tr>
 *
 * @author Kohsuke Kawaguchi
 */
public class InhabitantsFile {
    public static final String PATH = "META-INF/inhabitants";

    public static final String CLASS_KEY = "class";
    public static final String INDEX_KEY = "index";
    /**
     * Used as a metadata for inhabitants of {@link CompanionSeed},
     * to indicate what is the actual companion class. 
     */
    public static final String COMPANION_CLASS_KEY = "companionClass";
    /**
     * Used to point to {@link CageBuilder} for this component.
     */
    public static final String CAGE_BUILDER_KEY = "cageBuilder";
    /**
     * Used as metadafa for inhabitants of {@link CompanionSeed} to capture
     * metadata of the actual companioin class.
     */
    public static final String COMPANION_CLASS_METADATA_KEY = "companionClassMetadata";

    /**
     * Used as metadata for indentifying the type on which a {@link InhabitantAnnotation}
     * was annotated.
     */
    public static final String TARGET_TYPE = "targetType";
}
