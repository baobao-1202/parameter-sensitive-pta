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

package pta;

import static driver.PTAOptions.sparkOpts;

import pag.PAG;
import pag.MethodPAG;
import pag.builder.GlobalPAGBuilder;
import pag.builder.MtdPAGBuilder;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.call.VirtualInvokeSite;
import pag.node.var.Var_Node;
import pag.node.var.AllocDotField_Node;
import pta.pts.EmptyPTSet;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;
import reflection.DefaultReflectionModel;
import reflection.ReflectionModel;
import reflection.TraceBasedReflectionModel;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import driver.Config;
import driver.FakeMainFactory;
import driver.PTAOptions;
import soot.Context;
import soot.FastHierarchy;
import soot.G;
import soot.Kind;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PhaseOptions;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.ReachingTypeDumper;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.pag.*;
import soot.jimple.spark.solver.Propagator;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Targets;
import soot.jimple.toolkits.callgraph.VirtualCalls;
import soot.options.CGOptions;
import soot.options.Options;
import soot.options.SparkOptions;
import soot.util.NumberedString;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;
import util.PTAEvaluator;
import util.PTAUtils;
import util.SimplePTAEvaluator;

public class PTA implements PointsToAnalysis {
	protected PAG pag;
	protected CallGraphBuilder cgb;
	protected QueueReader<MethodOrMethodContext> reachablesReader;
	protected QueueReader<Edge> callEdges;
	protected static HashMap<SootMethod, Collection<Stmt>> methodToStmts = new HashMap<>();
	protected HashMap<SootMethod, MethodPAG> methodToPag = new HashMap<>();
	protected PTAEvaluator evaluator;
	
	public PTAEvaluator evaluator() {
		return evaluator;
	}
	public static Collection<Stmt> getMethodStmts(SootMethod m){
		Collection<Stmt> ret = methodToStmts.get(m);
		if (ret == null){
			methodToStmts.put(m, ret = new HashSet<>());
			if(m.isConcrete())
				m.retrieveActiveBody().getUnits().stream().map(u->(Stmt)u).forEach(ret::add);
		}
		return ret;
	}
	public MethodPAG getMethodPAG(SootMethod m){
		MethodPAG ret = methodToPag.get(m);
		if (ret == null)
			methodToPag.put(m, ret = new MethodPAG(pag, m));
		return ret;
	}
	public PAG getPag() {
		return pag;
	}
	public CallGraphBuilder getCgb() {
		return cgb;
	}
	public CallGraph getCallGraph() {
		return cgb.getCallGraph();
	}

	public PTA() {
		pag = new PAG();
		pag.setGlobalNodeFactory(new GlobalPAGBuilder(pag, this));
		Scene.v().setPointsToAnalysis(this);
		evaluator = new SimplePTAEvaluator(this);
	}

	private void init(){
		cgb = createCallGraphBuilder();
		callEdges = Scene.v().getCallGraph().listener();
		reachablesReader = cgb.reachables.listener();
	}
	
	protected CallGraphBuilder createCallGraphBuilder() {
		return new CallGraphBuilder();
	}

	public void run() {
		if (sparkOpts.force_gc()){
			// Do 5 times because the garbage collector doesn't seem to always collect everything on the first try.
			for(int i=0;i<5;i++)
				System.gc();
		}
		init();
		evaluator.begin();

		// Build type masks
		Date startTM = new Date();
		pag.getTypeManager().makeTypeMask();//
		Date endTM = new Date();
		reportTime("Type masks making", startTM, endTM);

		// Propagate
		Date startProp = new Date();
		getPropagator().propagate();
		Date endProp = new Date();
		reportTime("Points-to resolution:", startProp, endProp);

		if (!sparkOpts.on_fly_cg() || sparkOpts.vta()) {
			soot.jimple.toolkits.callgraph.CallGraphBuilder cicgb = new soot.jimple.toolkits.callgraph.CallGraphBuilder(
					this);
			cicgb.build();
		}
		
		evaluator.end();
		dumpStats();
	}

