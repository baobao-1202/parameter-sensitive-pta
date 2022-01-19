/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import driver.PTAOptions;
import pta.PTA;
import pta.PTA.CallGraphBuilder;
import soot.ArrayType;
import soot.G;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.util.NumberedString;

/**
 * DA: add transform functions
 * */
public class TraceBasedReflectionModel extends ReflectionModel {
	public static TraceBasedReflectionModel v;
	
	public static final Set<SootMethod> refBuilt=new HashSet<>();
	/**replace reflection call with appropriate statements*/
	public static void findReflectionStmt(SootMethod m) {
		if(!refBuilt.add(m))
			return;
		Collection<Unit> newUnits = new HashSet<>();
		Collection<Stmt> units = PTA.getMethodStmts(m);
		for (final Unit u : units) {
			final Stmt s = (Stmt) u;
			if (s.containsInvokeExpr()) {
				InvokeExpr ie = s.getInvokeExpr();
				if (ie instanceof InstanceInvokeExpr)
					newUnits.addAll(v.transform(m, s, (InstanceInvokeExpr) ie));
			}
		}
		newUnits.stream().map(u->(Stmt)u).forEach(units::add);
	}
//	class Guard {
//		final SootMethod container;
//		final Stmt stmt;
//		final String message;
//		
//		public Guard(SootMethod container, Stmt stmt, String message) {
//			this.container = container;
//			this.stmt = stmt;
//			this.message = message;
//		}
//	}

	protected final NumberedString sigInit = Scene.v().getSubSigNumberer().findOrAdd("void <init>()");
	
//	protected Set<Guard> guards;
	protected ReflectionTrace reflectionInfo;
//	private boolean registeredTransformation = false;

	public TraceBasedReflectionModel(CallGraphBuilder cgb) {
//		guards = new HashSet<Guard>();
		this.cgb = cgb;
		reflectionInfo = new ReflectionTrace(PTAOptions.REFLECTION_LOG);
		v=this;
	}

	public void classForName(MethodOrMethodContext container, Stmt forNameInvokeStmt) {}
	public void classNewInstance(MethodOrMethodContext container, Stmt newInstanceInvokeStmt) {}
	public void contructorNewInstance(MethodOrMethodContext container, Stmt newInstanceInvokeStmt) {}
	public void methodInvoke(MethodOrMethodContext container, Stmt invokeStmt) {}
	public void handleInvokeExpr(InvokeExpr ie, MethodOrMethodContext source, Stmt s) {}

	public Collection<Unit> transform(SootMethod source, Stmt s, InstanceInvokeExpr ie) {
		switch (ie.getMethodRef().getSignature()) {
//		case sigForName:
//			return transformClassForName(source, s);
//			break;
		case sigClassNewInstance:
			return transformClassNewInstance(source, s);
		case sigConstructorNewInstance:
			return transformContructorNewInstance(source, s);
		case sigMethodInvoke:
			return transformMethodInvoke(source, s);
		case sigFieldSet:
			return transformFieldSet(source, s);
		case sigFieldGet:
			return transformFieldGet(source, s);
		default:
			return Collections.emptySet();
		}
	}

//	private Set<Unit> transformClassForName(SootMethod container, Stmt s) {
//		if(!(s instanceof AssignStmt))
//			return Collections.emptySet();
//		Set<Unit> ret=new HashSet<>();
//		Set<String> classNames = reflectionInfo.classForNameClassNames(container);
//		if (classNames != null ){
//			for (String clsName : classNames) {
//				ret.add(transformConstantForName(clsName, container, s));
//			}
//		}
//		return ret;
//	}
	
//	private Unit transformConstantForName(String cls, SootMethod src, Stmt srcUnit) {
//		if (cls.length() > 0 && cls.charAt(0) == '[') {
//			if (cls.length() > 1 && cls.charAt(1) == 'L' && cls.charAt(cls.length() - 1) == ';') {
//				cls = cls.substring(2, cls.length() - 1);
//				return transformConstantForName(cls, src, srcUnit);
//			}
//			return null;
//		} else {
//			if (!Scene.v().containsClass(cls)) {
//				System.out.println("Warning: Class " + cls + " is" + " a dynamic class, and you did not specify"+ " it as such; graph will be incomplete!");
////				throw new RuntimeException();
//				return null;
//			} else {
//				SootClass sootcls = Scene.v().getSootClass(cls);
//				if (!sootcls.isPhantomClass()) {
//					for (SootMethod clinit : EntryPoints.v().clinitsOf(sootcls)) {
//						if(cgb.addExtraMethod(clinit))
//							System.out.println("Adding clinit for reflective for/getName(): " + sootcls);
//					}
//				}
//				Value lValue = ((AssignStmt) srcUnit).getLeftOp();
//				return new JAssignStmt(lValue, ClassConstant.v(cls));
//			}
//		}
//	}
	
