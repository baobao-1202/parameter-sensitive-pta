package pag.node.alloc;

import pag.PAG;
import soot.RefType;
import soot.jimple.StringConstant;

public class StringConstant_Node extends Constant_Node {

	public String toString() {
		return "StringConstantNode " + getNumber() + " " + getString();
	}

	public String getString() {
		return ((StringConstant) newExpr).value;
	}

	// changed from public access to package access
	public StringConstant_Node(PAG pag, StringConstant sc) {
		super(pag, sc, RefType.v("java.lang.String"), null);
		// System.out.println("Making string constant node: " +
		// this.toString());
	}
}
