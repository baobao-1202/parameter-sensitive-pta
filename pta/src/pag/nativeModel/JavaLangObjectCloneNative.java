/**
 * Simulates the native method side effects in class java.lang.Object.
 *
 * @author Feng Qian
 */

package pag.nativeModel;

import soot.*;

public class JavaLangObjectCloneNative extends NativeMethod {

  public JavaLangObjectCloneNative(SootMethod method) {
		super(method);
	}

/**
   * Implements the abstract method simulateMethod.
   * It distributes the request to the corresponding methods 
   * by signatures.
   */
  public void simulate(){
	  Value r0 = getThis();
	  addReturn(r0);
  }

//  /*********************** java.lang.Object *********************/
//  /**
//   * The return variable is assigned an abstract object representing
//   * all classes (UnknowClassObject) from environment.
//   *
//   * public final native java.lang.Class getClass();
//   */
//  public void java_lang_Object_getClass(SootMethod method,
//					       ReferenceVariable thisVar,
//					       ReferenceVariable returnVar,
//					       ReferenceVariable params[]) {
//    helper.assignObjectTo(returnVar, Environment.v().getClassObject());
//  }

  /**
   * Creates and returns a copy of this object. The precise meaning of
   * "copy" may depend on the class of the object. The general intent
   * is that, for any object x, the expression:
   * 
   *      x.clone() != x
   *
   * will be true, and that the expression:
   *
   *      x.clone().getClass() == x.getClass()
   *
   * will be true, but these are not absolute requirements. While it is
   * typically the case that:
   *
   *      x.clone().equals(x)
   *
   * will be true, this is not an absolute requirement. Copying an
   * object will typically entail creating a new instance of its
   * class, but it also may require copying of internal data
   * structures as well. No constructors are called.
   *
   * NOTE: it may raise an exception, the decision of cloning made by
   *       analysis by implementing the ReferneceVariable.cloneObject()
   *       method.
   *
   * protected native java.lang.Object clone() 
   *                  throws java.lang.CloneNotSupported
   */

  /**
   * Following methods have NO side effect
   *
   * private static native void registerNatives();
   * public native int hashCode();
   * public final native void notify();
   * public final native void notifyAll();
   * public final native void wait(long) 
   *              throws java.lang.InterruptedException;
   */

}