	private Collection<Unit> transformClassNewInstance(SootMethod source, Stmt s) {
		Collection<Unit> ret = new HashSet<>();
		Collection<String> classNames = reflectionInfo.classNewInstanceClassNames(source);
		if (classNames != null) {
			Value lvalue = null;
			if (s instanceof AssignStmt)
				lvalue = ((AssignStmt) s).getLeftOp();
			for (String clsName : classNames) {
				SootClass cls = Scene.v().getSootClass(clsName);
				JNewExpr newExpr = new JNewExpr(cls.getType());
				Local newLocal = new JimpleLocal(clsName, cls.getType());
				ret.add(new JAssignStmt(newLocal, newExpr));
				if (lvalue != null)
					ret.add(new JAssignStmt(lvalue, newLocal));
				if (cls.declaresMethod(sigInit)) {
					SootMethod constructor = cls.getMethod(sigInit);
					ret.add(new JInvokeStmt(
							new JVirtualInvokeExpr(newLocal, constructor.makeRef(), Collections.emptyList())));
				}
			}
		}
		return ret;
	}
	private Collection<Unit> transformContructorNewInstance(SootMethod source, Stmt s) {
		Collection<Unit> ret = new HashSet<>();
		Collection<String> constructorSignatures = reflectionInfo.constructorNewInstanceSignatures(source);
		if (constructorSignatures != null) {
			Value lvalue = null;
			if (s instanceof AssignStmt)
				lvalue = ((AssignStmt) s).getLeftOp();
			InvokeExpr iie = s.getInvokeExpr();
			Value arg = null;
			if(iie.getArgCount()>0){
				arg = iie.getArg(0);
				if(arg.getType() instanceof ArrayType){
					ArrayRef arrayRef = new JArrayRef(arg, IntConstant.v(0));
					arg = new JimpleLocal(arrayRef.toString(), ((ArrayType)arg.getType()).getElementType());
					ret.add(new JAssignStmt(arg, arrayRef));
				}else{
					System.out.println("args of refl-call is of type: "+arg.getType());
				}
			}
			for (String constructorSignature : constructorSignatures) {
				SootMethod constructor = Scene.v().getMethod(constructorSignature);
				SootClass cls = constructor.getDeclaringClass();
				JNewExpr newExpr = new JNewExpr(cls.getType());
				Local newLocal = new JimpleLocal(constructorSignature, cls.getType());
				ret.add(new JAssignStmt(newLocal, newExpr));
				if (lvalue != null)
					ret.add(new JAssignStmt(lvalue, newLocal));
				List<Value> args;
				if(arg==null)
					args = Collections.emptyList();
				else{
					int argCount = constructor.getParameterCount();
					args = new ArrayList<>(argCount);
					for(int i=0;i<argCount;i++){
						args.add(arg);
					}
				}
				ret.add(new JInvokeStmt(new JVirtualInvokeExpr(newLocal, constructor.makeRef(), args)));
			}
		}
		return ret;
	}

