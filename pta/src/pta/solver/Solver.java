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

package pta.solver;

import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.alloc.Constant_Node;
import pag.node.alloc.ContextAlloc_Node;
import pag.node.call.VirtualInvokeSite;
import pag.node.var.AllocDotField_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.Var_Node;

import java.util.Collection;
import java.util.TreeSet;

import driver.FakeMainFactory;
import driver.PTAOptions;
import pta.PTA.CallGraphBuilder;
import pta.ContextSensPTA;
import pta.ContextSensPTA.SCGBuilder;
import pta.PTA;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;
import soot.Context;
import soot.MethodOrMethodContext;
import soot.RefType;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.spark.pag.SparkField;
import soot.jimple.spark.solver.Propagator;
import soot.toolkits.scalar.Pair;
import soot.util.queue.QueueReader;

public final class Solver extends Propagator {
	protected PAG pag;
	protected PTA pta;
	protected CallGraphBuilder cgb;
	protected final TreeSet<Var_Node> varNodeWorkList = new TreeSet<Var_Node>();

	public Solver(PTA _pta) {
		cgb = _pta.getCgb();
		pag = _pta.getPag();
		pta = _pta;
	}

	@Override
	public final void propagate() {
		final QueueReader<Pair<Alloc_Node, Var_Node>> addedAllocEdges = pag.allocReader();
		final QueueReader<Pair<Var_Node, Var_Node>> addedSimpleEdges = pag.simpleReader();
		final QueueReader<Pair<Var_Node, FieldRef_Node>> addedStoreEdges = pag.storeReader();
		final QueueReader<Pair<FieldRef_Node, Var_Node>> addedLoadEdges = pag.loadReader();
		pta.build();
		if(PTAOptions.staticcontext == PTAOptions.THIS && pta instanceof ContextSensPTA){
			ContextSensPTA csPTA = (ContextSensPTA)pta;
			Context emptyContext = csPTA.emptyContext();
			Alloc_Node rootNode = pag.makeAllocNode("ROOT", RefType.v("java.lang.Object"), null);
			rootNode = (Alloc_Node) ((ContextSensPTA) pta).parameterize(rootNode, emptyContext);
			SootMethod fakeMain = FakeMainFactory.getFakeMain();
			GNode thisRef = pta.getMethodPAG(fakeMain).nodeFactory().caseThis();
			thisRef = csPTA.parameterize(thisRef, emptyContext);
			pag.addEdge(rootNode, thisRef);
			
			pag.rootNode = rootNode;
		}
		new TopoSorter(pag, false).sort();
		handleAddedSimpleEdges(addedAllocEdges,addedSimpleEdges);
		
		while (!varNodeWorkList.isEmpty()) {
			final Var_Node src = varNodeWorkList.pollFirst();
			
			propagateFromSrc(src);
			
			updateCallGraph(src);
			
			handleAddedComplexEdges(addedStoreEdges,addedLoadEdges);
			
			handleStoreAndLoadOnBase(src);
			
			src.getP2Set().flushNew();
			
			handleAddedSimpleEdges(addedAllocEdges,addedSimpleEdges);
		}
	}

	private void propagateFromSrc(Var_Node src) {
		final PTSetInternal newset = src.getP2Set().getNewSet();
		pag.simpleLookup(src).forEach(element -> {
			if (addAll(element, newset)) {
				varNodeWorkList.add(element);
			}
			if(element instanceof AllocDotField_Node){
				Alloc_Node baseAlloc = ((AllocDotField_Node) element).getBase();
				if(isConstant(baseAlloc))
					throw new RuntimeException("Modifying ConstNode:" + element + " with " +src);
			}
		});
	}

	private void handleStoreAndLoadOnBase(Var_Node src) {
		final PTSetInternal newP2Set = src.getP2Set().getNewSet();
		for (final FieldRef_Node fr : src.getAllFieldRefs()) {
			final SparkField fld = fr.getField();
			/// foreach src.fld = v do add simple from v-->o.fld where o\in
			/// pts(src)
			for (final Var_Node v : pag.storeInvLookup(fr)) {
				newP2Set.forall(new PTSetVisitor() {
					public final void visit(GNode n) {
						Alloc_Node a = (Alloc_Node) n;
						if(isConstant(a))
							return; //	cannot modify a const!
						final Var_Node oDotF = pta.parameterize(fld, a);
						pag.addSimpleEdge(v, oDotF);
					}
				});
			}
			/// foreach v = src.fld do add simple from o.fld-->v where o\in
			/// pts(src)
			for (final Var_Node element : pag.loadLookup(fr))
				newP2Set.forall(new PTSetVisitor() {
					public final void visit(GNode n) {
						final Var_Node oDotF = pta.parameterize(fld, (Alloc_Node) n);
						pag.addSimpleEdge(oDotF, element);		
					}
				});
		}
	}

