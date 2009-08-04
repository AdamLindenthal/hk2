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
package com.sun.enterprise.module.single;

import com.sun.enterprise.module.bootstrap.StartupContext;
import java.io.File;
import java.util.List;

import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Inhabitants;

/**
 * Implementation of the modules registry that use a single class loader to load
 * all available classes. There is one virtual module available in the modules
 * registry and that module's class loader is the single class loader used to
 * load all artifacts.
 *
 * @author Jerome Dochez
 */
public class StaticModulesRegistry extends SingleModulesRegistry {

    final private StartupContext startupContext; 

    public StaticModulesRegistry(ClassLoader singleCL) {
        super(singleCL);
        startupContext = null;
    }

    public StaticModulesRegistry(ClassLoader singleCL, StartupContext startupContext) {
        super(singleCL);
        this.startupContext = startupContext;
    }

    public StaticModulesRegistry(ClassLoader singleCL, List<ManifestProxy.SeparatorMappings> mappings, StartupContext startupContext) {
        super(singleCL, mappings);
        this.startupContext = startupContext;
    }

    @Override
    protected void populateConfig(Habitat habitat) {
        // do nothing...
    }

    @Override                         
    public Habitat createHabitat(String name) throws ComponentException {

        StartupContext sc = startupContext;
        Habitat habitat = super.newHabitat();

        if (startupContext==null) {
            File dir = new File(System.getProperty("java.io.tmpdir"));
            sc = new StartupContext(dir, new String[0]);
        }
        super.createHabitat("default", habitat);
        habitat.add(Inhabitants.create(sc));
        return habitat;
    }

}
