package pag.node.alloc;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import pag.PAG;
import pag.node.GNode;
import pag.node.var.AllocDotField_Node;
import pag.node.var.ContextVar_Node;
import pta.context.ContextElement;
import soot.PhaseOptions;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.options.CGOptions;

/**
 * Represents an allocation site node (Blue) in the pointer assignment graph.
 * 
 * @author Ondrej Lhotak
 */
public class Alloc_Node extends GNode implements ContextElement{
	/** Returns the new expression of this allocation site. */
	public Object getNewExpr() {
		return newExpr;
	}

	/** Returns all field ref nodes having this node as their base. */
	public Collection<AllocDotField_Node> getAllFieldRefs() {
		if (fields == null)
			return Collections.emptySet();
		return fields.values();
	}

	/**
	 * Returns the field ref node having this node as its base, and field as its
	 * field; null if nonexistent.
	 */
	public AllocDotField_Node dot(Object field) {
		return fields == null ? null : fields.get(field);
	}

	public String toString() {
		return "AllocNode " + getNumber() + " " + newExpr + " in method " + method;
	}

	/* End of public methods. */

	public Alloc_Node(PAG pag, Object newExpr, Type t, SootMethod m) {
		super(pag, t);
		if (t instanceof RefType && ((RefType) t).getSootClass().isAbstract()
				&& new CGOptions(PhaseOptions.v().getPhaseOptions("cg")).reflection_log() == null)
			throw new RuntimeException("Attempt to create allocnode with abstract type " + t);
		this.method = m;
		this.newExpr = newExpr;
		if (newExpr instanceof ContextVar_Node)
			throw new RuntimeException();
		pag.getAllocNodeNumberer().add(this);
	}

	/** Registers a AllocDotField as having this node as its base. */
	public void addField(AllocDotField_Node adf, Object field) {
		if (fields == null)
			fields = new HashMap<>();
		fields.put(field, adf);
	}

	/* End of package methods. */

	protected Object newExpr;
	protected Map<Object, AllocDotField_Node> fields;

	private SootMethod method;

	public SootMethod getMethod() {
		return method;
	}
}
