/* Java and Android Analysis Framework
 * Copyright (C) 2017 Jingbo Lu and Yulei Sui
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

package util;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import driver.FakeMainFactory;
import driver.PTAOptions;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.var.AllocDotField_Node;
import pag.node.var.ContextVar_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.GlobalVar_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.PTA;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;
import soot.G;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.RefLikeType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;

/**
 * Gather limited stats on the performance and precision of a PTA run.
 */
public class SimplePTAEvaluator extends PTAEvaluator{
	
	public SimplePTAEvaluator(PTA pta) {
		super(pta);
	}

	@Override
	public void begin() {
		addLine(makeUp("Analysis: ") + PTAOptions.ptaPattern);
		startTime = new Date();// get current date time with Date()
	}

	@Override
	public void end() {
		// done with processing
		Date endTime = new Date();
		long elapsedTime = endTime.getTime() - startTime.getTime();
		addLine(makeUp("Time (sec):") + format( ((double) elapsedTime) / 1000.0 ));
		// memory stats
	}
	
	@Override
	public void doStatistics(){
		down=true;
		addLine(" ====== Call Graph ======");
		callGraphProcessing();
		addLine(" ====== Statements ======");
		stmtProcessing();
		addLine(" ====== Nodes ======");
		varNodeProcessing();
		allocNodeProcessing();
		addLine(" ====== Assignments ======");
		asmtProcessing();
		addLine(" ====== Classes ======");
		clzProcessing();
	}

	@Override
	protected void callGraphProcessing() {
		int entries = 0;
		
		CallGraph callGraph = Scene.v().getCallGraph();
		// fill reachable methods map
		reachableMethods = new LinkedHashSet<SootMethod>();
		reachableParameterizedMethods = new LinkedHashSet<MethodOrMethodContext>();

		Set<InsensEdge> insEdges = new HashSet<InsensEdge>();
		for (QueueReader<? extends MethodOrMethodContext> qr = pta.getCgb().getReachableMethods().listener(); qr
				.hasNext();) {
			final MethodOrMethodContext momc = qr.next();
			final SootMethod m = momc.method();
			reachableParameterizedMethods.add(momc);
			reachableMethods.add(m);
			for (Iterator<Edge> iterator = callGraph.edgesInto(momc); iterator.hasNext();) {
				Edge e = iterator.next();
				if(e.src()==FakeMainFactory.getFakeMain()) {
					entries++;
					continue;
				}
				insEdges.add(new InsensEdge(e));
			}
		}
		reachableMethods.stream().filter(m -> m.isNative())
				.forEach(m -> G.v().out.printf("Warning: %s is a native method!\n", m));
		
		addLine(makeUp("#Method (Static):") + (Scene.v().getMethodNumberer().size()-1));//-fakeMain
		addLine(makeUp("#Reachable Method (CI):") + (reachableMethods.size()-1));//-fakeMain
		addLine(makeUp("#Reachable Method (CS):") + (reachableParameterizedMethods.size()-1));//-fakeMain
		addLine(makeUp("#Call Edge(CI):") + insEdges.size());
		addLine(makeUp("#Call Edge(CS):") + (callGraph.size()-entries));
	}

	@Override
	protected void stmtProcessing() {
		final TypeMask typeManager = pag.getTypeManager();// Get type manager
															// from Soot
		CallGraph callGraph = Scene.v().getCallGraph();

		int totalCasts = 0;
		int totalCastsMayFail = 0;
		int totalVirtualCalls = 0;
		int totalPolyCalls = 0;
		int totalStaticCalls = 0;
		// loop over all reachable method's statement to find casts, local
		// references, virtual call sites
		for (SootMethod sm : reachableMethods) {
			if(sm==FakeMainFactory.getFakeMain())
				continue;
			if (!sm.isConcrete())
				continue;
			if (!sm.hasActiveBody())
				sm.retrieveActiveBody();

			// All the statements in the method
			for (Iterator<Unit> stmts = sm.getActiveBody().getUnits().iterator(); stmts.hasNext();) {
				Stmt st = (Stmt) stmts.next();
				
				// virtual calls
				if (st.containsInvokeExpr()) {
					InvokeExpr ie = st.getInvokeExpr();
					if (ie instanceof StaticInvokeExpr)
						totalStaticCalls++;
					else {// Virtual, Special or Instance
						totalVirtualCalls++;
						// have to check target soot method, cannot just
						// count edges
						Set<SootMethod> targets = new HashSet<SootMethod>();

						for (Iterator<Edge> it = callGraph.edgesOutOf(st); it.hasNext();)
							targets.add(it.next().tgt());
						if (targets.size() == 0) {
						}
						if (targets.size() > 1) {
							targets.size();
							totalPolyCalls++;
						}
					}
				}else if (st instanceof AssignStmt) {
					Value rhs = ((AssignStmt) st).getRightOp();
					Value lhs = ((AssignStmt) st).getLeftOp();
					if (rhs instanceof CastExpr && lhs.getType() instanceof RefLikeType) {
						final Type targetType = (RefLikeType) ((CastExpr) rhs).getCastType();
						Value v = ((CastExpr) rhs).getOp();
						if (!(v instanceof Local))
							continue;
						totalCasts++;
						boolean fails = false;
						Set<GNode> pts =new HashSet<>();
						((PTSetInternal) pta.reachingObjects((Local) v)).forall(new PTSetVisitor() {
							@Override
							public void visit(GNode n) {
								pts.add(n);
							}
						});
						for(GNode n:pts){
							if (fails)
								break;
							fails = !typeManager.castNeverFails(n.getType(), targetType);
						}

						if (fails)
							totalCastsMayFail++;
					}
				}
			}
		}
		addLine(makeUp("#Cast (Total):") + totalCasts);
		addLine(makeUp("#May Fail Cast (Total):") + totalCastsMayFail);
		addLine(makeUp("#Static Call Site(Total):") + totalStaticCalls);
		addLine(makeUp("#Virtual Call Site(Total):") + totalVirtualCalls);
		addLine(makeUp("#Virtual Call Site(Polymorphic):") + totalPolyCalls);
	}

