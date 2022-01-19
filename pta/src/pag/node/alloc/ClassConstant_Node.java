package pag.node.alloc;

import pag.PAG;
import soot.RefType;
import soot.jimple.ClassConstant;

public class ClassConstant_Node extends Constant_Node {
	public String toString() {
		return "ClassConstantNode " + getNumber() + " " + newExpr;
	}

	public ClassConstant getClassConstant() {
		return (ClassConstant) newExpr;
	}

	/* End of public methods. */

	// changed from public access to package access
	public ClassConstant_Node(PAG pag, ClassConstant cc) {
		super(pag, cc, RefType.v("java.lang.Class"), null);
	}
}
