package pag.node.var;

import pag.PAG;
import pag.node.GNode;
import soot.jimple.spark.pag.SparkField;

/**
 * Represents a field reference node (Red) in the pointer assignment graph.
 * 
 * @author Ondrej Lhotak
 */
public class FieldRef_Node extends Val_Node {
	/** Returns the base of this field reference. */
	public Var_Node getBase() {
		return base;
	}

	public GNode getReplacement() {
		if (replacement == this) {
			if (base.getReplacement() == base)
				return this;
			FieldRef_Node newRep = pag.makeFieldRefNode((Var_Node) base.getReplacement(), field);
			newRep.mergeWith(this);
			return replacement = newRep.getReplacement();
		} else
			return replacement = replacement.getReplacement();
	}

	/** Returns the field of this field reference. */
	public SparkField getField() {
		return field;
	}

	public String toString() {
		return "FieldRefNode " + getNumber() + " " + base + "." + field;
	}

	/* End of public methods. */

	public FieldRef_Node(PAG pag, Var_Node base, SparkField field) {
		super(pag, null);
		if (field == null)
			throw new RuntimeException("null field");
		this.base = base;
		this.field = field;
		base.addField(this, field);
		pag.getFieldRefNodeNumberer().add(this);
	}

	/* End of package methods. */

	protected Var_Node base;
	protected SparkField field;
}