	protected Propagator getPropagator(){
		return new pta.solver.Solver(this);
	}

	private static void reportTime(String desc, Date start, Date end) {
		long time = end.getTime() - start.getTime();
		G.v().out.println("[PTA] " + desc + " in " + time / 1000 + "." + (time / 100) % 10 + " seconds.");
	}

	private void dumpStats() {
		final String output_dir = SourceLocator.v().getOutputDir();
		if (sparkOpts.dump_answer())
			new ReachingTypeDumper(this, output_dir).dump();
		if (PTAOptions.dumppts)
			PTAUtils.dumpPts(this, !PTAOptions.dumplibpts);
		if (PTAOptions.dumpCallGraph)
//			PTAUtils.dumpCallGraph(getCallGraph(),true);
			PTAUtils.dumpSlicedCallGraph(getCallGraph(), Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(java.lang.Object)>"));
		if (PTAOptions.dumppag) {
			PTAUtils.dumpPAG(pag, "final_pag");
			PTAUtils.dumpMPAGs(this, "mpags");
			PTAUtils.dumpNodeNames("nodeNames");
		}
		// if (DruidOptions.dumphtml)
		// new PAG2HTML(pag, output_dir).dump();
	}
	
	public void build() {//Solver调用pta.build()
		while (true) {
			if(!reachablesReader.hasNext()){
				cgb.reachables.update();
				if(!reachablesReader.hasNext())
					break;
			}
			MethodOrMethodContext momc = reachablesReader.next();
			SootMethod method = momc.method();
			if (method.isPhantom())
				continue;

			MethodPAG mpag = getMethodPAG(method);
			addToPAG(mpag, momc.context());
			
			Collection<Stmt> added = FakeMainFactory.modifyFakeMain(mpag.clinits);
			if(!added.isEmpty())
				updateClinits(added);
			
			cgb.handleInvoke(momc, mpag.invokeStmts);
		}
		callEdges.forEachRemaining(e -> processCallEdge(e));
	}
	
	void updateClinits(Collection<Stmt> added) {
		cgb.handleInvoke(FakeMainFactory.getFakeMain(), added);
	}
	
	protected void addToPAG(MethodPAG mpag, Context cxt) {
		for(QueueReader<GNode> reader = mpag.getInternalReader().clone();reader.hasNext();)
			pag.addEdge(reader.next(),reader.next());
	}
	
	public Var_Node parameterize(SparkField fld, Alloc_Node n) {
//		if(n==null)
//			return pag.makeGlobalVarNode(fld, fld.getType());//field based
		return pag.makeAllocDotField(n, fld);
	}

	public Collection<Var_Node> getVarNodes(Local local) {
		return Collections.singleton(pag.findLocalVarNode(local));
	}

	/**
	 * Adds method target as a possible target of the invoke expression in s. If
	 * target is null, only creates the nodes for the call site, without
	 * actually connecting them to any target method.
	 **/
	protected void processCallEdge(Edge e) {//dealInvoke,targstMethod Is Known
		MethodPAG srcmpag = getMethodPAG(e.src());
		MethodPAG tgtmpag = getMethodPAG(e.tgt());
		Stmt s = (Stmt) e.srcUnit();
		MtdPAGBuilder srcnf = srcmpag.nodeFactory();
		MtdPAGBuilder tgtnf = tgtmpag.nodeFactory();
		SootMethod tgtmtd = tgtmpag.getMethod();
		InvokeExpr ie = s.getInvokeExpr();
		int numArgs = ie.getArgCount();
		for (int i = 0; i < numArgs; i++) {
			Value arg = ie.getArg(i);
			if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant)
				continue;
			Type tgtType = tgtmtd.getParameterType(i);
			if (!(tgtType instanceof RefLikeType) )
				continue;
			GNode argNode = srcnf.getNode(arg);
			GNode parm = tgtnf.caseParm(i);
			pag.addEdge(argNode, parm);
		}
		if (s instanceof AssignStmt) {
			Value dest = ((AssignStmt) s).getLeftOp();
			if (dest.getType() instanceof RefLikeType && tgtmtd.getReturnType()instanceof RefLikeType) {
				GNode destNode = srcnf.getNode(dest);
				GNode retNode = tgtnf.caseRet();
				pag.addEdge(retNode, destNode);
			}
		}
	}

//	protected void handlePriviledgedEdge(MethodPAG srcmpag, MethodPAG tgtmpag, Edge e) {
//		// Flow from first parameter of doPrivileged() invocation
//		// to this of target, and from return of target to the
//		// return of doPrivileged()
//		InvokeExpr ie = e.srcStmt().getInvokeExpr();
//		GNode parm = srcmpag.nodeFactory().getNode(ie.getArg(0)).getReplacement();
//		GNode thiz = tgtmpag.nodeFactory().caseThis().getReplacement();
//		pag.addEdge(parm, thiz);
//		if (e.srcUnit() instanceof AssignStmt) {
//			AssignStmt as = (AssignStmt) e.srcUnit();
//			GNode ret = tgtmpag.nodeFactory().caseRet().getReplacement();
//			GNode lhs = srcmpag.nodeFactory().getNode(as.getLeftOp()).getReplacement();
//			pag.addEdge(ret, lhs);
//		}
//	}
//
//	protected void handleFinalizeMethod(MethodPAG srcmpag, MethodPAG tgtmpag, Edge e) {
//		GNode srcThis = srcmpag.nodeFactory().caseThis();
//		srcThis = srcThis.getReplacement();
//		GNode tgtThis = tgtmpag.nodeFactory().caseThis();
//		tgtThis = tgtThis.getReplacement();
//		pag.addEdge(srcThis, tgtThis);
//	}
//
//	protected void handleNewInstance(MethodPAG srcmpag, MethodPAG tgtmpag, Edge e) {
//		Stmt s = (Stmt) e.srcUnit();
//		InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
//		GNode cls = srcmpag.nodeFactory().getNode(iie.getBase());
//		cls = cls.getReplacement();
//		GNode newObject = pag.GlobalNodeFactory().caseNewInstance((Var_Node) cls);
//		GNode initThis = tgtmpag.nodeFactory().caseThis();
//		initThis = initThis.getReplacement();
//		pag.addEdge(newObject, initThis);
//		if (s instanceof AssignStmt) {
//			AssignStmt as = (AssignStmt) s;
//			GNode asLHS = srcmpag.nodeFactory().getNode(as.getLeftOp());
//			asLHS = asLHS.getReplacement();
//			pag.addEdge(newObject, asLHS);
//		}
//	}
//
//	protected void handleReflectionInvoke(MethodPAG srcmpag, MethodPAG tgtmpag, Edge e) {
//		// Flow (1) from first parameter of invoke(..) invocation
//		// to this of target, (2) from the contents of the second
//		// (array) parameter
//		// to all parameters of the target, and (3) from return of
//		// target to the
//		// return of invoke(..)
//
//		// (1)
//		InvokeExpr ie = e.srcStmt().getInvokeExpr();
//		Value arg0 = ie.getArg(0);
//		// if "null" is passed in, omit the edge
//		if (arg0 != NullConstant.v()) {
//			GNode parm0 = srcmpag.nodeFactory().getNode(arg0).getReplacement();
//			GNode thiz = tgtmpag.nodeFactory().caseThis().getReplacement();
//			pag.addEdge(parm0, thiz);
//		}
//		// (2)
//		Value arg1 = ie.getArg(1);
//		SootMethod tgt = e.getTgt().method();
//		// if "null" is passed in, or target has no parameters, omit the edge
//		if (arg1 != NullConstant.v() && tgt.getParameterCount() > 0) {
//			GNode parm1 = srcmpag.nodeFactory().getNode(arg1);
//			parm1 = parm1.getReplacement();
//			FieldRef_Node parm1contents = pag.makeFieldRefNode((Var_Node) parm1, ArrayElement.v());
//			for (int i = 0; i < tgt.getParameterCount(); i++) {
//				// if no reference type, create no edge
//				if (!(tgt.getParameterType(i) instanceof RefLikeType))
//					continue;
//				GNode tgtParmI = tgtmpag.nodeFactory().caseParm(i);
//				tgtParmI = tgtParmI.getReplacement();
//				pag.addEdge(parm1contents, tgtParmI);
//			}
//		}
//
//		// (3) only create return edge if we are actually assigning the
//		// return value and the return type of the callee is actually a
//		// reference type
//		if (e.srcUnit() instanceof AssignStmt && (tgt.getReturnType() instanceof RefLikeType)) {
//			AssignStmt as = (AssignStmt) e.srcUnit();
//			GNode ret = tgtmpag.nodeFactory().caseRet();
//			ret = ret.getReplacement();
//			GNode lhs = srcmpag.nodeFactory().getNode(as.getLeftOp());
//			lhs = lhs.getReplacement();
//			pag.addEdge(ret, lhs);
//		}
//	}
//
//	protected void handleReflNewInstance(MethodPAG srcmpag, MethodPAG tgtmpag, Edge e) {
//		// (1) create a fresh node for the new object
//		// (2) create edge from this object to "this" of the constructor
//		// (3) if this is a call to Constructor.newInstance and not
//		// Class.newInstance,
//		// create edges passing the contents of the arguments array of
//		// the call
//		// to all possible parameters of the target
//		// (4) if we are inside an assign statement,
//		// assign the fresh object from (1) to the LHS of the assign
//		// statement
//		Stmt s = (Stmt) e.srcUnit();
//		InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
//
//		// (1)
//		GNode cls = srcmpag.nodeFactory().getNode(iie.getBase());
//		cls = cls.getReplacement();
//		if (cls instanceof ContextVar_Node)
//			cls = pag.findLocalVarNode(((Var_Node) cls).getVariable());
//
//		Var_Node newObject = pag.makeGlobalVarNode(cls, RefType.v("java.lang.Object"));
//		SootClass tgtClass = e.getTgt().method().getDeclaringClass();
//		RefType tgtType = tgtClass.getType();
//		Alloc_Node site = pag.makeAllocNode(new Pair<GNode, SootClass>(cls, tgtClass), tgtType, null);
//		pag.addEdge(site, newObject);
//
//		// (2)
//		GNode initThis = tgtmpag.nodeFactory().caseThis();
//		initThis = initThis.getReplacement();
//		pag.addEdge(newObject, initThis);
//
//		// (3)
//		if (e.kind() == Kind.REFL_CONSTR_NEWINSTANCE) {
//			Value arg = iie.getArg(0);
//			SootMethod tgt = e.getTgt().method();
//			// if "null" is passed in, or target has no parameters, omit the edge
//			if (arg != NullConstant.v() && tgt.getParameterCount() > 0) {
//				GNode parm0 = srcmpag.nodeFactory().getNode(arg);
//				parm0 = parm0.getReplacement();
//				FieldRef_Node parm1contents = pag.makeFieldRefNode((Var_Node) parm0, ArrayElement.v());
//				for (int i = 0; i < tgt.getParameterCount(); i++) {
//					// if no reference type, create no edge
//					if (!(tgt.getParameterType(i) instanceof RefLikeType))
//						continue;
//					GNode tgtParmI = tgtmpag.nodeFactory().caseParm(i);
//					tgtParmI = tgtParmI.getReplacement();
//					pag.addEdge(parm1contents, tgtParmI);
//				}
//			}
//		}
//
//		// (4)
//		if (s instanceof AssignStmt) {
//			AssignStmt as = (AssignStmt) s;
//			GNode asLHS = srcmpag.nodeFactory().getNode(as.getLeftOp());
//			asLHS = asLHS.getReplacement();
//			pag.addEdge(newObject, asLHS);
//		}
//	}

