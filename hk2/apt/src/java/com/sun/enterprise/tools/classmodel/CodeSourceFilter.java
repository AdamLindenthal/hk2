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
package com.sun.enterprise.tools.classmodel;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.jvnet.hk2.component.classmodel.ClassPath;

/**
 * CodeSourceFilter is used for determining if classes are in the ClassPath.
 * 
 * @author Jeff Trent
 * @since 3.1
 */
public class CodeSourceFilter {

  private final ClassPath filter;

  private final HashSet<String> classes = new HashSet<String>();

  public CodeSourceFilter(ClassPath filter) {
    this.filter = filter;
    try {
      initialize();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String toString() {
    return (new StringBuilder()).append(getClass().getSimpleName()).append(":")
        .append(classes.toString()).toString();
  }

  /**
   * @return true if the given className is present in the ClassPath
   */
  public boolean matches(String className) {
    return classes.contains(className);
  }

  private void initialize() throws IOException {
    for (File file : filter.getFileEntries()) {
      if (file.exists()) {
        if (file.isFile()) {
          index(new JarFile(file));
        } else if (file.isDirectory()) {
          index("", file);
        }
      }
    }
  }

  private void index(JarFile jarFile) throws IOException {
    JarEntry entry;
    for (Enumeration<JarEntry> en = jarFile.entries(); en.hasMoreElements(); ) {
      entry = (JarEntry) en.nextElement();
      index(entry.getName().replace("/", "."));
    }
    jarFile.close();
  }

  private void index(String baseName, File directory) {
    File files[] = directory.listFiles();
    if (null == files) {
      return;
    }
    
    for (File file : files) {
      if (file.isHidden()) {
        continue;
      }
      
      if (file.isDirectory()) {
        index((new StringBuilder()).append(baseName).append(
            baseName.isEmpty() ? "" : ".").append(file.getName()).toString(),
            file);
      } else {
        index((new StringBuilder()).append(baseName).append(
            baseName.isEmpty() ? "" : ".").append(file.getName()).toString());
      }
    }
  }

  private void index(String name) {
    if (name.endsWith(".class") && !isnum(name.charAt(0))) {
      classes.add(name.substring(0, name.length() - 6));
    }
  }

  private boolean isnum(char ch) {
    return ch >= '0' && ch <= '9';
  }
}
