package driver;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.Body;
import soot.MethodSource;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.JimpleBody;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;

public class FakeMainFactory {
	private static FakeMainFactory v;
	public static SootMethod getFakeMain() {
		return v.FakeMain;
	}
	public static SootMethod makeFakeMain() {
		v = new FakeMainFactory();
		v.addEntries();
		return v.FakeMain;
	}
	public static int clinitsSize() {
		return v.clinits.size();
	}
	public static Collection<Stmt> modifyFakeMain(Collection<SootMethod> clinits){
		Collection<Stmt> added = new HashSet<>();
		clinits.removeAll(v.clinits);
		v.clinits.addAll(clinits);
		for(SootMethod entry: clinits){
			JInvokeStmt stmt = getInvokeStmt(entry);
			added.add(stmt);
		}
		return added;
	}
	private static JInvokeStmt getInvokeStmt(SootMethod method, Value... args){
		SootMethodRef methodRef = method.makeRef();
		List<Value> argsL = Arrays.asList(args);
		return new JInvokeStmt(new JStaticInvokeExpr(methodRef,argsL));
	}
	
	private SootMethod FakeMain;
	public Set<SootMethod> clinits = new HashSet<>();;
	private PatchingChain<Unit> units;
	private int localStart = 0;
	private FakeMainFactory(){
		SootClass fakeClass  = new SootClass("FakeMain");
		fakeClass.setResolvingLevel(SootClass.BODIES);
		SootMethod fakeMain = new SootMethod("fakeMain", null, VoidType.v());
		fakeMain.setModifiers(Modifier.STATIC);
		fakeClass.addMethod(fakeMain);
		fakeMain.setSource(new MethodSource() {
			@Override
			public Body getBody(SootMethod m, String phaseName) {
				return new JimpleBody(fakeMain);
			}
		});
		Body body = fakeMain.retrieveActiveBody();
		units = body.getUnits();
		FakeMain = fakeMain;
	}
	private void addEntries() {
		for(SootMethod entry: SootUtils.getEntryPoints()){
			if(PTAOptions.hack) {
				if(entry.isStatic()) {
					if(!clinits.add(entry))
						continue;
					addInvokeStmt(entry);
				}else {
					Value baseValue = new JimpleLocal("dummyLocal", RefType.v("java.lang.Object"));
					addInvokeStmt(baseValue, entry);
				}
			}else {
				if(!clinits.add(entry))
					continue;
				switch (entry.getSignature()) {
				case "<java.lang.ClassLoader: long findNative(java.lang.ClassLoader,java.lang.String)>":
					addInvokeStmt(entry, getDefaultClassLoader(), NullConstant.v());
					break;
				case "<java.lang.ClassLoader: void <init>()>":
					addInvokeStmt(getDefaultClassLoader(), entry);
					break;
				case "<java.lang.ClassLoader: java.lang.Class loadClassInternal(java.lang.String)>":
				case "<java.lang.ClassLoader: void addClass(java.lang.Class)>":
					addInvokeStmt(getDefaultClassLoader(), entry, NullConstant.v());
					break;
				case "<java.lang.ClassLoader: void checkPackageAccess(java.lang.Class,java.security.ProtectionDomain)>":
					addInvokeStmt(getDefaultClassLoader(), entry, NullConstant.v(), NullConstant.v());
					break;
				case "<java.lang.Thread: void exit()>":
					addInvokeStmt(getMainThread(), entry);
					break;
				case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.Runnable)>":
				case "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>":
					addInvokeStmt(getMainThread(), entry, getMainThreadGroup(), NullConstant.v());
					break;
				case "<java.lang.ThreadGroup: void <init>()>":
					addInvokeStmt(getMainThreadGroup(), entry);
					break;
				case "<java.lang.ThreadGroup: void uncaughtException(java.lang.Thread,java.lang.Throwable)>":
					addInvokeStmt(getMainThreadGroup(), entry, getMainThread(), NullConstant.v());
					break;
				case "<java.lang.ref.Finalizer: void runFinalizer()>":
					addInvokeStmt(getFinalizer(), entry);
					break;
				case "<java.security.PrivilegedActionException: void <init>(java.lang.Exception)>":
					addInvokeStmt(getPrivilegedActionException(), entry, getThrow());
					break;
				default:
					addInvokeStmt(entry);
					break;
				}
			}
		}
	}
	
	Value defaultClassLoader;
	private Value getDefaultClassLoader() {
		if(defaultClassLoader==null)
			defaultClassLoader = getNew(RefType.v("java.lang.ClassLoader")); //should be ?extends type but cannot impl here
		return defaultClassLoader;
	}
	Value mainTread;
	private Value getMainThread() {
		if(mainTread==null)
			mainTread = getNew(RefType.v("java.lang.Thread"));
		return mainTread;
	}
	Value mainTreadGroup;
	private Value getMainThreadGroup() {
		if(mainTreadGroup==null)
			mainTreadGroup = getNew(RefType.v("java.lang.ThreadGroup"));
		return mainTreadGroup;
	}
	Value finalizer;
	private Value getFinalizer() {
		if(finalizer==null)
			finalizer = getNew(RefType.v("java.lang.ref.Finalizer"));
		return finalizer;
	}
	Value privilegedActionException;
	private Value getPrivilegedActionException() {
		if(privilegedActionException==null)
			privilegedActionException = getNew(RefType.v("java.security.PrivilegedActionException")); //may be ?extends type but cannot impl here
		return privilegedActionException;
	}
	Value globalThrow;
	private Value getThrow() {
		if(globalThrow==null)
			globalThrow = getNew(RefType.v("java.lang.Throwable"));
		return globalThrow;
	}
	protected Value getNextLocal(Type type) {
        return getLocal(type, localStart++);
    }

    private Value getLocal(Type type, int index) {
    	return new JimpleLocal("r"+index, type);
    }
    protected void addAssign(Value lValue, Value rValue) {
        units.add(new JAssignStmt(lValue, rValue));
    }
    protected Value getNew(RefType type) {
        Value newExpr = new JNewExpr(type);
        Value local = getNextLocal(type);
        addAssign(local, newExpr);
        return local;
    }
	private void addInvokeStmt(Value receiver, SootMethod method, Value... args){
		SootMethodRef methodRef = method.makeRef();
		List<Value> argsL = Arrays.asList(args);
		Value invoke = methodRef.declaringClass().isInterface()?new JInterfaceInvokeExpr(receiver,methodRef,argsL):new JVirtualInvokeExpr(receiver,methodRef,argsL);
		units.add(new JInvokeStmt(invoke));
	}
	private void addInvokeStmt(SootMethod method, Value... args){
		units.add(getInvokeStmt(method,args));
	}
}
