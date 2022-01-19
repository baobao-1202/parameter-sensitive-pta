package pag.builder;

import soot.jimple.spark.pag.*;
import soot.jimple.*;
import static driver.PTAOptions.sparkOpts;

import java.util.HashSet;
import java.util.Set;

import driver.PTAOptions;
import pag.PAG;
import pag.MethodPAG;
import pag.builder.MtdPAGBuilder;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.var.Var_Node;
import soot.*;
import soot.toolkits.scalar.Pair;
import soot.shimple.*;

/**
 * Class implementing builder parameters (this decides what kinds of nodes
 * should be built for each kind of Soot value).
 * 
 * @author Ondrej Lhotak
 */
public class MtdPAGBuilder extends AbstractShimpleValueSwitch {
	public Set<SootClass> clinitclasses = new HashSet<>();
	private void addToClinits(SootClass cls){
		clinitclasses.add(cls);
	}

	public MtdPAGBuilder(PAG pag, MethodPAG mpag) {
		this.pag = pag;
		this.mpag = mpag;
		method = mpag.getMethod();
    }
	public GNode getNode(Value v) {
		v.apply(this);
		return getNode();
	}

	/** Adds the edges required for this statement to the graph. */
	final public void handleStmt(Stmt s) {
		if (s.containsInvokeExpr()){
			mpag.invokeStmts.add(s);
			handleInvokeStmt(s);
		}
		else
			handleIntraStmt(s);
		
	}
	