	public PointsToSet reachingObjects(SparkField f) {
		if (f instanceof SootField)
			return reachingObjects((SootField)f);
		final PTSetInternal ret = pag.setFactory.newSet(null,pag);
		pag.valToAllocNode.values().forEach(a->{
			AllocDotField_Node adf = a.dot(f);
			if(adf!=null){
				adf.getP2Set().forall(new PTSetVisitor() {
					@Override
					public void visit(GNode n) {
						ret.add(n);
					}
				});
			}
//			ret.addAll(adf.getP2Set(),null);
		});
		return ret;
	}
	@Override
	public PointsToSet reachingObjects(SootField f) {
		if (!f.isStatic()){
//			throw new RuntimeException("The parameter f must be a *static* field.");
			final PTSetInternal ret = pag.setFactory.newSet(((SootField) f).getType(),pag);
			pag.valToAllocNode.values().forEach(a->{
				AllocDotField_Node adf = a.dot(f);
				if(adf!=null){
					adf.getP2Set().forall(new PTSetVisitor() {
						@Override
						public void visit(GNode n) {
							ret.add(n);
						}
					});
				}
//				ret.addAll(adf.getP2Set(),null);
			});
			return ret;
		}
			
		Var_Node n = pag.findGlobalVarNode(f);
		if (n == null)
			return EmptyPTSet.v();
		return n.getP2Set();
	}
	/** Returns the set of objects pointed to by variable l. */
	@Override
	public PointsToSet reachingObjects(Local l) {
		Var_Node n = pag.findLocalVarNode(l);
		if (n == null) {
			return EmptyPTSet.v();
		}
		return n.getP2Set();
	}

