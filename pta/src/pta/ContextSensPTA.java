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

import driver.*;
import org.jboss.util.collection.CachedList;
import pag.MethodPAG;
import pag.builder.MtdPAGBuilder;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.alloc.ContextAlloc_Node;
import pag.node.call.VirtualInvokeSite;
import pag.node.var.ContextVar_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.GlobalVar_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.context.*;
import pta.pts.EmptyPTSet;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;

import java.util.*;

import soot.AnySubType;
import soot.Context;
import soot.Kind;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.scalar.Pair;
import soot.util.NumberedString;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;

public class ContextSensPTA extends PTA {
	interface ContextGenerator {
		 Context heapSelector(Context context);
		 Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode);
	}
	private class CallsiteSensCxtGenerator implements ContextGenerator  {
		@Override
		public Context heapSelector(Context context) {
			if (hk == k)
				return context;
			if (hk == 0)
				return emptyContext;
			ContextElement[] array = new ContextElement[hk];
			if (context instanceof ContextElements) {
				ContextElement[] cxt = ((ContextElements) context).getElements();
				System.arraycopy(cxt, 0, array, 0, Math.min(cxt.length, hk));
			} else
				array[0] = (ContextElement) context;
			return new ContextElements(array);
		}

		@Override
		public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			ContextElement[] array = new ContextElement[k];
			array[0] = callSite;
			if (k > 1) {
				if (callerContext instanceof ContextElements) {
					ContextElement[] cxt = ((ContextElements) callerContext).getElements();
					System.arraycopy(cxt, 0, array, 1, Math.min(cxt.length, k - 1));
				} else
					array[1] = (ContextElement) callerContext;
			}
			return new ContextElements(array);
		}
	}
	//implementation of obj context...(Ana Tosem'05)
	private class ObjectSensCxtGenerator implements ContextGenerator {
		@Override
		public Context heapSelector(Context context) {
			if (hk == k)
				return context;
			if (hk == 0)
				return emptyContext;
			ContextElement[] array = new ContextElement[hk];
			if (context instanceof ContextElements) {
				ContextElement[] cxtAllocs = ((ContextElements) context).getElements();
				System.arraycopy(cxtAllocs, 0, array, 0, Math.min(hk, cxtAllocs.length));
			} else
				array[0] = (ContextElement) context;
			return new ContextElements(array);
		}

		@Override
		public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			if (receiverNode == null)// static invoke
				return PTAOptions.staticcontext==PTAOptions.CALLER?callerContext:emptyContext;
			ContextElement[] array = new ContextElement[k];
			array[0] = receiverNode.base();
			if (k > 1) {
				Context context = receiverNode.context();
				if (context instanceof ContextElements) {
					ContextElement[] cxtAllocs = ((ContextElements) context).getElements();
					System.arraycopy(cxtAllocs, 0, array, 1, Math.min(k - 1, cxtAllocs.length));
				} else
					array[1] = (ContextElement) context;
			}
			return new ContextElements(array);
		}
	}

	private class ParamSensCxtGenerator implements ContextGenerator{

		@Override
		public Context heapSelector(Context context) {
			if (hk == k)
				return context;
			if (hk == 0)
				return emptyContext;
			ContextElement[] array = new ContextElement[hk];
			if (context instanceof ContextElements) {
				ContextElement[] cxtAllocs = ((ContextElements) context).getElements();
				System.arraycopy(cxtAllocs, 0, array, 0, Math.min(hk, cxtAllocs.length));
			} else
				array[0] = (ContextElement) context;
			return new ContextElements(array);
		}

		@Override

		public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			ContextElement[] array = new ContextElement[k];
			List<Value> params = new ArrayList<>();
			Alloc_Node base = null;
			Stmt stmt = (Stmt) callSite.getUnit();
			InvokeExpr ie = stmt.getInvokeExpr();
			if(receiverNode!=null){
				base = receiverNode.base();
			}
			params.addAll(ie.getArgs());
			array[0] = ParamContextElement.v(base,params);
			if(k>1){}
			return new ContextElements(array);
		}

		/*public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			ContextElement[] array = new ContextElement[k];
			Alloc_Node base = null;
			List<PointsToSet> paramPTS = new ArrayList<>();

			Stmt stmt = (Stmt) callSite.getUnit();
			InvokeExpr ie = stmt.getInvokeExpr();
			if(stmt instanceof JAssignStmt){
				base = receiverNode.base();
			}
			MethodPAG srcmpag = getMethodPAG(method);
			MtdPAGBuilder srcnf = srcmpag.nodeFactory();
			int count = ie.getArgCount();
			for(int i=0;i<count;i++){
				Value arg = ie.getArg(i);
				if(!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant){
					continue;
				}
				GNode argNode = srcnf.getNode(arg);
				//argNode = parameterize(argNode, callerContext);
				//argNode = argNode.getReplacement();
				paramPTS.add(argNode.getP2Set());
			}
			array[0] = ParamContextElement.v(base,paramPTS);
			if(k>1){}
			return new ContextElements(array);
		}*/

		/*public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			ContextElement[] array = new ContextElement[k];
			List<GNode> paramNode = new ArrayList<>();

			Stmt stmt = (Stmt) callSite.getUnit();
			InvokeExpr ie = stmt.getInvokeExpr();
			if(stmt instanceof JAssignStmt){
				paramNode.add(receiverNode.base());
			}

			MethodPAG srcmpag = getMethodPAG(method);
			MtdPAGBuilder srcnf = srcmpag.nodeFactory();
			int count = ie.getArgCount();
			for(int i=0;i<count;i++){
				Value arg = ie.getArg(i);
				if(!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant){
					continue;
				}
				GNode argNode = srcnf.getNode(arg);
				paramNode.add(argNode);
				//argNode = parameterize(argNode, callerContext);
				//argNode = argNode.getReplacement();
			}
			array[0] = ParamContextElement.v(paramNode);
			if(k>1){}
			return new ContextElements(array);
		}*/
	}

	//implementation of type context...(Yannis popl'11)
	private class TypeSensCxtGenerator implements ContextGenerator {
		@Override
		public Context heapSelector(Context context) {
			if (hk == k)
				return context;
			if (hk == 0)
				return emptyContext;
			ContextElement[] array = new ContextElement[hk];
			if (context instanceof ContextElements) {
				ContextElement[] ctxAllocs = ((ContextElements) context).getElements();
				System.arraycopy(ctxAllocs, 0, array, 0, Math.min(hk,ctxAllocs.length));
			} else
				array[0] = (ContextElement) context;
			return new ContextElements(array);
		}

		@Override
		public Context selector(SootMethod method,Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode) {
			if (receiverNode == null)// static invoke
				return PTAOptions.staticcontext==PTAOptions.CALLER?callerContext:emptyContext;
//				return emptyContext;
			ContextElement[] array = new ContextElement[k];
			array[0] = TypeContextElement.getTypeContextElement(receiverNode.base());
			if (k > 1) {
				Context context = receiverNode.context();
				if (context instanceof ContextElements) {
					ContextElement[] ctxAllocs = ((ContextElements) context).getElements();
					System.arraycopy(ctxAllocs, 0, array, 1, Math.min(k - 1,ctxAllocs.length));
				} else
					array[1] = (ContextElement) context;
			}
			return new ContextElements(array);
		}
	}
	
	protected ContextGenerator cxtGen;
	protected int k, hk;
	
	protected Context heapSelector(Alloc_Node alloc, Context context) {
		return cxtGen.heapSelector(context);
	}

	protected Context selector(SootMethod method, Context callerContext, CallSite callSite, ContextAlloc_Node receiverNode){
		return cxtGen.selector(method,callerContext, callSite, receiverNode);
	}

	/// Context-sensitive points-to analysis
	private Map<Var_Node, HashMap<Context, ContextVar_Node>> contextVarNodeMap = new HashMap<>(16000);
	private Map<Alloc_Node, HashMap<Context, ContextAlloc_Node>> contextAllocNodeMap = new HashMap<>(6000);
	private Map<SootMethod, HashMap<Context, ContextMethod>> contextMethodMap = new HashMap<>(6000);
	private Map<MethodPAG, Set<Context>> addedContexts =new HashMap<>();
	protected Context emptyContext;

	public Context emptyContext() {
		return emptyContext;
	}

	public Map<Var_Node, HashMap<Context, ContextVar_Node>> getContextVarNodeMap() {
		return contextVarNodeMap;
	}
	public Map<Alloc_Node, HashMap<Context, ContextAlloc_Node>> getContextAllocNodeMap(){
		return contextAllocNodeMap;
	}
	public Map<SootMethod, HashMap<Context, ContextMethod>> getContextMethodMap() {
		return contextMethodMap;
	}

	public ContextSensPTA(int k, int hk){
		this.k=k;
		this.hk=hk;

		if (PTAOptions.ptaPattern.isCallSiteSens())
			cxtGen = new CallsiteSensCxtGenerator();
		else if (PTAOptions.ptaPattern.isObjSens())
			cxtGen = new ObjectSensCxtGenerator();
		else if (PTAOptions.ptaPattern.isTypeSens())
			cxtGen = new TypeSensCxtGenerator();
		else if (PTAOptions.ptaPattern.isParamSens())
			cxtGen = new ParamSensCxtGenerator();
		else
			throw new RuntimeException("Unsupported context kind.");

		emptyContext = new ContextElements(new ContextElement[k]);
	}
	
	@Override
	protected CallGraphBuilder createCallGraphBuilder() {
		return new SCGBuilder();
	}
	@Override
	public CallGraph getCallGraph() {
		return ((SCGBuilder) cgb).getCICallGraph();
	}

	@Override
	void updateClinits(Collection<Stmt> added) {
		cgb.handleInvoke(parameterize(FakeMainFactory.getFakeMain(), emptyContext), added);
	}
	
	protected void addToPAG(MethodPAG mpag, Context cxt) {
		Set<Context> contexts = addedContexts.get(mpag);
		if(contexts==null)
			addedContexts.put(mpag, contexts=new HashSet<>());
		if (!contexts.add(cxt))
			return;
		for(QueueReader<GNode> reader = mpag.getInternalReader().clone();reader.hasNext();)
			pag.addEdge(parameterize(reader.next(), cxt), parameterize(reader.next(), cxt));
	}

	public GNode parameterize(GNode n, Context varNodeParameter) {
		if (varNodeParameter == null)
			throw new RuntimeException("null context!!!");
		if (n instanceof LocalVar_Node)
			return parameterize((LocalVar_Node) n, varNodeParameter);
		if (n instanceof FieldRef_Node)
			return parameterize((FieldRef_Node) n, varNodeParameter);
		if (n instanceof Alloc_Node)
			return parameterize((Alloc_Node) n, varNodeParameter);
		if (n instanceof GlobalVar_Node)
			return n;
		throw new RuntimeException("cannot parameterize this node: "+n);
	}
	
	protected ContextVar_Node parameterize(LocalVar_Node vn, Context varNodeParameter) {
		return makeContextVarNode(vn, varNodeParameter);
	}

	protected FieldRef_Node parameterize(FieldRef_Node frn, Context varNodeParameter) {
		return pag.makeFieldRefNode((Var_Node) parameterize(frn.getBase(), varNodeParameter), frn.getField());
	}

	protected ContextAlloc_Node parameterize(Alloc_Node node, Context context) {
		return makeContextAllocNode(node, heapSelector(node, context));
	}
	/** Finds or creates the ContextMethod for method and context. */
	public ContextMethod parameterize(SootMethod method, Context context) {
		return makeContextMethod(context,method);
	}

	/** Finds the ContextVarNode for base variable value and context context, or returns null.*/
	public ContextVar_Node findContextVarNode(Local baseValue, Context context) {
		HashMap<Context, ContextVar_Node> contextMap = contextVarNodeMap.get(pag.findLocalVarNode(baseValue));
		return contextMap == null ? null : contextMap.get(context);
	}
	@Override
	public Collection<Var_Node> getVarNodes(Local local) {
		Map<?, ContextVar_Node> subMap=contextVarNodeMap.get(pag.findLocalVarNode(local));
		if(subMap==null)
			return Collections.emptySet();
		Collection<Var_Node> ret=new HashSet<>();
		ret.addAll(subMap.values());
		return ret;
	}

	/**Finds or creates the ContextVarNode for base variable base and context.*/
	protected ContextVar_Node makeContextVarNode(Var_Node base, Context context) {
		HashMap<Context, ContextVar_Node> contextMap = contextVarNodeMap.get(base);
		if (contextMap == null)
			contextVarNodeMap.put(base, contextMap = new HashMap<Context, ContextVar_Node>());
		ContextVar_Node cxtVarNode = contextMap.get(context);
		if (cxtVarNode == null) {
			contextMap.put(context, cxtVarNode = new ContextVar_Node(pag, base, context));
		}
		return cxtVarNode;
	}

	/** Finds or creates the ContextAllocNode for base allocsite and context. */
	protected ContextAlloc_Node makeContextAllocNode(Alloc_Node allocNode, Context context) {
		ContextAlloc_Node contextAllocNode;
		HashMap<Context, ContextAlloc_Node> contextMap = contextAllocNodeMap.get(allocNode);
		if (contextMap == null)
			contextAllocNodeMap.put(allocNode, contextMap = new HashMap<>());
		contextAllocNode = contextMap.get(context);
		if (contextAllocNode == null) {
			contextMap.put(context, contextAllocNode = new ContextAlloc_Node(pag, allocNode, context));
		}
		return contextAllocNode;
	}
	
	/** Finds or creates the ContextMethod for method and context. */
	public ContextMethod makeContextMethod(Context context,SootMethod method) {
		HashMap<Context, ContextMethod> contextMap = contextMethodMap.get(method);
		if (contextMap == null)
			contextMethodMap.put(method, contextMap = new HashMap<Context, ContextMethod>());
		ContextMethod contextMethod = contextMap.get(context);
		if (contextMethod == null)
			contextMap.put(context, contextMethod = new ContextMethod(context,method));
		return contextMethod;
	}

	/**
	 * Adds method target as a possible target of the invoke expression in s. If
	 * target is null, only creates the nodes for the call site, without
	 * actually connecting them to any target method.
	 **/
	@Override
	protected void processCallEdge(Edge e) {
		MethodPAG srcmpag = getMethodPAG(e.src());
		MethodPAG tgtmpag = getMethodPAG(e.tgt());
		Stmt s = (Stmt) e.srcUnit();
		Context srcContext = e.srcCtxt();
		Context tgtContext = e.tgtCtxt();
		MtdPAGBuilder srcnf = srcmpag.nodeFactory();
		MtdPAGBuilder tgtnf = tgtmpag.nodeFactory();
		SootMethod tgtmtd = tgtmpag.getMethod();
		InvokeExpr ie = s.getInvokeExpr();
		int numArgs = ie.getArgCount();
		for (int i = 0; i < numArgs; i++) {
			Value arg = ie.getArg(i);
			if (!(arg.getType() instanceof RefLikeType)|| arg instanceof NullConstant)
				continue;
			Type tgtType = tgtmtd.getParameterType(i);
			if (!(tgtType instanceof RefLikeType) )
				continue;
			GNode argNode = srcnf.getNode(arg);
			argNode = parameterize(argNode, srcContext);
			argNode = argNode.getReplacement();
			GNode parm = tgtnf.caseParm(i);
			parm = parameterize(parm, tgtContext);
			parm = parm.getReplacement();
			pag.addEdge(argNode, parm);
		}
		if (s instanceof AssignStmt) {
			Value dest = ((AssignStmt) s).getLeftOp();
			if (dest.getType() instanceof RefLikeType && tgtmtd.getReturnType()instanceof RefLikeType) {
				GNode destNode = srcnf.getNode(dest);
				destNode = parameterize(destNode, srcContext);
				destNode = destNode.getReplacement();
				GNode retNode = tgtnf.caseRet();
				retNode = parameterize(retNode, tgtContext);
				retNode = retNode.getReplacement();
				pag.addEdge(retNode, destNode);
			}
		}
	}

	/** Returns the set of objects pointed to by variable l. */
	@Override
	public PointsToSet reachingObjects(Local l) {
		// find all context nodes, and collect their answers
		final PTSetInternal ret = pag.setFactory.newSet(l.getType(), pag);
		getVarNodes(l).forEach(vn->{
			ret.addAll(vn.getP2Set(),null);
		});;
		return ret;
	}

	/** Returns the set of objects pointed to by variable l in context c. */
	@Override
	public PointsToSet reachingObjects(Context c, Local l) {
		Var_Node n = findContextVarNode(l, c);
		if (n == null) {
			return EmptyPTSet.v();
		}
		return n.getP2Set();
	}

	public class SCGBuilder extends CallGraphBuilder {
		protected final HashMap<Var_Node, Collection<Pair<MethodOrMethodContext,Unit>>> receiverToStaticSites = new HashMap<>(Scene.v().getLocalNumberer().size());
		protected ChunkedQueue<Pair<MethodOrMethodContext,Unit>> staticEdgeQueue = new ChunkedQueue<>();
		public Collection<Pair<MethodOrMethodContext,Unit>> staticCallSitesLookUp(Var_Node vn){
			Collection<Pair<MethodOrMethodContext,Unit>> sites = receiverToStaticSites.get(vn);
			if(sites==null)
				return Collections.emptySet();
			return sites;
		}

		public QueueReader<Pair<MethodOrMethodContext,Unit>> staticEdgeReader() {
			return staticEdgeQueue.reader();
		}
		
		public CallGraph getCICallGraph() {
			final CallGraph cicg = new CallGraph();
			Map<Unit, Map<SootMethod, Set<SootMethod>>> map = new HashMap<>();
			cg.forEach(e -> {
				SootMethod src = e.src();
				SootMethod tgt = e.tgt();
				Unit unit = e.srcUnit();
				Map<SootMethod, Set<SootMethod>> submap = map.get(unit);
				if (submap == null)
					map.put(unit, submap = new HashMap<>());
				Set<SootMethod> set = submap.get(src);
				if(set==null)
					submap.put(src, set=new HashSet<>());
				if(set.add(tgt))
					cicg.addEdge(new Edge(src, e.srcUnit(), tgt, e.kind()));
			});
			return cicg;
		}

		@Override
		protected List<? extends MethodOrMethodContext> getEntryPoints() {
			SootMethod fakeMain = FakeMainFactory.makeFakeMain();
			return Collections.singletonList(parameterize(fakeMain, emptyContext));
		}

		@Override
		/** connect static CG-Edge; record instance CG_Edge */
		protected void handleInvoke(MethodOrMethodContext m, Collection<Stmt> stmts) {//
			for (final Stmt s : stmts) {
				InvokeExpr ie = s.getInvokeExpr();
				reflectionModel.handleInvokeExpr(ie, m, s);//先忽略
				if (ie instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
					Local receiver = (Local) iie.getBase();
					if(PTAOptions.hack&&receiver.getName().equals("dummyLocal")) {
						reachables.addMethod(parameterize(ie.getMethod(), getImplicitContext(ie.getMethod())));
						continue;
					}
					Var_Node recNode = getReceiverVarNode(receiver, m);
					NumberedString subSig = iie.getMethodRef().getSubSignature();
					recordVirtualCallSite(recNode, new VirtualInvokeSite(recNode, s, m, iie, subSig, Edge.ieToKind(iie)));
				} else {
					SootMethod tgt = ie.getMethod();
					if (tgt != null) // static invoke or dynamic invoke
						if(PTAOptions.staticcontext==PTAOptions.THIS){
							Var_Node recNode = (Var_Node) getMethodPAG(m.method()).nodeFactory().caseThis();
							recNode = (Var_Node) parameterize(recNode, m.context());
							recordStaticCallSite(recNode, new Pair<MethodOrMethodContext,Unit>(m,s));
						}else
							addStaticEdge(m, s, tgt, Edge.ieToKind(ie));
					else if (!Options.v().ignore_resolution_errors())
						throw new InternalError("Unresolved target " + ie.getMethod()+ ". Resolution error should have occured earlier.");
				}
			}
		}
		
		private void recordStaticCallSite(Var_Node receiver, Pair<MethodOrMethodContext,Unit> site) {
			Collection<Pair<MethodOrMethodContext,Unit>> sites = receiverToStaticSites.get(receiver);
			if (sites == null)
				receiverToStaticSites.put(receiver, sites = new HashSet<>());
			if(sites.add(site))
				staticEdgeQueue.add(site);
		}
		
		@Override
		protected Var_Node getReceiverVarNode(Local receiver, MethodOrMethodContext m) {
			LocalVar_Node base = pag.makeLocalVarNode(receiver, receiver.getType(), m.method());
			return parameterize(base, m.context());
		}

		protected Var_Node getParamNode(Local param,MethodOrMethodContext m){
			LocalVar_Node base = pag.makeLocalVarNode(param,param.getType(),m.method());
			return parameterize(base,m.context());
		}
		
		/**Only unsed for static call iff THIS context is used
		 * (All static calls are regarded as virtual calls) 
		 * */
		public void updateCallGraph(PTSetInternal p2set, Pair<MethodOrMethodContext, Unit> site) {
			Stmt stmt = (Stmt) site.getO2();
			InvokeExpr ie = stmt.getInvokeExpr();
			SootMethod target = ie.getMethod();//直接得到calleeMethod？
			Kind kind = Edge.ieToKind(ie);
			p2set.forall(new PTSetVisitor() {
				public final void visit(GNode n) {
					addVirtualEdge(site.getO1(), stmt, target, kind, (Alloc_Node) n);
				}
			});
		}

		@Override
		protected void addVirtualEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod callee, Kind kind, Alloc_Node receiverNode) {
			MethodOrMethodContext container = caller;
			Context tgtContext = selector(callee, container.context(), CallSite.v(callStmt),(ContextAlloc_Node) receiverNode);
			//ContextMethod cstarget = parameterize(callee, tgtContext);
			HashMap<Context, ContextMethod> contextMap = contextMethodMap.get(callee);
			if (contextMap == null)
				contextMethodMap.put(callee, contextMap = new HashMap<Context, ContextMethod>());
			ContextMethod cstarget = contextMap.get(tgtContext);
			if (cstarget == null){
				contextMap.put(tgtContext, cstarget = new ContextMethod(tgtContext,callee));

				addCGEdge(container, callStmt, cstarget, kind);
				GNode thisRef = getMethodPAG(callee).nodeFactory().caseThis().getReplacement();
				thisRef = parameterize(thisRef, cstarget.context()).getReplacement();
				pag.addEdge(receiverNode, thisRef);
			}

		}

		@Override
		public void addStaticEdge(MethodOrMethodContext caller, Unit callStmt, SootMethod callee, Kind kind) {
			Context tgtContext = selector(callee, caller.context(), CallSite.v(callStmt), null);
			//ContextMethod callee = parameterize(calleem, typeContext);

			HashMap<Context, ContextMethod> contextMap = contextMethodMap.get(callee);
			if (contextMap == null)
				contextMethodMap.put(callee, contextMap = new HashMap<Context, ContextMethod>());
			ContextMethod contextMethod = contextMap.get(tgtContext);
			if (contextMethod == null) {
				contextMap.put(tgtContext, contextMethod = new ContextMethod(tgtContext, callee));

				addCGEdge(caller, callStmt, contextMethod, kind);
			}
		}
	}

	public Context getImplicitContext(SootMethod method) {
		Alloc_Node alloc = null;
		switch (method.getSignature()) {
		case "<java.lang.ClassLoader: void <init>()>":
		case "<java.lang.ClassLoader: java.lang.Class loadClassInternal(java.lang.String)>":
		case "<java.lang.ClassLoader: void checkPackageAccess(java.lang.Class,java.security.ProtectionDomain)>":
		case "<java.lang.ClassLoader: void addClass(java.lang.Class)>":
			alloc = pag.makeAllocNode(PointsToAnalysis.DEFAULT_CLASS_LOADER,
					AnySubType.v(RefType.v("java.lang.ClassLoader")), null);
			break;
		case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.Runnable)>":
		case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>":
		case "<java.lang.Thread: void exit()>":
			alloc = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_NODE, RefType.v("java.lang.Thread"),null);
			break;
		case "<java.lang.ThreadGroup: void <init>()>":
		case "<java.lang.ThreadGroup: void uncaughtException(java.lang.Thread,java.lang.Throwable)>":
			alloc = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE,
					RefType.v("java.lang.ThreadGroup"), null);
			break;
		case "<java.lang.ref.Finalizer: void runFinalizer()>":
			alloc = pag.makeAllocNode(PointsToAnalysis.FINALIZE_QUEUE,
					RefType.v("java.lang.ref.Finalizer"), null);
			break;
		case "<java.security.PrivilegedActionException: void <init>(java.lang.Exception)>":
			alloc = pag.makeAllocNode(PointsToAnalysis.PRIVILEGED_ACTION_EXCEPTION, AnySubType.v(RefType.v("java.security.PrivilegedActionException")), null);
			break;
		default:
		}
		ContextAlloc_Node csAlloc_Node = parameterize(alloc, emptyContext);
		return cxtGen.selector(null,null, null, csAlloc_Node);
	}
}
