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
import java.util.Map;
import java.util.Set;
import pag.MethodPAG;
import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.alloc.ContextAlloc_Node;
import pag.node.var.ContextVar_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.context.ContextMethod;
import pta.eagle.EagleTransGraph;
import pta.eagle.EagleTransGraph.BNode;
import soot.Context;
import soot.Local;
import soot.RefLikeType;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.util.queue.QueueReader;
import util.MemoryListener;

/**
 * An abstract class for performing all sorts of EAGLE-guided context-sensitive analyses
 * (with, for example, ek-obj, as a special case)
 */
public abstract class EagleContextSensPTA extends ContextSensPTA{
	PTA prePTA;
	PAG prePAG;
	Set<Object> CSNodes = new HashSet<>();
	Set<SootMethod> CSMethods = new HashSet<>();
	
	Map<SootMethod, Set<Object>> methodToNodes = new HashMap<>();
	
	public EagleContextSensPTA(int k, int hk) {
		super(k, hk);
	}
	
	@Override
	public void run(){
		// Perform Andersen's analysis 
		long time = System.currentTimeMillis();
		prePTA = new PTA();
		
		MemoryListener listener = new MemoryListener();
		listener.listen();
		
		prePTA.run();
		
		listener.stop("Spark Memory", "MB");
		System.out.println("Spark time:" + (System.currentTimeMillis() - time)/1000 +"s");
		
		// Perform the Eagle pre-analysis 
		
		listener.listen();
		
		time = System.currentTimeMillis();
		select();
		
		listener.stop("Select Memory", "MB");
		listener.cancel();
		
		System.out.println("Select time:" + (System.currentTimeMillis() - time)/1000 +"s");
		extraStats();
		
		// Perform Eagle-Guided k-obj, i.e., ek-obj
		
		System.out.println("selective cs-pta starts!");
		super.run();
	}
	
	//======pointer analysis========
	/**
	* Select the contexts for variables
	*/
	@Override
	protected ContextVar_Node parameterize(LocalVar_Node vn, Context context) {
		return makeContextVarNode(vn, contextTailor(context, CSNodes.contains(vn.getVariable())));
	}
	/**
	* Select the contexts for allocation sites
	*/
	@Override
	protected ContextAlloc_Node parameterize(Alloc_Node node, Context context) {
		context = heapSelector(node, context);
		return makeContextAllocNode(node, contextTailor(context, CSNodes.contains(node.getNewExpr())));
	}
	/**
	* Select the contexts for methods
	*/
	@Override
	public ContextMethod parameterize(SootMethod method, Context context) {
		return makeContextMethod(contextTailor(context, CSMethods.contains(method)),method);
	}
	protected Context contextTailor(Context context, boolean cs) {
		return cs?context:emptyContext;
	}
	
	//=========context selector=============
	/**
	* The Eagle Pre-Analysis
	*/
	protected void select() {
		System.out.print("Construct transPAG...");
		long time = System.currentTimeMillis();
		
		// Get PAG, i.e., G_{pag} in the paper
		
		prePAG=prePTA.getPag();
		
		// Build the regularised PAG, i.e., G_{R-pag} without the match edges
		
		EagleTransGraph eagleTransGraph = new EagleTransGraph();
		addTransEdgesFromMtdPag(eagleTransGraph);
		System.out.println((System.currentTimeMillis() - time)/1000 +"s");
		
		// Perform Eagle as a taint analysis according to the rules in paper
        // (with the match edges added dynamically during the analysis)
		
		System.out.println("Propagate..");
		eagleTransGraph.propagate();
		
		//fill nToContextLength map
		eagleTransGraph.sparkNode2BNode.forEach((sparkNode,map)->{
			BNode forwardNode = map.get(true);
			if(forwardNode==null)return;
			BNode backwardNode = map.get(false);
			if(backwardNode==null)return;
			if(forwardNode.cs&&backwardNode.cs){
				CSNodes.add(forwardNode.getIR());
				SootMethod method;
				if(sparkNode instanceof LocalVar_Node)
					method = ((LocalVar_Node) sparkNode).getMethod();
				else if(sparkNode instanceof Alloc_Node){
					method = ((Alloc_Node) sparkNode).getMethod();
					if(method==null)return;
				}
				else
					return;
				CSMethods.add(method);
			}
		});
	}
	