	/** Returns the set of objects pointed to by variable l in context c. */
	@Override
	public PointsToSet reachingObjects(Context c, Local l) {
		return null;
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects
	 * in the PointsToSet s.
	 */
	@Override
	public PointsToSet reachingObjects(PointsToSet s, final SootField f) {
		if (f.isStatic())
			throw new RuntimeException("The parameter f must be an *instance* field.");

		return reachingObjectsInternal(s, f);
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects
	 * pointed to by l.
	 */
	@Override
	public PointsToSet reachingObjects(Local l, SootField f) {
		return reachingObjects(reachingObjects(l), f);
	}

	/**
	 * Returns the set of objects pointed to by instance field f of the objects
	 * pointed to by l in context c.
	 */
	@Override
	public PointsToSet reachingObjects(Context c, Local l, SootField f) {
		return reachingObjects(reachingObjects(c, l), f);
	}

	/**
	 * Returns the set of objects pointed to by elements of the arrays in the
	 * PointsToSet s.
	 */
	@Override
	public PointsToSet reachingObjectsOfArrayElement(PointsToSet s) {
		return reachingObjectsInternal(s, ArrayElement.v());
	}

	private PointsToSet reachingObjectsInternal(PointsToSet s, final SparkField f) {
		if (sparkOpts.field_based() || sparkOpts.vta()) {
			Var_Node n = pag.findLocalVarNode(f);
			if (n == null) {
				return EmptyPTSet.v();
			}
			return n.getP2Set();
		}
		if (sparkOpts.propagator() == SparkOptions.propagator_alias) {
			throw new RuntimeException(
					"The alias edge propagator does not compute points-to information for instance fields! Use a different propagator.");
		}
		PTSetInternal bases = (PTSetInternal) s;
		final PTSetInternal ret = pag.setFactory.newSet((f instanceof SootField) ? ((SootField) f).getType() : null,
				pag);
		bases.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				GNode nDotF = ((Alloc_Node) n).dot(f);
				if (nDotF != null)
					ret.addAll(nDotF.getP2Set(), null);
			}
		});
		return ret;
	}

	public class CallGraphBuilder {
		private Map<MethodOrMethodContext, Integer> apiCallDepthMap;

		/** context-insensitive stuff */
		public final CGOptions options = new CGOptions(PhaseOptions.v().getPhaseOptions("cg"));
		protected final ReflectionModel reflectionModel;
		protected final NumberedString sigFinalize = Scene.v().getSubSigNumberer().findOrAdd("void finalize()");
		protected final NumberedString sigStart = Scene.v().getSubSigNumberer().findOrAdd("void start()");
		protected final NumberedString sigRun = Scene.v().getSubSigNumberer().findOrAdd("void run()");
		protected final RefType clRunnable = RefType.v("java.lang.Runnable");

		/** context-sensitive stuff */
		protected final CallGraph cg = new CallGraph();
		protected ReachableMethods reachables;
		protected final HashMap<Var_Node, Collection<VirtualInvokeSite>> receiverToSites = new HashMap<>(Scene.v().getLocalNumberer().size());
		protected ChunkedQueue<VirtualInvokeSite> edgeQueue = new ChunkedQueue<>();
		protected final ChunkedQueue<SootMethod> targetsQueue = new ChunkedQueue<SootMethod>();
		protected final QueueReader<SootMethod> targets = targetsQueue.reader();
		
		public CallGraph getCallGraph() {
			return cg;
		}
		public ReachableMethods getReachableMethods() {
			return reachables;
		}
		// initialize the receiver to sites map with the number of locals * an
		// estimate for the number of contexts per methods
		public HashMap<Var_Node, Collection<VirtualInvokeSite>> getReceiverToSitesMap() {
			return receiverToSites;
		}

		public QueueReader<VirtualInvokeSite> edgeReader() {
			return edgeQueue.reader();
		}

		public Collection<VirtualInvokeSite> callSitesLookUp(Var_Node vn){
			Collection<VirtualInvokeSite> sites = receiverToSites.get(vn);
			if(sites==null)
				return Collections.emptySet();
			return sites;
		}
		
		protected ReflectionModel createReflectionModel(){
			ReflectionModel model;
			if (PTAOptions.REFLECTION_LOG != null && PTAOptions.REFLECTION_LOG.length() > 0) {
				model = new TraceBasedReflectionModel(this);
			} else {
				model = new DefaultReflectionModel();
			}
			return model;
		}

		public CallGraphBuilder() {
			reflectionModel = createReflectionModel();
			Scene.v().setCallGraph(cg);
			reachables = new ReachableMethods(getEntryPoints());
			apiCallDepthMap = new HashMap<MethodOrMethodContext, Integer>();
			QueueReader<MethodOrMethodContext> qr = (QueueReader<MethodOrMethodContext>) reachables.listener();
			while (qr.hasNext())
				apiCallDepthMap.put(qr.next(), 0);
		}

		protected List<? extends MethodOrMethodContext> getEntryPoints() {
//			return SootUtils.getEntryPoints();
			return Collections.singletonList(FakeMainFactory.makeFakeMain());
		}
		
		protected Var_Node getReceiverVarNode(Local receiver, MethodOrMethodContext m) {
			return pag.makeLocalVarNode(receiver, receiver.getType(), m.method());
		}

		protected void dispatch(Type type, VirtualInvokeSite site) {//dealInvoke——findCalleeMethod
			FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
			MethodOrMethodContext container = site.container();
			if (site.kind() == Kind.THREAD && !fh.canStoreType(type, clRunnable))
				return;
			// boolean special =false;
			if (site.iie() instanceof SpecialInvokeExpr && site.kind() != Kind.THREAD) {
				// special = true;
				SootMethod target = VirtualCalls.v().resolveSpecial((SpecialInvokeExpr) site.iie(), site.subSig(),
						container.method());
				// if the call target resides in a phantom class then
				// "target" will be null, simply do not add the target in
				// that case
				if (target != null)
					targetsQueue.add(target);
			} else
				VirtualCalls.v().resolve(type, site.recNode().getType(), site.subSig(), container.method(), targetsQueue);
		}

		protected void addVirtualEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod callee, Kind kind, Alloc_Node receiverNode) {
			addCGEdge(caller, callStmt, callee, kind);
			GNode thisRef = getMethodPAG(callee).nodeFactory().caseThis().getReplacement();
			pag.addEdge(receiverNode, thisRef);
		}

		public void addStaticEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod callee, Kind kind) {
			addCGEdge(caller, callStmt, callee, kind);
		}

		public void addCGEdge(MethodOrMethodContext caller, Unit callStmt, MethodOrMethodContext callee, Kind kind) {
			if (checkAPICallDepth(caller, callee))
				cg.addEdge(new Edge(caller, callStmt, callee, kind));
		}

		/** connect static CG-Edge; record instance CG_Edge */
		protected void handleInvoke(MethodOrMethodContext m, Collection<Stmt> stmts) {
			for (final Stmt s : stmts) {
				InvokeExpr ie = s.getInvokeExpr();
				reflectionModel.handleInvokeExpr(ie, m, s);
				if (ie instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
					Local receiver = (Local) iie.getBase();
					if(PTAOptions.hack&&receiver.getName().equals("dummyLocal")) {
						reachables.addMethod(ie.getMethod());
						continue;
					}
					Var_Node recNode = getReceiverVarNode(receiver, m);
					NumberedString subSig = iie.getMethodRef().getSubSignature();
					recordVirtualCallSite(recNode, new VirtualInvokeSite(recNode, s, m, iie, subSig, Edge.ieToKind(iie)));
				} else {
					SootMethod tgt = ie.getMethod();
					if (tgt != null) // static invoke or dynamic invoke
						addStaticEdge(m, s, tgt, Edge.ieToKind(ie));
					else if (!Options.v().ignore_resolution_errors())
						throw new InternalError("Unresolved target " + ie.getMethod()+ ". Resolution error should have occured earlier.");
				}
			}
		}

		/* End of public methods. */
		protected void recordVirtualCallSite(Var_Node receiver, VirtualInvokeSite site) {
			Collection<VirtualInvokeSite> sites = receiverToSites.get(receiver);
			if (sites == null)
				receiverToSites.put(receiver, sites = new HashSet<>());
			if(sites.add(site))
				edgeQueue.add(site);
		}