	@Override
	protected void varNodeProcessing() {
		int totalGlobalPointers = 0;
		int totalGlobalPointsToCi = 0;
		int totalGlobalPointsToCs = 0;
		int totalLocalPointersCi = 0;
		int totalLocalPointersCs = 0;
		int totalLocalCiToCi = 0;
		int totalLocalCsToCs = 0;
		
		long varpointsto = 0;
		long sfpt=0,ofpt=0,lvpt=0;

		// globals
		for (Object global : pag.getGlobalPointers()) {
			try {
				if (!(global instanceof SootField))
					continue;
				if(!((SootField)global).isStatic())
					continue;
				GlobalVar_Node gvn = pag.findGlobalVarNode(global);

				totalGlobalPointers++;

				final Set<Object> allocSites = new HashSet<Object>();

				PTSetInternal pts = gvn.getP2Set();
				pts.forall(new PTSetVisitor() {
					public void visit(GNode n) {
						allocSites.add(((Alloc_Node) n).getNewExpr());
					}
				});

				totalGlobalPointsToCi += allocSites.size();
				totalGlobalPointsToCs += pts.size();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// locals exclude Exceptions
		for (Local local : pag.getLocalPointers()) {
			try {
				if (isExceptionType(local.getType()))
					continue;
				totalLocalPointersCi++;
				
				Collection<Var_Node> varNodes=pta.getVarNodes(local);
				totalLocalPointersCs += varNodes.size();

				final Set<Object> allocSites = new HashSet<>();
				final Set<Type> allocTypes = new HashSet<>();
				PTSetInternal pts = (PTSetInternal) pta.reachingObjects(local);
				pts.forall(new PTSetVisitor() {
					@Override
					public void visit(GNode n) {
						allocSites.add(((Alloc_Node) n).getNewExpr());
						allocTypes.add(n.getType());
					}
				});
				totalLocalCiToCi += allocSites.size();
				
				for (Var_Node cvn : varNodes) {
					final Set<Object> callocSites = new HashSet<Object>();
					PTSetInternal cpts = cvn.getP2Set();
					cpts.forall(new PTSetVisitor() {
						@Override
						public void visit(GNode n) {
							callocSites.add(((Alloc_Node) n).getNewExpr());
						}
					});
					totalLocalCsToCs += cpts.size();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// all vars
		for(Var_Node var:pag.getVarNodeNumberer())
			varpointsto+=var.getP2Set().size();
		for(Var_Node var:pag.getVarNodeNumberer()) {
			if(var instanceof GlobalVar_Node) {
				sfpt+=var.getP2Set().size();
			}else if(var instanceof AllocDotField_Node) {
				ofpt+=var.getP2Set().size();
			}else if(var instanceof LocalVar_Node || var instanceof ContextVar_Node && ((ContextVar_Node)var).base() instanceof LocalVar_Node) {
				lvpt+=var.getP2Set().size();
			}
		}

		addLine(makeUp("#Global Pointer (lib + app):") + totalGlobalPointers);
		addLine(makeUp("#Global Avg Points-To Target(CI):")
				+ format( ((double) totalGlobalPointsToCi) / ((double) totalGlobalPointers) ));
		addLine(makeUp("#Global Avg Points-To Target(CS):")
				+ format( ((double) totalGlobalPointsToCs) / ((double) totalGlobalPointers) ));
		addLine(makeUp("#Local Pointer (lib + app):") + totalLocalPointersCi);
		addLine(makeUp("#Local Avg Points-To Target(CI):")
				+ format( ((double) totalLocalCiToCi) / ((double) totalLocalPointersCi) ));
		addLine(makeUp("#Context Local Pointer (lib + app):") + totalLocalPointersCs);
		addLine(makeUp("#Context Local Avg Points-To Target(CS):")
				+ format( ((double) totalLocalCsToCs) / ((double) totalLocalPointersCs) ));
		addLine(makeUp("#Points-to relation:") + varpointsto);
		addLine(makeUp("\t#Static Field pt:") + sfpt);
		addLine(makeUp("\t#Alloc Field pt:") + ofpt);
		addLine(makeUp("\t#Local pt:") + lvpt);
	}

	@Override
	protected void asmtProcessing() {
		int sp = 0, st = 0, l = 0;
		for (Entry<Var_Node, Set<Var_Node>> s : pag.getSimple().entrySet()) {
			sp += s.getValue().size();
		}
		for (Entry<FieldRef_Node, Set<Var_Node>> s : pag.getStoreInv().entrySet()){
			st += s.getValue().size();
		}
		for (Entry<FieldRef_Node, Set<Var_Node>> s : pag.getLoad().entrySet()){
			l += s.getValue().size();
		}
		
		addLine(makeUp("#Simple-pag-edge:") + sp);
		addLine(makeUp("#Store-pag-edge:") + st);
		addLine(makeUp("#Load-pag-edge:") + l);
	}

	@Override
	protected void clzProcessing() {
		Set<SootClass> reachableClasses = reachableMethods.stream().map(mtd->mtd.getDeclaringClass()).collect(Collectors.toSet());

		addLine(makeUp("#Class:") + Scene.v().getClasses().size());
		addLine(makeUp("#Class(reachable):") + reachableClasses.size());
	}
	
	 public static String format(double data){
		 return String.format("%.2f", data);
	 }
}