	protected void extraStats() {
		int[] totalN = new int[1];
		prePTA.getCgb().getReachableMethods().listener().forEachRemaining(momc->{
			SootMethod method =(SootMethod) momc;
			Set<Object> nodes = methodToNodes.get(method);
			if(nodes==null)methodToNodes.put(method, nodes=new HashSet<>());
			
			if (method.isPhantom())
				return;
			MethodPAG srcmpag = prePTA.getMethodPAG(method);
//			MtdPAGBuilder srcnf = srcmpag.nodeFactory();
//			nodes.add(((Var_Node)srcnf.caseThis()).getVariable());
			QueueReader<GNode> reader = srcmpag.getInternalReader().clone();
			while (reader.hasNext()){
				GNode from=reader.next(), to=reader.next();
				if(from instanceof LocalVar_Node)
					nodes.add(((Var_Node) from).getVariable());
				else if(from instanceof Alloc_Node)
					nodes.add(((Alloc_Node)from).getNewExpr());
				else if(from instanceof FieldRef_Node){
					FieldRef_Node fr = (FieldRef_Node) from;
					Var_Node base = fr.getBase();
					if(base instanceof LocalVar_Node)
						nodes.add(base.getVariable());
				}
				
				if(to instanceof LocalVar_Node){
					nodes.add(((Var_Node) to).getVariable());
				}
				else if(to instanceof FieldRef_Node){
					FieldRef_Node fr = (FieldRef_Node) to;
					Var_Node base = fr.getBase();
					if(base instanceof LocalVar_Node)
						nodes.add(base.getVariable());
				}
			}
			for (final Unit u : srcmpag.invokeStmts) {
				final Stmt s = (Stmt) u;
				
				InvokeExpr ie = s.getInvokeExpr();
				int numArgs = ie.getArgCount();
				for (int i = 0; i < numArgs; i++) {
					Value arg = ie.getArg(i);
					if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant)
						continue;
					nodes.add(arg);
				}
				
				if (s instanceof AssignStmt) {
					Value dest = ((AssignStmt) s).getLeftOp();
					if (dest.getType() instanceof RefLikeType)
						nodes.add(dest);
				}
				if (ie instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
					Value base = iie.getBase();
					if(base instanceof Local)
						nodes.add(base);
				}
			}
			totalN[0]+=nodes.size();
		});
		int[] insens=new int[1],partial=new int[1],sens=new int[1];
		int[] insensN =new int[1], sensN = new int[1];
		prePTA.getCgb().getReachableMethods().listener().forEachRemaining(momc->{
			SootMethod method =(SootMethod) momc;
			Set<Object> nodes = methodToNodes.get(method);
			int incs=0, cs = 0;
			for(Object l:nodes){
				if(CSNodes.contains(l))
					cs++;
				else
					incs++;
			}
			if(cs==0)
				insens[0]++;
			else if(cs==nodes.size())
				sens[0]++;
			else {
				partial[0]++;
			}
			
			insensN[0]+=incs;
			sensN[0]+=cs;
			
		});
		System.out.println("methods:");
		System.out.println("insensM: "+(insens[0]-1));//remove fake main
		System.out.println("fullsens: "+sens[0]);
		System.out.println("partial: "+partial[0]);
		System.out.println("nodes:");
		System.out.println("insensN: "+insensN[0]);
		System.out.println("sensN: "+sensN[0]);
		System.out.println("totalN: "+totalN[0]);
	}
	
	/**
	* Build the regularised G_{R-pag} as described in the paper
	*/
	abstract void addTransEdgesFromMtdPag(EagleTransGraph eagleTransGraph);

}