	/** Adds the edges required for this statement to the graph.
	 * Add throw stmt if the invoke method throws an Exception.
	 * */
	protected void handleInvokeStmt(Stmt s) {	
		InvokeExpr ie = s.getInvokeExpr();
		int numArgs = ie.getArgCount();
		for (int i = 0; i < numArgs; i++) {
			Value arg = ie.getArg(i);
			if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant)
				continue;
			arg.apply(this);
		}
		if (s instanceof AssignStmt) {
			Value l = ((AssignStmt) s).getLeftOp();
			if ((l.getType() instanceof RefLikeType))
				l.apply(this);
		}
		if (ie instanceof InstanceInvokeExpr) {
			((InstanceInvokeExpr) ie).getBase().apply(this);
		}//TODO
		else if(PTAOptions.clinit==PTAOptions.ONFLY && ie instanceof StaticInvokeExpr){
			addToClinits(ie.getMethodRef().declaringClass());
		}
	}
	
	/** Adds the edges required for this statement to the graph. */
	final private void handleIntraStmt(Stmt s) {
		s.apply(new AbstractStmtSwitch() {
			final public void caseAssignStmt(AssignStmt as) {
				Value l = as.getLeftOp();
				Value r = as.getRightOp();
				
				if(PTAOptions.clinit==PTAOptions.ONFLY&&(l instanceof StaticFieldRef||r instanceof StaticFieldRef)) {
					StaticFieldRef sfr = l instanceof StaticFieldRef?(StaticFieldRef) l:(StaticFieldRef) r;
					SootField field = sfr.getField();
					addToClinits(field.getDeclaringClass());
				}
				
				if (!(l.getType() instanceof RefLikeType))
					return;
				// check for improper casts, with mal-formed code we might get
				// l = (refliketype)int_type, if so just return
				if (r instanceof CastExpr && (!(((CastExpr) r).getOp().getType() instanceof RefLikeType))) {
					return;
				}

				if (!(r.getType() instanceof RefLikeType))
					throw new RuntimeException("Type mismatch in assignment (rhs not a RefLikeType) " + as
							+ " in method " + method.getSignature());

				GNode dest = getNode(l);
				GNode src = getNode(r);
				
				if (r instanceof StaticFieldRef) {
					StaticFieldRef sfr = (StaticFieldRef) r;
					SootFieldRef s = sfr.getFieldRef();
					if (sparkOpts.empties_as_allocs()) {
						if (s.declaringClass().getName().equals("java.util.Collections")) {
							if (s.name().equals("EMPTY_SET")) {
								src = pag.makeAllocNode(RefType.v("java.util.HashSet"), RefType.v("java.util.HashSet"),
										method);
							} else if (s.name().equals("EMPTY_MAP")) {
								src = pag.makeAllocNode(RefType.v("java.util.HashMap"), RefType.v("java.util.HashMap"),
										method);
							} else if (s.name().equals("EMPTY_LIST")) {
								src = pag.makeAllocNode(RefType.v("java.util.LinkedList"),
										RefType.v("java.util.LinkedList"), method);
							}
						} else if (s.declaringClass().getName().equals("java.util.Hashtable")) {
							if (s.name().equals("emptyIterator")) {
								src = pag.makeAllocNode(RefType.v("java.util.Hashtable$EmptyIterator"),
										RefType.v("java.util.Hashtable$EmptyIterator"), method);
							} else if (s.name().equals("emptyEnumerator")) {
								src = pag.makeAllocNode(RefType.v("java.util.Hashtable$EmptyEnumerator"),
										RefType.v("java.util.Hashtable$EmptyEnumerator"), method);
							}
						}
					}
				}
				mpag.addInternalEdge(src, dest);
			}

			final public void caseReturnStmt(ReturnStmt rs) {
				if (!(rs.getOp().getType() instanceof RefLikeType))
					return;
				GNode retNode = getNode(rs.getOp());
				mpag.addInternalEdge(retNode, caseRet());
			}

			final public void caseIdentityStmt(IdentityStmt is) {
				if (!(is.getLeftOp().getType() instanceof RefLikeType))
					return;
				GNode dest = getNode(is.getLeftOp());
				GNode src = getNode(is.getRightOp());
				mpag.addInternalEdge(src, dest);
			}

			final public void caseThrowStmt(ThrowStmt ts) {
				mpag.addInternalEdge(getNode(ts.getOp()), pag.GlobalNodeFactory().caseThrow());
			}
		});
	}

	final public GNode getNode() {
		return (GNode) getResult();
	}

	final public GNode caseThis() {
		Type type = method.isStatic()?RefType.v("java.lang.Object"):method.getDeclaringClass().getType();
		Var_Node ret = pag.makeLocalVarNode(new Pair<>(method, PointsToAnalysis.THIS_NODE),
				type, method);
		ret.setInterProcTarget();
		return ret;
	}

	public GNode caseParm(int index) {
		Var_Node ret = pag.makeLocalVarNode(Parm.v(method, index), method.getParameterType(index),
				method);
		ret.setInterProcTarget();
		return ret;
	}
	public GNode caseRet() {
		Var_Node ret = pag.makeLocalVarNode(Parm.v(method, PointsToAnalysis.RETURN_NODE), method.getReturnType(),
				method);
		ret.setInterProcSource();
		return ret;
	}
	final public void casePhiExpr(PhiExpr e) {
		Pair<PhiExpr, String> phiPair = new Pair<>(e, PointsToAnalysis.PHI_NODE);
		GNode phiNode = pag.makeLocalVarNode(phiPair, e.getType(), method);
		for (Value op : e.getValues()) {
			GNode opNode = getNode(op);
			mpag.addInternalEdge(opNode, phiNode);
		}
		setResult(phiNode);
	}

	

	final public GNode caseArray(Var_Node base) {
		return pag.makeFieldRefNode(base, ArrayElement.v());
	}
	/* End of public methods. */
	/* End of package methods. */

	// OK, these ones are public, but they really shouldn't be; it's just
	// that Java requires them to be, because they override those other
	// public methods.
	@Override
	final public void caseArrayRef(ArrayRef ar) {
		caseLocal((Local) ar.getBase());
		setResult(caseArray((Var_Node) getNode()));
	}

	final public void caseCastExpr(CastExpr ce) {
		GNode opNode = getNode(ce.getOp());
		GNode castNode = pag.makeLocalVarNode(ce, ce.getCastType(), method);
		mpag.addInternalEdge(opNode, castNode);
		setResult(castNode);
	}

	@Override
	final public void caseCaughtExceptionRef(CaughtExceptionRef cer) {
		setResult(pag.GlobalNodeFactory().caseThrow());
	}

	@Override
	final public void caseInstanceFieldRef(InstanceFieldRef ifr) {
		if (sparkOpts.field_based() || sparkOpts.vta()) {
			setResult(pag.makeGlobalVarNode(ifr.getField(), ifr.getField().getType()));
		} else {
			setResult(pag.makeFieldRefNode(pag.makeLocalVarNode(ifr.getBase(), ifr.getBase().getType(), method), ifr.getField()));
		}
	}

	@Override
	final public void caseLocal(Local l) {
		setResult(pag.makeLocalVarNode(l, l.getType(), method));
	}

	@Override
	final public void caseNewArrayExpr(NewArrayExpr nae) {
		ArrayType arrtype = (ArrayType) nae.getType();
		setResult(pag.makeAllocNode(nae, arrtype, method));
	}

	@Override
	final public void caseNewExpr(NewExpr ne) {
		RefType type = ne.getBaseType();
		setResult(pag.makeAllocNode(ne, type, method));
		//TODO
		if(PTAOptions.clinit==PTAOptions.ONFLY)
			addToClinits(type.getSootClass());
	}

	@Override
	final public void caseNewMultiArrayExpr(NewMultiArrayExpr nmae) {
		ArrayType type = (ArrayType) nmae.getType();
		Alloc_Node prevAn = pag.makeAllocNode(new Pair<>(nmae, new Integer(type.numDimensions)), type, method);
		Var_Node prevVn = pag.makeLocalVarNode(prevAn.getNewExpr(), prevAn.getType(), method);
		mpag.addInternalEdge(prevAn, prevVn);
		setResult(prevAn);
		Type t;
		while (true) {
			t = type.getElementType();
			if (!(t instanceof ArrayType))
				break;
			type = (ArrayType) t;
			Alloc_Node an = pag.makeAllocNode(new Pair<>(nmae, new Integer(type.numDimensions)), type, method);
			Var_Node vn = pag.makeLocalVarNode(an.getNewExpr(), an.getType(), method);
			mpag.addInternalEdge(an, vn);
			mpag.addInternalEdge(vn, pag.makeFieldRefNode(prevVn, ArrayElement.v()));
			prevAn = an;
			prevVn = vn;
		}
	}

	@Override
	final public void caseParameterRef(ParameterRef pr) {
		setResult(caseParm(pr.getIndex()));
	}

	@Override
	final public void caseStaticFieldRef(StaticFieldRef sfr) {
		SootField field = sfr.getField();
		Type type = field.getType();
		setResult(pag.makeGlobalVarNode(field, type));
	}

	@Override
	final public void caseThisRef(ThisRef tr) {
		setResult(caseThis());
	}

	@Override
	final public void caseNullConstant(NullConstant nr) {
		setResult(null);
	}
	
	@Override
	final public void caseStringConstant(StringConstant sc) {
		Alloc_Node stringConstantNode = pag.makeStringConstantNode(sc);
		Var_Node stringConstantVar = pag.makeGlobalVarNode(sc, RefType.v("java.lang.String"));
		pag.GlobalNodeFactory().addParameterizedGlobalPAGEdge(stringConstantNode, stringConstantVar);
		Var_Node vn = pag.makeLocalVarNode(new Pair<>(method, sc),RefType.v("java.lang.String"), method);
		mpag.addInternalEdge(stringConstantVar, vn);
		setResult(vn);
	}
	@Override
	final public void caseClassConstant(ClassConstant cc) {
		Alloc_Node classConstant = pag.makeClassConstantNode(cc);
		Var_Node classConstantVar = pag.makeGlobalVarNode(cc, RefType.v("java.lang.Class"));
		pag.GlobalNodeFactory().addParameterizedGlobalPAGEdge(classConstant, classConstantVar);
		Var_Node vn = pag.makeLocalVarNode(new Pair<>(method, cc),RefType.v("java.lang.Class"), method);
		mpag.addInternalEdge(classConstantVar, vn);
		setResult(vn);
		
//		//TODO
//		if(PTAOptions.clinit==PTAOptions.ONFLY)
//			addToClinits(cc..getDeclaringClass());
	}

	@Override
	final public void defaultCase(Object v) {
		throw new RuntimeException("failed to handle " + v);
	}

	protected PAG pag;
	protected MethodPAG mpag;
	protected SootMethod method;
}
