/* Eagle-Guided Object-Sensitive Pointer Analysis
 * Copyright (C) 2019 Jingbo Lu and Jingling Xue
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

package pta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import driver.PTAOptions;
import pag.MethodPAG;
import pag.builder.MtdPAGBuilder;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.eagle.EagleTransGraph;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;
import soot.RefLikeType;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.pag.SparkField;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;

/**
 * A concrete class for performing Eagle-guided k-obj, i.e., ek-obj
 */
public class EagleObjectSensPTA extends EagleContextSensPTA{
	
	public EagleObjectSensPTA(int k) {
		super(k, k-1);
	}

	/**
	* Select the contexts for fields
	*/
	@Override
	public Var_Node parameterize(SparkField fld,Alloc_Node n) {
		return CSNodes.contains(fld)?pag.makeAllocDotField(n, fld):pag.makeGlobalVarNode(fld, fld.getType());
	}
	
	//=========context selector=============
	
	/**
	* Build the regularised G_{R-pag} as described in the paper
	*/
	@Override
	void addTransEdgesFromMtdPag(EagleTransGraph eagleTransGraph) {
		
		//add "This" pointer for all static methods
		
		if(PTAOptions.staticcontext != PTAOptions.EMPTY)
			addStaticThis();
		
		prePTA.getCgb().getReachableMethods().listener().forEachRemaining(momc->{
			SootMethod method =(SootMethod) momc;
			if (method.isPhantom())
				return;
			MethodPAG srcmpag = prePTA.getMethodPAG(method);
			MtdPAGBuilder srcnf = srcmpag.nodeFactory();
			QueueReader<GNode> reader = srcmpag.getInternalReader().clone();
			while (reader.hasNext()){
				GNode from=reader.next(), to=reader.next();
				if(from instanceof LocalVar_Node){
					if(to instanceof LocalVar_Node)
						eagleTransGraph.addAssignEdge((LocalVar_Node) from, (LocalVar_Node)to);
					else if(to instanceof FieldRef_Node){
						FieldRef_Node fr = (FieldRef_Node) to;
						eagleTransGraph.addStoreEdge((LocalVar_Node)from, (LocalVar_Node) fr.getBase());
					}//else//local-global
				}else if(from instanceof Alloc_Node)
					eagleTransGraph.addNewEdge((Alloc_Node)from, (LocalVar_Node) to);
				else if (from instanceof FieldRef_Node){
					FieldRef_Node fr = (FieldRef_Node) from;
					eagleTransGraph.addAssignEdge((LocalVar_Node) fr.getBase(), (LocalVar_Node)to);
				}//else//global-local
			}
			
			LocalVar_Node thisRef = (LocalVar_Node) srcnf.caseThis();
			int numParms = method.getParameterCount();
			Var_Node[] parms = new Var_Node[numParms];
			for (int i = 0; i < numParms; i++) {
				if (method.getParameterType(i) instanceof RefLikeType)
					parms[i]=(Var_Node) srcnf.caseParm(i);
			}
			Var_Node mret=method.getReturnType()instanceof RefLikeType?(Var_Node) srcnf.caseRet():null;
			
			if(method.isStatic()){
				if(PTAOptions.staticcontext != PTAOptions.EMPTY)
					getPTS(thisRef).forEach(a->{
						eagleTransGraph.addHloadEdge(a, thisRef);
						for (int i = 0; i < numParms; i++) {
							if (parms[i]!=null)
								eagleTransGraph.addHloadEdge(a, parms[i]);
						}
						if (mret!=null)
							eagleTransGraph.addHstoreEdge(mret,a);
					});
			}else{
				thisRef.getP2Set().forall(new PTSetVisitor() {
					@Override
					public void visit(GNode n) {
						Alloc_Node a = (Alloc_Node) n;
						eagleTransGraph.addHloadEdge(a, thisRef);
						for (int i = 0; i < numParms; i++) {
							if (parms[i]!=null)
								eagleTransGraph.addHloadEdge(a, parms[i]);
						}
						if (mret!=null)
							eagleTransGraph.addHstoreEdge(mret,a);
					}
				});
			}
			for (final Unit u : srcmpag.invokeStmts) {
				final Stmt s = (Stmt) u;
				
				InvokeExpr ie = s.getInvokeExpr();
				int numArgs = ie.getArgCount();
				Value[] args = new Value[numArgs];
				for (int i = 0; i < numArgs; i++) {
					Value arg = ie.getArg(i);
					if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant)
						continue;
					args[i]=arg;
				}
				LocalVar_Node retDest=null;
				if (s instanceof AssignStmt) {
					Value dest = ((AssignStmt) s).getLeftOp();
					if (dest.getType() instanceof RefLikeType)
						retDest=prePAG.findLocalVarNode(dest);
				}
				LocalVar_Node receiver=null;
				if (ie instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
					receiver = prePAG.findLocalVarNode(iie.getBase());
				}else{//static call
					if(PTAOptions.staticcontext != PTAOptions.EMPTY)
						receiver=thisRef;
				}
				for(Iterator<Edge> it=prePTA.getCallGraph().edgesOutOf(u);it.hasNext();){
					Edge e=it.next();
					SootMethod tgtmtd=e.tgt();
					MethodPAG tgtmpag = prePTA.getMethodPAG(tgtmtd);
					MtdPAGBuilder tgtnf = tgtmpag.nodeFactory();
					for (int i = 0; i < numArgs; i++) {
						if (args[i]==null||!(tgtmtd.getParameterType(i) instanceof RefLikeType))
							continue;
						Var_Node parm = (Var_Node)tgtnf.caseParm(i);
						Var_Node argNode = prePAG.findVarNode(args[i]);
						if (argNode instanceof LocalVar_Node) {
							if(receiver==null){//static call in empty context
								if(PTAOptions.staticcontext != PTAOptions.EMPTY)
									eagleTransGraph.addAssignEdge((LocalVar_Node) argNode, (LocalVar_Node)parm);
							}
							else
								eagleTransGraph.addStoreEdge((LocalVar_Node) argNode, receiver);
						}
					}
					if (retDest!=null&&tgtmtd.getReturnType()instanceof RefLikeType) {
						LocalVar_Node ret = (LocalVar_Node)tgtnf.caseRet();
						if(receiver==null){
							if(PTAOptions.staticcontext != PTAOptions.EMPTY)
								eagleTransGraph.addAssignEdge(ret, retDest);
						}
						else
							eagleTransGraph.addAssignEdge(receiver, retDest);
					}
					if(receiver!=null)
						eagleTransGraph.addStoreEdge(receiver, receiver);//do not move this out of loop
				}
			}
		});
		
