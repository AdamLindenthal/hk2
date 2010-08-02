/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License. You can obtain
 *  a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *  or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *  Sun designates this particular file as subject to the "Classpath" exception
 *  as provided by Sun in the GPL Version 2 section of the License file that
 *  accompanied this code.  If applicable, add the following below the License
 *  Header, with the fields enclosed by brackets [] replaced by your own
 *  identifying information: "Portions Copyrighted [year]
 *  [name of copyright owner]"
 *
 *  Contributor(s):
 *
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */

package org.glassfish.hk2.classmodel.reflect;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.jar.Manifest;

/**
 * adapter for reading archive style structure
 *
 * @author Jerome Dochez
 */
public interface ArchiveAdapter extends Closeable {

    /**
     * Returns the URI of the archive
     *
     * @return URI of the archive
     */
    public URI getURI();

    /**
     * Returns the manifest instance for the archive.
     *
     * @return the archive's manifest
     * @throws IOException if the manifest cannot be loaded.
     */
    public Manifest getManifest() throws IOException ;

    /**
     * defines the notion of an archive entry task which is a task
     * aimed to be run on particular archive entry.
     */
    public interface EntryTask {
        /**
         * callback to do some processing on an archive entry.
         *
         * @param e the archive entry information such as its name, size...
         * @param is the archive entry input stream to access the archive entry
         * content.
         * @throws IOException if the input stream reading generates a failure
         */
        public void on(final Entry e, InputStream is) throws IOException;
    }

    /**
     * perform a task on each archive entry
     *
     * @param task the task to perform
     * @throws IOException can be generated while reading the archive entries
     */
    public void onEachEntry(EntryTask task) throws IOException;


    /**
     * Definition of an archive entry
     */
    public final class Entry {
        final public String name;
        final public long size;
        final boolean isDirectory;

        /**
         * creates a new archive entry
         * @param name the entry name
         * @param size the entry size
         * @param isDirectory true if this entry is a directory
         */
        public Entry(String name, long size, boolean isDirectory) {
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
        }
    }
}
