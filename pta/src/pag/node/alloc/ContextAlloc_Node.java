package pag.node.alloc;

import pag.PAG;
import pta.context.ContextElements;
import soot.Context;

/**
 * This class represents context in a context sensitive PTA.
 * 
 * It is a list of new expressions
 *
 */
public class ContextAlloc_Node extends Alloc_Node implements Context{

	/** Array for context elements */
	private Context context;
	private Alloc_Node base;

	public Context context() {
		return context;
	}

	public Alloc_Node base() {
		return base;
	}

	public String toString() {
		return "ContextAllocNode "+getNumber()+"("+base+", "+context + ")";
	}

	public ContextAlloc_Node(PAG pag, Alloc_Node base, Context context) {
		super(pag, base.getNewExpr(), base.getType(), base.getMethod());
		this.context = context;
		this.base = base;
	}

	public boolean noContext() {
		if (context == null)
			return true;
		if (context instanceof ContextElements)
			return ((ContextElements) context).numContextElements() == 0;
		return false;
	}
}
