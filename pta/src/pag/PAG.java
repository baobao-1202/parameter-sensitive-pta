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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import driver.PTAOptions;
import pag.builder.GlobalPAGBuilder;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.alloc.ClassConstant_Node;
import pag.node.alloc.StringConstant_Node;
import pag.node.var.AllocDotField_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.GlobalVar_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.pts.DoublePTSet;
import pta.pts.HybridPTSet;
import pta.pts.PTSetFactory;

import static driver.PTAOptions.sparkOpts;

import soot.FastHierarchy;
import soot.Local;
import soot.PointsToAnalysis;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.jimple.ClassConstant;
import soot.jimple.StringConstant;
import soot.jimple.spark.pag.SparkField;
import soot.options.SparkOptions;
import soot.toolkits.scalar.Pair;
import soot.util.ArrayNumberer;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;
import util.TypeMask;

/**
 * Pointer assignment graph.
 * 
 * @author Ondrej Lhotak
 */
public class PAG {
	// ==========================outer objects==============================
	protected TypeMask typeManager;
	private GlobalPAGBuilder nodeFactory;
	public PTSetFactory setFactory;

	// ==========================parms==============================
	public int maxFinishNumber = 0;

	// ==========================data=========================
	
	public final Map<Object, Alloc_Node> valToAllocNode = new HashMap<>(10000);
	public final Map<Object, Var_Node> valToVarNode = new HashMap<>(100000);//LargeNumberedMap?
	
	private final Set<SootField> globals = new HashSet<>(100000);
	private final Set<Local> locals = new HashSet<>(100000);
	private final ArrayNumberer<Alloc_Node> allocNodeNumberer = new ArrayNumberer<Alloc_Node>();
	private final ArrayNumberer<Var_Node> varNodeNumberer = new ArrayNumberer<Var_Node>();
	private final ArrayNumberer<FieldRef_Node> fieldRefNodeNumberer = new ArrayNumberer<FieldRef_Node>();
	// temporary hack to reduce memory-inefficiency (use new field
	// additionalVirtualCalls instead of callAssigns)
	// public HashMultiMap /* InvokeExpr -> Set[Pair] */ callAssigns = new
	// HashMultiMap();
	protected ChunkedQueue<Pair<Alloc_Node,Var_Node>> allocQueue = new ChunkedQueue<>();
	protected ChunkedQueue<Pair<Var_Node,Var_Node>> simpleQueue = new ChunkedQueue<>();
	protected ChunkedQueue<Pair<FieldRef_Node,Var_Node>> loadQueue = new ChunkedQueue<>();
	protected ChunkedQueue<Pair<Var_Node,FieldRef_Node>> storeQueue = new ChunkedQueue<>();
	protected Map<Alloc_Node, Set<Var_Node>> alloc = new HashMap<Alloc_Node, Set<Var_Node>>();
	protected Map<Var_Node, Set<Var_Node>> simple = new HashMap<Var_Node, Set<Var_Node>>();
	protected Map<Var_Node, Set<Var_Node>> simpleInv = new HashMap<Var_Node, Set<Var_Node>>();//used in some pre-analysis, can be removed if not needed
	protected Map<FieldRef_Node, Set<Var_Node>> load = new HashMap<FieldRef_Node, Set<Var_Node>>();
	protected Map<FieldRef_Node, Set<Var_Node>> storeInv = new HashMap<FieldRef_Node, Set<Var_Node>>();
	public Alloc_Node rootNode;

	public PAG() {
		setupPTSOptions();
		typeManager = new TypeMask(this);
		if (!sparkOpts.ignore_types()){
			FastHierarchy fh = new util.FastHierarchy();//继承关系
			Scene.v().setFastHierarchy(fh);
			typeManager.setFastHierarchy(fh);
		}
	}

	private void setupPTSOptions() {
		switch (sparkOpts.set_impl()) {
		case SparkOptions.set_impl_double:
			PTSetFactory oldF;
			PTSetFactory newF;
			
			switch (sparkOpts.double_set_old()) {
			case SparkOptions.double_set_old_hybrid:
				oldF = HybridPTSet.getFactory();
				break;
			default:
				throw new RuntimeException();
			}
			switch (sparkOpts.double_set_new()) {
			case SparkOptions.double_set_new_hybrid:
				newF = HybridPTSet.getFactory();
				break;
			default:
				throw new RuntimeException();
			}
			
			setFactory = DoublePTSet.getFactory(newF, oldF);
			break;
		default:
			throw new RuntimeException();
		}
	}

	// ========================getters and setters=========================
	public GlobalPAGBuilder GlobalNodeFactory() {
		return nodeFactory;
	}

	public void setGlobalNodeFactory(GlobalPAGBuilder nodeFactory) {
		this.nodeFactory = nodeFactory;
	}

	public ArrayNumberer<Alloc_Node> getAllocNodeNumberer() {
		return allocNodeNumberer;
	}

	public ArrayNumberer<Var_Node> getVarNodeNumberer() {
		return varNodeNumberer;
	}

