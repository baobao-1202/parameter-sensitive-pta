package pag.nativeModel;

import soot.*;

public class JavaIoFileSystemGetFileSystemNative extends NativeMethod {
    public JavaIoFileSystemGetFileSystemNative( SootMethod method ) { super(method); }

  /************************ java.io.FileSystem ***********************/
  /**
   * Returns a variable pointing to the file system constant
   *
   *    public static native java.io.FileSystem getFileSystem();
   */
  public 
    void simulate() {
	  //new Abstract Class is allowed here, or we need to reload Unix/Win32/WinNT FileSystem
	  Value newLocal0 = getNew(RefType.v("java.io.FileSystem"));
	  addReturn(newLocal0);
	  
//	  Value newLocal0 = getNew(RefType.v("java.io.UnixFileSystem"));
//	  addReturn(newLocal0);
//	  Value newLocal1 = getNew(RefType.v("java.io.Win32FileSystem"));
//	  addReturn(newLocal1);
//	  Value newLocal2 = getNew(RefType.v("java.io.WinNTFileSystem"));
//	  addReturn(newLocal2);
  }
}
