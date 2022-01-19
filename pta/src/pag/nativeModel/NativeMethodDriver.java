/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Feng Qian
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/**
 * A wrapper for native method side-effect simulation.
 * The caller passes in a native method with parameters, 
 * the corresponding native simulator gets called.
 *
 * @author Feng Qian
 */

package pag.nativeModel;

import soot.*;
import java.util.*;

public class NativeMethodDriver {
	public static final Set<SootMethod> nativeBuilt=new HashSet<>();
	public static void buildNative(SootMethod method) {
		if(!nativeBuilt.add(method))
			return;
		String sig = method.getSignature();
		switch (sig) {
		case "<java.lang.Object: java.lang.Object clone()>":
		case "<pta.nativemodel.JavaLangObject: java.lang.Object clone()>":
			new JavaLangObjectCloneNative(method).simulate();
			break;
		case "<java.lang.System: void setIn0(java.io.InputStream)>":
			new JavaLangSystemSetIn0Native(method).simulate();
			break;
		case "<java.lang.System: void setOut0(java.io.PrintStream)>":
			new JavaLangSystemSetOut0Native(method).simulate();
			break;
		case "<java.lang.System: void setErr0(java.io.PrintStream)>":
			new JavaLangSystemSetErr0Native(method).simulate();
			break;
		case "<java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>":
			new JavaLangSystemArraycopyNative(method).simulate();
			break;
		case "<java.io.FileSystem: java.io.FileSystem getFileSystem()>":
		case "<pta.nativemodel.JavaIoFileSystem: java.lang.Object getFileSystem()>":
			new JavaIoFileSystemGetFileSystemNative(method).simulate();
			break;
		case "<java.io.UnixFileSystem: java.lang.String[] list(java.io.File)>":
		case "<pta.nativemodel.JavaIoFileSystem: java.lang.String[] list(java.io.File)>":
			new JavaIoFileSystemListNative(method).simulate();
			break;
		case "<java.lang.ref.Finalizer: void invokeFinalizeMethod(java.lang.Object)>":
			new JavaLangRefFinalizerInvokeFinalizeMethodNative(method).simulate();
			break;
		case "<pta.nativemodel.JavaLangRefFinalizer: void invokeFinalizeMethod(java.lang.Object)>":
			new JavaLangRefFinalizerInvokeFinalizeMethodForTestNative(method).simulate();
			break;
		case "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction)>":
		case "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction,java.security.AccessControlContext)>":
			new JavaSecurityAccessControllerDoPrivilegedNative(method).simulate();
			break;
		case "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction)>":
		case "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction,java.security.AccessControlContext)>":
			new JavaSecurityAccessControllerDoPrivileged_ExceptionNative(method).simulate();
			break;
		default:
			break;
		}
	}
	
//	private final HashMap<String,NativeMethod> m2sim = new HashMap<>();
    public NativeMethodDriver() {
//    	m2sim.put("java.lang.Object: java.lang.Object clone()", new JavaLangObjectCloneNative());
//    	m2sim.put("pta.nativemodel.JavaLangObject: java.lang.Object clone()", new JavaLangObjectCloneNativeForTest());
//      cnameToSim.put("java.lang.Class", new JavaLangClassNative(helper));	
//    	m2sim.put("java.lang.System", new JavaLangSystemSetIn0Native(helper));
//    	m2sim.put("java.io.FileSystem", new JavaIoFileSystemNative(helper));
//    	m2sim.put("pta.nativemodel.JavaIoFileSystem", new JavaIoFileSystemNativeForTest(helper));
//    	m2sim.put("java.lang.ref.Finalizer", new JavaLangRefFinalizerNative(helper));
//    	m2sim.put("pta.nativemodel.JavaLangRefFinalizer", new JavaLangRefFinalizerNativeForTest(helper));
//    	m2sim.put("java.security.AccessController",new JavaSecurityAccessControllerNative(helper));
        
//        cnameToSim.put("java.lang.Runtime", new JavaLangRuntimeNative(helper));
//        cnameToSim.put("java.lang.Shutdown", new JavaLangShutdownNative(helper));
//        cnameToSim.put("java.lang.String", new JavaLangStringNative(helper));
//        cnameToSim.put("java.lang.Float", new JavaLangFloatNative(helper));
//        cnameToSim.put("java.lang.Double", new JavaLangDoubleNative(helper));
//        cnameToSim.put("java.lang.StrictMath", new JavaLangStrictMathNative(helper));
//        cnameToSim.put("java.lang.Throwable", new JavaLangThrowableNative(helper));

//        cnameToSim.put("java.lang.Package", new JavaLangPackageNative(helper));
//        cnameToSim.put("java.lang.Thread", new JavaLangThreadNative(helper));
//        cnameToSim.put("java.lang.ClassLoader", new JavaLangClassLoaderNative(helper));
//        cnameToSim.put("java.lang.ClassLoader$NativeLibrary",
//                       new JavaLangClassLoaderNativeLibraryNative(helper));
//        cnameToSim.put("java.lang.SecurityManager",
//                       new JavaLangSecurityManagerNative(helper));
//
//
//        cnameToSim.put("java.lang.reflect.Field",
//                       new JavaLangReflectFieldNative(helper));
//        cnameToSim.put("java.lang.reflect.Array",
//                       new JavaLangReflectArrayNative(helper));
//        cnameToSim.put("java.lang.reflect.Method",
//                       new JavaLangReflectMethodNative(helper));
//        cnameToSim.put("java.lang.reflect.Constructor",
//                       new JavaLangReflectConstructorNative(helper));
//        cnameToSim.put("java.lang.reflect.Proxy",
//                       new JavaLangReflectProxyNative(helper));
//
//
//        cnameToSim.put("java.io.FileInputStream", 
//                       new JavaIoFileInputStreamNative(helper));
//        cnameToSim.put("java.io.FileOutputStream", 
//                       new JavaIoFileOutputStreamNative(helper));
//        cnameToSim.put("java.io.ObjectInputStream",
//                       new JavaIoObjectInputStreamNative(helper));
//        cnameToSim.put("java.io.ObjectOutputStream",
//                       new JavaIoObjectOutputStreamNative(helper));
//        cnameToSim.put("java.io.ObjectStreamClass",
//                       new JavaIoObjectStreamClassNative(helper));
  
//        cnameToSim.put("java.io.FileDescriptor", new JavaIoFileDescriptorNative(helper));
//
//
//        cnameToSim.put("java.util.ResourceBundle", 
//                       new JavaUtilResourceBundleNative(helper));
//        cnameToSim.put("java.util.TimeZone", new JavaUtilTimeZoneNative(helper));
//        
//
//        cnameToSim.put("java.util.jar.JarFile",
//                       new JavaUtilJarJarFileNative(helper));
//        
//        cnameToSim.put("java.util.zip.CRC32",
//                       new JavaUtilZipCRC32Native(helper));
//        cnameToSim.put("java.util.zip.Inflater",
//                       new JavaUtilZipInflaterNative(helper));
//        cnameToSim.put("java.util.zip.ZipFile",
//                       new JavaUtilZipZipFileNative(helper));
//        cnameToSim.put("java.util.zip.ZipEntry",
//                       new JavaUtilZipZipEntryNative(helper));   
//        
//
//        cnameToSim.put("java.net.InetAddress", 
//                       new JavaNetInetAddressNative(helper));
//        cnameToSim.put("java.net.InetAddressImpl", 
//                       new JavaNetInetAddressImplNative(helper));
//
//
//        cnameToSim.put("sun.misc.Signal",
//                       new SunMiscSignalNative(helper));
//        cnameToSim.put("sun.misc.NativeSignalHandler",
//                       new SunMiscSignalHandlerNative(helper));
//        cnameToSim.put("sun.misc.Unsafe",
//                       new SunMiscUnsafeNative(helper));
    }

}