	public ArrayNumberer<FieldRef_Node> getFieldRefNodeNumberer() {
		return fieldRefNodeNumberer;
	}

	public PTSetFactory getSetFactory() {
		return setFactory;
	}

	public TypeMask getTypeManager() {
		return typeManager;
	}

	public Map<Alloc_Node, Set<Var_Node>> getAlloc() {
		return alloc;
	}

	public Map<Var_Node, Set<Var_Node>> getSimple() {
		return simple;
	}
	
	public Map<Var_Node, Set<Var_Node>> getSimpleInv() {
		return simpleInv;
	}

	public Map<FieldRef_Node, Set<Var_Node>> getLoad() {
		return load;
	}

	public Map<FieldRef_Node, Set<Var_Node>> getStoreInv() {
		return storeInv;
	}

	/** Returns list of dereferences variables. */

	// ===============================read data==========================

	public QueueReader<Pair<Alloc_Node, Var_Node>> allocReader() {
		return allocQueue.reader();
	}
	public QueueReader<Pair<Var_Node, Var_Node>> simpleReader() {
		return simpleQueue.reader();
	}
	public QueueReader<Pair<FieldRef_Node, Var_Node>> loadReader() {
		return loadQueue.reader();
	}
	public QueueReader<Pair<Var_Node, FieldRef_Node>> storeReader() {
		return storeQueue.reader();
	}

	public Collection<Alloc_Node> getAllocNodes() {
		return valToAllocNode.values();
	}

	public Set<SootField> getGlobalPointers() {
		return globals;
	}

	public Set<Local> getLocalPointers() {
		return locals;
	}

	// =======================add edge===============================
	public static <K, V> boolean addToMap(Map<K, Set<V>> m, K key, V value) {
		Set<V> valueList = m.get(key);
		if (valueList == null)
			m.put(key, valueList = new HashSet<V>(4));
		return valueList.add(value);
	}

	public void addAllocEdge(Alloc_Node from, Var_Node to) {
		FastHierarchy fh = typeManager.getFastHierarchy();
		if ((fh == null || to.getType() == null || fh.canStoreType(from.getType(), to.getType()))
				&& addToMap(alloc, from, to)) {
			allocQueue.add(new Pair<>(from,to));
		}
	}

	public void addSimpleEdge(Var_Node from, Var_Node to) {
		if (addToMap(simple, from, to)) {
			simpleQueue.add(new Pair<>(from,to));
			addToMap(simpleInv, to, from);
		}
		if (sparkOpts.simple_edges_bidirectional() && addToMap(simple, to, from)) {
			simpleQueue.add(new Pair<>(to,from));
		}
	}

	public void addStoreEdge(Var_Node from, FieldRef_Node to) {
		if (!sparkOpts.rta() && addToMap(storeInv, to, from)) {
			storeQueue.add(new Pair<>(from,to));
		}
	}

	public void addLoadEdge(FieldRef_Node from, Var_Node to) {
		if (!sparkOpts.rta() && addToMap(load, from, to)) {
			loadQueue.add(new Pair<>(from,to));
		}
	}

	/** Adds an edge to the graph, returning false if it was already there. */
	public final void addEdge(GNode from, GNode to) {
		from = from.getReplacement();
		to = to.getReplacement();
		if (from instanceof Var_Node)
			if (to instanceof Var_Node)
				addSimpleEdge((Var_Node) from, (Var_Node) to);
			else
				addStoreEdge((Var_Node) from, (FieldRef_Node) to);
		else if (from instanceof FieldRef_Node)
			addLoadEdge((FieldRef_Node) from, (Var_Node) to);
		else
			addAllocEdge((Alloc_Node) from, (Var_Node) to);
	}

	// ======================lookups===========================
	protected static <K, V> Set<V> lookup(Map<K, Set<V>> m, K key) {
		Set<V> valueList = m.get(key);
		if (valueList == null)
			return Collections.emptySet();
		return valueList;
	}

	public Set<Var_Node> simpleLookup(Var_Node key) {
		return lookup(simple, key);
	}
	public Set<Var_Node> simpleInvLookup(Var_Node key) {
		return lookup(simpleInv, key);
	}
	public Set<Var_Node> loadLookup(FieldRef_Node key) {
		return lookup(load, key);
	}

	public Set<Var_Node> storeInvLookup(FieldRef_Node key) {
		return lookup(storeInv, key);
	}

	// ===================find nodes==============================
	/** Finds the GlobalVarNode for the variable value, or returns null. */
	public GlobalVar_Node findGlobalVarNode(Object value) {
		return (GlobalVar_Node) findVarNode(value);
	}
	// ===================find nodes==============================
	/** Finds the LocalVarNode for the variable value, or returns null. */
	public LocalVar_Node findLocalVarNode(Object value) {
		return (LocalVar_Node) findVarNode(value);
	}
	// ===================find nodes==============================
	/** Finds the VarNode for the variable value, or returns null. */
	public Var_Node findVarNode(Object value) {
		if (sparkOpts.rta())
			value = null;
		return valToVarNode.get(value);
	}