	private void updateCallGraph(Var_Node src) {
		final QueueReader<VirtualInvokeSite> addedSites = cgb.edgeReader();
		final PTSetInternal newP2Set = src.getP2Set().getNewSet();
		Collection<VirtualInvokeSite> sites = cgb.callSitesLookUp(src);
		for (VirtualInvokeSite site : sites)
			cgb.updateCallGraph(newP2Set, site);
		
		if(PTAOptions.staticcontext == PTAOptions.THIS && pta instanceof ContextSensPTA){
			SCGBuilder scgb = (SCGBuilder) cgb;
			final QueueReader<Pair<MethodOrMethodContext, Unit>> addedStaticSites = ((SCGBuilder) cgb).staticEdgeReader();
			Collection<Pair<MethodOrMethodContext, Unit>> staticSites = ((SCGBuilder) cgb).staticCallSitesLookUp(src);
			for (Pair<MethodOrMethodContext, Unit> site : staticSites)
				scgb.updateCallGraph(newP2Set, site);
			
			pta.build();
			
			while(addedSites.hasNext()||addedStaticSites.hasNext()){ // This part is not needed if context is not adptive at var level(e.g. eagle)
				while(addedSites.hasNext()){
					final VirtualInvokeSite site = addedSites.next();
					final Var_Node receiver = site.recNode();
					cgb.updateCallGraph(receiver.getP2Set().getOldSet(), site);
					if(receiver==src)
						cgb.updateCallGraph(receiver.getP2Set().getNewSet(), site);
				}
				while(addedStaticSites.hasNext()){
					final Pair<MethodOrMethodContext, Unit> site = addedStaticSites.next();
					MethodOrMethodContext caller = site.getO1();
					GNode thisRef = pta.getMethodPAG(caller.method()).nodeFactory().caseThis();
					final Var_Node receiver = (Var_Node) ((ContextSensPTA)pta).parameterize(thisRef, caller.context());
					scgb.updateCallGraph(receiver.getP2Set().getOldSet(), site);
					if(receiver==src)
						scgb.updateCallGraph(receiver.getP2Set().getNewSet(), site);
				}
				pta.build();
			}
			
		}else{
			pta.build();
			
			while(addedSites.hasNext()){
				final VirtualInvokeSite site = addedSites.next();
				final Var_Node receiver = site.recNode();
				cgb.updateCallGraph(receiver.getP2Set().getOldSet(), site);
				if(receiver==src)
					cgb.updateCallGraph(receiver.getP2Set().getNewSet(), site);
				
				pta.build();
			}
		}
	}

	private void handleAddedComplexEdges(QueueReader<Pair<Var_Node, FieldRef_Node>> addedStoreEdges,
			QueueReader<Pair<FieldRef_Node, Var_Node>> addedLoadEdges) {
		while(addedStoreEdges.hasNext()){
			Pair<Var_Node, FieldRef_Node> storeEdge = addedStoreEdges.next();
			final Var_Node srcv = storeEdge.getO1();
			final FieldRef_Node tgtfrn = storeEdge.getO2();
			final SparkField fld = tgtfrn.getField();
//			pag.addSimpleEdge(srcv,pta.parameterize(fld,null));//field base
			tgtfrn.getBase().getP2Set().getOldSet().forall(new PTSetVisitor() {
				public void visit(GNode n) {
					Alloc_Node a = (Alloc_Node) n;
					if(isConstant(a))
						return; //	cannot modify a const!
					final Var_Node oDotF = pta.parameterize(fld, a);
					pag.addSimpleEdge(srcv, oDotF);
				}
			});
		}		
		while(addedLoadEdges.hasNext()){
			Pair<FieldRef_Node, Var_Node> loadEdge = addedLoadEdges.next();
			final FieldRef_Node srcfrn = loadEdge.getO1();
			final Var_Node tgtv = loadEdge.getO2();
			final SparkField fld = srcfrn.getField();
//			pag.addSimpleEdge(pta.parameterize(fld,null), tgtv);//field base
			srcfrn.getBase().getP2Set().getOldSet().forall(new PTSetVisitor() {
				public void visit(GNode n) {
					final Var_Node oDotF = pta.parameterize(fld, (Alloc_Node) n);
					pag.addSimpleEdge(oDotF, tgtv);
				}
			});
		}
	}
	
	private boolean isConstant(Alloc_Node a) {
		if(a instanceof ContextAlloc_Node)
			a = ((ContextAlloc_Node) a).base();
		return a instanceof Constant_Node;
	}

	private void handleAddedSimpleEdges(QueueReader<Pair<Alloc_Node, Var_Node>> addedAllocEdges,
			QueueReader<Pair<Var_Node, Var_Node>> addedSimpleEdges) {
		while(addedAllocEdges.hasNext()){
			Pair<Alloc_Node, Var_Node> allocEdge = addedAllocEdges.next();
			Alloc_Node src = allocEdge.getO1();
			Var_Node tgt = allocEdge.getO2();
			if (tgt.makeP2Set().add(src))
				varNodeWorkList.add(tgt);
		}
		while(addedSimpleEdges.hasNext()){
			Pair<Var_Node, Var_Node> simpleEdge = addedSimpleEdges.next();
			final Var_Node srcv = simpleEdge.getO1();
			final Var_Node tgtv = simpleEdge.getO2();
			if (addAll(tgtv, srcv.getP2Set().getOldSet()))
				varNodeWorkList.add(tgtv);
		}
	}

	private boolean addAll(final Var_Node pointer, PTSetInternal other) {
		final PTSetInternal addTo = pointer.makeP2Set();
		// deprecated because not equal to forall->add
		// return addTo.addAll(other, null);
		return other.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				if (addTo.add(n))
					returnValue = true;
			}
		});
	}
}
