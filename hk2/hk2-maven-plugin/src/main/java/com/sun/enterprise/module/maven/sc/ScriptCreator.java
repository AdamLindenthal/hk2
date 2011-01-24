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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.enterprise.module.maven.sc;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.sun.enterprise.module.maven.sc.ScriptConstants.*;

/** The entry-point into the script-creation classes. It parses the sections
 *  in the given config file and writes them out into the destination folder
 *  as executable script.
 *
 * @author Kedar Mhaswade (km@dev.java.net)
 */
public final class ScriptCreator {
    
    private final File src;
    private final File dest;
    private final Properties env;
    
    private Map<String, Section> sections;
    
    public ScriptCreator(Properties env) {
        if (env == null)
            throw new IllegalArgumentException("Null arguments");
        this.env    = env;
        this.src  = new File(env.getProperty(SRC));
        this.dest = new File(env.getProperty(DEST));
        if (!src.exists() || !dest.getParentFile().exists())
            throw new IllegalArgumentException("Either of these does not exist: "
                    + src.getAbsolutePath() + ", " + dest.getParentFile().getAbsolutePath());
        this.sections = new HashMap<String, Section>();
    }
    
    public void create() throws ScriptCreationException {
        try {
            parseSections();
            writeSections();
        } catch(Exception e) {
            throw new ScriptCreationException(e);
        }
    }
    
    //// Private methods ////
    
