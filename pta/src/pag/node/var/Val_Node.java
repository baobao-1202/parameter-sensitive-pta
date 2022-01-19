package pag.node.var;

import pag.PAG;
import pag.node.GNode;
import soot.Type;

/**
 * Represents a simple of field ref node (Green or Red) in the pointer
 * assignment graph.
 * 
 * @author Ondrej Lhotak
 */
public class Val_Node extends GNode {
	public Val_Node(PAG pag, Type t) {
		super(pag, t);
	}
}
