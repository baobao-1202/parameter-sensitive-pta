package pag.nativeModel;

import soot.*;

public class JavaLangRefFinalizerInvokeFinalizeMethodNative extends NativeMethod {
    public JavaLangRefFinalizerInvokeFinalizeMethodNative( SootMethod method ) { super(method); }

  /**
   * public static native java.ref.Finalizer invokeFinalizeMethod(java.lang.Object)
   */
  public 
    void simulate() {
	  Value r0 = getPara(0);
	  addInvoke(r0, "<java.lang.Object: void finalize()>");
  }
}
