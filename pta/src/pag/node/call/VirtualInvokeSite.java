package pag.node.call;

import pag.node.var.Var_Node;
import soot.*;
import soot.jimple.*;

import soot.util.*;

/**
 * Holds relevant information about a particular virtual call site.
 * 
 * @author Ondrej Lhotak
 */
public class VirtualInvokeSite{
	private Var_Node recNode;
	private Stmt stmt;
	private MethodOrMethodContext container;
	private InstanceInvokeExpr iie;
	private NumberedString subSig;
	private Kind kind;

	public VirtualInvokeSite(Var_Node recNode, Stmt stmt, MethodOrMethodContext container, InstanceInvokeExpr iie, NumberedString subSig,
			Kind kind) {
		this.recNode=recNode;
		this.stmt = stmt;
		this.container = container;
		this.iie = iie;
		this.subSig = subSig;
		this.kind = kind;
	}

	public Var_Node recNode() {
		return recNode;
	}
	
	public Stmt stmt() {
		return stmt;
	}

	public MethodOrMethodContext container() {
		return container;
	}

	public InstanceInvokeExpr iie() {
		return iie;
	}

	public NumberedString subSig() {
		return subSig;
	}

	public Kind kind() {
		return kind;
	}
}
