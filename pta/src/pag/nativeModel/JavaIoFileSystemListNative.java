package pag.nativeModel;

import soot.*;

public class JavaIoFileSystemListNative extends NativeMethod {
    public JavaIoFileSystemListNative( SootMethod method ) { super(method); }

  
  /************************ java.io.FileSystem ***********************/
  /**
   * Returns a String[] */
  public 
    void simulate() {
	  Value arrLocal = getNewArray(RefType.v("java.lang.String"));
	  addReturn(arrLocal);
  }
}