    private void parseSections() throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(src));
            String line;
            Section s = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(COMMENT0) || isEmpty(line))
                    continue;
                if (isSection(line)) {
                    String name = line.substring(1, line.length() - 1);
                    s = new Section(name, env);
                    sections.put(name, s);
                }
                else {
                    s.put(line);
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch(IOException e) {
                    //ignore
                }
            }
        }
    }
    private boolean isEmpty(String line) {
        return ( line.trim().length() == 0 ); //has all white spaces
    }
    private boolean isSection(String line) {
        return ( line.startsWith(SECTION_START) && line.endsWith(SECTION_END) );
    }
    
    private void writeSections()throws IOException {
        PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(dest))){
            @Override
            public void println(String s) {
                if (windows()) {
                    super.print(backwards(s) + "\r\n"); //This is upon Byron's "notepad" request, he should really use vi
                } else {
                    super.println(forwards(s));
                }
            }
        };
        try {
            writePreamble(sections.get(COPYRIGHT_SECTION), writer);
            writeHeader(sections.get(HEADER_SECTION), writer);
            writeSourceFiles(sections.get(SOURCE_SECTION), writer);
            writeEnvironmentVariables(sections.get(ENVVARS_SECTION), writer);
            writeShellVariables(sections.get(SHELLVARS_SECTION), writer);
            writeJava(sections.get(JVM_SECTION), sections.get(CLASSPATH_SECTION), sections.get(SYS_PROPS_SECTION), writer);
            writeEpilog(writer);
        } finally {
            if (writer != null)
                writer.close();
        }
    }
    
    private void writePreamble(Section cprsec, PrintWriter writer) {
        if (windows()) {
            writer.println("@echo off");
            writer.println(commentIt(getCopyrightText(cprsec)));
            writer.println("setlocal");
        } else {
            writer.println(DEFAULT_SHELL_PATH_VALUE);
            writer.println(commentIt(getCopyrightText(cprsec)));
        }
    }
    
    private void writeEpilog(PrintWriter writer) {
        if (windows()) {
            writer.println("endlocal");
        }
    }
    
    private void writeHeader(Section header, PrintWriter writer) {
        Map<String, String> props = null;
        if (header == null || ((props = header.getProps()) == null))
            return;
        String line = null;
        for (String value : props.values()) {
            line = commentIt(value);
            writer.println(line);
        }
    }
    
    private String commentIt(String s) {
        if (windows())
            return "REM " + s;
        else
            return ( COMMENT0 + s );

    }
    private static String getCopyrightText(Section cps) {
        String text = "";
        if (cps != null) {
            Map<String, String> props = cps.getProps();
            for (String value : props.values()) {
                text += value + " ";
            }
        }
        return ( text );
    }
    
    private void writeSourceFiles(Section s, PrintWriter writer) {
        Map<String, String>props = null;
        if (s == null || (props = s.getProps()) == null)
            return; //no such section, or no properties available
        String line = null;
        for (String value : props.values()) {
            if (windows()) {
                line = "call " + value + WIN_SCRIPT_EXTENSION;
            } else { //only Windows behaves differently
                line = ". " + value;
            }
            writer.println(line);
        }        
    }
    
    private void writeEnvironmentVariables(Section s, PrintWriter writer) {
        Map<String, String>props = null;
        if (s == null || (props = s.getProps()) == null)
            return; //no such section, or no properties available
        String line = null;
        for (String key : props.keySet()) {
            if (windows()) {
                line = "set " + key + "=" + props.get(key);
            } else { //only Windows behaves differently
                line = "export " + key + "=" + props.get(key);
            }
            writer.println(line);
        }
        //now write special if block for variable that points to the "java" executable
        writeJavaBlock(writer);
    }
    private void writeJavaBlock(PrintWriter writer) {
        if (windows()) {
            writer.println("if defined JAVA_HOME (");
            writer.println("set JAVA=%JAVA_HOME%/bin/java");
            writer.println(") else (");
            writer.println("set JAVA=java");
            writer.println(")");
        } else {
            writer.println("if [ ${JAVA_HOME}abc = \"abc\" ]"); 
            writer.println("then"); 
            writer.println("  export JAVA=${JAVA_HOME}/bin/java");
            writer.println("else");
            writer.println("  export JAVA=java");
        }
    }
    private void writeShellVariables(Section s, PrintWriter writer) {
        Map<String, String> props = null;
        if (s == null || (props = s.getProps()) == null)
            return; //no such section, or no properties available        
        String line = null;
        for (String key : props.keySet()) {
            if (windows()) {
                line = "set " + key + "=" + props.get(key);
            } else { //only Windows behaves differently
                line = key + "=" + props.get(key);
            }
            writer.println(line);
        }
    }
    
    private void writeJava(Section jvm, Section cp, Section sysPropsSection, PrintWriter writer) {
        if (jvm == null) {
            return; // if [jvm] section is absent, just return
        }
        String javaPath      = getJavaPath() + " ";
        String jvmOptions    = "";
        if (jvm.getProperty(JVM_OPTS_PROP) != null)
            jvmOptions = jvm.getProperty(JVM_OPTS_PROP) + " ";
        String thingThatRuns = getMainClassOrJarFile(jvm) + " ";
        String classpath     = getClasspathStr(cp) + " ";
        String systemProps   = getSystemProps(sysPropsSection) + " ";
        String params        = getParams();
        StringBuffer sb      = new StringBuffer();
        sb = sb.append(javaPath).append(jvmOptions).append(classpath).append(systemProps).append(thingThatRuns).append(params);
        writer.println(sb.toString());
    }
    
    private String getJavaPath() {
        if (windows()) {
            return "%JAVA%";
        } else {
            return "$JAVA";
        }
    }

    private String getMainClassOrJarFile(Section jvm) {
        Map<String, String> props = jvm.getProps();
        String runner = null;
        String jar   = props.get(MAIN_JAR_PROP);
        if (jar != null) {
            runner = "-jar " + jar;
        } else {
            String mc = props.get(MAIN_CLASS_PROP);
            if (mc == null) {
                throw new IllegalArgumentException("Either: " + MAIN_JAR_PROP + " or " + MAIN_CLASS_PROP + "needs to be specified");
            }
            runner = mc;
        }
        return ( runner );
    }
    
    private String getClasspathStr(Section cp) {
        Map<String, String> props = null;
        if (cp == null || (props = cp.getProps())== null)
            return "";
        String delim = null;
        if (windows())
            delim = ";"; //only Windows behaves differently
        else
            delim = ":";
        StringBuilder sb = new StringBuilder("-cp ");
        int i = 0;
        for (String value : props.values()) {
            value = quote(value);
            if (i++ < props.size() - 1)
                value = value + delim;
            sb.append(value);
        }
        return ( sb.toString() );
    }
    private String getSystemProps(Section section) {
        Map<String, String> sp = null;
        String line = "";
        if (section == null || (sp = section.getProps()) == null)
            return ( line );
        
        for(String key : sp.keySet()) {
            line += quote("-D" + key + "=" + sp.get(key));
            line += " ";
        }
        return ( line );
    }
    
    private String getParams() {
        if (windows())
            return quote("%*");
        else
            return quote("${@}");
    }
    
    boolean windows() {
        return ( (WINDOWS.equals(env.get(OPERATING_SYSTEM))) );
    }
    
    private static String quote(String s) {
        if (s != null) {
            s = "\"" + s + "\"";
        }
        return ( s );
    }
    
    private static String forwards(String s) {
        if (s != null) {
            s = s.replace('\\', '/'); // replaceAll takes a "regex"!
        }
        return ( s );
    }
    
    private static String backwards(String s) {
        if (s != null) {
            s = s.replace('/', '\\');
        }
        return ( s );        
    }
}
