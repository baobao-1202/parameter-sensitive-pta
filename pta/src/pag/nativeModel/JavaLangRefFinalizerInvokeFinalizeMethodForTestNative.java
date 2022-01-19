package pag.nativeModel;

import soot.*;

public class JavaLangRefFinalizerInvokeFinalizeMethodForTestNative extends NativeMethod {
    public JavaLangRefFinalizerInvokeFinalizeMethodForTestNative( SootMethod method ) { super(method); }


    /**
     * public static native pta.nativemodel.JavaLangRefFinalizer invokeFinalizeMethod(java.lang.Object)
     */
  public 
    void simulate() {
	  Value r0 = getPara(0);
	  addInvoke(r0, "<pta.nativemodel.JavaLangRefFinalizer: void foo()>");
  }
}
