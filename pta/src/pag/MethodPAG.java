 /* Soot - a J*va Optimization Framework
 * Copyright (C) 2002 Ondrej Lhotak
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

package pag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import driver.PTAOptions;
import pag.builder.MtdPAGBuilder;
import pag.nativeModel.NativeMethodDriver;
import pag.node.GNode;
import pta.PTA;
import reflection.TraceBasedReflectionModel;
import soot.Body;
import soot.EntryPoints;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;
import soot.util.NumberedString;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;

/**
 * Part of a pointer assignment graph for a single method.
 * 
 * @author Ondrej Lhotak
 */
public class MethodPAG {
	protected PAG pag;
	SootMethod method;
	private final ChunkedQueue<GNode> internalEdges = new ChunkedQueue<GNode>();
	private final QueueReader<GNode> internalReader = internalEdges.reader();
	private Collection<Stmt> stmts;
	public Collection<Stmt> invokeStmts = new HashSet<>();
	public Map<Stmt, Value> callToThrow = new HashMap<>();
	public Map<Value, List<Trap>> throwToCatch = new HashMap<>();
	
	public Set<SootMethod> clinits = new HashSet<>();
	void addClinits(){
		nodeFactory.clinitclasses.forEach(cl->{
			clinits.addAll(EntryPoints.v().clinitsOf(cl));
		});
	}
	
	Value getThrow(Stmt stmt){
		Value ret = callToThrow.get(stmt);
		if(ret == null)
			callToThrow.put(stmt, ret=new JimpleLocal(stmt.toString(), RefType.v("java.lang.Exception")));
		return ret;
	}
	void addCatch(Value toThrow, Trap trap){
		List<Trap> traps = throwToCatch.get(toThrow);
		if(traps==null)
			throwToCatch.put(toThrow, traps=new ArrayList<>());
		traps.add(trap);
	}

	public PAG pag() {
		return pag;
	}

	public MethodPAG(PAG pag, SootMethod m) {
		this.pag = pag;
		this.method = m;
		this.nodeFactory = new MtdPAGBuilder(pag, this);
		this.stmts = PTA.getMethodStmts(m);
		build();
	}

	
	public SootMethod getMethod() {
		return method;
	}

	protected MtdPAGBuilder nodeFactory;

	public MtdPAGBuilder nodeFactory() {
		return nodeFactory;
	}

	protected void build() {
		buildReflective();
		buildNative();
		buildNormal();
		addClinits();
//		buildException();
		addMiscEdges();
	}

	protected void buildReflective(){
		if (method.isConcrete() && PTAOptions.REFLECTION_LOG!=null)
			TraceBasedReflectionModel.findReflectionStmt(method);
	}
	protected void buildNative(){
		if (method.isNative() && PTAOptions.sparkOpts.simulate_natives())
			NativeMethodDriver.buildNative(method);
	}
	
	protected void buildNormal() {
		for (Iterator<Stmt> unitsIt = stmts.iterator(); unitsIt.hasNext();)
			try{
				nodeFactory.handleStmt((Stmt) unitsIt.next());
			}catch (Exception e) {
				System.out.println("Warning:" +e);
			}
			
	}
	protected void buildException(){
		if(!method.isConcrete())
			return;
		Body body = method.getActiveBody();
		Chain<Trap> traps = body.getTraps();
		PatchingChain<Unit> units = body.getUnits();
		traps.forEach(trap->{
			units.iterator(trap.getBeginUnit(), trap.getEndUnit()).forEachRemaining(unit->{
				Stmt stmt = (Stmt) unit;
				Value toThrow;
 				if(stmt.containsInvokeExpr()){
					if(stmt.getInvokeExpr().getMethod().getExceptions().isEmpty())
						return;
					toThrow = getThrow(stmt);
				}else if(stmt instanceof ThrowStmt)
					toThrow = ((ThrowStmt) stmt).getOp();
				else
					return;
 				addCatch(toThrow, trap);
			});
			
		});
	}

