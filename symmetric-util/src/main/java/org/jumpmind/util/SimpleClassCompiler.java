/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compile Java code in memory, load it, and return a new instance.  The class name is changed to make it unique
 * and the caller should cast to a parent class or interface.  On subsequent calls, if the same Java code
 * is passed again, the cached object is returned.  Otherwise, a new class is compiled, loaded, and an instance returned.
 *
 */
@IgnoreJRERequirement
@SuppressWarnings({ "rawtypes" })
public class SimpleClassCompiler {

    public static final String CLASSNAME_TOKEN = "$(CLASSNAME)";
    
    protected static final String CLASSNAME_TOKEN_REGEX = "\\$\\(CLASSNAME\\)";

    protected static SimpleClassCompiler instance;
    
    protected Map<Integer, Object> objectMap = new HashMap<Integer, Object>();
    
    protected int classSuffix;
    
    private static Logger log = LoggerFactory.getLogger(SimpleClassCompiler.class);
    
    public static SimpleClassCompiler getInstance() {
        if (instance == null) {
            instance = new SimpleClassCompiler();
        }
        return instance;
    }
    
    public Object getCompiledClass(String javaCode) throws Exception {

        Integer id = javaCode.hashCode();
        Object javaObject = objectMap.get(id);
        
        if (javaObject == null ) {
            String className = getNextClassName();
            javaCode = javaCode.replaceAll(CLASSNAME_TOKEN_REGEX, className);
            if (log.isDebugEnabled()) {
                log.debug("Compiling code: \n" + javaCode);
            }
            
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Cannot find a Java compiler.  "
                        + "Please check that you are running SymmetricDS under a JDK (versus a JRE)");
            } 
            JavaFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
            DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<JavaFileObject>();
            List<JavaFileObject> javaFiles = new ArrayList<JavaFileObject>();
            javaFiles.add(new JavaObjectFromString(className, javaCode));
            Boolean success = compiler.getTask(null, fileManager, diag, null, null, javaFiles).call();
            
            if (success) {
                log.debug("Compilation has succeeded");
                javaObject = fileManager.getClassLoader(null).loadClass(className).newInstance();
                objectMap.put(id, javaObject);
            } else {
                log.error("Compilation failed for:\n" + javaCode);
                for (Diagnostic diagnostic : diag.getDiagnostics()) {
                    log.error("At line " + diagnostic.getLineNumber() + ", column " + diagnostic.getColumnNumber() + ": " + 
                            diagnostic.getMessage(null));
                }
                throw new SimpleClassCompilerException(diag.getDiagnostics());
            }
        }

        return javaObject;
    }

    protected synchronized String getNextClassName() {
        return getClass().getSimpleName() + (classSuffix++);
    }

    @IgnoreJRERequirement
    class JavaObjectFromString extends SimpleJavaFileObject {
        private String data = null;

        public JavaObjectFromString(String className, String data) throws Exception {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.data = data;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return data;
        }
    }

    @IgnoreJRERequirement
    class JavaClassObject extends SimpleJavaFileObject {
        protected final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        public JavaClassObject(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return bos;
        }
    }
    
    @IgnoreJRERequirement
    public class ClassFileManager extends ForwardingJavaFileManager {
        private HashMap<String, JavaClassObject> jclassObjects = new HashMap<String, JavaClassObject>();

        @SuppressWarnings("unchecked")
        public ClassFileManager(StandardJavaFileManager standardManager) {
            super(standardManager);
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return new SecureClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    JavaClassObject jclassObject = jclassObjects.get(name);
                    byte[] bytes = jclassObject.getBytes();
                    return super.defineClass(name, bytes, 0, bytes.length);
                }
            };
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) 
                throws IOException {
            JavaClassObject jclassObject = new JavaClassObject(className, kind);
            jclassObjects.put(className, jclassObject);
            return jclassObject;
        }
    }
}