	private Collection<Unit> transformMethodInvoke(SootMethod source, Stmt s) {
		Collection<Unit> ret = new HashSet<>();
		Collection<String> methodSignatures = reflectionInfo.methodInvokeSignatures(source);
		if (methodSignatures != null ) {
			Value lvalue = null;
			if (s instanceof AssignStmt)
				lvalue = ((AssignStmt) s).getLeftOp();
			InvokeExpr iie = s.getInvokeExpr();
			Value base = iie.getArg(0);
			Value arg = null;
			if(iie.getArgCount()>1){
				arg = iie.getArg(1);
				if(arg.getType() instanceof ArrayType){
					ArrayRef arrayRef = new JArrayRef(arg, IntConstant.v(0));
					arg = new JimpleLocal(arrayRef.toString(), ((ArrayType)arg.getType()).getElementType());
					ret.add(new JAssignStmt(arg, arrayRef));
				}
			}
			
			for (String methodSignature : methodSignatures) {
				SootMethod method = Scene.v().getMethod(methodSignature);
				List<Value> args;
				if(arg==null)
					args = Collections.emptyList();
				else{
					int argCount = method.getParameterCount();
					args = new ArrayList<>(argCount);
					for(int i=0;i<argCount;i++){
						args.add(arg);
					}
				}
				InvokeExpr ie;
				if(method.isStatic()){
					if(!(base instanceof NullConstant))
						G.v().out.println("Warning: Static ref method on an object(ignored).");
					ie=new JStaticInvokeExpr(method.makeRef(), args);
				}else{//instance
					if(base instanceof NullConstant)
						continue;//receiverObject for virtual call cannot be null
					ie=new JVirtualInvokeExpr(base, method.makeRef(), args);
				}
		
				Stmt stmt;
				if(lvalue==null)//not assign
					stmt=new JInvokeStmt(ie);
				else//assign
					stmt=new JAssignStmt(lvalue, ie);	
				ret.add(stmt);
			}
		}
		return ret;
	}
	
	private Collection<Unit> transformFieldSet(SootMethod source, Stmt s) {
		Collection<Unit> ret = new HashSet<>();
		Collection<String> fieldSignatures = reflectionInfo.fieldSetSignatures(source);
		if (fieldSignatures != null ) {
			InvokeExpr iie = s.getInvokeExpr();
			Value base = iie.getArg(0);
			Value rValue = iie.getArg(1);
			for (String fieldSignature : fieldSignatures) {
				SootField field = Scene.v().getField(fieldSignature).makeRef().resolve();
//				if(field.isFinal())//final field cannot be set!
//					continue;
				FieldRef fieldRef=null;
				if(field.isStatic()){
					if(!(base instanceof NullConstant))
						G.v().out.println("Warning: Static Field on an object(ignored).");
					try {
						fieldRef=Jimple.v().newStaticFieldRef(field.makeRef());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}else{//instance
					if(base instanceof NullConstant)
						continue;//receiverObject for InstanceField cannot be null
					fieldRef=new JInstanceFieldRef(base, field.makeRef());
				}
				Stmt stmt = new JAssignStmt(fieldRef, rValue);
				ret.add(stmt);
			}
		}
		return ret;
	}
	
	private Collection<Unit> transformFieldGet(SootMethod source, Stmt s) {
		Collection<Unit> ret = new HashSet<>();
		Collection<String> fieldSignatures = reflectionInfo.fieldGetSignatures(source);
		if (fieldSignatures != null && s instanceof AssignStmt){
			Value lvalue = ((AssignStmt) s).getLeftOp();
			InvokeExpr iie = s.getInvokeExpr();
			Value base = iie.getArg(0);
			for (String fieldSignature : fieldSignatures) {
				SootField field = Scene.v().getField(fieldSignature).makeRef().resolve();
				FieldRef fieldRef=null;
				if(field.isStatic()){
					if(!(base instanceof NullConstant))
						G.v().out.println("Warning: Static Field on an object(ignored).");
					try {
						fieldRef=Jimple.v().newStaticFieldRef(field.makeRef());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}else{//instance
					if(base instanceof NullConstant)
						continue;//receiverObject for InstanceField cannot be null
					fieldRef=new JInstanceFieldRef(base, field.makeRef());
				}
				Stmt stmt = new JAssignStmt(lvalue, fieldRef);
				ret.add(stmt);
			}
		}
		return ret;
	}
}
