package pag.node.var;

import pag.PAG;
import soot.SootMethod;
import soot.Type;

/**
 * Represents a simple variable node (Green) in the pointer assignment graph
 * that is specific to a particular method invocation.
 * 
 * @author Ondrej Lhotak
 */
public class LocalVar_Node extends Var_Node {
	public SootMethod getMethod() {
		return method;
	}

	public String toString() {
		return "LocalVarNode " + getNumber() + " " + variable + " " + method;
	}
	/* End of public methods. */

	public LocalVar_Node(PAG pag, Object variable, Type t, SootMethod m) {
		super(pag, variable, t);
		this.method = m;
		// if( m == null ) throw new RuntimeException( "method shouldn't be
		// null" );
	}

	/* End of package methods. */

	protected SootMethod method;

	/** Returns true if this VarNode represents the THIS pointer */
	public boolean isThis() {
		return !method.isStatic() && variable.toString().equals("r0");
	}
}
