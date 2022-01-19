package pag.node.alloc;

import pag.PAG;
import soot.SootMethod;
import soot.Type;

public abstract class Constant_Node extends Alloc_Node {
	protected Constant_Node(PAG pag, Object newExpr, Type t, SootMethod m) {
		super(pag, newExpr, t, m);
	}
}
