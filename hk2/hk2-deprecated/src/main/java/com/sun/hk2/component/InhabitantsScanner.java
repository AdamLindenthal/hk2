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
package com.sun.hk2.component;

import org.jvnet.hk2.component.ComponentException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

/**
 * Scans the stream that follows the <tt>/META-INF/inhabitants/*</tt> format.
 *
 * <p>
 * This class implements {@link Iterable} so that it can be used in for-each loop,
 * but it cannot parse the same stream twice.
 *
 * @author Kohsuke Kawaguchi
 */
@Deprecated
public class InhabitantsScanner implements Iterable<InhabitantParser>, Closeable {
    private int lineNumber = 0;
    private final String systemId;
    private BufferedReader r;
    private final KeyValuePairParser kvpp = new KeyValuePairParser();

    public InhabitantsScanner(InputStream in, String systemId) throws IOException {
        r = new BufferedReader(new InputStreamReader(in,"UTF-8"));
        this.systemId = systemId;
    }

    public String getSystemId() {
        return systemId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Iterator<InhabitantParser> iterator() {
        return new Iterator<InhabitantParser>() {
            private String nextLine;

            public boolean hasNext() {
                fetch();
                return nextLine!=null;
            }

            private void fetch() {
                if(null == r || nextLine!=null)  return;

                try {
                    while((nextLine=r.readLine())!=null) {
                        lineNumber++;
                        if(nextLine.startsWith("#"))
                            continue;   // comment

                        return; // found the next line
                    }
                } catch (IOException e) {
                    throw new ComponentException("Failed to parse line " + lineNumber + " of " + systemId,e);
                }
            }

            public InhabitantParser next() {
                fetch();
                kvpp.set(nextLine);
                nextLine=null;
                return new InhabitantFileBasedParser(kvpp);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public void close() throws IOException {
      if (null != r) {
        r.close();
        r = null;
      }
    }
}
