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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import driver.Config;
import driver.FakeMainFactory;
import driver.PTAOptions;
import pag.MethodPAG;
import pag.PAG;
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
import soot.PointsToSet;
import soot.RefLikeType;
import soot.RefType;
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
import soot.jimple.spark.pag.SparkField;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.queue.QueueReader;

/**
 * Gather stats on the performance and precision of a PTA run.
 * 
 * Each new run of Spark will over-write stats
 * 
 * - Wall time (sec) - Memory (max, current) before and after - Reachable
 * methods (context and no-context) - Total reachable casts - Reachable casts
 * that may fail - Call graph edges - Context sensitive call graph edges - Total
 * reachable virtual call sites - Polymorphic virtual call sites (sites with >1
 * target methods) - Number of pointers (local and global) - Total points to
 * sets size (local and global) context insensitive (convert to alloc site)
 *
 */
public class PTAEvaluator {
	private static final int STATLENGTH = 50;
	private static final char MAKEUPCHAR = ' ';
	private static final int GB = 1024 * 1024 * 1024;

	protected Date startTime;
	private StringBuffer report=new StringBuffer();

	/** all method reachable from the harness main */
	protected Set<SootMethod> reachableMethods;
	/** add context+methods that are reachable */
	protected Set<MethodOrMethodContext> reachableParameterizedMethods;

	protected PTA pta;
	protected PAG pag;

	private static RefType exceptionType=RefType.v("java.lang.Throwable");
	
	public PTAEvaluator(PTA pta) {
		this.pta = pta;
		pag = pta.getPag();
	}

	/**
	 * Note the start of a pta run.
	 */
	public void begin() {
		Runtime runtime = Runtime.getRuntime();// Getting the runtime reference
												// from system
		addLine(" ====== Memory Usage ======");
		addLine("Used Memory Before" , (runtime.totalMemory() - runtime.freeMemory()) / GB + " GB");// Print used memory
		addLine("Free Memory Before" , runtime.freeMemory() / GB + " GB");// Print free memory
		addLine("Total Memory Before", runtime.totalMemory() / GB + " GB");// Print total available memory
		addLine("Max Memory Before" , runtime.maxMemory() / GB + " GB");// Print Maximum available memory
		addLine("Analysis" , PTAOptions.ptaPattern);
		startTime = new Date();// get current date time with Date()
	}

	/**
	 * Note the end of a pta run.
	 */
	public void end() {
		// done with processing
		Date endTime = new Date();
		long elapsedTime = endTime.getTime() - startTime.getTime();
		addLine(makeUp("Time (sec):") + (((double) elapsedTime) / 1000.0));
		// memory stats
		Runtime runtime = Runtime.getRuntime();// Getting the runtime reference
												// from system
		addLine(makeUp("Used Memory After:") + (runtime.totalMemory() - runtime.freeMemory()) / GB + " GB");// Print used memory
		addLine(makeUp("Free Memory After:") + runtime.freeMemory() / GB + " GB");// Print free memory
		addLine(makeUp("Total Memory After:") + runtime.totalMemory() / GB + " GB");// Print total available memory
		addLine(makeUp("Max Memory After:") + runtime.maxMemory() / GB + " GB");// Print Maximum available memory
	}
	boolean down = false;
	public void doStatistics(){
		down=true;
		addLine(" ====== Call Graph ======");
		callGraphProcessing();
		addLine(" ====== Statements ======");
		stmtProcessing();
		addLine(" ====== Nodes ======");
		varNodeProcessing();
		allocNodeProcessing();
		aliasProcessing();
		addLine(" ====== Assignments ======");
		asmtProcessing();
		addLine(" ====== Classes ======");
		clzProcessing();
	}

