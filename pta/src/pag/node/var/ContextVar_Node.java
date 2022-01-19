package pag.node.var;

import pag.PAG;
import soot.Context;

public class ContextVar_Node extends Var_Node {
	private Context context;
	private Var_Node base;

	@Override
	public Context context() {
		return context;
	}

	public Var_Node base() {
		return base;
	}

	public String toString() {
		return "ContextVarNode "+getNumber()+"("+base+", "+context + ")";
	}

	/* End of public methods. */

	public ContextVar_Node(PAG pag, Var_Node base, Context context) {
		super(pag, base.getVariable(), base.getType());
		this.context = context;
		this.base = base;
	}
}
