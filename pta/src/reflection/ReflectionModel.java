/* Java and Android Analysis Framework
 * Copyright (C) 2017 Yifei Zhang, Tian Tan, Yue Li and Jingling Xue
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
package reflection;

import pag.node.var.Var_Node;
import pta.PTA.CallGraphBuilder;
import pta.pts.PTSetInternal;
import soot.MethodOrMethodContext;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

/**
 * DA: Adapted for new CallGraphBuilder
 * 
 * @author Jingbo Lu
 *
 */
public abstract class ReflectionModel {
	protected CallGraphBuilder cgb;
	protected final String sigForName = "<java.lang.Class: java.lang.Class forName(java.lang.String)>";
	protected final String sigClassNewInstance = "<java.lang.Class: java.lang.Object newInstance()>";
	protected final String sigConstructorNewInstance = "<java.lang.reflect.Constructor: java.lang.Object newInstance(java.lang.Object[])>";
	protected final String sigMethodInvoke = "<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>";
	protected final String sigFieldSet = "<java.lang.reflect.Field: void set(java.lang.Object,java.lang.Object)>";
	protected final String sigFieldGet = "<java.lang.reflect.Field: java.lang.Object get(java.lang.Object)>";
	
	abstract void classForName(MethodOrMethodContext source, Stmt s);
	abstract void classNewInstance(MethodOrMethodContext source, Stmt s);
	abstract void contructorNewInstance(MethodOrMethodContext source, Stmt s);
	abstract void methodInvoke(MethodOrMethodContext source, Stmt invokeStmt);

	public void handleInvokeExpr(InvokeExpr ie, MethodOrMethodContext source, Stmt s) {
		switch (ie.getMethodRef().getSignature()) {
		case sigForName:
			classForName(source, s);
			break;
		case sigClassNewInstance:
			classNewInstance(source, s);
			break;
		case sigConstructorNewInstance:
			contructorNewInstance(source, s);
			break;
		case sigMethodInvoke:
			methodInvoke(source, s);
			break;
		default:
			break;
		}
	}

	public void updateNode(final Var_Node vn, PTSetInternal p2set) {}
}