	protected void callGraphProcessing() {
		int entries = 0;
		
		CallGraph callGraph = Scene.v().getCallGraph();
		// fill reachable methods map
		reachableMethods = new LinkedHashSet<SootMethod>();
		reachableParameterizedMethods = new LinkedHashSet<MethodOrMethodContext>();
		Set<MethodOrMethodContext> reachableAppParameterizedMethods = new LinkedHashSet<MethodOrMethodContext>();
		Set<SootMethod> reachableAppMethods = new LinkedHashSet<SootMethod>();

		Set<InsensEdge> insEdges = new HashSet<InsensEdge>();
		int CSStaticToStatic = 0, CSStaticToInstance=0, CSInstanceToStatic=0, CSInstancetoInstance=0;
		int CIStaticToStatic = 0, CIStaticToInstance=0, CIInstanceToStatic=0, CIInstancetoInstance=0;
		for (QueueReader<? extends MethodOrMethodContext> qr = pta.getCgb().getReachableMethods().listener(); qr
				.hasNext();) {
			final MethodOrMethodContext momc = qr.next();
			final SootMethod m = momc.method();
			reachableParameterizedMethods.add(momc);//加上context的method
			reachableMethods.add(m);//纯Method
			if (Config.v().isAppClass(m.getDeclaringClass())) {
				reachableAppParameterizedMethods.add(momc);
				reachableAppMethods.add(momc.method());
			}

			for (Iterator<Edge> iterator = callGraph.edgesInto(momc); iterator.hasNext();) {
				Edge e = iterator.next();
				if(e.src()==FakeMainFactory.getFakeMain()) {
					entries++;
					continue;
				}
				if (e.src().isStatic())
					if(e.isStatic())
						CSStaticToStatic++;
					else
						CSStaticToInstance++;
				else
					if(e.isStatic())
						CSInstanceToStatic++;
					else
						CSInstancetoInstance++;
				if (insEdges.add(new InsensEdge(e))) {//insensitivity只计算一次对callEdge
					if (e.src().isStatic())
						if(e.isStatic())
							CIStaticToStatic++;
						else
							CIStaticToInstance++;
					else
						if(e.isStatic())
							CIInstanceToStatic++;
						else
							CIInstancetoInstance++;
				}
			}
		}
		reachableMethods.stream().filter(m -> m.isNative())
				.forEach(m -> G.v().out.printf("Warning: %s is a native method!\n", m));
//		int lines=0;
//		for(SootMethod m:reachableMethods)
//			if(m.getDeclaringClass().isApplicationClass()&&m.isConcrete())
//				lines+=m.getActiveBody().getUnits().size();
//		addLine(makeUp("#lines of App code:") + lines);

		
		addLine(makeUp("#Method (Static):") + (Scene.v().getMethodNumberer().size()-1));//-fakeMain
		addLine(makeUp("#Reachable Method (CI):") + (reachableMethods.size()-1));//-fakeMain
		addLine(makeUp("#Reachable Method (CS):") + (reachableParameterizedMethods.size()-1));//-fakeMain
		addLine(makeUp("#Reachable App Method (CI):") + reachableAppMethods.size());
		addLine(makeUp("#Reachable App Method (CS):") + reachableAppParameterizedMethods.size());
		addLine(makeUp("#Call Edge(CI):") + insEdges.size());
		addLine(makeUp("\t#Static-Static Call Edge(CI):") + CIStaticToStatic);
		addLine(makeUp("\t#Static-Instance Call Edge(CI):") + CIStaticToInstance);
		addLine(makeUp("\t#Instance-Static Call Edge(CI):") + CIInstanceToStatic);
		addLine(makeUp("\t#Instance-Instance Call Edge(CI):") + CIInstancetoInstance);
		addLine(makeUp("#Call Edge(CS):") + (callGraph.size()-entries));
		addLine(makeUp("\t#Static-Static Call Edge(CS):") + (CSStaticToStatic-FakeMainFactory.clinitsSize()));
		addLine(makeUp("\t#Static-Instance Call Edge(CS):") + (CSStaticToInstance));
		addLine(makeUp("\t#Instance-Static Call Edge(CS):") + (CSInstanceToStatic));
		addLine(makeUp("\t#Instance-Instance Call Edge(CS):") + (CSInstancetoInstance));
		addLine(makeUp("#receivers:") + pta.getCgb().getReceiverToSitesMap().size());
		addLine(makeUp("\t#thisreceivers:") + pta.getCgb().getReceiverToSitesMap().entrySet().stream()
				.filter(e->e.getKey() instanceof ContextVar_Node)
				.filter(e->((ContextVar_Node) e.getKey()).base() instanceof LocalVar_Node)
				.filter(e->((LocalVar_Node) ((ContextVar_Node) e.getKey()).base()).isThis())
				.count());
		addLine(makeUp("#avg p2s size for virtualcalls:")
				+ (callGraph.size() - CSStaticToStatic - CSInstanceToStatic) * 1.0 / pta.getCgb().getReceiverToSitesMap().size());
	}

