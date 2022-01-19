package pag.nativeModel;

import soot.*;

public class JavaSecurityAccessControllerDoPrivilegedNative extends NativeMethod {
    public JavaSecurityAccessControllerDoPrivilegedNative( SootMethod method ) { super(method); }


  /**public static native java.lang.Object doPrivileged(java.security.PrivilegedAction)
	 public static native java.lang.Object doPrivileged(java.security.PrivilegedAction,java.security.AccessControlContext)
   */
  public 
    void simulate() {
	  Value r0 = getPara(0);
	  Value r1 = getInvoke(r0, "<java.security.PrivilegedAction: java.lang.Object run()>");
	  addReturn(r1);
  }
}
