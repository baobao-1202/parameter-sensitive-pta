package pag.node.var;

import pag.PAG;
import soot.SootClass;
import soot.SootField;
import soot.Type;

/**
 * Represents a simple variable node (Green) in the pointer assignment graph
 * that is not associated with any particular method invocation.
 * 
 * @author Ondrej Lhotak
 */
public class GlobalVar_Node extends Var_Node {
	public GlobalVar_Node(PAG pag, Object variable, Type t) {
		super(pag, variable, t);
	}

	public String toString() {
		return "GlobalVarNode " + getNumber() + " " + variable;
	}

	public SootClass getDeclaringClass() {
		if (variable instanceof SootField)
			return ((SootField) variable).getDeclaringClass();

		return null;
	}
}