	protected void stmtProcessing() {
		final TypeMask typeManager = pag.getTypeManager();// Get type manager
															// from Soot
		CallGraph callGraph = Scene.v().getCallGraph();

		int totalCasts = 0;
		int appCasts = 0;
		int totalCastsMayFail = 0;
		int appCastsMayFail = 0;
		int totalVirtualCalls = 0;
		int appVirtualCalls = 0;
		int totalPolyCalls = 0;
		int appPolyCalls = 0;
		int totalStaticCalls = 0;
		int totalPolyCallTargets = 0;
		int unreachable = 0;
		// loop over all reachable method's statement to find casts, local
		// references, virtual call sites
		for (SootMethod sm : reachableMethods) {
			if (!sm.isConcrete())
				continue;
			if (!sm.hasActiveBody())
				sm.retrieveActiveBody();

			boolean app = Config.v().isAppClass(sm.getDeclaringClass());

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
						if (app)
							appVirtualCalls++;
						// have to check target soot method, cannot just
						// count edges
						Set<SootMethod> targets = new HashSet<SootMethod>();

						for (Iterator<Edge> it = callGraph.edgesOutOf(st); it.hasNext();)
							targets.add(it.next().tgt());
						if (targets.size() == 0)
							unreachable++;
						if (targets.size() > 1) {
							totalPolyCallTargets += targets.size();
							totalPolyCalls++;
							if (app)
								appPolyCalls++;
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
						if (app)
							appCasts++;
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

						if (fails) {
							totalCastsMayFail++;
							if (app)
								appCastsMayFail++;
						}
					}
				}
			}
		}
		addLine(makeUp("#Cast (Total):") + totalCasts);
		addLine(makeUp("#Cast (AppOnly):") + appCasts);
		addLine(makeUp("#May Fail Cast (Total):") + totalCastsMayFail);
		addLine(makeUp("#May Fail Cast (AppOnly):") + appCastsMayFail);
		addLine(makeUp("#Static Call Site(Total):") + (totalStaticCalls-FakeMainFactory.clinitsSize()));
		addLine(makeUp("#Virtual Call Site(Total):") + totalVirtualCalls);
		addLine(makeUp("#Virtual Call Site(AppOnly):") + appVirtualCalls);
		addLine(makeUp("#Virtual Call Site(Polymorphic):") + totalPolyCalls);
		addLine(makeUp("#Virtual Call Site(Polymorphic AppOnly):") + appPolyCalls);
		addLine(makeUp("#Virtual Call Site(Unreachable):") + unreachable);
		addLine(makeUp("#Avg Poly Call Targets:") + 1.0 * totalPolyCallTargets / totalPolyCalls);
	}

	protected void varNodeProcessing() {
		int totalGlobalPointers = 0;
		int totalGlobalPointsToCi = 0;
		int totalGlobalPointsToCs = 0;
		int appGlobalPointers = 0;
		int appGlobalPointsToCi = 0;
		int appGlobalPointsToCs = 0;
		int totalLocalPointersCi = 0;
		int totalLocalPointersCs = 0;
		int totalLocalCiToCi = 0;
		int totalLocalCiToType = 0;
		int totalLocalCiToCs = 0;
		int totalLocalCsToCi = 0;
		int totalLocalCsToCs = 0;
		int appLocalPointersCi = 0;
		int appLocalPointersCs = 0;
		int appLocalCiToCi = 0;
		int appLocalCiToCs = 0;
		int appLocalCsToCi = 0;
		int appLocalCsToCs = 0;
		long varpointsto = 0;
		int sf=0,of=0,lv=0;
		long sfpt=0,ofpt=0,lvpt=0;

		// globals
		for (Object global : pag.getGlobalPointers()) {
			try {
				if (!(global instanceof SootField))
					continue;
				if(!((SootField)global).isStatic())
					continue;
				GlobalVar_Node gvn = pag.findGlobalVarNode(global);
				boolean app = Config.v().isAppClass(gvn.getDeclaringClass());

				totalGlobalPointers++;
				if (app)
					appGlobalPointers++;

				final Set<Object> allocSites = new HashSet<Object>();

				PTSetInternal pts = gvn.getP2Set();
				pts.forall(new PTSetVisitor() {
					public void visit(GNode n) {
						allocSites.add(((Alloc_Node) n).getNewExpr());
					}
				});

				totalGlobalPointsToCi += allocSites.size();
				totalGlobalPointsToCs += pts.size();
				if (app) {
					appGlobalPointsToCi += allocSites.size();
					appGlobalPointsToCs += pts.size();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// locals exclude Exceptions
		for (Local local : pag.getLocalPointers()) {
			try {
				if (isExceptionType(local.getType()))
					continue;
				LocalVar_Node lvn = pag.findLocalVarNode(local);
				boolean app = Config.v().isAppClass(lvn.getMethod().getDeclaringClass());
				totalLocalPointersCi++;
				if (app) {
					appLocalPointersCi++;
				}
				Collection<Var_Node> varNodes=pta.getVarNodes(local);
				totalLocalPointersCs += varNodes.size();
				if (app) {
					appLocalPointersCs += varNodes.size();
				}

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
				totalLocalCiToType += allocTypes.size();
				totalLocalCiToCi += allocSites.size();
				totalLocalCiToCs += pts.size();
				if (app) {
					appLocalCiToCi += allocSites.size();
					appLocalCiToCs += pts.size();
				}
				
				for (Var_Node cvn : varNodes) {
					final Set<Object> callocSites = new HashSet<Object>();
					PTSetInternal cpts = cvn.getP2Set();
					cpts.forall(new PTSetVisitor() {
						@Override
						public void visit(GNode n) {
							callocSites.add(((Alloc_Node) n).getNewExpr());
						}
					});
					totalLocalCsToCi += callocSites.size();
					totalLocalCsToCs += cpts.size();
					if (app) {
						appLocalCsToCi += callocSites.size();
						appLocalCsToCs += cpts.size();
					}
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
				sf++;
				sfpt+=var.getP2Set().size();
			}else if(var instanceof AllocDotField_Node) {
				of++;
				ofpt+=var.getP2Set().size();
			}else if(var instanceof LocalVar_Node || var instanceof ContextVar_Node && ((ContextVar_Node)var).base() instanceof LocalVar_Node) {
				lv++;
				lvpt+=var.getP2Set().size();
			}
		}

		addLine(makeUp("#Global Pointer (lib + app):") + totalGlobalPointers);
		addLine(makeUp("#Global Avg Points-To Target(CI):")
				+ ((double) totalGlobalPointsToCi) / ((double) totalGlobalPointers));
		addLine(makeUp("#Global Avg Points-To Target(CS):")
				+ ((double) totalGlobalPointsToCs) / ((double) totalGlobalPointers));
		addLine(makeUp("#App Global Pointer:") + appGlobalPointers);
		addLine(makeUp("#App Global Avg Points-To Target(CI):")
				+ ((double) appGlobalPointsToCi) / ((double) appGlobalPointers));
		addLine(makeUp("#App Global Avg Points-To Target(CS):")
				+ ((double) appGlobalPointsToCs) / ((double) appGlobalPointers));
		addLine(makeUp("#Local Pointer (lib + app):") + totalLocalPointersCi);
		addLine(makeUp("#Local Avg Points-To Type:")
				+ ((double) totalLocalCiToType) / ((double) totalLocalPointersCi));
		addLine(makeUp("#Local Avg Points-To Target(CI):")
				+ ((double) totalLocalCiToCi) / ((double) totalLocalPointersCi));
		addLine(makeUp("#Local Avg Points-To Target(CS):")
				+ ((double) totalLocalCiToCs) / ((double) totalLocalPointersCi));
		addLine(makeUp("#App Local Pointer:") + appLocalPointersCi);
		addLine(makeUp("#App Local Avg Points-To Target(CI):")
				+ ((double) appLocalCiToCi) / ((double) appLocalPointersCi));
		addLine(makeUp("#App Local Avg Points-To Target(CS):")
				+ ((double) appLocalCiToCs) / ((double) appLocalPointersCi));
		addLine(makeUp("#Context Local Pointer (lib + app):") + totalLocalPointersCs);
		addLine(makeUp("#Context Local Avg Points-To Target(CI):")
				+ ((double) totalLocalCsToCi) / ((double) totalLocalPointersCs));
		addLine(makeUp("#Context Local Avg Points-To Target(CS):")
				+ ((double) totalLocalCsToCs) / ((double) totalLocalPointersCs));
		addLine(makeUp("#App Context Local Pointer:") + appLocalPointersCs);
		addLine(makeUp("#App Context Local Avg Points-To Target(CI):")
				+ ((double) appLocalCsToCi) / ((double) appLocalPointersCs));
		addLine(makeUp("#App Context Local Avg Points-To Target(CS):")
				+ ((double) appLocalCsToCs) / ((double) appLocalPointersCs));
		addLine(makeUp("#Points-to sets:") + pag.getVarNodeNumberer().size());
		addLine(makeUp("\t#Static Field pts:") + sf);
		addLine(makeUp("\t#Alloc Field pts:") + of);
		addLine(makeUp("\t#Local pts:") + lv);
		addLine(makeUp("#Points-to relation:") + varpointsto);
		addLine(makeUp("\t#Static Field pt:") + sfpt);
		addLine(makeUp("\t#Alloc Field pt:") + ofpt);
		addLine(makeUp("\t#Local pt:") + lvpt);
	}

	protected void allocNodeProcessing() {
		addLine(makeUp("#Alloc Node(CI): ") + pag.getAllocNodes().size());
		addLine(makeUp("#Alloc Node(CS): ") + pag.getAlloc().keySet().size());
	}
	
	Map<LocalVar_Node,Set<LocalVar_Node>> assignMap = new HashMap<>();
	Map<SparkField, Map<Boolean, Set<LocalVar_Node>>> globalMap = new HashMap<>();
	int intraAlias = 0, intraAlias_incstst = 0, globalAlias = 0, globalAlias_incstst = 0;
	protected void aliasProcessing(){
		recordAndIntra(reachableMethods.stream().filter(m->Config.v().isAppClass(m.getDeclaringClass())).collect(Collectors.toSet()));
		addLine("intraAlias(App)" , intraAlias);
		addLine("intraAlias_incstst(App)" , intraAlias_incstst);
		inter();
		addLine("globalAlias(App)" , globalAlias);
		addLine("globalAlias_incstst(App)" , globalAlias_incstst);
		
		globalAlias = 0; globalAlias_incstst = 0;
		recordAndIntra(reachableMethods.stream().filter(m->!Config.v().isAppClass(m.getDeclaringClass())).collect(Collectors.toSet()));
		addLine("intraAlias" , intraAlias);
		addLine("intraAlias_incstst" , intraAlias_incstst);
		inter();
		addLine("globalAlias" , globalAlias);
		addLine("globalAlias_incstst" , globalAlias_incstst);
	}
	private void recordAndIntra(Set<SootMethod> reachableMethods){
		for(SootMethod m: reachableMethods){
			Map<SparkField, Map<Boolean, Set<LocalVar_Node>>> localMap = new HashMap<>();
			
			MethodPAG srcmpag = pta.getMethodPAG(m);
			QueueReader<GNode> reader = srcmpag.getInternalReader().clone();
			while (reader.hasNext()){
				GNode from=reader.next(), to=reader.next();
				if(from instanceof LocalVar_Node){
					if(to instanceof LocalVar_Node){
						if(!(((Var_Node) from).getVariable() instanceof Local))
							continue;
						if(!(((Var_Node) to).getVariable() instanceof Local))
							continue;
						PAG.addToMap(assignMap, (LocalVar_Node) from, (LocalVar_Node)to);
						PAG.addToMap(assignMap, (LocalVar_Node) to, (LocalVar_Node)from);
					}else if(to instanceof FieldRef_Node){
						FieldRef_Node fr = (FieldRef_Node) to;
						LocalVar_Node base = (LocalVar_Node)fr.getBase();
						if(!(((Var_Node) base).getVariable() instanceof Local))
							continue;
						addToMap(globalMap, fr.getField(), true, base);
						addToMap(localMap, fr.getField(), true, base);
					}//else//local-global
				}else if (from instanceof FieldRef_Node){
					FieldRef_Node fr = (FieldRef_Node) from;
					LocalVar_Node base = (LocalVar_Node)fr.getBase();
					if(!(((Var_Node) base).getVariable() instanceof Local))
						continue;
					addToMap(globalMap, fr.getField(), false, base);
					addToMap(localMap, fr.getField(), false, base);
				}//else//global-local or new
			}
			
			int methodAlias = 0, methodAlias_incstst = 0;
			for(Map<Boolean, Set<LocalVar_Node>> subMap : localMap.values()){
				Set<LocalVar_Node> storeSet = subMap.getOrDefault(true, Collections.emptySet());
				Set<LocalVar_Node> loadSet = subMap.getOrDefault(false, Collections.emptySet());
				int stld = checkAlias(storeSet, loadSet, assignMap)+checkAlias(loadSet, storeSet, assignMap);
				int stst = checkAlias(storeSet, storeSet, assignMap);
				methodAlias += stld;
				methodAlias_incstst += stld+stst;
			}
			
			intraAlias += methodAlias;
			intraAlias_incstst += methodAlias_incstst;
		}
		
	}
	private void inter() {
		for(Map<Boolean, Set<LocalVar_Node>> subMap : globalMap.values()){
			Set<LocalVar_Node> storeSet = subMap.getOrDefault(true, Collections.emptySet());
			Set<LocalVar_Node> loadSet = subMap.getOrDefault(false, Collections.emptySet());
			int stld = checkAlias(storeSet, loadSet, assignMap)+checkAlias(loadSet, storeSet, assignMap);
			int stst = checkAlias(storeSet, storeSet, assignMap);
			globalAlias += stld;
			globalAlias_incstst += stld+stst;
		}
	}
	private int checkAlias(Set<LocalVar_Node> set1, Set<LocalVar_Node> set2, Map<LocalVar_Node, Set<LocalVar_Node>> exclMap) {
		int num=0;
		for(LocalVar_Node l1: set1){
			Set<LocalVar_Node> exclSet = exclMap.getOrDefault(l1, Collections.emptySet());
			int l1Hashcode = l1.hashCode();
			for(LocalVar_Node l2: set2){
				int l2Hashcode = l2.hashCode();
				if(l2Hashcode<=l1Hashcode)
					continue;
				if(exclSet.contains(l2))
					continue;
				if(checkAlias(l1, l2))
					num++;
			}
		}
		return num;
	}

	private boolean checkAlias(LocalVar_Node l1, LocalVar_Node l2) {
		PointsToSet pts1= pta.reachingObjects((Local) l1.getVariable());
		PointsToSet pts2= pta.reachingObjects((Local) l2.getVariable());
		return pts1.hasNonEmptyIntersection(pts2);
	}

	public static <K, T, V> boolean addToMap(Map<K, Map<T, Set<V>>> m, K key1, T key2, V value) {
		Map<T, Set<V>> subMap = m.get(key1);
		if(subMap==null)
			m.put(key1, subMap=new HashMap<>());
		return PAG.addToMap(subMap, key2, value);
	}
	
	protected void asmtProcessing() {
		int a = 0, sp = 0, ov = 0, vo = 0, st = 0, l = 0, tst = 0, tl = 0;
		for (Set<Var_Node> s : pag.getAlloc().values())
			a += s.size();
		for (Entry<Var_Node, Set<Var_Node>> e : pag.getSimple().entrySet()) {
			Set<Var_Node> tagets = e.getValue();
			int nt = tagets.size();
			sp += nt;
			if (e.getKey() instanceof AllocDotField_Node)
				ov += nt;
			else
				for (Var_Node v : tagets)
					if (v instanceof AllocDotField_Node)
						vo++;
		}
		for (Entry<FieldRef_Node, Set<Var_Node>> s : pag.getStoreInv().entrySet()){
			st += s.getValue().size();
			Var_Node v = s.getKey().getBase();
			if(v instanceof ContextVar_Node)
				v=((ContextVar_Node) v).base();
			if(v instanceof LocalVar_Node&& ((LocalVar_Node) v).isThis())
				tst+=s.getValue().size();
		}
		for (Entry<FieldRef_Node, Set<Var_Node>> s : pag.getLoad().entrySet()){
			l += s.getValue().size();
			Var_Node v = s.getKey().getBase();
			if(v instanceof ContextVar_Node)
				v=((ContextVar_Node) v).base();
			if(v instanceof LocalVar_Node&& ((LocalVar_Node) v).isThis())
				tl+=s.getValue().size();
		}

		addLine(makeUp("#Alloc-pag-edge:") + a);
		addLine(makeUp("#Simple-pag-edge:") + sp);
		addLine(makeUp("\t#Local-to-Local:") + (sp - ov - vo));
		addLine(makeUp("\t#Field-to-Local:") + ov);
		addLine(makeUp("\t#Local-to-Field:") + vo);
		addLine(makeUp("#Store-pag-edge:") + st);
		addLine(makeUp("\t#This-Store:") + tst);
		addLine(makeUp("#Load-pag-edge:") + l);
		addLine(makeUp("\t#This-Load:") + tl);
	}

	protected void clzProcessing() {
		Set<SootClass> reachableClasses = reachableMethods.stream().map(mtd->mtd.getDeclaringClass()).collect(Collectors.toSet());
		Set<SootClass> reachableAppClasses = reachableClasses.stream().filter(Config.v()::isAppClass).collect(Collectors.toSet());

		addLine(makeUp("#Class:") + Scene.v().getClasses().size());
		addLine(makeUp("#Appclass:") + Scene.v().getApplicationClasses().size());
		addLine(makeUp("#Libclass:") + (Scene.v().getClasses().size() - Scene.v().getApplicationClasses().size()
				- Scene.v().getPhantomClasses().size()));
		addLine(makeUp("#Phantomclass:") + Scene.v().getPhantomClasses().size());
		addLine(makeUp("#Class(reachable):") + reachableClasses.size());
		addLine(makeUp("#Appclass(reachable):") + reachableAppClasses.size());
		addLine(makeUp("#Libclass(reachable):") + (reachableClasses.size() - reachableAppClasses.size()-1));//-FakeMain
	}

	public static boolean isExceptionType(Type type) {
		if (type instanceof RefType) {
			SootClass sc = ((RefType) type).getSootClass();
			if (!sc.isInterface()
					&& Scene.v().getActiveHierarchy().isClassSubclassOfIncluding(sc, exceptionType.getSootClass())) {
				return true;
			}
		}
		return false;
	}

	private void addLine(String str, Object data) {
		if(!str.endsWith(":"))
			str+=':';
		addLine(makeUp(str)+data);
	}
	protected void addLine(String str) {
		report.append(str + '\n');
	}
	protected String makeUp(String string) {
		String ret = "";
		for (int i = 0; i < STATLENGTH - string.length(); i++) {
			ret += MAKEUPCHAR;
		}
		return string + ret;
	}
	
	@Override
	public String toString() {
		if(!down)
			doStatistics();
		return report.toString();
	}

	class InsensEdge {
		SootMethod src;
		SootMethod dst;
		Unit srcUnit;

		public InsensEdge(Edge edge) {
			this.src = edge.src();
			this.dst = edge.tgt();
			srcUnit = edge.srcUnit();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((dst == null) ? 0 : dst.hashCode());
			result = prime * result + ((src == null) ? 0 : src.hashCode());
			result = prime * result + ((srcUnit == null) ? 0 : srcUnit.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			InsensEdge other = (InsensEdge) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (dst == null) {
				if (other.dst != null)
					return false;
			} else if (!dst.equals(other.dst))
				return false;
			if (src == null) {
				if (other.src != null)
					return false;
			} else if (!src.equals(other.src))
				return false;
			if (srcUnit == null) {
				if (other.srcUnit != null)
					return false;
			} else if (!srcUnit.equals(other.srcUnit))
				return false;
			return true;
		}

		private PTAEvaluator getOuterType() {
			return PTAEvaluator.this;
		}
	}
}