		prePAG.getAllocNodes().forEach(a->{
			a.getAllFieldRefs().forEach(odf->{
				Object field = odf.getField();
				if(!prePAG.simpleLookup(odf).isEmpty())
					eagleTransGraph.addHstoreEdge(field, a);
				if(!prePAG.simpleInvLookup(odf).isEmpty())
					eagleTransGraph.addHloadEdge(a, field);
			});
		});
	}
	
	private void addStaticThis() {
		pts=new HashMap<>();
		Set<SootMethod> workList = new HashSet<>();
		//add all instance methods which potentially contain static call
		prePTA.getCgb().getReachableMethods().listener().forEachRemaining(momc->{
			SootMethod method =(SootMethod) momc;
			if(!method.isPhantom()&&!method.isStatic()){
				MethodPAG srcmpag = prePTA.getMethodPAG(method);
				LocalVar_Node thisRef = (LocalVar_Node) srcmpag.nodeFactory().caseThis();
				final PTSetInternal other = thisRef.makeP2Set();
				
				for (final Unit u : srcmpag.invokeStmts) {
					final Stmt s = (Stmt) u;
					
					InvokeExpr ie = s.getInvokeExpr();
					if (ie instanceof StaticInvokeExpr){
						for(Iterator<Edge> it=prePTA.getCallGraph().edgesOutOf(u);it.hasNext();){
							Edge e=it.next();
							SootMethod tgtmtd=e.tgt();
							MethodPAG tgtmpag = prePTA.getMethodPAG(tgtmtd);
							MtdPAGBuilder tgtnf = tgtmpag.nodeFactory();
							LocalVar_Node tgtThisRef = (LocalVar_Node) tgtnf.caseThis();// create "THIS" ptr for static method 
							Set<Alloc_Node> addTo = getPTS(tgtThisRef);
							if(other.forall(new PTSetVisitor() {
								public final void visit(GNode n) {
									if(addTo.add((Alloc_Node) n)&&PTAOptions.staticcontext==PTAOptions.THIS)
										returnValue = true;
								}
							}))
								workList.add(tgtmtd);
						}
					}
				}
			}
		});
		while(!workList.isEmpty()){
			SootMethod method =workList.iterator().next();
			workList.remove(method);
			MethodPAG srcmpag = prePTA.getMethodPAG(method);
			LocalVar_Node thisRef = (LocalVar_Node) srcmpag.nodeFactory().caseThis();
			final Set<Alloc_Node> other = getPTS(thisRef);
			
			for (final Unit u : srcmpag.invokeStmts) {
				final Stmt s = (Stmt) u;
				
				InvokeExpr ie = s.getInvokeExpr();
				if (ie instanceof StaticInvokeExpr){
					for(Iterator<Edge> it=prePTA.getCallGraph().edgesOutOf(u);it.hasNext();){
						Edge e=it.next();
						SootMethod tgtmtd=e.tgt();
						MethodPAG tgtmpag = prePTA.getMethodPAG(tgtmtd);
						MtdPAGBuilder tgtnf = tgtmpag.nodeFactory();
						LocalVar_Node tgtThisRef = (LocalVar_Node) tgtnf.caseThis();// create "THIS" ptr for static method 
						Set<Alloc_Node> addTo = getPTS(tgtThisRef);
						if(addTo.addAll(other))
							workList.add(tgtmtd);
					}
				}
			}
		}
	}

	Map<LocalVar_Node, Set<Alloc_Node>> pts;
	private Set<Alloc_Node> getPTS(LocalVar_Node localVar_Node){
		Set<Alloc_Node> ret = pts.get(localVar_Node);
		if(ret==null)
			pts.put(localVar_Node, ret=new HashSet<>());
		return ret;
	}
}
