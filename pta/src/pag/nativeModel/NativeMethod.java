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
 * NativeMethodClass defines side-effect simulation of native methods 
 * in a class. 
 */
package pag.nativeModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import pta.PTA;
import soot.*;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;

public abstract class NativeMethod {
	
	private SootMethod method;
	private Collection<Stmt> stmts;
	private Value thisLocal;
	private Value[] paraLocals;
	private int paraStart,localStart;
	
	NativeMethod(SootMethod method) {
		this.method=method;
		this.stmts = PTA.getMethodStmts(method);
		int paraCount = method.getParameterCount();
		paraLocals = new Value[paraCount];
		this.paraStart=method.isStatic()?0:1;
		this.localStart=this.paraStart+paraCount;
	}
	/* If a native method has no side effect, call this method.
	 * Currently, it does nothing.
	 * 
	 * TO BE OVERRIDED
	 */
	abstract void simulate();
	
	protected Value getThis(){
		if(thisLocal==null){
			RefType type = method.getDeclaringClass().getType();
			Value thisRef = new ThisRef(type);
			thisLocal = getLocal(type,0);
			addIdentity(thisLocal, thisRef);
		}
		return thisLocal;
	}
	
	protected Value getPara(int index){
		Value paraLocal = paraLocals[index];
		if(paraLocal==null){
			Type type = method.getParameterType(index);
			Value paraRef = new ParameterRef(type, index);
			paraLocal = getLocal(type,paraStart+index);
			addIdentity(paraLocal, paraRef);
			paraLocals[index]=paraLocal;
		}
		return paraLocal;
	}
	
	private void addIdentity(Value lValue, Value rValue) {
		stmts.add(new JIdentityStmt(lValue, rValue));
	}
	
	protected Value getNew(RefType type){
		Value newExpr = new JNewExpr(type);
		Value local = getNextLocal(type);
		addAssign(local, newExpr);
		return local;
	}
	
	protected Value getNewArray(RefType type){
		Value newExpr = new JNewArrayExpr(type,IntConstant.v(0));
		Value local = getNextLocal(ArrayType.v(type,1));
		addAssign(local, newExpr);
		return local;
	}
	
	protected Value getNextLocal(Type type){
		return getLocal(type,localStart++);
	}
	
	private Value getLocal(Type type,int index){
		return new JimpleLocal("r"+index, type);
	}
	
	protected void addReturn(Value ret){
		stmts.add(new JReturnStmt(ret));
	}
	
	protected Value getStaticFieldRef(String className, String name){
		return Jimple.v().newStaticFieldRef(RefType.v(className).getSootClass().getFieldByName(name).makeRef());
	}
	
	protected Value getArrayRef(Value base){
		return new JArrayRef(base, IntConstant.v(0));
	}
	/**add an instance invocation
	 * receiver.sig(args)
	 * @param receiver
	 * @param sig
	 * @param args
	 * */
	protected void addInvoke(Value receiver, String sig, Value... args){
		SootMethodRef methodRef = Scene.v().getMethod(sig).makeRef();
		List<Value> argsL = Arrays.asList(args);
		Value invoke = methodRef.declaringClass().isInterface()?new JInterfaceInvokeExpr(receiver,methodRef,argsL):new JVirtualInvokeExpr(receiver,methodRef,argsL);
		stmts.add(new JInvokeStmt(invoke));
	}
	/**add an instance invocation and get the return value
	 * rx = receiver.sig(args)
	 * @param receiver
	 * @param sig
	 * @param args
	 * @return rx
	 * */
	protected Value getInvoke(Value receiver, String sig, Value... args){
		SootMethodRef methodRef = Scene.v().getMethod(sig).makeRef();
		List<Value> argsL = Collections.emptyList();
		Value invoke = methodRef.declaringClass().isInterface()?new JInterfaceInvokeExpr(receiver,methodRef,argsL):new JVirtualInvokeExpr(receiver,methodRef,argsL);
		Value rx = getNextLocal(methodRef.returnType());
		addAssign(rx, invoke);
		return rx;
	}
	/**add a static invocation
	 * sig(args)
	 * @param sig
	 * @param args
	 * */
	protected void addInvoke(String sig, Value... args){
		SootMethodRef methodRef = Scene.v().getMethod(sig).makeRef();
		List<Value> argsL = Arrays.asList(args);
		stmts.add(new JInvokeStmt(new JStaticInvokeExpr(methodRef,argsL)));
	}
	/**add a static invocation and get the return value
	 * rx = sig(args)
	 * @param sig
	 * @param args
	 * @return rx
	 * */
	protected Value getInvoke(String sig, Value... args){
		SootMethodRef methodRef = Scene.v().getMethod(sig).makeRef();
		List<Value> argsL = Collections.emptyList();
		Value rx = getNextLocal(methodRef.returnType());
		addAssign(rx, new JStaticInvokeExpr(methodRef,argsL));
		return rx;
	}
	
	protected void addAssign(Value lValue, Value rValue) {
		stmts.add(new JAssignStmt(lValue, rValue));
	}
}
