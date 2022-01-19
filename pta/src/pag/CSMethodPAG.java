package pag;

import pag.builder.MtdPAGBuilder;
import pag.node.GNode;
import soot.SootMethod;
import soot.Trap;
import soot.Value;
import soot.jimple.Stmt;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;

import java.util.*;

public class CSMethodPAG {

    protected PAG pag;
    SootMethod method;
    private final ChunkedQueue<GNode> internalEdges = new ChunkedQueue<GNode>();
    private final QueueReader<GNode> internalReader = internalEdges.reader();
    private Collection<Stmt> stmts;
    public Collection<Stmt> invokeStmts = new HashSet<>();
    public Map<Stmt, Value> callToThrow = new HashMap<>();
    public Map<Value, List<Trap>> throwToCatch = new HashMap<>();

    public Set<SootMethod> clinits = new HashSet<>();
    protected MtdPAGBuilder nodeFactory;


}
