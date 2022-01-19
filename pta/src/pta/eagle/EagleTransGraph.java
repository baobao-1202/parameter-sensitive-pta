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

package pta.eagle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import pag.node.alloc.Alloc_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import soot.SootField;
import soot.jimple.spark.pag.ArrayElement;

public class EagleTransGraph{
	
	public Map<Object, Map<Boolean, BNode>> sparkNode2BNode=new HashMap<>();
	
	BNode getNode(Object origin,Boolean forward){
		Map<Boolean, BNode> subMap = sparkNode2BNode.get(origin);
		if(subMap==null)
			sparkNode2BNode.put(origin, subMap = new HashMap<>());
		BNode ret = subMap.get(forward);
		if(ret==null)
			subMap.put(forward, ret=new BNode(origin,forward));
		return ret;
	}
	
	protected Set<BNode> entrys = new HashSet<>();
	
	// The following methods are used to create the edges in G_{R-pag} as described in the paper.
	
	public void addNewEdge(Alloc_Node from, LocalVar_Node to) {
		BNode fromE = getNode(from,true),toE = getNode(to,true);
		fromE.addOutEdge(toE,0);
		BNode fromEI = getNode(from, false),toEI = getNode(to, false);
		toEI.addOutEdge(fromEI, 0);
	}
	
	public void addAssignEdge(LocalVar_Node from, LocalVar_Node to) {
		BNode fromE =getNode(from,true),toE=getNode(to,true);
		fromE.addOutEdge(toE, 0);
		BNode fromEI =getNode(from,false),toEI=getNode(to,false);
		toEI.addOutEdge(fromEI, 0);
	}
	
	public void addStoreEdge(LocalVar_Node from, LocalVar_Node base) {
		BNode fromE = getNode(from,true),baseEI=getNode(base,false);
		fromE.addOutEdge(baseEI, 0);
		BNode fromEI = getNode(from,false),baseE=getNode(base,true);
		baseE.addOutEdge(fromEI, 0);
	}
	
	public void addHstoreEdge(Object from, Alloc_Node baseObj) {
		int ctx=-1;
		BNode fromE = getNode(from,true),baseObjE=getNode(baseObj,true);
		fromE.addOutEdge(baseObjE, ctx);
		BNode fromEI = getNode(from,false),baseObjEI=getNode(baseObj,false);
		baseObjEI.addOutEdge(fromEI, -ctx);
		
		entrys.add(fromEI);
	}
	
	public void addHloadEdge(Alloc_Node baseObj, Object to) {
		int ctx=1;
		BNode baseObjEI=getNode(baseObj,false),toE=getNode(to,true);
		baseObjEI.addOutEdge(toE, ctx);
		BNode baseObjE=getNode(baseObj,true),toEI=getNode(to,false);
		toEI.addOutEdge(baseObjE, -ctx);
		
		entrys.add(toE);
	}

	// Perform Eagle as a taint analysis according to the rules
	
	public void propagate(){
		long time;
		HashSet<BNode> workList=new HashSet<>();
		
		System.out.print("set all entry context sensitivity (and balanced) ...");
		time = System.currentTimeMillis();
		
		//start from all "parameter/field" node
		entrys.forEach(n->{
			n.cs=true;
			workList.add(n);
		});
		
		Set<Object> matchedObjects = new HashSet<>(); // Object which has a match edge
		while(!workList.isEmpty()){
			BNode node = workList.iterator().next();
			workList.remove(node);
			node.outEdges.forEach((o1,o2)->{
					if(o2>=0){ // EntryCTx & Prop
						if(o1.setCS()){
							workList.add(o1);
						}
					}else{ //o2<0 ExitCtx
						Alloc_Node receiverObj = (Alloc_Node) o1.sparkNode;
						if(!(node.sparkNode instanceof SootField)&&!(node.sparkNode instanceof ArrayElement)||
							getNode(receiverObj, false).outEdges.containsKey(node)
								){
							if(matchedObjects.add(receiverObj)){ //add match edges
								BNode fromE = getNode(receiverObj,true),fromEI = getNode(receiverObj, false);
								if(fromEI.addOutEdge(fromE,0)&&fromEI.cs){
									workList.add(fromEI);//add src of the match edge to worklist
								}
							}
						}
					}
			});
		}
		System.out.println((System.currentTimeMillis() - time)/1000 +"s");
	}
	
	// sparkNode * {+, -}
	// The data structure used for representing every edge in G_{pag}
    // with an edge in G_{R-pag} with its node states explicitly given according to
    // the DFA.
	public static class BNode{
		public Object sparkNode;
		public Boolean forward;
		public boolean cs;
		Map<BNode, Integer> outEdges = new HashMap<>();

		public boolean addOutEdge(BNode toE, int i) {
			if(!outEdges.containsKey(toE)){
				outEdges.put(toE, i);
				return true;
			}
			return false;
		}
		
		BNode(Object origin, Boolean forward){
			this.sparkNode=origin;
			this.forward=forward;
		}
		
		boolean setCS(){
			if(this.cs)
				return false;
			return this.cs=true;
		}
		
		boolean isHeapPlus() {
	        return sparkNode instanceof Alloc_Node && this.forward;
	    }

	    boolean isHeapMinus() {
	        return sparkNode instanceof Alloc_Node && !this.forward;
	    }
		
		public Object getIR(){
			if(sparkNode instanceof Var_Node)
				return ((Var_Node) sparkNode).getVariable();
			else if(sparkNode instanceof Alloc_Node)
				return ((Alloc_Node) sparkNode).getNewExpr();
			else//sparkField?
				return sparkNode;
		}
		@Override
		public String toString() {
			return sparkNode+","+forward;
		}
	}
}