	/**
	 * Finds the FieldRefNode for base variable value and field field, or
	 * returns null.
	 */
	public FieldRef_Node findLocalFieldRefNode(Object baseValue, SparkField field) {
		Var_Node base = findVarNode(baseValue);
		if (base == null)
			return null;
		return base.dot(field);
	}

	/**
	 * Finds the FieldRefNode for base variable value and field field, or
	 * returns null.
	 */
	public FieldRef_Node findGlobalFieldRefNode(Object baseValue, SparkField field) {
		Var_Node base = findVarNode(baseValue);
		if (base == null)
			return null;
		return base.dot(field);
	}

	/**
	 * Finds the AllocDotField for base AllocNode an and field field, or returns
	 * null.
	 */
	public AllocDotField_Node findAllocDotField(Alloc_Node an, Object field) {
		return an.dot(field);
	}

	// ==========================create nodes==================================
	public Alloc_Node makeAllocNode(Object newExpr, Type type, SootMethod m) {
		if (sparkOpts.types_for_sites() || sparkOpts.vta())
			newExpr = type;
		Alloc_Node ret = valToAllocNode.get(newExpr);
		if (ret == null) {
			valToAllocNode.put(newExpr, ret = new Alloc_Node(this, newExpr, type, m));
		} else if (!(ret.getType().equals(type)))
			throw new RuntimeException(
					"NewExpr " + newExpr + " of type " + type + " previously had type " + ret.getType());
		return ret;
	}

	public Alloc_Node makeStringConstantNode(StringConstant sc) {
		if (!PTAOptions.stringConstants ||  //merge all string constants
				(sparkOpts.types_for_sites() || sparkOpts.vta())&&!Scene.v().containsClass(sc.value))//merge all string constants which are not classnames
			sc = StringConstant.v(PointsToAnalysis.STRING_NODE);
		Alloc_Node ret = valToAllocNode.get(sc);
		if (ret==null)
			valToAllocNode.put(sc, ret = new StringConstant_Node(this, sc));
		return ret;
//		return makeAllocNode(sc, RefType.v("java.lang.String"), null);
	}

	public Alloc_Node makeClassConstantNode(ClassConstant cc) {
		if (sparkOpts.types_for_sites() || sparkOpts.vta())
			cc=ClassConstant.v("java.lang.Class");
		Alloc_Node ret = valToAllocNode.get(cc);
		if (ret == null) {
			valToAllocNode.put(cc, ret = new ClassConstant_Node(this, cc));
		}
		return ret;
//		return makeAllocNode(cc, RefType.v("java.lang.Class"), null);
	}

	/**
	 * Finds or creates the GlobalVarNode for the variable value, of type type.
	 */
	public GlobalVar_Node makeGlobalVarNode(Object value, Type type) {
		if (sparkOpts.rta()) {
			value = null;
			type = RefType.v("java.lang.Object");
		}
		GlobalVar_Node ret = (GlobalVar_Node) valToVarNode.get(value);
		if (ret == null) {
			valToVarNode.put(value, ret = new GlobalVar_Node(this, value, type));
			if(value instanceof SootField && ((SootField) value).isStatic())
				globals.add((SootField) value);
		} else if (!(ret.getType().equals(type)))
			throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
		return ret;
	}

	/**
	 * Finds or creates the LocalVarNode for the variable value, of type type.
	 */
	public LocalVar_Node makeLocalVarNode(Object value, Type type, SootMethod method) {
		if (sparkOpts.rta()) {
			value = null;
			type = RefType.v("java.lang.Object");
			method = null;
		} 
		LocalVar_Node ret = (LocalVar_Node) valToVarNode.get(value);
		if (ret == null) {
			valToVarNode.put(value, ret = new LocalVar_Node(this, value, type, method));
			if(value instanceof Local){
				Local local = (Local) value;
				if (local.getNumber() == 0)
					Scene.v().getLocalNumberer().add(local);
				locals.add(local);
			}
		} else if (!(ret.getType().equals(type)))
			throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
		return ret;
	}

	/**
	 * Finds or creates the FieldRefNode for base variable base and field field,
	 * of type type.
	 */
	public FieldRef_Node makeFieldRefNode(Var_Node base, SparkField field) {
		FieldRef_Node ret = base.dot(field);
		if (ret == null) {
			ret = new FieldRef_Node(this, base, field);
		}
		return ret;
	}

	/**
	 * Finds or creates the AllocDotField for base variable baseValue and field
	 * field, of type t.
	 */
	public AllocDotField_Node makeAllocDotField(Alloc_Node an, Object field) {
		AllocDotField_Node ret = an.dot(field);
		return ret != null ? ret : new AllocDotField_Node(this, an, field);
	}

	public void mergedWith(GNode myRep, GNode other) {
	}

//	public void setInitialReader() {
//		initialReader = edgeReader();
//	}
}