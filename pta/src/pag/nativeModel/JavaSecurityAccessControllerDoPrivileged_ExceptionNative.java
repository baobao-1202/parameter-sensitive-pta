package pag.nativeModel;

import soot.*;

public class JavaSecurityAccessControllerDoPrivileged_ExceptionNative extends NativeMethod {
    public JavaSecurityAccessControllerDoPrivileged_ExceptionNative( SootMethod method ) { super(method); }


  /**public static native java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction)
	 public static native java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction,java.security.AccessControlContext)
   */
  public 
    void simulate() {
	  Value r0 = getPara(0);
	  Value r1 = getInvoke(r0, "<java.security.PrivilegedExceptionAction: java.lang.Object run()>");
	  addReturn(r1);
  }
}
