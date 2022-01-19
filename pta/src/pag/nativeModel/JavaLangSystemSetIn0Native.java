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
 * Simulates the native method side effects in class java.lang.System
 *
 * @author Feng Qian
 */

package pag.nativeModel;

import soot.*;

public class JavaLangSystemSetIn0Native extends NativeMethod {
    public JavaLangSystemSetIn0Native( SootMethod method ) { super(method); }

  /** 
   * NOTE: this native method is not documented in JDK API. 
   *       It should have the side effect:
   *       System.in = parameter
   *
   * private static native void setIn0(java.io.InputStream);
   */
  public void simulate() {
	  Value r1 = getPara(0);
	  Value systemIn = getStaticFieldRef("java.lang.System","in");
	  addAssign(systemIn, r1);
  }
}
