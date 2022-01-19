/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package reflection;

import java.util.HashSet;

import soot.MethodOrMethodContext;
import soot.PhaseOptions;
import soot.jimple.Stmt;
import soot.options.CGOptions;

public class DefaultReflectionModel extends ReflectionModel {

	protected CGOptions options = new CGOptions(PhaseOptions.v().getPhaseOptions("cg"));

	protected HashSet<MethodOrMethodContext> warnedAlready = new HashSet<MethodOrMethodContext>();

	public void classForName(MethodOrMethodContext source, Stmt s) {
		/*
		 * List<Local> stringConstants = (List<Local>)
		 * methodToStringConstants.get(source); if( stringConstants == null )
		 * methodToStringConstants.put(source, stringConstants = new
		 * ArrayList<Local>()); InvokeExpr ie = s.getInvokeExpr(); Value
		 * className = ie.getArg(0); if( className instanceof StringConstant ) {
		 * String cls = ((StringConstant) className ).value; constantForName(
		 * cls, source, s); } else { Local constant = (Local) className; if(
		 * options.safe_forname() ) { for (SootMethod tgt :
		 * EntryPoints.v().clinits()) {
		 * System.out.println("Adding clinit call for safety: " + tgt); addEdge(
		 * source, s, tgt, Kind.CLINIT ); } } else { for (SootClass cls :
		 * Scene.v().dynamicClasses()) { for (SootMethod clinit :
		 * EntryPoints.v().clinitsOf(cls)) { addEdge( source, s, clinit,
		 * Kind.CLINIT); System.out.println("Adding clinit call for dynamic?: "
		 * + clinit); } } VirtualCallSite site = new VirtualCallSite( s, source,
		 * null, null, Kind.CLINIT ); List<VirtualCallSite> sites =
		 * stringConstToSites.get(constant); if (sites == null) {
		 * stringConstToSites.put(constant, sites = new
		 * ArrayList<VirtualCallSite>()); stringConstants.add(constant); }
		 * sites.add(site); } }
		 */
	}

	public void classNewInstance(MethodOrMethodContext source, Stmt s) {
		/*
		 * if( options.safe_newinstance() ) { for (SootMethod tgt :
		 * EntryPoints.v().inits()) { addEdge( source, s, tgt, Kind.NEWINSTANCE
		 * ); } } else { for (SootClass cls : Scene.v().dynamicClasses()) { if(
		 * cls.declaresMethod(sigInit) ) { addEdge( source, s,
		 * cls.getMethod(sigInit), Kind.NEWINSTANCE ); } }
		 * 
		 * if( options.verbose() ) { G.v().out.println(
		 * "Warning: Method "+source+
		 * " is reachable, and calls Class.newInstance;"+
		 * " graph will be incomplete!"+
		 * " Use safe-newinstance option for a conservative result." ); } }
		 */
	}

	public void contructorNewInstance(MethodOrMethodContext source, Stmt s) {
		/*
		 * if( options.safe_newinstance() ) { for (SootMethod tgt :
		 * EntryPoints.v().allInits()) { addEdge( source, s, tgt,
		 * Kind.NEWINSTANCE ); } } else { for (SootClass cls :
		 * Scene.v().dynamicClasses()) { for(SootMethod m: cls.getMethods()) {
		 * if(m.getName().equals("<init>")) { addEdge( source, s, m,
		 * Kind.NEWINSTANCE ); } } } if( options.verbose() ) {
		 * G.v().out.println( "Warning: Method "+source+
		 * " is reachable, and calls Constructor.newInstance;"+
		 * " graph will be incomplete!"+
		 * " Use safe-newinstance option for a conservative result." ); } }
		 */
	}

	public void methodInvoke(MethodOrMethodContext container, Stmt invokeStmt) {
		/*
		 * if( !warnedAlready(container) ) { if( options.verbose() ) {
		 * G.v().out.println( "Warning: call to "+
		 * "java.lang.reflect.Method: invoke() from "+container+
		 * "; graph will be incomplete!" ); } markWarned(container); }
		 */
	}
	/*
	 * private void markWarned(MethodContext m) { warnedAlready.add(m); }
	 * 
	 * private boolean warnedAlready(MethodContext m) { return
	 * warnedAlready.contains(m); }
	 */
}