//		//clinit methods are now added as entries
//		private void addClinitTargets(MethodOrMethodContext source, Body b) {
//			for (Iterator<Unit> sIt = b.getUnits().iterator(); sIt.hasNext();) {
//				final Stmt s = (Stmt) sIt.next();
//				if (s.containsInvokeExpr()) {
//					InvokeExpr ie = s.getInvokeExpr();
//					if (ie instanceof StaticInvokeExpr) {
//						SootClass cl = ie.getMethodRef().declaringClass();
//						for (SootMethod clinit : EntryPoints.v().clinitsOf(cl)) {
//							addStaticEdge(source, s, clinit, Kind.CLINIT);
//						}
//					}
//				}
//				if (s.containsFieldRef()) {
//					FieldRef fr = s.getFieldRef();
//					if (fr instanceof StaticFieldRef)
//						for (SootMethod clinit : EntryPoints.v().clinitsOf(fr.getFieldRef().declaringClass()))
//							addStaticEdge(source, s, clinit, Kind.CLINIT);
//				}
//				if (s instanceof AssignStmt) {
//					Value rhs = ((AssignStmt) s).getRightOp();
//					if (rhs instanceof NewExpr) {
//						NewExpr r = (NewExpr) rhs;
//						SootClass cl = r.getBaseType().getSootClass();
//						for (SootMethod clinit : EntryPoints.v().clinitsOf(cl)) {
//							addStaticEdge(source, s, clinit, Kind.CLINIT);
//						}
//					} else if (rhs instanceof NewArrayExpr || rhs instanceof NewMultiArrayExpr) {
//						Type t = rhs.getType();
//						if (t instanceof ArrayType)
//							t = ((ArrayType) t).baseType;
//						if (t instanceof RefType) {
//							SootClass cl = ((RefType) t).getSootClass();
//							for (SootMethod clinit : EntryPoints.v().clinitsOf(cl)) {
//								addStaticEdge(source, s, clinit, Kind.CLINIT);
//							}
//						}
//					}
//				}
//			}
//		}

		public void updateCallGraph(PTSetInternal p2set, VirtualInvokeSite site) {
			p2set.forall(new PTSetVisitor() {
				public final void visit(GNode n) {
					Alloc_Node receiverNode = (Alloc_Node) n;
					dispatch(receiverNode.getType(), site);
					while (targets.hasNext())
						addVirtualEdge(site.container(), site.stmt(), targets.next(), site.kind(), receiverNode);
					// if (targets.hasNext())
					// throw new RuntimeException("multiple targets for one dynamic
					// type!!");
				}
			});
			reflectionModel.updateNode(site.recNode(), p2set);
		}

		private boolean checkAPICallDepth(MethodOrMethodContext src, MethodOrMethodContext tgt) {
			if (PTAOptions.apicalldepth < 0)
				return true;
			if (Config.v().isAppClass(tgt.method().getDeclaringClass())) {
				apiCallDepthMap.put(tgt, 0);
				return true;
			}
			Integer prevTgtDepth = apiCallDepthMap.get(tgt);
			int currentTgtDepth = apiCallDepthMap.get(src) + 1;
			if (prevTgtDepth == null || currentTgtDepth < prevTgtDepth)
				apiCallDepthMap.put(tgt, currentTgtDepth);
			else
				currentTgtDepth = prevTgtDepth;
			return currentTgtDepth <= PTAOptions.apicalldepth;
		}

		public class ReachableMethods {
			private Iterator<Edge> edgeSource;
			private final ChunkedQueue<MethodOrMethodContext> reachables = new ChunkedQueue<MethodOrMethodContext>();
			private final Set<MethodOrMethodContext> set = new HashSet<MethodOrMethodContext>();
			private QueueReader<MethodOrMethodContext> unprocessedMethods;
			private final QueueReader<MethodOrMethodContext> allReachables = reachables.reader();

			public ReachableMethods(Collection<? extends MethodOrMethodContext> entryPoints) {
				addMethods(entryPoints.iterator());
				unprocessedMethods = reachables.reader();
				this.edgeSource = cg.listener();
			}

			public void addMethods(Iterator<? extends MethodOrMethodContext> methods) {
				while (methods.hasNext())
					addMethod(methods.next());
			}

			public boolean addMethod(MethodOrMethodContext m) {
				if (set.add(m)){
					reachables.add(m);
					return true;
				}
				return false;
			}

			/**
			 * Causes the QueueReader objects to be filled up with any methods
			 * that have become reachable since the last call.
			 */
			public void update() {
				while (edgeSource.hasNext()) {
					Edge e = edgeSource.next();
					if (set.contains(e.getSrc()))
						addMethod(e.getTgt());
					else
						throw new RuntimeException("edge src not reachable");
				}
				while (unprocessedMethods.hasNext()) {
					MethodOrMethodContext m = unprocessedMethods.next();
					Iterator<Edge> targets = cg.edgesOutOf(m);
					addMethods(new Targets(targets));
				}
			}

			/**
			 * Returns a QueueReader object containing all methods found
			 * reachable so far, and which will be informed of any new methods
			 * that are later found to be reachable.
			 */
			public QueueReader<MethodOrMethodContext> listener() {
				return allReachables.clone();
			}

			/**
			 * Returns a QueueReader object which will contain ONLY NEW methods
			 * which will be found to be reachable, but not those that have
			 * already been found to be reachable.
			 */
			public QueueReader<MethodOrMethodContext> newListener() {
				return reachables.reader();
			}

			/** Returns true iff method is reachable. */
			public boolean contains(MethodOrMethodContext m) {
				return set.contains(m);
			}

			/** Returns the number of methods that are reachable. */
			public int size() {
				return set.size();
			}
		}
	}
}
