package pag.builder;

import soot.jimple.spark.pag.*;
import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.var.Var_Node;
import pta.ContextSensPTA;
import pta.PTA;
import soot.*;
import soot.toolkits.scalar.Pair;

/**
 * Factory for nodes not specific to a given method.
 * 
 * @author Ondrej Lhotak
 */
public class GlobalPAGBuilder {

	public GlobalPAGBuilder(PAG pag, PTA pta) {
		this.pag = pag;
		this.pta = pta;
	}

	final public GNode caseDefaultClassLoader() {
		Alloc_Node a = pag.makeAllocNode(PointsToAnalysis.DEFAULT_CLASS_LOADER,
				AnySubType.v(RefType.v("java.lang.ClassLoader")), null);
		Var_Node v = pag.makeGlobalVarNode(PointsToAnalysis.DEFAULT_CLASS_LOADER_LOCAL,
				RefType.v("java.lang.ClassLoader"));
		addParameterizedGlobalPAGEdge(a, v);
		return v;
	}

	final public GNode caseMainClassNameString() {
		Alloc_Node a = pag.makeAllocNode(PointsToAnalysis.MAIN_CLASS_NAME_STRING, RefType.v("java.lang.String"), null);
		Var_Node v = pag.makeGlobalVarNode(PointsToAnalysis.MAIN_CLASS_NAME_STRING_LOCAL,
				RefType.v("java.lang.String"));
		addParameterizedGlobalPAGEdge(a, v);
		return v;
	}

	final public GNode caseMainThreadGroup() {
		Alloc_Node threadGroupNode = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE,
				RefType.v("java.lang.ThreadGroup"), null);
		Var_Node threadGroupNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.MAIN_THREAD_GROUP_NODE_LOCAL,
				RefType.v("java.lang.ThreadGroup"));
		addParameterizedGlobalPAGEdge(threadGroupNode, threadGroupNodeLocal);
		return threadGroupNodeLocal;
	}

	final public GNode casePrivilegedActionException() {
		Alloc_Node a = pag.makeAllocNode(PointsToAnalysis.PRIVILEGED_ACTION_EXCEPTION,
				AnySubType.v(RefType.v("java.security.PrivilegedActionException")), null);
		Var_Node v = pag.makeGlobalVarNode(PointsToAnalysis.PRIVILEGED_ACTION_EXCEPTION_LOCAL,
				RefType.v("java.security.PrivilegedActionException"));
		addParameterizedGlobalPAGEdge(a, v);
		return v;
	}

	final public GNode caseCanonicalPath() {
		Alloc_Node a = pag.makeAllocNode(PointsToAnalysis.CANONICAL_PATH, RefType.v("java.lang.String"), null);
		Var_Node v = pag.makeGlobalVarNode(PointsToAnalysis.CANONICAL_PATH_LOCAL, RefType.v("java.lang.String"));
		addParameterizedGlobalPAGEdge(a, v);
		return v;
	}

	final public GNode caseMainThread() {
		Alloc_Node threadNode = pag.makeAllocNode(PointsToAnalysis.MAIN_THREAD_NODE, RefType.v("java.lang.Thread"),
				null);
		Var_Node threadNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.MAIN_THREAD_NODE_LOCAL,
				RefType.v("java.lang.Thread"));
		addParameterizedGlobalPAGEdge(threadNode, threadNodeLocal);
		return threadNodeLocal;
	}

	final public GNode caseFinalizeQueue() {
		return pag.makeGlobalVarNode(PointsToAnalysis.FINALIZE_QUEUE, RefType.v("java.lang.Object"));
	}

	final public GNode caseArgv() {
		Alloc_Node argv = pag.makeAllocNode(PointsToAnalysis.STRING_ARRAY_NODE,
				ArrayType.v(RefType.v("java.lang.String"), 1), null);
		Var_Node sanl = pag.makeGlobalVarNode(PointsToAnalysis.STRING_ARRAY_NODE_LOCAL,
				ArrayType.v(RefType.v("java.lang.String"), 1));
		Alloc_Node stringNode = pag.makeAllocNode(PointsToAnalysis.STRING_NODE, RefType.v("java.lang.String"), null);
		Var_Node stringNodeLocal = pag.makeGlobalVarNode(PointsToAnalysis.STRING_NODE_LOCAL,
				RefType.v("java.lang.String"));
		addParameterizedGlobalPAGEdge(argv, sanl);
		addParameterizedGlobalPAGEdge(stringNode, stringNodeLocal);
		addParameterizedGlobalPAGEdge(stringNodeLocal, pag.makeFieldRefNode(sanl, ArrayElement.v()));
		return sanl;
	}

	final public GNode caseNewInstance(Var_Node cls) {
		Var_Node local = pag.makeGlobalVarNode(cls, RefType.v("java.lang.Object"));
		for (SootClass cl : Scene.v().dynamicClasses()) {
			Alloc_Node site = pag.makeAllocNode(new Pair<Var_Node, SootClass>(cls, cl), cl.getType(), null);
			addParameterizedGlobalPAGEdge(site, local);
		}
		return local;
	}

	public GNode caseThrow() {
		Var_Node ret = pag.makeGlobalVarNode(PointsToAnalysis.EXCEPTION_NODE, RefType.v("java.lang.Throwable"));
		ret.setInterProcTarget();
		ret.setInterProcSource();
		return ret;
	}
	/* End of public methods. */
	/* End of package methods. */

	protected void addParameterizedGlobalPAGEdge(GNode from, GNode to) {
		if (pta instanceof ContextSensPTA){
			ContextSensPTA cspta = (ContextSensPTA) pta;
			from = cspta.parameterize(from, cspta.emptyContext());
			to = cspta.parameterize(to, cspta.emptyContext());
		}
		pag.addEdge(from, to);
	}

	protected PAG pag;
	protected PTA pta;
}
