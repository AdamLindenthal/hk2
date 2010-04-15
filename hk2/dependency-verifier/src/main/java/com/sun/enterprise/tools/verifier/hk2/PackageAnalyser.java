/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
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


package com.sun.enterprise.tools.verifier.hk2;

import com.sun.enterprise.module.ModuleDefinition;
import com.sun.enterprise.module.ModuleDependency;
import com.sun.enterprise.module.Repository;
import com.sun.enterprise.tools.verifier.apiscan.classfile.ClassFile;
import com.sun.enterprise.tools.verifier.apiscan.classfile.ClassFileLoader;
import com.sun.enterprise.tools.verifier.apiscan.classfile.ClassFileLoaderFactory;
import com.sun.enterprise.tools.verifier.apiscan.classfile.Util;
import static com.sun.enterprise.tools.verifier.hk2.PackageAnalyser.Token.TYPE.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jvnet.hk2.osgiadapter.OSGiDirectoryBasedRepository;
import org.jvnet.hk2.osgiadapter.OSGiFactoryImpl;
import org.osgi.framework.Version;
import org.osgi.framework.Constants;

/**
 * A class that inspects module definitions of a bundle and processes them
 * to come up with package dependency matrix, split-packages, etc.
 * Does not handle version information correctly while matching exporter to importers,
 * so it assumes a package or a bundle
 * has only one version at any given time in a distribution.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class PackageAnalyser {
    private Set<Bundle> bundles;
    private Logger logger;

    final static char QUOTE = '\"';
    final static char COMMA = ',';
    final static char SEMICOLON = ';';
    final static char COLON = ':';
    final static char EQUALS = '=';

    /**
     * Holds information about a range of Versions.
     */
    private static class VersionRange {
        private final Version lowerVersion;
        private final boolean lowerVersionInclussive; // is it [1.0 or (1.0
        private final Version upperVersion;
        private final boolean upperVersionInclussive; // is it 2.0] or 2.0)
        private static final VersionRange DEFAULT_VERSION_RANGE = new VersionRange();
        private static final String LSB = "[";
        private static final String LP = "(";
        private static final String RSB = "]";

        public VersionRange(Version lowerVersion, boolean lowerVersionInclussive, Version upperVersion, boolean upperVersionInclussive) {
            this.lowerVersion = lowerVersion;
            this.lowerVersionInclussive = lowerVersionInclussive;
            this.upperVersion = upperVersion;
            this.upperVersionInclussive = upperVersionInclussive;
        }

        private VersionRange(Version lowerVersion) {
            this(lowerVersion, true, null, false);
        }

        public VersionRange() {
            this(Version.emptyVersion, true, null, false);
        }

        public static VersionRange valueOf(String s) {
            if (s == null) s = "";
            // The string can be any of the following:
            // "v1" -> [v1, infinity)
            // v1 -> [v1, inifinity)
            // "[v1, v2)"
            // "(v1, v2)"
            // "(v1, v2]"
            // "[v1, v2]"

            // Let's get rid of the sorrounding quotes.
            if(s.startsWith("\"")) {
                assert(s.endsWith("\""));
                s = s.substring(1, s.length()-1);
            }

            if (s.length() == 0) return DEFAULT_VERSION_RANGE;

            if (s.startsWith(LP) || s.startsWith(LSB)) {
                int comma = s.indexOf(COMMA, 1);
                String lvs = s.substring(1, comma).trim();
                Version lv = new Version(lvs);
                String uvs = s.substring(comma+1, s.length() -1).trim();
                Version uv = new Version(uvs);
                boolean lowerVersionInclussive = s.startsWith(LSB);
                boolean upperVersionInclussive = s.endsWith(RSB);
                return new VersionRange(lv, lowerVersionInclussive, uv, upperVersionInclussive);
            } else {
                Version lv = new Version(s.trim());
                return new VersionRange(lv);
            }
        }

        /**
         * Returns true if the given version falls in the version range.
         * @param version
         * @return
         */
        public boolean isInRange(Version version) {
            final int distanceFromLowerVersion = version.compareTo(lowerVersion);
            boolean lowerVersionMatches =
                    lowerVersionInclussive ? (distanceFromLowerVersion >=0) : (distanceFromLowerVersion > 0);
            if (lowerVersionMatches) {
                if (upperVersion != null) {
                    final int distanceFromUpperVersion = version.compareTo(upperVersion);
                    boolean upperVersionMatches =
                            upperVersionInclussive ? (distanceFromUpperVersion <= 0) : (distanceFromUpperVersion < 0);
                    return upperVersionMatches;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("\"");
            sb.append(lowerVersionInclussive ? LSB : LP).append(lowerVersion).append(", ");
            sb.append(upperVersion != null ? upperVersion.toString() : "infinity").append(upperVersionInclussive ? RSB : ")");
            sb.append("\"");
            return sb.toString();
        }
    }

    /**
     * Holds information about an exported package.
     */
    public static class PackageCapability implements Comparable<PackageCapability> {
        private String name;
        private Version version = Version.emptyVersion;

        public PackageCapability(String name, String versionStr) {
            this.name = name.trim();
            if (versionStr != null && versionStr.trim().length() > 0) {
                this.version = new Version(versionStr.trim());
            }
        }

        public PackageCapability(String name) {
            this(name, null);
        }

        @Override
        public String toString() {
            return name + "; version=" + version;
        }

        public String getName() {
            return name;
        }

        public Version getVersion() {
            return version;
        }

        public int compareTo(PackageCapability o) {
            Collator collator = Collator.getInstance();
            int i = collator.compare(getName(), o.getName());
            if (i == 0) {
                i = getVersion().compareTo(o.getVersion());
            }
            return i;

        }
    }

    // Represents an imported pkg.
    private class PackageRequirement implements Comparable<PackageRequirement> {
        private String name;
        private VersionRange versionRange;

        private PackageRequirement(String name, VersionRange versionRange) {
            this.name = name;
            this.versionRange = versionRange;
        }

        public String getName() {
            return name;
        }

        public VersionRange getVersionRange() {
            return versionRange;
        }

        public int compareTo(PackageRequirement o) {
            Collator collator = Collator.getInstance();
            int i = collator.compare(getName(), o.getName());
            if (i == 0) {
                i = getVersionRange().toString().compareTo(o.getVersionRange().toString());
            }
            return i;
        }

        @Override
        public String toString() {
            return name + "; version=" + versionRange;
        }
    }

    /**
     * A dats structure to capture bundle details needed for our
     * processing.
     */
    public static class Bundle {
        private ModuleDefinition md;
        /**
         * Packages exported by this bundle
         */
        private Set<PackageCapability> exportedPkgs = new HashSet<PackageCapability>();

        private Set<String> exportedPkgNames = new HashSet<String>();

        /**
         * Packages needed by this bundle. This is NOT necessarily same as
         * what is mentioned in Import-Package header. We introspect the classes
         * of  a bundle and generate this list.
         */
        private Set<String> requiredPkgs = new HashSet<String>();

        /**
         * Packages imported by this bundle via Import-Package header.
         */
        private Set<PackageRequirement> importedPkgs = new HashSet<PackageRequirement>();

        /**
         * List of all the required bundles.
         */
        private Set<Bundle> requiredBundles = new HashSet<Bundle>();

        Bundle(ModuleDefinition md) {
            this.setMd(md);
        }

        public Set<PackageCapability> getExportedPkgs() {
            return exportedPkgs;
        }

        public void setExportedPkgs(Set<PackageCapability> exportedPkgs) {
            this.exportedPkgs = exportedPkgs;
            for (PackageCapability p : exportedPkgs) {
                exportedPkgNames.add(p.getName());
            }
        }

        public Set<PackageRequirement> getImportedPkgs() {
            return importedPkgs;
        }

        public void setImportedPkgs(Set<PackageRequirement> importedPkgs) {
            this.importedPkgs = importedPkgs;
        }

        public Set<String> getRequiredPkgs() {
            return requiredPkgs;
        }

        public void setRequiredPkgs(Set<String> requiredPkgs) {
            this.requiredPkgs = requiredPkgs;
        }

        public Set<Bundle> getRequiredBundles() {
            return requiredBundles;
        }

        public void setRequiredBundles(Set<Bundle> requiredBundles) {
            this.requiredBundles = requiredBundles;
        }

        public ModuleDefinition getMd() {
            return md;
        }

        public boolean provides(String pkg) {
            return exportedPkgNames.contains(pkg);
        }

        public boolean requires(PackageCapability p) {
            return getRequiredPkgs().contains(p.getName());
        }

        @Override
        public int hashCode() {
            return getMd().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bundle) {
                return this.getMd().equals(Bundle.class.cast(obj).getMd());
            }
            return false;
        }

        public String getName() {
            return getMd().getName();
        }

        public void setMd(ModuleDefinition md) {
            this.md = md;
        }

    }

    /**
     * A wire represents a connection between an exporter bundle to an importer
     * bundle for a particular package.
     */
    public static class Wire {
        /**
         * Package that is being wired
         */
        String pkg;
        /**
         * The bundle that exports this package
         */
        Bundle exporter;

        /**
         * The bundle that imports this package
         */
        Bundle importer;

        public Wire(String pkg, Bundle importer, Bundle exporter) {
            this.exporter = exporter;
            this.importer = importer;
            this.pkg = pkg;
        }


        public String getPkg() {
            return pkg;
        }

        public Bundle getExporter() {
            return exporter;
        }

        public Bundle getImporter() {
            return importer;
        }

        @Override
        public int hashCode() {
            return pkg.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            boolean b = false;
            if (obj instanceof Wire) {
                Wire other = Wire.class.cast(obj);
                b = pkg.equals(other.pkg);
                if (b) {
                    if (exporter != null) {
                        b = exporter.equals(other.exporter);
                    } else {
                        b = other.exporter == null;
                    }
                    if (b) {
                        if (importer != null) {
                            b = importer.equals(other.importer);
                        } else {
                            b = other.importer == null;
                        }
                    }
                }
            }
            return b;
        }

        @Override
        public String toString() {
            return "Wire [Package = " + pkg + ", Importer = " + importer.getMd().getName() + ", Exporter = " + exporter.getMd().getName() + "]";
        }
    }

    /**
     * Holds information about a split-package.
     * A split-package is a package whose contents come from multiple bundles.
     * Note that, a package can be exported by multiple bundles. It leads to
     * problematic scenarios when the symmetric difference of set of classes
     * for a package from the two bundles is not an empty set. In other words,
     * they should contain identical set of classes from the same package.
     */
    public static class SplitPackage {
        /*
         * TODO(Sahoo):
         * 1. Report packages that are really split. Currently,
         * it reports a package as split if it is exported by multiple bundles.
         * 2. Handle version 
         */

        /**
         * name of package
         */
        String name;

        /**
         * Bundles exporting this package
         */
        Set<Bundle> exporters = new HashSet<Bundle>();

        public SplitPackage(String name, Set<Bundle> exporters) {
            this.name = name;
            this.exporters = exporters;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SplitPackage) {
                return name.equals(SplitPackage.class.cast(obj));
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("name " + name + " (" + exporters.size() + " times):\n");
            for (Bundle b : exporters) {
                sb.append(b.getMd().getName() + "\n");
            }
            return sb.toString();
        }
    }

    private Repository moduleRepository;

    public PackageAnalyser(Repository moduleRepository) {
        this(moduleRepository, Logger.getAnonymousLogger());
    }

    public PackageAnalyser(Repository repo, Logger logger) {
        this.moduleRepository = repo;
        this.logger = logger;
    }

    /**
     * Analyse the dependency of a bundle and updates it in the given bundle object.
     *
     * @param bundle to be analysed
     */
    public void analyse(Bundle bundle) throws IOException {
        bundle.setRequiredBundles(computeRequiredBundles(bundle));
        bundle.setExportedPkgs(computeExportedPackages(bundle));
        bundle.setImportedPkgs(computeImportedPackages(bundle));
        bundle.setRequiredPkgs(computeRequiredPackages(bundle));
    }

    private Set<PackageRequirement> computeImportedPackages(Bundle bundle) {
        // The header looks like this:
        // Import-Package: p1; p2; version=1.4; foo=bar, p3;version="[1, 5)", p4

        String importPkgHeader = bundle.getMd().getManifest().getMainAttributes().getValue(Constants.IMPORT_PACKAGE);
        if (importPkgHeader == null) {
            return Collections.EMPTY_SET;
        }
        Set<PackageRequirement> importedPkgs = new HashSet<PackageRequirement>();
        List<Token> tokens = tokenize(importPkgHeader);
        List<String> pkgNames = new ArrayList<String>();
        VersionRange versionRange = VersionRange.DEFAULT_VERSION_RANGE;
        boolean nextPkgGroup = false;
        for (Token t : tokens) {
            switch (t.type) {
                case PKG:
                    if(nextPkgGroup) {
                        // Add parsed package details and prepare for next iteration
                        for (String name : pkgNames) {
                            PackageRequirement pr = new PackageRequirement(name, versionRange);
                            importedPkgs.add(pr);
                        }
                        pkgNames.clear();
                        versionRange = VersionRange.DEFAULT_VERSION_RANGE;
                        nextPkgGroup = false;
                    }
                    pkgNames.add(t.value);
                    break;
                case DIRECTIVE:
                    nextPkgGroup = true;
                    break;
                case ATTRIBUTE:
                    nextPkgGroup = true;
                    int idx = t.value.indexOf(EQUALS);
                    assert(idx != -1);
                    String attrName = t.value.substring(0, idx);
                    if (Constants.VERSION_ATTRIBUTE.equals(attrName)) {
                        String versionRangeStr = t.value.substring(idx +1);
                        versionRange = VersionRange.valueOf(versionRangeStr);
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown token type. Fix the program.");
            }
        }
        if (!pkgNames.isEmpty()) {
            for (String name : pkgNames) {
                PackageRequirement pr = new PackageRequirement(name, versionRange);
                importedPkgs.add(pr);
            }
        }
        return importedPkgs;
    }

    private Set<String> computeRequiredPackages(Bundle bundle) throws IOException {
        Set<String> requiredPkgs = new HashSet<String>();
        File moduleFile = new File(bundle.getMd().getLocations()[0]);
        String classpath = moduleFile.getAbsolutePath();
        JarFile moduleJar = new JarFile(moduleFile);
        ClassFileLoader cfl = ClassFileLoaderFactory.newInstance(new Object[]{classpath});
        final String classExt = ".class";
        for (Enumeration<JarEntry> entries = moduleJar.entries(); entries.hasMoreElements();) {
            JarEntry je = entries.nextElement();
            if (je.getName().endsWith(classExt)) {
                String className = Util.convertToExternalClassName(je.getName().substring(0, je.getName().length() - classExt.length()));
                ClassFile cf = null;
                try {
                    cf = cfl.load(className);
                    for (String c : cf.getAllReferencedClassNames()) {
                        requiredPkgs.add(Util.getPackageName(c));
                    }
                } catch (IOException e) {
                    logger.logp(Level.FINE, "PackageAnalyser", "computeRequiredPackages", "Skipping analysis of {0} as the following exception was thrown:\n {1}", new Object[]{className, e});
                }
            }
        }
        return requiredPkgs;
    }

    static class Token {
        enum TYPE {
            PKG, ATTRIBUTE, DIRECTIVE
        }

        final String value;
        final TYPE type;

        private Token(String value, TYPE type) {
            this.value = value;
            this.type = type;
        }

        public static Token createToken(String s, TYPE type) {
            System.out.println("createToken(" + s + ", " + type+ ")");
            return new Token(s, type);
        }

    }



    private List<Token> tokenize(String header) {
        // The string looks like
        // Export-Package: p1;p2;version=1.4;uses:="q1,q2...,qn",p3;uses:="q1,q2";p4;p5;version=...
        List<Token> tokens = new ArrayList<Token>();
        StringBuilder token = new StringBuilder();

        Token.TYPE type = PKG; // next token type
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            switch(c) {
                case QUOTE :
                    int nextQuote = header.indexOf(QUOTE, i+1);
                    token.append(header.substring(i, nextQuote+1));
                    i = nextQuote;
                    break;
                case COLON:
                    token.append(c);
                    char c2 = header.charAt(i+1);
                    if (c2 == EQUALS) {
                        type = DIRECTIVE;
                        token.append(c2);
                        i++;
                    }
                    break;
                case EQUALS:
                    type = ATTRIBUTE;
                    token.append(c);
                    break;
                case COMMA :
                case SEMICOLON:
                    tokens.add(Token.createToken(token.toString(), type));
                    token.delete(0, token.length());
                    type = PKG;
                    break;
                default:
                    token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(Token.createToken(token.toString(), type));
        }
        return tokens;
    }

    private static class PackageGroup {
        final List<String> pkgNames;
        final List<String> pkgAttributes;
        final List<String> pkgDirectives;

        private PackageGroup(List<String> pkgNames, List<String> pkgAttributes, List<String> pkgDirectives) {
            this.pkgNames = pkgNames;
            this.pkgAttributes = pkgAttributes;
            this.pkgDirectives = pkgDirectives;
        }
    }

    private List<PackageGroup> parseExportPackage(String header) {
        // The string looks like
        // Export-Package: p1;p2;version=1.4;uses:="q1,q2...,qn",p3;uses:="q1,q2";p4;p5;version=...

        List<PackageGroup> pkgGroups = new ArrayList<PackageGroup>();
        List<Token> tokens = tokenize(header);
        List<String> pkgNames = new ArrayList<String>();
        List<String> pkgAttributes = new ArrayList<String>();
        List<String> pkgDirectives = new ArrayList<String>();
        boolean nextPkgGroup = false;
        for (Token t : tokens) {
            switch (t.type) {
                case PKG:
                    if (nextPkgGroup) {
                        // Add parsed package details and prepare for next iteration
                        pkgGroups.add(new PackageGroup(pkgNames, pkgAttributes, pkgDirectives));
                        pkgNames = new ArrayList<String>();
                        pkgAttributes = new ArrayList<String>();
                        pkgDirectives = new ArrayList<String>();
                        nextPkgGroup = false;                   
                    }
                    pkgNames.add(t.value);
                    break;
                case ATTRIBUTE:
                    nextPkgGroup = true;
                    pkgAttributes.add(t.value);
                    break;
                case DIRECTIVE:
                    nextPkgGroup = true;
                    pkgDirectives.add(t.value);
                    break;
                default:
                    throw new RuntimeException("Unknown token type. Fix the program");
            }
        }
        if (!pkgNames.isEmpty()) {
            // Add parsed package details
            pkgGroups.add(new PackageGroup(pkgNames, pkgAttributes, pkgDirectives));
            pkgNames.clear();
            pkgAttributes.clear();
            pkgDirectives.clear();
        }
        return pkgGroups;
    }

    private Set<PackageCapability> computeExportedPackages(Bundle bundle) {
        Set<PackageCapability> exportedPkgs = new HashSet<PackageCapability>();
        String exportedPkgsAttr = bundle.getMd().getManifest().getMainAttributes().getValue("Export-Package");
        if (exportedPkgsAttr == null) return exportedPkgs;

        Collection<PackageGroup> pkgGroups = parseExportPackage(exportedPkgsAttr);
        for (PackageGroup pg : pkgGroups) {
            String version = null;
            for (String attr : pg.pkgAttributes) {
                int idx = attr.indexOf(EQUALS);
                assert(idx!= -1);
                if (Constants.VERSION_ATTRIBUTE.equals(attr.substring(0, idx))) {
                    version = attr.substring(idx+1);
                    // if version contains quotes around it, remove them.
                    if(version.startsWith("\"") && version.endsWith("\"")) {
                        version = version.substring(1, version.length()-1);
                    }
                }
            }
            for (String pkgName : pg.pkgNames) {
                PackageCapability p = new PackageCapability(pkgName, version);
                exportedPkgs.add(p);
            }
        }
        return exportedPkgs;
    }

    private Set<Bundle> computeRequiredBundles(Bundle bundle) {
        Set<Bundle> requiredBundles = new HashSet<Bundle>();
        for (ModuleDependency dep : bundle.getMd().getDependencies()) {
            ModuleDefinition md = moduleRepository.find(dep.getName(), dep.getVersion());
            if (md != null) {
                requiredBundles.add(new Bundle(md));
            } else {
                System.out.println("WARNING: Missing dependency: [" + dep + "] for module [" + bundle.getName() + "]");
            }
        }
        return requiredBundles;
    }

    public Collection<Wire> analyseWirings() throws IOException {
        List<ModuleDefinition> moduleDefs =
                moduleDefs = moduleRepository.findAll();
        bundles = new HashSet<Bundle>();
        for (ModuleDefinition moduleDef : moduleDefs) {
            Bundle bundle = new Bundle(moduleDef);
            bundles.add(bundle);
            analyse(bundle);
        }
        Set<Wire> wires = new HashSet<Wire>();
        for (Bundle importer : bundles) {
            for (String pkg : importer.getRequiredPkgs()) {
                for (Bundle exporter : bundles) {
                    if (exporter.provides(pkg)) {
                        Wire w = new Wire(pkg, importer, exporter);
                        wires.add(w);
                    }
                }
            }
        }
        List<Wire> sorted = new ArrayList<Wire>(wires);
        Collections.sort(sorted, new Comparator<Wire>() {
            Collator collator = Collator.getInstance();

            public int compare(Wire o1, Wire o2) {
                return collator.compare(o1.pkg, o2.pkg);
            }
        });
        return sorted;
    }

    /**
     * Inspects bundles and reports spli-packages.
     * Before calling this method, you must call {@link this#analyseWirings()}
     * The colection is already sorted.
     *
     * @return set of split-packages, en empty set if none is found.
     */
    public Collection<SplitPackage> findSplitPackages() {
        assert (bundles != null);
        Map<String, Set<Bundle>> packages = new HashMap<String, Set<Bundle>>();
        for (Bundle b : bundles) {
            for (PackageCapability p : b.getExportedPkgs()) {
                Set<Bundle> exporters = packages.get(p.getName());
                if (exporters == null) {
                    exporters = new HashSet<Bundle>();
                    packages.put(p.getName(), exporters);
                }
                exporters.add(b);
            }
        }
        Set<SplitPackage> splitPkgs = new HashSet<SplitPackage>();
        for (Map.Entry<String, Set<Bundle>> entry : packages.entrySet()) {
            if (entry.getValue().size() > 1) {
                splitPkgs.add(new SplitPackage(entry.getKey(), entry.getValue()));
            }
        }
        List<SplitPackage> sortedSplitPkgs = new ArrayList<SplitPackage>(splitPkgs);
        Collections.sort(sortedSplitPkgs, new Comparator<SplitPackage>() {
            Collator collator = Collator.getInstance();

            public int compare(SplitPackage o1, SplitPackage o2) {
                return collator.compare(o1.name, o2.name);
            }
        });

        return sortedSplitPkgs;
    }

    public Collection<PackageCapability> findAllExportedPackages() {
        List<PackageCapability> packages = new ArrayList<PackageCapability>();
        for (Bundle b : bundles) {
            packages.addAll(b.getExportedPkgs());
        }
        Collections.sort(packages);
        return packages;
    }

    public Collection<String> findAllExportedPackageNames() {
        Collection<PackageCapability> packages = findAllExportedPackages();
        Set<String> pkgNames = new HashSet<String>(packages.size());
        for (PackageCapability p : packages) {
            pkgNames.add(p.getName());
        }

        List<String> sorted = new ArrayList<String>(pkgNames);
        Collections.sort(sorted, new Comparator<String>() {
            Collator collator = Collator.getInstance();

            public int compare(String o1, String o2) {
                return collator.compare(o1, o2);
            }
        });
        return sorted;
    }

    public Set<Bundle> findAllBundles() {
        return bundles;
    }

    public Collection<PackageCapability> findUnusedExports() {
        List<PackageCapability> unusedPackages = new ArrayList<PackageCapability>();
        for (Bundle exporter : bundles) {
            for (PackageCapability p : exporter.getExportedPkgs()) {
                boolean used = false;
                for (Bundle importer : bundles) {
                    if (importer != exporter && importer.requires(p)) {
                        used = true;
                        break;
                    }
                }
                if (!used) unusedPackages.add(p);
            } 
        }
        Collections.sort(unusedPackages);
        return unusedPackages;
    }

    public void generateWiringReport(Collection<String> exportedPkgs, Collection<PackageAnalyser.Wire> wires, PrintStream out) {
        out.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        out.println("<?xml-stylesheet type=\"text/xsl\" href=\"wires.xsl\"?>");
        out.println("<Wires>");
        for (String p : exportedPkgs) {
            StringBuilder sb = new StringBuilder();
            sb.append("\t<Package name = \"" + p + "\">\n");
            sb.append("\t\t<Exporters>\n");
            Set<String> exporters = new HashSet<String>();
            for (PackageAnalyser.Wire w : wires) {
                if (w.getPkg().equals(p)) {
                    exporters.add(w.getExporter().getName());
                }
            }
            for (String e : exporters) {
                sb.append(e + " ");
            }
            sb.append("\n\t\t</Exporters>\n");
            sb.append("\t\t<Importers>\n");
            for (PackageAnalyser.Wire w : wires) {
                if (w.getPkg().equals(p)) {
                    sb.append(w.getImporter().getName() + " ");
                }
            }
            sb.append("\n\t\t</Importers>\n");
            sb.append("\t</Package>");
            out.println(sb);
        }
        out.println("</Wires>");
    }

    public void generateBundleReport(PrintStream out) {
        out.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
        out.println("<?xml-stylesheet type=\"text/xsl\" href=\"bundles.xsl\"?>");
        out.println("<Bundles>");
        for (Bundle b : bundles) {
            StringBuilder sb = new StringBuilder();
            sb.append("\t<Bundle name = \"" + b.getName() + "\">\n");
            sb.append("\t\t<Exports>\n");
            List<PackageCapability> pcs = new ArrayList<PackageCapability>(b.getExportedPkgs());
            Collections.sort(pcs);
            int i = 0;
            for (PackageCapability pc : pcs) {
                sb.append("\t\t\t" + pc);
                if (++i < pcs.size()) {
                    sb.append(",\\");
                }
                sb.append("\n");
            }
            sb.append("\t\t</Exports>\n");
            sb.append("\t\t<Imports>\n");
            List<PackageRequirement> prs = new ArrayList<PackageRequirement>(b.getImportedPkgs());
            Collections.sort(prs);
            i = 0;
            for (PackageRequirement pr : prs) {
                sb.append("\t\t\t" + pr);
                if (++i < prs.size()) {
                    sb.append(",\\");
                }
                sb.append("\n");
            }
            sb.append("\t\t</Imports>\n");
            sb.append("\t</Bundle>");
            out.println(sb);
        }
        out.println("</Bundles>");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("Usage: java " + PackageAnalyser.class.getName() +
                    " <Repository Dir Path> <output file name for bundle details>" +
                    " <output file name for wiring details> <output file name for split-packages> <output file name for unused packages>");

            System.out.println("Example(s):\n" +
                    "Following command analyses all modules in the specified repository:\n" +
                    " java " + PackageAnalyser.class.getName() +
                    " /tmp/glassfish/modules/ bundles.xml wires.xml sp.txt unused.txt\n\n");
            return;
        }
        String repoPath = args[0];
        PrintStream bundleOut = new PrintStream(new FileOutputStream(args[1]));
        PrintStream wireOut = new PrintStream(new FileOutputStream(args[2]));
        PrintStream spOut = new PrintStream(new FileOutputStream(args[3]));
        PrintStream unusedPkgOut = new PrintStream(new FileOutputStream(args[4]));
        File f = new File(repoPath) {
            @Override
            public File[] listFiles() {
                List<File> files = new ArrayList<File>();
                for (File f : super.listFiles()) {
                    if (f.isDirectory()) {
                        for (File f2 : f.listFiles()) {
                            if (f2.isFile() && f2.getName().endsWith(".jar")) {
                                files.add(f2);
                            }
                        }
                    } else if (f.isFile() && f.getName().endsWith(".jar")) {
                        files.add(f);
                    }
                }
                return files.toArray(new File[files.size()]);
            }
        };
        OSGiFactoryImpl.initialize(null);
        Repository moduleRepository = new OSGiDirectoryBasedRepository("repo", f);
        moduleRepository.initialize();

        PackageAnalyser analyser = new PackageAnalyser(moduleRepository);
        Collection<Wire> wires = analyser.analyseWirings();
        Collection<String> exportedPkgs = analyser.findAllExportedPackageNames();
        analyser.generateBundleReport(bundleOut);
        analyser.generateWiringReport(exportedPkgs, wires, wireOut);
        Collection<SplitPackage> splitPkgs = analyser.findSplitPackages();

        for (SplitPackage p : splitPkgs) spOut.println(p + "\n");
        spOut.println("Total number of Split Packages = " + splitPkgs.size());

        Collection<PackageCapability> unusedPackages = analyser.findUnusedExports();
        for (PackageCapability p : unusedPackages) unusedPkgOut.println(p + "\n");
        unusedPkgOut.println("Total number of Unused Packages = " + unusedPackages.size());

        System.out.println("******** GROSS STATISTICS *********");
        System.out.println("Total number of bundles in this repository: " + analyser.findAllBundles().size());
        System.out.println("Total number of wires = " + wires.size());
        System.out.println("Total number of exported packages = " + exportedPkgs.size());
        System.out.println("Total number of split-packages = " + splitPkgs.size());
        System.out.println("Total number of unused-packages = " + unusedPackages.size());
    }

}
