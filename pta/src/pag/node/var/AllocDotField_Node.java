package pag.node.var;

import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import soot.jimple.spark.pag.SparkField;

/**
 * Represents an alloc-site-dot-field node (Yellow) in the pointer assignment
 * graph.
 * 
 * @author Ondrej Lhotak
 */
public class AllocDotField_Node extends Var_Node {
	/** Returns the base AllocNode. */
	public Alloc_Node getBase() {
		return base;
	}

	/** Returns the field of this node. */
	public Object getField() {
		return field;
	}

	public String toString() {
		return "AllocDotField " + getNumber() + " " + base + "." + field;
	}

	/* End of public methods. */

	public AllocDotField_Node(PAG pag, Alloc_Node base, Object field) {
		super(pag, field,field instanceof SparkField?((SparkField) field).getType():((GNode) field).getType());
		this.base = base;
		this.field = field;
		base.addField(this, field);
	}

	/* End of package methods. */

	protected Alloc_Node base;
	protected Object field;
}