	protected void addMiscEdges() {
		// Add node for parameter (String[]) in main method
		if (method.getNumberedSubSignature().equals(sigMain))
			addInternalEdge(pag().GlobalNodeFactory().caseArgv(), nodeFactory.caseParm(0));
		else if (method.getNumberedSubSignature().equals(sigCanonicalize)) {
			SootClass cl = method.getDeclaringClass();
			while (true) {
				if (cl.equals(Scene.v().getSootClass("java.io.FileSystem"))) {
					addInternalEdge(pag.GlobalNodeFactory().caseCanonicalPath(), nodeFactory.caseRet());
					break;
				}
				if (!cl.hasSuperclass())
					break;
				cl = cl.getSuperclass();
			}
		} else if(PTAOptions.hack)
			switch (method.getSignature()) {
			case "<java.lang.ClassLoader: long findNative(java.lang.ClassLoader,java.lang.String)>":
				addInternalEdge(pag().GlobalNodeFactory().caseDefaultClassLoader(), nodeFactory.caseParm(0));
				break;
			case "<java.lang.ClassLoader: void <init>()>":
			case "<java.lang.ClassLoader: java.lang.Class loadClassInternal(java.lang.String)>":
			case "<java.lang.ClassLoader: void checkPackageAccess(java.lang.Class,java.security.ProtectionDomain)>":
			case "<java.lang.ClassLoader: void addClass(java.lang.Class)>":
				addInternalEdge(pag.GlobalNodeFactory().caseDefaultClassLoader(), nodeFactory.caseThis());
				break;
			case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.Runnable)>":
			case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>":
				addInternalEdge(pag().GlobalNodeFactory().caseMainThread(), nodeFactory.caseThis());
				addInternalEdge(pag().GlobalNodeFactory().caseMainThreadGroup(), nodeFactory.caseParm(0));
				break;
			case "<java.lang.Thread: void exit()>":
				addInternalEdge(pag.GlobalNodeFactory().caseMainThread(), nodeFactory.caseThis());
				break;
			case "<java.lang.ThreadGroup: void <init>()>":
				addInternalEdge(pag.GlobalNodeFactory().caseMainThreadGroup(), nodeFactory.caseThis());
				break;
			case "<java.lang.ThreadGroup: void uncaughtException(java.lang.Thread,java.lang.Throwable)>":
				addInternalEdge(pag.GlobalNodeFactory().caseMainThreadGroup(), nodeFactory.caseThis());
				addInternalEdge(pag.GlobalNodeFactory().caseMainThread(), nodeFactory.caseParm(0));
				break;
//			case "<java.lang.ref.Finalizer: void <init>(java.lang.Object)>":
//				addInternalEdge(nodeFactory.caseThis(), pag().GlobalNodeFactory().caseFinalizeQueue());
//				break;
			case "<java.lang.ref.Finalizer: void runFinalizer()>":
				addInternalEdge(pag.GlobalNodeFactory().caseFinalizeQueue(), nodeFactory.caseThis());
				break;
//			case "<java.lang.ref.Finalizer: void access$100(java.lang.Object)>":
//				addInternalEdge(pag.GlobalNodeFactory().caseFinalizeQueue(), nodeFactory.caseParm(0));
//				break;
			case "<java.security.PrivilegedActionException: void <init>(java.lang.Exception)>":
				addInternalEdge(pag.GlobalNodeFactory().casePrivilegedActionException(), nodeFactory.caseThis());
				addInternalEdge(pag.GlobalNodeFactory().caseThrow(), nodeFactory.caseParm(0));
				break;
			default:
			}

//		boolean isImplicit = false;
//		for (SootMethod implicitMethod : EntryPoints.v().implicit()) {
//			if (implicitMethod.getNumberedSubSignature().equals(method.getNumberedSubSignature())) {
//				isImplicit = true;
//				break;
//			}
//		}
//		if (isImplicit) {
//			SootClass c = method.getDeclaringClass();
//			outer: do {
//				while (!c.getName().equals("java.lang.ClassLoader")) {
//					if (!c.hasSuperclass())
//						break outer;
//					c = c.getSuperclass();
//				}
//				if (method.getName().equals("<init>"))
//					continue;
//				addInternalEdge(pag().GlobalNodeFactory().caseDefaultClassLoader(), nodeFactory.caseThis());
//				addInternalEdge(pag().GlobalNodeFactory().caseMainClassNameString(), nodeFactory.caseParm(0));
//			} while (false);
//		}
	}

	public void addInternalEdge(GNode src, GNode dst) {
		if (src == null)
			return;
		internalEdges.add(src);
		internalEdges.add(dst);
	}

	public QueueReader<GNode> getInternalReader() {
		return internalReader;
	}

	protected static final NumberedString sigMain = Scene.v().getSubSigNumberer()
			.findOrAdd("void main(java.lang.String[])");
	protected static final NumberedString sigCanonicalize = Scene.v().getSubSigNumberer()
			.findOrAdd("java.lang.String canonicalize(java.lang.String)");
}
